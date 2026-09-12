package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.ConversationLoadState
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

sealed interface ConversationDecodeResult { data class Valid(val value: Conversation): ConversationDecodeResult; data class Quarantined(val fields: Set<String>): ConversationDecodeResult }

fun decodeConversationEntity(entity: ConversationEntity, messageNodes: List<MessageNode> = emptyList(), loadedState: ConversationLoadState = ConversationLoadState.COMPLETE, corruptNodeIds: Set<String> = emptySet()): ConversationDecodeResult {
    val id = runCatching { Uuid.parse(entity.id) }.getOrNull() ?: return ConversationDecodeResult.Quarantined(setOf("conversationId"))
    val assistant = runCatching { Uuid.parse(entity.assistantId) }.getOrNull() ?: return ConversationDecodeResult.Quarantined(setOf("assistantId"))
    val integrity = linkedSetOf<String>()
    fun strings(raw: String, field: String) = runCatching { JsonInstant.decodeFromString<List<String>>(raw) }.getOrElse { integrity += field; emptyList() }
    fun uuids(raw: String, field: String) = runCatching { JsonInstant.decodeFromString<Set<Uuid>>(raw) }.getOrElse { integrity += field; emptySet() }
    val folder = entity.folderId.ifEmpty { null }?.let { runCatching { Uuid.parse(it) }.getOrElse { integrity += "folderId"; null } }
    return ConversationDecodeResult.Valid(Conversation(id, assistant, entity.title, messageNodes.filter { it.messages.isNotEmpty() }, strings(entity.chatSuggestions,"suggestions"), entity.isPinned, Instant.ofEpochMilli(entity.createAt), Instant.ofEpochMilli(entity.updateAt), entity.customSystemPrompt.ifEmpty { null }, uuids(entity.modeInjectionIds,"modeInjectionIds"), uuids(entity.lorebookIds,"lorebookIds"), entity.workspaceCwd.ifEmpty { null }, folder, loadState = if (integrity.isNotEmpty() || loadedState == ConversationLoadState.PARTIAL) ConversationLoadState.PARTIAL else loadedState, corruptNodeIds = corruptNodeIds, integrityFields = integrity))
}
