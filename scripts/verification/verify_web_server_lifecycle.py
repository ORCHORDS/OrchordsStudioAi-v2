#!/usr/bin/env python3
"""Run #346 host regressions against the real WebServerManager source.

Android, Ktor and NSD collaborators are controlled test doubles; sockets are
real. This proves manager ordering/error handling, not Android instrumentation
or real Ktor/JmDNS compatibility. No repository source is rewritten for testing.
Requires a JDK, kotlinc, and a coroutines-core JVM jar (bundled Kotlin jar is the
default; --coroutines-jar can select the repository-pinned dependency).
"""
from __future__ import annotations

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

FIXTURES = {
    'Android.kt': r'''package android.content
open class Context
''',
    'App.kt': r'''package com.orchords.orchordsai
import kotlinx.coroutines.*
class AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}
''',
    'CIO.kt': r'''package io.ktor.server.cio
class CIOApplicationEngine { class Configuration }
''',
    'Chat.kt': r'''package com.orchords.orchordsai.service
class ChatService
''',
    'Data.kt': r'''package com.orchords.orchordsai.data.datastore
class SettingsStore
''',
    'Engine.kt': r'''package io.ktor.server.engine
import com.orchords.orchordsai.web.Fixture
import java.net.InetAddress
import java.net.ServerSocket
import java.io.IOException
import java.util.concurrent.TimeUnit
class EmbeddedServer<E, C>(private val port: Int, private val host: String) {
    private var socket: ServerSocket? = null
    fun start(wait: Boolean): EmbeddedServer<E, C> {
        Fixture.startEntered.countDown()
        check(Fixture.startRelease.await(5, TimeUnit.SECONDS)) { "fixture start timed out" }
        socket = ServerSocket(port, 50, InetAddress.getByName(host))
        Fixture.starts.incrementAndGet()
        return this
    }
    fun stop(gracePeriod: Long, timeout: Long) {
        Fixture.stopEntered.countDown()
        check(Fixture.stopRelease.await(5, TimeUnit.SECONDS)) { "fixture stop timed out" }
        if (Fixture.failStop.compareAndSet(true, false)) throw IOException("injected stop failure")
        closeSocket()
        Fixture.stops.incrementAndGet()
    }
    fun closeSocket() { socket?.close(); socket = null }
}
''',
    'Files.kt': r'''package com.orchords.orchordsai.data.files
class FilesManager
''',
    'LifecycleHarness.kt': r'''package com.orchords.orchordsai.web

import android.content.Context
import com.orchords.orchordsai.AppScope
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.repository.ConversationRepository
import com.orchords.orchordsai.data.repository.FolderRepository
import com.orchords.orchordsai.service.ChatService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun await(latch: CountDownLatch) = check(latch.await(3, TimeUnit.SECONDS)) { "fixture latch timed out" }
private fun port() = ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { it.localPort }
private suspend fun WebServerManager.waitState(predicate: (WebServerState) -> Boolean) {
    check(withTimeoutOrNull(1200) { state.first(predicate) } != null) { "state condition not reached: ${state.value}" }
}
private fun scenario(block: suspend (WebServerManager, Int) -> Unit) = runBlocking {
    Fixture.reset()
    val scope = AppScope()
    val manager = WebServerManager(Context(), scope, ChatService(), ConversationRepository(), FolderRepository(), SettingsStore(), FilesManager())
    try { block(manager, port()) }
    finally {
        Fixture.startRelease.countDown(); Fixture.stopRelease.countDown()
        scope.coroutineContext[Job]!!.cancelAndJoin()
        Fixture.engines.forEach { it.closeSocket() }
    }
}

fun main(args: Array<String>) {
    val tests = linkedMapOf<String, () -> Unit>(
        "stop during unfinished start leaves no listener or NSD registration" to {
            scenario { manager, p ->
                Fixture.startRelease = CountDownLatch(1)
                manager.start(port = p, localhostOnly = false)
                await(Fixture.startEntered)
                manager.stop()
                Fixture.startRelease.countDown()
                manager.waitState { !it.isRunning && !it.isLoading && Fixture.stops.get() == 1 }
                check(Fixture.unregisters.get() == 1)
                ServerSocket(p).use { }
            }
        },
        "start during shutdown waits and rebinds the exact port" to {
            scenario { manager, p ->
                manager.start(port = p, localhostOnly = true)
                manager.waitState { it.isRunning }
                Fixture.stopRelease = CountDownLatch(1)
                manager.stop()
                await(Fixture.stopEntered)
                manager.start(port = p, localhostOnly = true)
                check(Fixture.starts.get() == 1)
                Fixture.stopRelease.countDown()
                manager.waitState { it.isRunning && Fixture.starts.get() == 2 }
                check(Fixture.stops.get() == 1)
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading }
            }
        },
        "duplicate stops close and unregister exactly once" to {
            scenario { manager, p ->
                manager.start(port = p, localhostOnly = false)
                manager.waitState { it.hostname != null }
                Fixture.stopRelease = CountDownLatch(1)
                manager.stop()
                await(Fixture.stopEntered)
                repeat(30) { manager.stop() }
                Fixture.stopRelease.countDown()
                // A queued subsequent start is also a barrier for all preceding stops.
                manager.start(port = p, localhostOnly = true)
                manager.waitState { it.isRunning && Fixture.starts.get() == 2 }
                check(Fixture.stops.get() == 1)
                check(Fixture.unregisters.get() == 1)
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading }
            }
        },
        "restart does not block the calling thread" to {
            scenario { manager, p ->
                manager.start(port = p, localhostOnly = true)
                manager.waitState { it.isRunning }
                Fixture.stopRelease = CountDownLatch(1)
                val returned = CountDownLatch(1)
                val caller = Thread { manager.restart(); returned.countDown() }.apply { start() }
                try {
                    check(returned.await(500, TimeUnit.MILLISECONDS)) { "restart blocked its caller" }
                    await(Fixture.stopEntered)
                } finally { Fixture.stopRelease.countDown(); caller.join(3000) }
                manager.waitState { it.isRunning && Fixture.starts.get() == 2 }
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading }
            }
        },
        "duplicate pending starts create only one listener" to {
            scenario { manager, p ->
                Fixture.startRelease = CountDownLatch(1)
                manager.start(port = p, localhostOnly = true)
                await(Fixture.startEntered)
                repeat(20) { manager.start(port = p, localhostOnly = true) }
                Fixture.startRelease.countDown()
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading && Fixture.stops.get() == 1 }
                check(Fixture.engines.size == 1) { "duplicate listeners were constructed" }
                ServerSocket(p).use { }
            }
        },
        "idle stop cannot consume the next start" to {
            scenario { manager, p ->
                repeat(10) { manager.stop() }
                manager.start(port = p, localhostOnly = true)
                manager.waitState { it.isRunning }
                check(Fixture.starts.get() == 1)
                check(Fixture.stops.get() == 0)
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading }
            }
        },
        "failed engine stop still attempts NSD cleanup and prevents replacement" to {
            scenario { manager, p ->
                manager.start(port = p, localhostOnly = false)
                manager.waitState { it.hostname != null }
                Fixture.failStop.set(true)
                manager.restart()
                manager.waitState { !it.isLoading && it.error != null }
                check(Fixture.starts.get() == 1)
                check(Fixture.unregisters.get() == 1) { "NSD cleanup skipped after stop failure" }
                manager.stop()
                manager.waitState { !it.isRunning && !it.isLoading && it.error == null }
                ServerSocket(p).use { }
            }
        }
    )
    var failures = 0
    tests.filterKeys { args.isEmpty() || it == args[0] }.forEach { (name, run) ->
        try { run(); println("PASS: $name") }
        catch (error: Throwable) { failures++; println("FAIL: $name: ${error.message}") }
    }
    check(failures == 0) { "$failures regression(s) failed" }
}
''',
    'Log.kt': r'''package android.util
object Log {
    fun i(tag: String, text: String): Int = 0
    fun w(tag: String, text: String, error: Throwable? = null): Int = 0
    fun e(tag: String, text: String, error: Throwable? = null): Int = 0
}
''',
    'Repos.kt': r'''package com.orchords.orchordsai.data.repository
class ConversationRepository
class FolderRepository
''',
    'Web.kt': r'''package com.orchords.orchordsai.web
import android.content.Context
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.repository.*
import com.orchords.orchordsai.service.ChatService
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
const val DEFAULT_SERVICE_NAME = "orchordsai"
object Fixture {
    var startEntered = CountDownLatch(1)
    var startRelease = CountDownLatch(0)
    var stopEntered = CountDownLatch(1)
    var stopRelease = CountDownLatch(0)
    val starts = AtomicInteger()
    val stops = AtomicInteger()
    val registers = AtomicInteger()
    val unregisters = AtomicInteger()
    val failStop = AtomicBoolean()
    val engines = CopyOnWriteArrayList<EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>>()
    fun reset() {
        engines.forEach { it.closeSocket() }; engines.clear()
        startEntered = CountDownLatch(1); startRelease = CountDownLatch(0)
        stopEntered = CountDownLatch(1); stopRelease = CountDownLatch(0)
        starts.set(0); stops.set(0); registers.set(0); unregisters.set(0); failStop.set(false)
    }
}
data class RegisteredServiceInfo(val serviceName: String, val hostname: String, val address: InetAddress)
class NsdServiceRegistrar(context: Context) {
    suspend fun register(port: Int, serviceName: String, onRegistered: (RegisteredServiceInfo) -> Unit) {
        Fixture.registers.incrementAndGet()
        onRegistered(RegisteredServiceInfo(serviceName, "$serviceName.local", InetAddress.getLoopbackAddress()))
    }
    suspend fun unregister() { Fixture.unregisters.incrementAndGet() }
}
fun startWebServer(port: Int, host: String, module: () -> Unit): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    module()
    return EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>(port, host).also { Fixture.engines.add(it) }
}
fun configureWebApi(context: Context, chat: ChatService, conversations: ConversationRepository,
    folders: FolderRepository, settings: SettingsStore, files: FilesManager) = Unit
''',
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--coroutines-jar", type=Path)
    parser.add_argument("--repeat", type=int, default=1)
    args = parser.parse_args()
    if not 1 <= args.repeat <= 100:
        parser.error("--repeat must be between 1 and 100")
    compiler = shutil.which("kotlinc")
    java = shutil.which("java")
    if not compiler or not java:
        parser.error("Install a JDK and Kotlin compiler (java and kotlinc must be on PATH)")
    jar = args.coroutines_jar or Path(compiler).resolve().parents[1] / "lib/kotlinx-coroutines-core-jvm.jar"
    if not jar.is_file():
        parser.error("Supply an existing coroutines-core JVM jar with --coroutines-jar")
    source = args.repo / "app/src/main/java/com/orchords/orchordsai/web"
    manager = source / "WebServerManager.kt"
    if not manager.is_file():
        parser.error(f"Missing production source: {manager}")
    sources = [manager]
    for name in ("WebServerLifecycleQueue.kt", "ShutdownGate.kt"):
        path = source / name
        if path.is_file():
            sources.append(path)
    print("Host manager tests: controlled Android/Ktor/NSD doubles; real TCP sockets", flush=True)
    subprocess.run([compiler, "-version"], check=True)
    with tempfile.TemporaryDirectory(prefix="orchords-web-lifecycle-") as temp:
        directory = Path(temp)
        for name, text in FIXTURES.items():
            path = directory / name
            path.write_text(text, encoding="utf-8")
            sources.append(path)
        output = directory / "regressions.jar"
        # compiler is resolved from the CI-controlled PATH and argv is executed with shell=False.
        subprocess.run(  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-tainted-env-args.dangerous-subprocess-use-tainted-env-args
            [compiler, *map(str, sources), "-nowarn", "-cp", str(jar), "-include-runtime", "-d", str(output)],
            check=True,
            timeout=120,
        )
        import os
        classpath = os.pathsep.join((str(output), str(jar.resolve())))
        for iteration in range(args.repeat):
            print(f"Iteration {iteration + 1}/{args.repeat}", flush=True)
            # java is resolved from the CI-controlled PATH and argv is executed with shell=False.
            subprocess.run(  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-tainted-env-args.dangerous-subprocess-use-tainted-env-args
                [java, "-cp", classpath, "com.orchords.orchordsai.web.LifecycleHarnessKt"],
                check=True,
                timeout=60,
            )


if __name__ == "__main__":
    main()
