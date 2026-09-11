package com.orchords.orchordsai.data.sync.webdav

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.orchords.orchordsai.data.datastore.WebDavConfig
import com.orchords.orchordsai.data.sync.requireSecureBackupEndpoint
import java.io.File
import java.net.URI

internal fun webDavCollectionUrl(config: WebDavConfig): String {
    val base = config.url.trimEnd('/')
    val path = config.path.trim('/')
    return if (path.isEmpty()) "$base/" else "$base/$path/"
}

/** Resolve a PROPFIND href only when it stays inside the configured WebDAV collection. */
internal fun resolveWebDavResourceHref(collectionUrl: String, href: String): String {
    val collection = URI(collectionUrl).normalize()
    require(collection.scheme.equals("https", ignoreCase = true)) { "WebDAV collection must use HTTPS" }
    require(collection.host != null && collection.userInfo == null && collection.fragment == null) {
        "Invalid WebDAV collection URL"
    }

    val reference = URI(href)
    require(reference.fragment == null && reference.userInfo == null) { "Invalid WebDAV resource href" }
    require(reference.isAbsolute || href.startsWith('/')) { "Invalid WebDAV resource href" }
    val rawPath = reference.rawPath.orEmpty()
    require(rawPath.split('/').none { it == "." || it == ".." }) { "Invalid WebDAV resource href" }
    require(!rawPath.contains("%2e", ignoreCase = true)) { "Invalid WebDAV resource href" }
    require(!rawPath.contains("%2f", ignoreCase = true)) { "Invalid WebDAV resource href" }
    require(!rawPath.contains("%5c", ignoreCase = true)) { "Invalid WebDAV resource href" }

    val resource = if (reference.isAbsolute) {
        reference.normalize()
    } else {
        URI(collection.scheme, null, collection.host, collection.port, "/", null, null)
            .resolve(reference)
            .normalize()
    }

    fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }

    require(resource.scheme.equals(collection.scheme, ignoreCase = true)) { "WebDAV resource origin mismatch" }
    require(resource.host.equals(collection.host, ignoreCase = true)) { "WebDAV resource origin mismatch" }
    require(effectivePort(resource) == effectivePort(collection)) { "WebDAV resource origin mismatch" }
    require(resource.userInfo == null && resource.fragment == null) { "Invalid WebDAV resource href" }

    val collectionPath = collection.rawPath.orEmpty().let { if (it.endsWith('/')) it else "$it/" }
    val resourcePath = resource.rawPath.orEmpty()
    require(resourcePath.startsWith(collectionPath)) { "WebDAV resource is outside configured collection" }
    require(resourcePath.length > collectionPath.length) { "WebDAV resource href does not identify a child" }
    return resource.toASCIIString()
}

internal suspend fun downloadWebDavResourceToFile(
    config: WebDavConfig,
    httpClient: HttpClient,
    href: String,
    targetFile: File,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        requireSecureBackupEndpoint(config.url, "WebDAV")
        val url = resolveWebDavResourceHref(webDavCollectionUrl(config), href)
        httpClient.prepareRequest(url) {
            method = HttpMethod.Get
            basicAuth(config.username, config.password)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw WebDavException("Failed to download: ${response.status}", response.status.value, errorBody)
            }

            val channel = response.bodyAsChannel()
            targetFile.outputStream().use { outputStream ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}
