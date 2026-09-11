package com.orchords.orchordsai.utils

/**
 * Incremental visible-prose segmenter for ordinary chat TTS.
 *
 * It never emits fenced/inline-code content, waits for punctuation/paragraph/size boundaries,
 * and keeps incomplete trailing prose buffered until more text or terminal completion arrives.
 */
class StreamingSpeakableSegmenter(
    private val maxSegmentChars: Int = 240,
) {
    init {
        require(maxSegmentChars in 40..2_000)
    }

    private val visible = StringBuilder()
    private var pendingBackticks = 0
    private var inFence = false
    private var inInlineCode = false
    private var previousWasNewline = false

    fun append(delta: String, isFinal: Boolean = false): List<String> {
        val emitted = mutableListOf<String>()

        fun emitIfUseful() {
            val cleaned = visible.toString().stripMarkdown().trim()
            visible.clear()
            if (cleaned.isNotEmpty()) emitted += cleaned
        }

        fun flushBackticks(nextIsContent: Boolean) {
            if (pendingBackticks == 0) return
            when {
                pendingBackticks >= 3 -> {
                    if (!inFence) emitIfUseful()
                    inFence = !inFence
                    inInlineCode = false
                }
                pendingBackticks == 1 && !inFence -> inInlineCode = !inInlineCode
                !inFence && !inInlineCode && nextIsContent -> repeat(pendingBackticks) { visible.append('`') }
            }
            pendingBackticks = 0
        }

        delta.forEach { ch ->
            if (ch == '`') {
                pendingBackticks++
                return@forEach
            }

            flushBackticks(nextIsContent = true)
            if (inFence || inInlineCode) {
                previousWasNewline = ch == '\n'
                return@forEach
            }

            visible.append(ch)
            val paragraphBoundary = ch == '\n' && previousWasNewline
            val sentenceBoundary = ch in SENTENCE_ENDINGS
            val sizeBoundary = visible.length >= maxSegmentChars && ch.isWhitespace()
            if (paragraphBoundary || sentenceBoundary || sizeBoundary) emitIfUseful()
            previousWasNewline = ch == '\n'
        }

        if (isFinal) {
            flushBackticks(nextIsContent = false)
            emitIfUseful()
        }

        return emitted
    }

    fun reset() {
        visible.clear()
        pendingBackticks = 0
        inFence = false
        inInlineCode = false
        previousWasNewline = false
    }

    private companion object {
        val SENTENCE_ENDINGS = setOf('.', '!', '?', '。', '！', '？')
    }
}
