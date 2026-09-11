package com.orchords.orchordsai.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatComposerPresentationStateTest {
    @Test
    fun `presentation state follows explicit priority without mutating draft ownership`() {
        fun expect(expected: ChatComposerPresentationState, signals: ChatComposerSignals) {
            assertEquals(expected, resolveChatComposerPresentationState(signals))
        }

        expect(ChatComposerPresentationState.COMPACT_READING, ChatComposerSignals())
        expect(ChatComposerPresentationState.EXPANDED_COMPOSING, ChatComposerSignals(focused = true))
        expect(ChatComposerPresentationState.EXPANDED_DRAFT, ChatComposerSignals(hasDraftText = true))
        expect(ChatComposerPresentationState.EXPANDED_DRAFT, ChatComposerSignals(hasAttachments = true))
        expect(ChatComposerPresentationState.STREAMING_COMPACT, ChatComposerSignals(loading = true))
        expect(
            ChatComposerPresentationState.EXPANDED_DRAFT,
            ChatComposerSignals(loading = true, hasDraftText = true),
        )
        expect(ChatComposerPresentationState.VOICE_ACTIVE, ChatComposerSignals(voiceActive = true))
        expect(
            ChatComposerPresentationState.EDITING_MESSAGE,
            ChatComposerSignals(editing = true, loading = true),
        )
        expect(
            ChatComposerPresentationState.FULLSCREEN_EDITOR,
            ChatComposerSignals(fullscreen = true, voiceActive = true),
        )
        expect(
            ChatComposerPresentationState.EXPANDED_COMPOSING,
            ChatComposerSignals(explicitlyExpanded = true),
        )
    }
}
