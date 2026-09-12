package com.orchords.orchordsai.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.filter
import androidx.paging.map
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.MessageNodePayloadStore
import com.orchords.orchordsai.data.db.fts.MessageFtsManager
import com.orchords.orchordsai.data.db.fts.MessageSearchSort
import com.orchords.orchordsai.data.db.dao.ConversationDAO
import com.orchords.orchordsai.data.db.dao.FavoriteDAO
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.favorite.NodeFavoriteAdapter
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.ConversationLoadState
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.data.model.TemporaryConversationRegistry
import com.orchords.orchordsai.data.model.allowsDurablePersistence
import com.orchords.orchordsai.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val favoriteDAO: FavoriteDAO,
    private val database: AppDatabase,
    private val filesManager: FilesManager,
    private val messageFtsManager: MessageFtsManager,
    private val messageNodePayloadStore: MessageNodePayloadStore,
    private val favoriteRepository: FavoriteRepository,
) {

    private val payloadSource = object : ConversationNodePayloadSource {
        override suspend fun resolve(entity: MessageNodeEntity): String? {
            val blobId = entity.payloadBlobId
            if (blobId != null) {
                return messageNodePayloadStore.load(blobId)
            }
            val inline = entity.messages
            return inline.takeIf { it.isNotEmpty() }
        }
    }
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
    }

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> =
        conversationDAO.getRecentConversationsOfAssistant(assistantId.toString(), limit).mapNotNull { entity ->
            runCatching {
                val loaded = loadMessageNodes(entity.id)
                conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
            }.getOrNull()
        }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> =
        conversationDAO.getConversationsOfAssistant(assistantId.toString()).map { list ->
            list.mapNotNull { runCatching { conversationEntityToConversation(it, emptyList()) }.getOrNull() }
        }

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    fun getUnfiledConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    fun getConversationsOfFolderPaging(folderId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getConversationsOfFolderPaging(folderId.toString()) }
    ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    fun getConversationsOfFolderOfAssistantPaging(
        assistantId: Uuid,
        folderId: Uuid,
    ): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = {
            conversationDAO.getConversationsOfFolderOfAssistantPaging(
                assistantId = assistantId.toString(),
                folderId = folderId.toString(),
            )
        }
    ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    suspend fun getConversationsOfAssistantPage(assistantId: Uuid, offset: Int, limit: Int): ConversationPageResult =
        loadConversationPage(conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()), offset, limit)

    suspend fun searchConversationsOfAssistantPage(
        assistantId: Uuid,
        titleKeyword: String,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.searchConversationsOfAssistantPaging(assistantId.toString(), titleKeyword), offset, limit
    )

    suspend fun getUnfiledConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()), offset, limit
    )

    suspend fun getConversationsOfFolderPage(folderId: Uuid, offset: Int, limit: Int): ConversationPageResult =
        loadConversationPage(conversationDAO.getConversationsOfFolderPaging(folderId.toString()), offset, limit)

    suspend fun getConversationsOfFolderOfAssistantPage(
        assistantId: Uuid,
        folderId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getConversationsOfFolderOfAssistantPaging(
            assistantId = assistantId.toString(),
            folderId = folderId.toString(),
        ),
        offset,
        limit,
    )

    private suspend fun loadConversationPage(
        pagingSource: PagingSource<Int, LightConversationEntity>,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = try {
        when (val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = if (offset == 0) null else offset,
                loadSize = limit,
                placeholdersEnabled = false
            )
        )) {
            is PagingSource.LoadResult.Page -> ConversationPageResult(
                result.data.mapNotNull { conversationSummaryToConversation(it) }, result.nextKey
            )
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
        }
    } finally {
        pagingSource.invalidate()
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> =
        conversationDAO.searchConversations(titleKeyword).map { list ->
            list.mapNotNull { runCatching { conversationEntityToConversation(it, emptyList()) }.getOrNull() }
        }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> =
        conversationDAO.searchConversationsOfAssistant(assistantId.toString(), titleKeyword).map { list ->
            list.mapNotNull { runCatching { conversationEntityToConversation(it, emptyList()) }.getOrNull() }
        }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> =
        Pager(
            PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                conversationDAO.searchConversationsOfAssistantPaging(assistantId.toString(), titleKeyword)
            }
        ).flow.map { data -> data.filter { conversationSummaryToConversation(it) != null }.map { requireNotNull(conversationSummaryToConversation(it)) } }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString()) ?: return null
        return runCatching {
            val loaded = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
        }.getOrNull()
    }

    suspend fun existsConversationById(uuid: Uuid): Boolean = conversationDAO.existsById(uuid.toString())
    suspend fun countConversations(): Int = conversationDAO.countAll()

    suspend fun insertConversation(conversation: Conversation) {
        if (!conversation.allowsRepositoryPersistence()) return
        requireCompleteConversationForRewrite(conversation)
        database.withTransaction {
            conversationDAO.insert(conversationToConversationEntity(conversation))
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun updateConversation(conversation: Conversation) {
        if (!conversation.allowsRepositoryPersistence()) return
        requireCompleteConversationForRewrite(conversation)
        database.withTransaction {
            conversationDAO.update(conversationToConversationEntity(conversation))
            messageNodeDAO.deleteByConversation(conversation.id.toString())
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        if (!conversation.allowsRepositoryPersistence()) return
        val fullConversation = if (conversation.messageNodes.isEmpty()) {
            getConversationById(conversation.id) ?: conversation
        } else conversation
        messageFtsManager.deleteConversation(conversation.id.toString())
        database.withTransaction { conversationDAO.delete(conversationToConversationEntity(conversation)) }
        filesManager.deleteChatFiles(fullConversation.files)
    }

    suspend fun searchMessages(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ) = messageFtsManager.search(keyword, sort)

    suspend fun searchMessagesOfAssistant(
        assistantId: Uuid,
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ) = messageFtsManager.searchForAssistant(keyword, assistantId.toString(), sort)

    suspend fun rebuildAllIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        messageFtsManager.deleteAll()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val loaded = loadMessageNodes(entity.id)
            if (loaded.corruptNodeIds.isEmpty()) {
                messageFtsManager.indexConversation(
                    conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
                )
            }
            onProgress(index + 1, total)
        }
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { deleteConversation(it) }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        require(conversation.allowsRepositoryPersistence()) {
            "Temporary conversations cannot be serialized into the durable conversation store"
        }
        require(conversation.messageNodes.none { node -> node.messages.any { it.hasBase64Part() } })
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = "[]",
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            customSystemPrompt = conversation.customSystemPrompt ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
            folderId = conversation.folderId?.toString() ?: "",
        )
    }

    fun conversationEntityToConversation(
        entity: ConversationEntity,
        messageNodes: List<MessageNode>,
        loadState: ConversationLoadState = ConversationLoadState.COMPLETE,
        corruptNodeIds: Set<String> = emptySet(),
    ): Conversation = when (val result = decodeConversationEntity(entity, messageNodes, loadState, corruptNodeIds)) {
        is ConversationDecodeResult.Valid -> result.value
        is ConversationDecodeResult.Quarantined -> throw IllegalArgumentException("Invalid conversation: ${result.fields}")
    }

    private fun legacyConversationEntityToConversation(
        entity: ConversationEntity,
        messageNodes: List<MessageNode>,
        loadState: ConversationLoadState = ConversationLoadState.COMPLETE,
        corruptNodeIds: Set<String> = emptySet(),
    ): Conversation {
        val integrity = linkedSetOf<String>()
        fun uuid(raw: String, field: String): Uuid? = runCatching { Uuid.parse(raw) }.getOrElse {
            integrity += field
            null
        }
        fun jsonStrings(raw: String, field: String): List<String> = runCatching {
            JsonInstant.decodeFromString<List<String>>(raw)
        }.getOrElse { integrity += field; emptyList() }
        fun jsonUuids(raw: String, field: String): Set<Uuid> = runCatching {
            JsonInstant.decodeFromString<Set<Uuid>>(raw)
        }.getOrElse { integrity += field; emptySet() }
        val id = uuid(entity.id, "conversationId") ?: throw IllegalArgumentException("Invalid conversationId")
        val assistant = uuid(entity.assistantId, "assistantId") ?: throw IllegalArgumentException("Invalid assistantId")
        return Conversation(
            id = id,
            title = entity.title,
            messageNodes = messageNodes.filter { it.messages.isNotEmpty() },
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
            assistantId = assistant,
            chatSuggestions = jsonStrings(entity.chatSuggestions, "suggestions"),
            isPinned = entity.isPinned,
            customSystemPrompt = entity.customSystemPrompt.ifEmpty { null },
            modeInjectionIds = jsonUuids(entity.modeInjectionIds, "modeInjectionIds"),
            lorebookIds = jsonUuids(entity.lorebookIds, "lorebookIds"),
            workspaceCwd = entity.workspaceCwd.ifEmpty { null },
            folderId = entity.folderId.ifEmpty { null }?.let { uuid(it, "folderId") },
            loadState = if (integrity.isNotEmpty() || loadState == ConversationLoadState.PARTIAL) ConversationLoadState.PARTIAL else loadState,
            corruptNodeIds = corruptNodeIds,
            integrityFields = integrity,
        )
    }

    fun getPinnedConversations(): Flow<List<Conversation>> = conversationDAO.getPinnedConversations().map { list ->
        list.mapNotNull { runCatching { conversationEntityToConversation(it, emptyList()) }.getOrNull() }
    }

    suspend fun togglePinStatus(conversationId: Uuid) {
        conversationDAO.updatePinStatus(
            conversationId.toString(), !(getConversationById(conversationId)?.isPinned ?: false)
        )
    }

    suspend fun updateConversationFolderId(conversationId: Uuid, folderId: Uuid?) {
        conversationDAO.updateFolderId(conversationId.toString(), folderId?.toString() ?: "")
    }

    private fun conversationSummaryToConversation(entity: LightConversationEntity): Conversation? {
    val id = runCatching { Uuid.parse(entity.id) }.getOrNull() ?: return null
    val assistantId = runCatching { Uuid.parse(entity.assistantId) }.getOrNull() ?: return null
    val folderId = entity.folderId.ifEmpty { null }?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    val integrity = buildSet {
        if (runCatching { Uuid.parse(entity.id) }.getOrNull() != id) add("conversationId")
        if (runCatching { Uuid.parse(entity.assistantId) }.getOrNull() != assistantId) add("assistantId")
        if (entity.folderId.isNotEmpty() && folderId == null) add("folderId")
    }
    return Conversation(
        id = id,
        assistantId = assistantId,
        title = entity.title,
        isPinned = entity.isPinned,
        createAt = Instant.ofEpochMilli(entity.createAt),
        updateAt = Instant.ofEpochMilli(entity.updateAt),
        messageNodes = emptyList(),
        folderId = folderId,
        loadState = if (integrity.isNotEmpty()) ConversationLoadState.PARTIAL else ConversationLoadState.COMPLETE,
        integrityFields = integrity,
    )
    }

    private suspend fun loadMessageNodes(conversationId: String): MessageNodeLoadResult {
        val favoriteNodeIds = favoriteRepository.getNodeFavoritesOfConversation(Uuid.parse(conversationId))
            .mapNotNull { NodeFavoriteAdapter.decodeRef(it)?.nodeId }
            .toSet()
        return database.withTransaction {
            loadConversationNodesSafely(messageNodeDAO, conversationId, favoriteNodeIds, payloadSource)
        }
    }

    private suspend fun saveMessageNodes(conversationId: String, nodes: List<MessageNode>) {
        // Capture the previous blob ids BEFORE the row mutation so we can clean them up
        // after the transaction succeeds. `updateConversation` does
        // `deleteByConversation` then re-inserts, so anything previously externalized
        // becomes orphaned if we don't track it here.
        val previousBlobIds: Set<Long> = if (nodes.isNotEmpty()) {
            messageNodeDAO.getNodesMetaOfConversation(conversationId)
                .mapNotNull { it.payloadBlobId }
                .toSet()
        } else emptySet()

        // Externalize JSON for oversized nodes outside the DB transaction; the
        // store does I/O on Dispatchers.IO via FilesManager. Failures here must
        // surface so we never store a dangling reference.
        val prepared = nodes.mapIndexed { index, node ->
            val nodeId = node.id.toString()
            val json = JsonInstant.encodeToString(node.messages)
            val blobId = messageNodePayloadStore.store(nodeId, json)
            MessageNodeEntity(
                id = nodeId,
                conversationId = conversationId,
                nodeIndex = index,
                messages = if (blobId == null) json else "",
                selectIndex = node.selectIndex,
                payloadBlobId = blobId,
            )
        }
        messageNodeDAO.insertAll(prepared)

        // Best-effort orphan cleanup. Removing a managed file after the row
        // has been replaced is safe; failing to remove it leaves a harmless
        // dangling blob that the orphan-row cleanup pass can pick up later.
        previousBlobIds.forEach { messageNodePayloadStore.delete(it) }
    }

    private fun Conversation.allowsRepositoryPersistence(): Boolean =
        retention.allowsDurablePersistence() &&
            !TemporaryConversationRegistry.isTemporary(id.toString())
}

data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)

data class ConversationPageResult(
    val items: List<Conversation>,
    val nextOffset: Int?,
)
