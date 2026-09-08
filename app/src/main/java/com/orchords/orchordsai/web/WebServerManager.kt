package com.orchords.orchordsai.web

import android.content.Context
import android.util.Log
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.orchords.orchordsai.AppScope
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.repository.ConversationRepository
import com.orchords.orchordsai.data.repository.FolderRepository
import com.orchords.orchordsai.service.ChatService
import com.orchords.orchordsai.web.startWebServer
import java.net.ServerSocket

private const val TAG = "WebServerManager"
internal const val HOST_ALL_INTERFACES = "0.0.0.0"
internal const val HOST_LOOPBACK = "127.0.0.1"

/**
 * Pure address-selection policy used by [WebServerManager] when populating
 * [WebServerState.address]. Returns the literal address that the embedded
 * server should advertise to the UI as its LAN/host address:
 *
 *  * loopback (`127.0.0.1`) when the user asked for localhost-only;
 *  * the first non-loopback, up, non-virtual IPv4 interface when available;
 *  * the all-interfaces sentinel (`0.0.0.0`) when no LAN interface is reachable.
 *
 * The function never throws and never returns `null`, so the UI can render the
 * URL string unconditionally while the embedded Ktor listener binds to the
 * corresponding `host` parameter.
 */
internal fun selectWebServerAddress(
    localhostOnly: Boolean,
    networkInterfaces: List<NetworkInterfaceSnapshot> = currentNetworkInterfaces()
): String {
    if (localhostOnly) return HOST_LOOPBACK
    val lan = networkInterfaces.firstOrNull { iface ->
        !iface.isLoopback && iface.isUp && !iface.isVirtual && iface.ipv4 != null
    }
    return lan?.ipv4 ?: HOST_ALL_INTERFACES
}

internal data class NetworkInterfaceSnapshot(
    val name: String,
    val isLoopback: Boolean,
    val isUp: Boolean,
    val isVirtual: Boolean,
    val ipv4: String?
)

private fun currentNetworkInterfaces(): List<NetworkInterfaceSnapshot> {
    val found = runCatching { java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList() }
    return found.getOrDefault(emptyList()).map { nic ->
        val ipv4 = nic.inetAddresses?.toList()?.firstOrNull { addr ->
            addr is java.net.Inet4Address && !addr.isAnyLocalAddress
        }?.hostAddress
        NetworkInterfaceSnapshot(
            name = nic.name ?: "",
            isLoopback = runCatching { nic.isLoopback }.getOrDefault(false),
            isUp = runCatching { nic.isUp }.getOrDefault(false),
            isVirtual = runCatching { nic.isVirtual }.getOrDefault(false),
            ipv4 = ipv4,
        )
    }
}

data class WebServerState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val port: Int = 8080,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val localhostOnly: Boolean = false,
    val hostname: String? = null,
    val address: String? = null,
    val error: String? = null
)

class WebServerManager(
    private val context: Context,
    private val appScope: AppScope,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val settingsStore: SettingsStore,
    private val filesManager: FilesManager
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val nsdRegistrar = NsdServiceRegistrar(context)

    /**
     * Guard for [stop]. When the foreground service that owns this server is
     * destroyed through any path other than the explicit `ACTION_STOP` we still
     * need to tear down the Ktor listener and NSD registration. But a second
     * stop call before the first finishes must not flip state, must not relaunch
     * the listener, and must not surface a stale "stopping" UI for an already-idle
     * server — `restart()` chains `stop() -> start()` and a redundant stop after
     * `start()` would have to be a no-op as well.
     */
    private val shutdownGate = ShutdownGate()

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    fun start(
        port: Int = 8080,
        serviceName: String = DEFAULT_SERVICE_NAME,
        localhostOnly: Boolean = false
    ) {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

        appScope.launch {
            val host = if (localhostOnly) HOST_LOOPBACK else HOST_ALL_INTERFACES
            val address = selectWebServerAddress(localhostOnly)
            val baseState = WebServerState(
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
                address = address,
            )
            try {
                _state.value = _state.value.copy(isLoading = true)
                Log.i(TAG, "Starting web server on $host:$port (address=$address)")
                if (!isPortAvailable(port)) {
                    Log.w(TAG, "Port $port is already in use")
                    _state.value = baseState.copy(error = "Port $port is already in use")
                    return@launch
                }
                server = startWebServer(port = port, host = host) {
                    configureWebApi(context, chatService, conversationRepo, folderRepo, settingsStore, filesManager)
                }.start(wait = false)

                _state.value = baseState.copy(isRunning = true)
                if (!localhostOnly) {
                    runCatching {
                        nsdRegistrar.register(
                            port = port,
                            serviceName = serviceName,
                            onRegistered = { info ->
                                val nsdAddress = info.address.hostAddress
                                _state.value = _state.value.copy(
                                    serviceName = info.serviceName,
                                    hostname = info.hostname,
                                    address = if (nsdAddress.isNullOrBlank() || nsdAddress == "0.0.0.0") {
                                        _state.value.address ?: address
                                    } else {
                                        nsdAddress
                                    }
                                )
                            }
                        )
                    }.onFailure {
                        Log.w(TAG, "NSD register failed", it)
                    }
                }
                Log.i(TAG, "Web server started successfully on $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start web server", e)
                _state.value = baseState.copy(error = e.message)
            }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(isRunning = false, isLoading = false, error = message)
    }

    fun stop() {
        // Idempotent: an already-idle server (or a shutdown already in flight from
        // a previous stop call) returns immediately so the WebServerService.onDestroy()
        // path cannot leave the UI in a stale "stopping" state.
        if (server == null && !shutdownGate.isInFlight()) return
        if (!shutdownGate.enter()) return
        _state.value =
            _state.value.copy(isRunning = false, isLoading = true, hostname = null, address = null, error = null)
        // Ktor's CIO EmbeddedServer.stop() calls runBlocking internally for up to
        // gracePeriodMillis + timeoutMillis. Run it on Dispatchers.IO so neither
        // the foreground-service destruction path nor any UI-thread caller pays
        // that blocking cost on the main thread.
        appScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Stopping web server")
                server?.stop(1000, 2000)
                server = null
                runCatching {
                    nsdRegistrar.unregister()
                }.onFailure {
                    Log.w(TAG, "NSD unregister failed", it)
                }
                _state.value = _state.value.copy(isLoading = false)
                Log.i(TAG, "Web server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop web server", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            } finally {
                shutdownGate.exit()
            }
        }
    }

    /**
     * Synchronously stop the server. Used by restart() to fence start() behind
     * the previous listener being fully torn down. Returns true if the server
     * is fully stopped by the time this method returns.
     */
    private fun stopBlocking(): Boolean {
        // Idempotent: an already-idle server returns true immediately.
        if (server == null && !shutdownGate.isInFlight()) return true
        if (!shutdownGate.enter()) {
            // Another caller already owns the shutdown. Wait for it.
            return runBlocking(Dispatchers.IO) {
                while (shutdownGate.isInFlight()) {
                    kotlinx.coroutines.delay(10)
                }
                server == null
            }
        }
        _state.value =
            _state.value.copy(isRunning = false, isLoading = true, hostname = null, address = null, error = null)
        return try {
            runBlocking(Dispatchers.IO) {
                Log.i(TAG, "Stopping web server (synchronous)")
                server?.stop(1000, 2000)
                server = null
                runCatching {
                    nsdRegistrar.unregister()
                }.onFailure {
                    Log.w(TAG, "NSD unregister failed", it)
                }
                _state.value = _state.value.copy(isLoading = false)
                Log.i(TAG, "Web server stopped (synchronous)")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop web server (synchronous)", e)
            _state.value = _state.value.copy(isLoading = false, error = e.message)
            false
        } finally {
            shutdownGate.exit()
        }
    }

    fun restart(
        port: Int = _state.value.port,
        serviceName: String = _state.value.serviceName,
        localhostOnly: Boolean = _state.value.localhostOnly
    ) {
        // Restart must serialize stop() -> start(); otherwise the new server's
        // port-availability probe runs while the previous Ktor listener still
        // owns the socket, so port-restart appears to "do nothing".
        if (!stopBlocking()) {
            Log.w(TAG, "Restart aborted: previous shutdown failed")
            return
        }
        start(port, serviceName, localhostOnly)
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
