package com.orchords.orchordsai.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ExternalFolderGrantDescriptor(
    val id: String,
    val treeUri: String,
    val canRead: Boolean,
    val canWrite: Boolean,
)

enum class ExternalFolderGrantState {
    CONNECTED,
    REAUTHORIZE_REQUIRED,
}

/** User-granted SAF capability boundary. A stored URI string is never sufficient authority. */
object ExternalFolderGrantGateway {
    fun createPickerIntent(requestWrite: Boolean = true): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (requestWrite) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    fun persistReturnedGrant(
        context: Context,
        treeUri: Uri,
        resultFlags: Int,
        requestWrite: Boolean = true,
        id: String = Uuid.random().toString(),
    ): ExternalFolderGrantDescriptor {
        require(treeUri.scheme == "content" && DocumentsContract.isTreeUri(treeUri)) {
            "External folder selection must be a document tree URI"
        }
        val grantedFlags = effectiveTreeGrantFlags(resultFlags, requestWrite)
        require(grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            "External folder selection did not grant read access"
        }
        context.contentResolver.takePersistableUriPermission(treeUri, grantedFlags)
        return ExternalFolderGrantDescriptor(
            id = id,
            treeUri = treeUri.toString(),
            canRead = true,
            canWrite = grantedFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
    }

    fun state(
        context: Context,
        descriptor: ExternalFolderGrantDescriptor,
    ): ExternalFolderGrantState {
        val uri = runCatching { Uri.parse(descriptor.treeUri) }.getOrNull()
            ?: return ExternalFolderGrantState.REAUTHORIZE_REQUIRED
        val permission = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri }
            ?: return ExternalFolderGrantState.REAUTHORIZE_REQUIRED
        if (descriptor.canRead && !permission.isReadPermission) {
            return ExternalFolderGrantState.REAUTHORIZE_REQUIRED
        }
        if (descriptor.canWrite && !permission.isWritePermission) {
            return ExternalFolderGrantState.REAUTHORIZE_REQUIRED
        }
        return ExternalFolderGrantState.CONNECTED
    }
}

internal fun effectiveTreeGrantFlags(resultFlags: Int, requestWrite: Boolean): Int {
    val requested = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        (if (requestWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    return resultFlags and requested
}
