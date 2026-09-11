package com.orchords.orchordsai.web

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServerLifecycleQueueTest {
    private class PausedDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.add(block)
        }

        fun drain() {
            var remaining = 100_000
            while (true) {
                val task = tasks.poll() ?: return
                check(remaining-- > 0) { "Lifecycle queue did not become idle" }
                task.run()
            }
        }
    }

    private class Fixture {
        val dispatcher = PausedDispatcher()
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> errors.add(error) }
        )
        val queue = WebServerLifecycleQueue(scope, dispatcher)
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            test(fixture)
        } finally {
            fixture.scope.cancel()
            fixture.dispatcher.drain()
        }
    }

    @Test
    fun `operation is dispatched even when owner uses the same dispatcher`() = fixture { f ->
        var ran = false
        val job = f.queue.submit { ran = true }
        assertFalse(ran)
        assertFalse(job.isCompleted)
        f.dispatcher.drain()
        assertTrue(ran)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `stop queued before startup dispatch still executes after startup`() = fixture { f ->
        val events = mutableListOf<String>()
        var running = false
        f.queue.submit { running = true; events.add("start") }
        f.queue.submit { if (running) { running = false; events.add("stop") } }
        assertEquals(emptyList<String>(), events)
        f.dispatcher.drain()
        assertEquals(listOf("start", "stop"), events)
        assertFalse(running)
    }

    @Test
    fun `suspended startup holds later stop until listener ownership is established`() = fixture { f ->
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        f.queue.submit { events.add("starting"); release.await(); events.add("started") }
        f.queue.submit { events.add("stopped") }
        f.dispatcher.drain()
        assertEquals(listOf("starting"), events)
        release.complete(Unit)
        f.dispatcher.drain()
        assertEquals(listOf("starting", "started", "stopped"), events)
    }

    @Test
    fun `duplicate stop decisions are made against serialized state`() = fixture { f ->
        var running = false
        var closes = 0
        f.queue.submit { running = true }
        repeat(100) {
            f.queue.submit { if (running) { closes++; running = false } }
        }
        f.dispatcher.drain()
        assertEquals(1, closes)
        assertFalse(running)
    }

    @Test
    fun `restart closes and unregisters before replacement and later commands`() = fixture { f ->
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        f.queue.submit {
            events.add("close")
            release.await()
            events.add("unregister")
            events.add("replacement")
        }
        f.queue.submit { events.add("later-start") }
        f.dispatcher.drain()
        assertEquals(listOf("close"), events)
        release.complete(Unit)
        f.dispatcher.drain()
        assertEquals(listOf("close", "unregister", "replacement", "later-start"), events)
    }

    @Test
    fun `failed operation releases the lock for a later cleanup retry`() = fixture { f ->
        var retried = false
        val failed = f.queue.submit { error("expected failure") }
        f.queue.submit { retried = true }
        f.dispatcher.drain()
        assertTrue(failed.isCancelled)
        assertEquals("expected failure", f.errors.single().message)
        assertTrue(retried)
    }

    @Test
    fun `cancelled waiter cannot block or execute ahead of remaining operations`() = fixture { f ->
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        f.queue.submit { release.await(); events.add("first") }
        val cancelled = f.queue.submit { events.add("cancelled") }
        f.queue.submit { events.add("last") }
        f.dispatcher.drain()
        cancelled.cancel()
        release.complete(Unit)
        f.dispatcher.drain()
        assertEquals(listOf("first", "last"), events)
    }

    @Test
    fun `one thousand queued operations preserve submission order`() = fixture { f ->
        val actual = mutableListOf<Int>()
        val jobs = (0 until 1_000).map { value -> f.queue.submit { actual.add(value) } }
        f.dispatcher.drain()
        assertEquals((0 until 1_000).toList(), actual)
        assertTrue(jobs.all { it.isCompleted })
        assertTrue(f.errors.isEmpty())
    }
}
