package com.orchords.orchordsai.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTtsAutoPlayStateTest {
    @Test
    fun `streams only stable appended speech for one message revision`() {
        val state = StreamingTtsAutoPlayState(maxSegmentChars = 80)

        assertTrue(state.update("m1", "Hello world").segments.isEmpty())
        val first = state.update("m1", "Hello world. Next")
        assertEquals(listOf("Hello world."), first.segments)
        assertTrue(first.flushFirstSegment)

        val second = state.update("m1", "Hello world. Next sentence!")
        assertEquals(listOf("Next sentence!"), second.segments)
        assertFalse(second.flushFirstSegment)
    }

    @Test
    fun `rewrite of same message id resets queued old revision speech`() {
        val state = StreamingTtsAutoPlayState(maxSegmentChars = 80)
        state.update("m1", "Old answer. Tail")

        val replacement = state.update("m1", "Replacement answer.")

        assertTrue(replacement.resetPlayback)
        assertTrue(replacement.flushFirstSegment)
        assertEquals(listOf("Replacement answer."), replacement.segments)
    }

    @Test
    fun `terminal completion flushes only remaining tail and cannot replay on late state update`() {
        val state = StreamingTtsAutoPlayState(maxSegmentChars = 80)
        state.update("m1", "First sentence. Final tail")

        val completed = state.update("m1", "First sentence. Final tail", isFinal = true)
        assertEquals(listOf("Final tail"), completed.segments)
        assertFalse(completed.flushFirstSegment)

        val late = state.update("m1", "First sentence. Final tail")
        assertTrue(late.segments.isEmpty())
        assertFalse(late.resetPlayback)
    }

    @Test
    fun `new message never inherits buffered prior revision text`() {
        val state = StreamingTtsAutoPlayState(maxSegmentChars = 80)
        state.update("old", "Old buffered tail")

        val next = state.update("new", "New answer.")

        assertTrue(next.resetPlayback)
        assertEquals(listOf("New answer."), next.segments)
        assertTrue(next.flushFirstSegment)
    }
}
