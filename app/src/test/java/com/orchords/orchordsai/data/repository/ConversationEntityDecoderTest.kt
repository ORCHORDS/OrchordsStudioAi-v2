package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEntityDecoderTest {
    @Test
    fun `malformed optional metadata preserves messages and records degraded fields`() {
        val result = decodeConversationEntity(entity(chatSuggestions = "not-json", modeInjectionIds = "bad"))

        val conversation = (result as ConversationDecodeResult.Valid).value
        assertEquals(ConversationLoadState.PARTIAL, conversation.loadState)
        assertEquals(setOf("suggestions", "modeInjectionIds"), conversation.integrityFields)
        assertTrue(conversation.messageNodes.isEmpty())
        assertTrue(conversation.chatSuggestions.isEmpty())
        assertTrue(conversation.modeInjectionIds.isEmpty())
    }

    @Test
    fun `invalid identity is quarantined without fabricating a conversation`() {
        val result = decodeConversationEntity(entity(id = "bad-id"))

        assertEquals(
            ConversationDecodeResult.Quarantined(setOf("conversationId")),
            result,
        )
    }

    @Test
    fun `malformed folder detaches and records integrity`() {
        val result = decodeConversationEntity(entity(folderId = "bad-folder"))

        val conversation = (result as ConversationDecodeResult.Valid).value
        assertEquals(null, conversation.folderId)
        assertEquals(setOf("folderId"), conversation.integrityFields)
        assertEquals(ConversationLoadState.PARTIAL, conversation.loadState)
    }

    private fun entity(
        id: String = ID,
        assistantId: String = ASSISTANT,
        chatSuggestions: String = "[]",
        modeInjectionIds: String = "[]",
        folderId: String = "",
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = "title",
        nodes = "[]",
        createAt = 1L,
        updateAt = 2L,
        chatSuggestions = chatSuggestions,
        isPinned = false,
        modeInjectionIds = modeInjectionIds,
        folderId = folderId,
    )

    private companion object {
        const val ID = "00000000-0000-0000-0000-000000000001"
        const val ASSISTANT = "00000000-0000-0000-0000-000000000002"
    }
}
