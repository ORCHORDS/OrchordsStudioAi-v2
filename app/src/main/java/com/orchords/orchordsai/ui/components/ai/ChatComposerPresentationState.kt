package com.orchords.orchordsai.ui.components.ai

enum class ChatComposerPresentationState {
    COMPACT_READING,
    EXPANDED_COMPOSING,
    EXPANDED_DRAFT,
    STREAMING_COMPACT,
    VOICE_ACTIVE,
    EDITING_MESSAGE,
    FULLSCREEN_EDITOR,
}

data class ChatComposerSignals(
    val focused: Boolean = false,
    val hasDraftText: Boolean = false,
    val hasAttachments: Boolean = false,
    val loading: Boolean = false,
    val voiceActive: Boolean = false,
    val editing: Boolean = false,
    val fullscreen: Boolean = false,
    val explicitlyExpanded: Boolean = false,
)

/**
 * Pure composer presentation policy. It never mutates draft/attachment state and deliberately
 * keeps generation/loading as presentation state only; generation ownership stays in ChatVM.
 */
fun resolveChatComposerPresentationState(
    signals: ChatComposerSignals,
): ChatComposerPresentationState = when {
    signals.fullscreen -> ChatComposerPresentationState.FULLSCREEN_EDITOR
    signals.editing -> ChatComposerPresentationState.EDITING_MESSAGE
    signals.voiceActive -> ChatComposerPresentationState.VOICE_ACTIVE
    signals.hasDraftText || signals.hasAttachments -> ChatComposerPresentationState.EXPANDED_DRAFT
    signals.focused || signals.explicitlyExpanded -> ChatComposerPresentationState.EXPANDED_COMPOSING
    signals.loading -> ChatComposerPresentationState.STREAMING_COMPACT
    else -> ChatComposerPresentationState.COMPACT_READING
}
