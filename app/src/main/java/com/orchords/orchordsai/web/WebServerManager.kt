package com.orchords.orchordsai.web

import android.content.Context
import android.util.Log
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.orchords.orchordsai.AppScope
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.repository.ConversationRepository
import com.orchords.orchordsai.data.repository.FolderRepository
import com.orchords.orchordsai.service.ChatService
import com.orchords.orchordsai.web.startWebServer
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException

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

    // Application-owned: serviceScope cancellation must not cancel teardown.
    private val lifecycleQueue = WebServerLifecycleQueue(appScope)

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    fun start(
        port: Int = 8080,
        serviceName: String = DEFAULT_SERVICE_NAME,
        localhostOnly: Boolean = false
    ) {
        lifecycleQueue.submit { startLocked(port, serviceName, localhostOnly) }
    }

    /** Called only while lifecycleQueue owns the listener. */
    private suspend fun startLocked(port: Int, serviceName: String, localhostOnly: Boolean) {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

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
            val bindFailure = isAddressAvailable(host = host, port = port)
            if (bindFailure != null) {
                Log.w(TAG, "Cannot bind $host:$port: $bindFailure")
                _state.value = baseState.copy(error = "Cannot bind $host:$port: $bindFailure")
                return
            }
            val candidate = startWebServer(port = port, host = host) {
                configureWebApi(context, chatService, conversationRepo, folderRepo, settingsStore, filesManager)
            }
            // Retain ownership even if start throws after partially opening resources.
            server = candidate
            candidate.start(wait = false)

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
                    if (it is CancellationException) throw it
                    Log.w(TAG, "NSD register failed", it)
                }
            }
            Log.i(TAG, "Web server started successfully on $host:$port")
        } catch (e: Exception) {
            withContext(NonCancellable) { stopLocked() }
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to start web server", e)
            _state.value = baseState.copy(isRunning = server != null, error = e.message)
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(isRunning = false, isLoading = false, error = message)
    }

    fun stop() {
        // Always enqueue: server may still be null while an earlier start is queued.
        lifecycleQueue.submit { stopLocked() }
    }

    /** A failed close retains ownership and prevents a replacement bind. */
    private suspend fun stopLocked(keepLoading: Boolean = false): Boolean {
        val activeServer = server ?: return true
        _state.value =
            _state.value.copy(isRunning = false, isLoading = true, hostname = null, address = null, error = null)
        return try {
            try {
                activeServer.stop(1000, 2000)
                server = null
            } finally {
                // NSD cleanup must run even when Ktor shutdown throws.
                nsdRegistrar.unregister()
            }
            _state.value = _state.value.copy(isLoading = keepLoading)
            Log.i(TAG, "Web server stopped")
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to stop web server", e)
            _state.value = _state.value.copy(isRunning = server != null, isLoading = false, error = e.message)
            false
        }
    }

    fun restart(
        port: Int = _state.value.port,
        serviceName: String = _state.value.serviceName,
        localhostOnly: Boolean = _state.value.localhostOnly
    ) {
        // One queued operation: no caller-thread runBlocking, polling, or gap in
        // which another start could claim the old listener's socket/NSD identity.
        lifecycleQueue.submit {
            if (stopLocked(keepLoading = true)) {
                startLocked(port, serviceName, localhostOnly)
            } else {
                Log.w(TAG, "Restart aborted: previous shutdown failed")
            }
        }
    }

    /**
     * Probe whether [host]:[port] is bindable by attempting to bind a
     * throwaway `ServerSocket` on the *exact* address Ktor will use. Returns
     * `null` on success or a human-readable failure reason on bind failure.
     *
     * Unlike a wildcard probe, this checks the requested interface. This is
     * advisory only: closing the probe releases the port, so another listener
     * may bind before Ktor does. Only the real listener's bind can establish
     * readiness; this probe does not eliminate that check-then-bind race.
     */
    private fun isAddressAvailable(host: String, port: Int): String? = runCatching {
        val address = InetAddress.getByName(host)
        ServerSocket(port, 50, address).use { null }
    }.fold(
        onSuccess = { null },
        onFailure = { t ->
            when (t) {
                is BindException -> "${t.message ?: "address in use"}"
                is SocketException -> "${t.message ?: t.javaClass.simpleName}"
                else -> "address probe failed: ${t.message ?: t.javaClass.simpleName}"
            }
        },
    )
}
