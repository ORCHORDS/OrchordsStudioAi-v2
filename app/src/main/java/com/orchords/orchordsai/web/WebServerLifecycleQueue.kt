package com.orchords.orchordsai.web

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes listener start/stop/restart, including operations not yet dispatched. */
internal class WebServerLifecycleQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    fun submit(operation: suspend () -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        // Enter (or join the FIFO waiters) before returning to the caller. Launching
        // first on IO would let a later stop overtake an earlier queued start.
        mutex.withLock {
            // A normally dispatched child also works when scope already uses IO:
            // withContext(IO) alone could run inline after UNDISPATCHED entry.
            coroutineScope { async(dispatcher) { operation() }.await() }
        }
    }
}
