package com.orchords.orchordsai.data.model

import java.io.File

/**
 * Stores only the IDs of temporary conversations so their privacy classification survives
 * process recreation without placing conversation content in the ordinary database or backup.
 */
class TemporaryConversationMarkerStore(private val rootDir: File) {
    fun markTemporary(conversationId: String) {
        rootDir.mkdirs()
        markerFile(conversationId).apply {
            if (!exists()) createNewFile()
        }
    }

    fun markRetained(conversationId: String) {
        markerFile(conversationId).delete()
    }

    fun isTemporary(conversationId: String): Boolean = markerFile(conversationId).isFile

    fun listTemporaryIds(): Set<String> = rootDir
        .listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.name.endsWith(MARKER_SUFFIX) }
        .map { it.name.removeSuffix(MARKER_SUFFIX) }
        .filter { it.matches(SAFE_ID) }
        .toSet()

    private fun markerFile(conversationId: String): File {
        require(conversationId.matches(SAFE_ID)) { "Invalid conversation ID" }
        return File(rootDir, "$conversationId$MARKER_SUFFIX")
    }

    companion object {
        const val DIRECTORY_NAME = "temporary_conversations"
        private const val MARKER_SUFFIX = ".tmp"
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
