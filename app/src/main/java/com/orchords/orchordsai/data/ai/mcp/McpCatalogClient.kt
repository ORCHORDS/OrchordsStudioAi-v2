package com.orchords.orchordsai.data.ai.mcp

import com.orchords.orchordsai.utils.JsonInstant
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.seconds

/** A complete discovery has a bounded lifetime even if every page supplies another token. */
internal suspend fun fetchCompleteMcpTools(client: Client): List<Tool> =
    withTimeoutOrNull(60_000L) {
        collectMcpCatalog(McpCatalogLimits(), Tool::name) { cursor ->
            currentCoroutineContext().ensureActive()
            val page = try {
                client.listTools(
                    request = ListToolsRequest(params = cursor?.let { PaginatedRequestParams(cursor = it) }),
                    options = RequestOptions(timeout = 30.seconds),
                )
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw McpCatalogException(McpCatalogFailure.TIME_LIMIT)
            }
            McpCatalogPage(
                items = page.tools,
                nextCursor = page.nextCursor,
                encodedBytes = JsonInstant.encodeToString(page).toByteArray(Charsets.UTF_8).size.toLong(),
            )
        }
    } ?: throw McpCatalogException(McpCatalogFailure.TIME_LIMIT)
