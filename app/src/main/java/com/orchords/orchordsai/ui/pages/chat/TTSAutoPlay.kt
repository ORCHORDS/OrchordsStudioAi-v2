package com.orchords.orchordsai.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.orchords.ai.core.MessageRole
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.ui.context.LocalTTSState
import com.orchords.orchordsai.utils.extractQuotedContentAsText
import com.orchords.orchordsai.utils.removeBracketedContent

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    val tts = LocalTTSState.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)
    val streamingState = remember { StreamingTtsAutoPlayState() }

    val lastMessage = conversation.currentMessages.lastOrNull()
    val streamOrdinaryVisibleText = setting.displaySetting.autoPlayTTSAfterGeneration &&
        !setting.displaySetting.ttsOnlyReadQuoted &&
        !setting.displaySetting.ttsOnlyReadOutsideBrackets

    // Ordinary auto-read can begin from stable visible segments before terminal completion.
    LaunchedEffect(lastMessage?.id, lastMessage?.toText(), streamOrdinaryVisibleText) {
        if (!streamOrdinaryVisibleText || lastMessage?.role != MessageRole.ASSISTANT) {
            if (streamingState.clear()) tts.stop()
            return@LaunchedEffect
        }

        val update = streamingState.update(
            messageId = lastMessage.id.toString(),
            visibleText = lastMessage.toText(),
        )
        if (update.resetPlayback) tts.stop()
        update.segments.forEachIndexed { index, segment ->
            tts.speak(
                text = segment,
                flush = update.flushFirstSegment && index == 0,
            )
        }
    }

    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect {
            if (!updatedSetting.displaySetting.autoPlayTTSAfterGeneration) return@collect
            val message = currentConversation.currentMessages.lastOrNull()
            if (message == null || message.role != MessageRole.ASSISTANT) return@collect

            val quotedOnly = updatedSetting.displaySetting.ttsOnlyReadQuoted
            val outsideBracketsOnly = updatedSetting.displaySetting.ttsOnlyReadOutsideBrackets
            if (!quotedOnly && !outsideBracketsOnly) {
                val update = streamingState.update(
                    messageId = message.id.toString(),
                    visibleText = message.toText(),
                    isFinal = true,
                )
                if (update.resetPlayback) tts.stop()
                update.segments.forEachIndexed { index, segment ->
                    tts.speak(
                        text = segment,
                        flush = update.flushFirstSegment && index == 0,
                    )
                }
                return@collect
            }

            // Existing quote/bracket filters operate on the complete response. Preserve their
            // terminal semantics until their own streaming-safe parser exists.
            var textToSpeak = message.toText()
            if (quotedOnly) {
                textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
            }
            if (outsideBracketsOnly) {
                textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
            }
            if (textToSpeak.isNotBlank()) {
                tts.speak(textToSpeak)
            }
        }
    }
}
