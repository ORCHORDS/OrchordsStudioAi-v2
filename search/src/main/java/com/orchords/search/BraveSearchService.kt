package com.orchords.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.orchords.ai.core.InputSchema
import com.orchords.search.SearchResult.SearchResultItem
import com.orchords.search.SearchService.Companion.httpClient
import com.orchords.search.SearchService.Companion.json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

private const val MAX_BRAVE_QUERY_CHARS = 600
private const val MAX_BRAVE_QUERY_WORDS = 75
private const val MAX_BRAVE_RESULTS = 20

object BraveSearchService : SearchService<SearchServiceOptions.BraveOptions> {
    override val name: String = "Brave"

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://api.search.brave.com/")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override fun parameters(options: SearchServiceOptions.BraveOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Focused web search query, up to 600 characters and 75 words.")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.BraveOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BraveOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("query is required")
            val request = buildBraveSearchRequest(
                query = query,
                resultSize = commonOptions.resultSize,
                apiKey = serviceOptions.apiKey,
            )

            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Brave search failed with HTTP ${response.code}")
                }
                parseBraveSearchResponse(response.readBoundedSearchBody())
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BraveOptions
    ): Result<ScrapedResult> = Result.failure(
        UnsupportedOperationException("Scraping is not supported for Brave Search")
    )
}

internal fun buildBraveSearchRequest(
    query: String,
    resultSize: Int,
    apiKey: String,
): Request {
    val normalizedQuery = query.trim()
    require(normalizedQuery.isNotEmpty()) { "query is required" }
    require(normalizedQuery.length <= MAX_BRAVE_QUERY_CHARS) {
        "query must not exceed $MAX_BRAVE_QUERY_CHARS characters"
    }
    require(normalizedQuery.split(Regex("\\s+")).size <= MAX_BRAVE_QUERY_WORDS) {
        "query must not exceed $MAX_BRAVE_QUERY_WORDS words"
    }
    require(apiKey.isNotBlank()) { "Brave Search API key is required" }

    val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl()
        .newBuilder()
        .addQueryParameter("q", normalizedQuery)
        .addQueryParameter("count", resultSize.coerceIn(1, MAX_BRAVE_RESULTS).toString())
        .build()

    return Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("X-Subscription-Token", apiKey)
        .build()
}

internal fun parseBraveSearchResponse(raw: String): SearchResult {
    val searchResponse = SearchService.json.decodeFromString<BraveSearchResponse>(raw)
    val items = searchResponse.web?.results.orEmpty().take(MAX_BRAVE_RESULTS).map { result ->
        SearchResultItem(
            title = result.title,
            url = result.url,
            text = result.description ?: "",
        )
    }
    return SearchResult(answer = null, items = items)
}

@Serializable
internal data class BraveSearchResponse(
    val type: String? = null,
    val web: BraveWebResults? = null,
)

@Serializable
internal data class BraveWebResults(
    val type: String? = null,
    val results: List<BraveWebResult>? = null,
)

@Serializable
internal data class BraveWebResult(
    val type: String,
    val title: String,
    val url: String,
    val description: String? = null,
)
