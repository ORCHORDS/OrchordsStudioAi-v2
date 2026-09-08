package com.orchords.orchordsai.ui.components.message

import com.orchords.ai.core.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageAvatarPolicyTest {
    @Test
    fun `ordinary assistant model identity row stays hidden`() {
        assertFalse(
            shouldShowAssistantIdentityRow(
                role = MessageRole.ASSISTANT,
                useAssistantAvatar = false,
            )
        )
    }

    @Test
    fun `explicit assistant profile identity remains visible`() {
        assertTrue(
            shouldShowAssistantIdentityRow(
                role = MessageRole.ASSISTANT,
                useAssistantAvatar = true,
            )
        )
    }

    @Test
    fun `assistant identity row never appears on user messages`() {
        assertFalse(
            shouldShowAssistantIdentityRow(
                role = MessageRole.USER,
                useAssistantAvatar = true,
            )
        )
    }
}
