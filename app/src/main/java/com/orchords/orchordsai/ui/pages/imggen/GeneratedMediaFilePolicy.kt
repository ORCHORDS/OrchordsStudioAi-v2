package com.orchords.orchordsai.ui.pages.imggen

import java.io.File
import java.util.UUID

internal enum class GeneratedMediaFileKind(val prefix: String) {
    PREVIEW("imggen-preview"),
    FINAL("imggen"),
}

/**
 * Allocates generated-media filesystem identity from app-owned data only.
 * Provider/model display metadata must never participate in this filename.
 */
internal fun allocateGeneratedMediaFile(
    root: File,
    kind: GeneratedMediaFileKind,
    timestamp: Long,
    index: Int,
    nonce: String = UUID.randomUUID().toString(),
): File {
    require(index >= 0) { "Generated media index must be non-negative" }
    require(nonce.length in 1..64 && nonce.all { it.isLetterOrDigit() || it == '-' }) {
        "Generated media nonce must be opaque local identity"
    }

    val canonicalRoot = root.canonicalFile
    if (!canonicalRoot.exists()) canonicalRoot.mkdirs()
    require(canonicalRoot.isDirectory) { "Generated media root is not a directory" }

    val target = File(
        canonicalRoot,
        "${kind.prefix}_${timestamp}_${nonce}_$index.png",
    ).canonicalFile
    require(target.parentFile == canonicalRoot) { "Generated media target escaped its root" }
    return target
}
