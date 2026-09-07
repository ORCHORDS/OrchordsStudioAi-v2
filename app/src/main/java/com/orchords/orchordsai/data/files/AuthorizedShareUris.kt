package com.orchords.orchordsai.data.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** The only first-party gateway for creating Android FileProvider URIs. */
enum class StagedShareRoot(val directoryName: String) {
    CAMERA("camera"),
    TEMP("temp"),
    EXPORT("export"),
    WORKSPACE_SHARE("workspace_share"),
}

suspend fun FilesManager.createManagedUploadShareUri(
    context: Context,
    file: File,
): Uri {
    val filesDir = context.filesDir.canonicalFile
    val uploadDir = File(filesDir, FileFolders.UPLOAD).canonicalFile
    val candidate = file.canonicalFile
    require(candidate.isFile) { "Managed share file does not exist" }
    require(candidate.toPath().startsWith(uploadDir.toPath())) {
        "Managed share file is outside the upload root"
    }

    val relativePath = candidate.relativeTo(filesDir).invariantSeparatorsPath
    val entity = requireNotNull(getByRelativePath(relativePath)) {
        "Managed share file is not registered"
    }
    require(entity.folder == FileFolders.UPLOAD) {
        "Managed share file has an invalid ownership class"
    }
    val managedFile = requireNotNull(getFileOrNull(entity)) {
        "Managed share record resolves outside app storage"
    }.canonicalFile
    require(managedFile == candidate) {
        "Managed share record does not match requested file"
    }

    return providerUri(context, candidate)
}

fun createStagedShareUri(
    context: Context,
    file: File,
    root: StagedShareRoot,
): Uri {
    val allowedRoot = File(context.cacheDir, root.directoryName).canonicalFile
    val candidate = file.canonicalFile
    require(candidate.isFile) { "Staged share file does not exist" }
    require(candidate.toPath().startsWith(allowedRoot.toPath())) {
        "Staged share file is outside ${root.directoryName}"
    }
    return providerUri(context, candidate)
}

private fun providerUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file,
)
