package com.orchords.orchordsai.web.routes

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class ManagedFileDownloadHeaders(
    val contentType: String,
    val contentDisposition: String,
    val contentTypeOptions: String,
)

/**
 * Managed user/model/connector files are untrusted browser content. Serve them as inert downloads
 * from the authenticated application origin instead of replaying stored/client MIME inline.
 */
internal fun managedFileDownloadHeaders(displayName: String): ManagedFileDownloadHeaders {
    val basename = displayName.substringAfterLast('/').substringAfterLast('\\')
    val asciiFallback = basename
        .map { ch -> if (ch.code in 0x20..0x7E && ch != '"' && ch != '\\') ch else '_' }
        .joinToString("")
        .take(160)
        .ifBlank { "file" }
    val encoded = URLEncoder.encode(
        basename.take(240),
        StandardCharsets.UTF_8.toString(),
    ).replace("+", "%20")

    return ManagedFileDownloadHeaders(
        contentType = "application/octet-stream",
        contentDisposition = "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encoded",
        contentTypeOptions = "nosniff",
    )
}
