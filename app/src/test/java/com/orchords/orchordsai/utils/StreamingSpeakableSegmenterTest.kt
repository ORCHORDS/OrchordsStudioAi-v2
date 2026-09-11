package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSpeakableSegmenterTest {
    @Test
    fun `emits stable visible prose and withholds incomplete tail`() {
        val segmenter = StreamingSpeakableSegmenter(maxSegmentChars = 80)

        assertEquals(emptyList<String>(), segmenter.append("Hello wor"))
        assertEquals(listOf("Hello world."), segmenter.append("ld. Next par"))
        assertEquals(listOf("Next paragraph"), segmenter.append("agraph", isFinal = true))
    }

    @Test
    fun `never emits fenced or inline code content`() {
        val segmenter = StreamingSpeakableSegmenter(maxSegmentChars = 80)

        val first = segmenter.append("Intro. ```kotlin\nval secret = 42")
        val second = segmenter.append("\nprintln(secret)\n``` After `x == y` done.", isFinal = true)

        assertEquals(listOf("Intro."), first)
        assertEquals(listOf("After  done."), second)
        assertTrue((first + second).none { it.contains("secret") || it.contains("x == y") })
    }

    @Test
    fun `split fence markers across deltas remain suppressed`() {
        val segmenter = StreamingSpeakableSegmenter(maxSegmentChars = 80)

        assertEquals(emptyList<String>(), segmenter.append("Before ``"))
        assertEquals(listOf("Before"), segmenter.append("`\nhidden.\n``"))
        assertEquals(listOf("After."), segmenter.append("` After.", isFinal = true))
    }

    @Test
    fun `size boundary bounds long visible prose without reordering`() {
        val segmenter = StreamingSpeakableSegmenter(maxSegmentChars = 40)
        val text = "one two three four five six seven eight nine ten eleven twelve"
        val emitted = segmenter.append(text, isFinal = true)

        assertTrue(emitted.size >= 2)
        assertEquals(text, emitted.joinToString(" "))
        assertTrue(emitted.all { it.length <= 45 })
    }

    @Test
    fun `reset discards buffered old revision text`() {
        val segmenter = StreamingSpeakableSegmenter(maxSegmentChars = 80)
        segmenter.append("old unfinished revision")
        segmenter.reset()

        assertEquals(listOf("new revision."), segmenter.append("new revision.", isFinal = true))
    }
}
