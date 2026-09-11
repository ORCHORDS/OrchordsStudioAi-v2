package com.orchords.orchordsai.ui.pages.chat

import com.orchords.orchordsai.utils.StreamingSpeakableSegmenter

internal data class StreamingTtsUpdate(
    val segments: List<String>,
    val resetPlayback: Boolean,
    val flushFirstSegment: Boolean,
)

/**
 * Tracks one streamed assistant-message revision and emits only newly stabilized visible speech.
 * A message switch or non-prefix rewrite is treated as a new revision and resets queued playback.
 */
internal class StreamingTtsAutoPlayState(
    maxSegmentChars: Int = 240,
) {
    private val segmenter = StreamingSpeakableSegmenter(maxSegmentChars)
    private var activeMessageId: String? = null
    private var lastVisibleText: String = ""
    private var needsFlush = true

    fun update(
        messageId: String,
        visibleText: String,
        isFinal: Boolean = false,
    ): StreamingTtsUpdate {
        val resetPlayback = activeMessageId != messageId || !visibleText.startsWith(lastVisibleText)
        if (resetPlayback) {
            segmenter.reset()
            activeMessageId = messageId
            lastVisibleText = ""
            needsFlush = true
        }

        val delta = visibleText.substring(lastVisibleText.length)
        lastVisibleText = visibleText
        val segments = segmenter.append(delta, isFinal)
        val flushFirstSegment = needsFlush && segments.isNotEmpty()
        if (segments.isNotEmpty()) needsFlush = false

        if (isFinal) {
            // Preserve the final visible-text snapshot so a late Compose state update for the same
            // revision cannot replay the whole answer after the terminal flush.
            segmenter.reset()
        }

        return StreamingTtsUpdate(
            segments = segments,
            resetPlayback = resetPlayback,
            flushFirstSegment = flushFirstSegment,
        )
    }

    fun clear(): Boolean {
        val hadActiveRevision = activeMessageId != null || lastVisibleText.isNotEmpty()
        activeMessageId = null
        lastVisibleText = ""
        segmenter.reset()
        needsFlush = true
        return hadActiveRevision
    }
}
