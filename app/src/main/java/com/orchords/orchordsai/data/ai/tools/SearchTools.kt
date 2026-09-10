package com.orchords.orchordsai.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.orchords.ai.core.Tool
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.datastore.ORCHORDS_GATEWAY_BASE_URL
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.utils.JsonInstantPretty
import com.orchords.orchordsai.utils.toLocalString
import com.orchords.search.SearchService
import com.orchords.search.SearchServiceOptions
import java.time.LocalDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.uuid.Uuid

private val ORCHORDS_GATEWAY_ORIGIN = ORCHORDS_GATEWAY_BASE_URL.toHttpUrl()

/**
 * The first-party gateway bearer is audience-bound to the canonical Orchords
 * origin. A user-configured Search URL may keep its own Search credential, but
 * it must never inherit the gateway bearer unless it resolves to that exact
 * HTTPS origin (scheme, host and port).
 */
internal fun String.canReceiveOrchordsGatewayCredential(): Boolean {
    val endpoint = toHttpUrlOrNull() ?: return false
    return endpoint.scheme == ORCHORDS_GATEWAY_ORIGIN.scheme &&
        endpoint.host == ORCHORDS_GATEWAY_ORIGIN.host &&
        endpoint.port == ORCHORDS_GATEWAY_ORIGIN.port
}

/**
 * Resolve the selected executable external-search profile.
 *
 * Search services are tools/infrastructure, not alternate chat-model routes.
 * Legacy serialized search records remain readable, but an unsupported record
 * cannot silently execute as another provider. If the selected record is no
 * longer executable, use the first configured runtime-supported profile, then
 * the inert Orchords Search default.
 */
internal fun Settings.activeSearchOptions(): SearchServiceOptions {
    val selected = searchServices.getOrNull(searchServiceSelected)
        ?.takeIf(SearchService::isRuntimeSupported)
    val configured = selected
        ?: searchServices.firstOrNull(SearchService::isRuntimeSupported)
        ?: SearchServiceOptions.DEFAULT

    if (configured !is SearchServiceOptions.OrchordsAIOptions) return configured

    val gatewayKey = providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .firstOrNull { it.baseUrl.trimEnd('/') == ORCHORDS_GATEWAY_BASE_URL }
        ?.apiKey
        .orEmpty()

    val effectiveKey = if (configured.baseUrl.canReceiveOrchordsGatewayCredential()) {
        gatewayKey.ifBlank { configured.apiKey }
    } else {
        configured.apiKey
    }

    return configured.copy(apiKey = effectiveKey)
}

fun createSearchTools(settings: Settings): Set<Tool> {
    return buildSet {
        add(
            Tool(
                name = "search_web",
                description = """
                    Search the live web and return source-grounded information for the user's answer.
                    Use this for current, time-sensitive, niche, externally verifiable, or explicitly requested web information.
                    Rewrite the user's request into a focused search query. If the first result is weak, ambiguous, incomplete, or off-topic, run another more targeted search before answering. Today is ${LocalDate.now().toLocalString(true)}.

                    Response format:
                    - answer: a search-provider sourced answer when available
                    - items[].id, title, url, text: supporting web sources/snippets

                    Answering contract:
                    - Search is retrieval, not the final response. After searching, answer the user's original question directly in natural language.
                    - When `answer` is non-blank, use it as a sourced synthesis candidate and check it against the returned items.
                    - When `answer` or at least one usable item exists, do not claim that search is unavailable, that no information was found, or refuse merely because one field is empty. Synthesize the best supported answer from what was returned.
                    - If the evidence is partial, say what is known and what remains uncertain; do not invent missing facts.
                    - Only say the search produced no usable information after a reasonable targeted refinement and both `answer` and usable `items` are empty.
                    - Never fabricate a source, URL, citation id, quote, or fact.

                    Citations:
                    - Cite source-dependent claims with `[citation,domain](id)` using only ids returned in `items[]`.
                    - Prefer authoritative/original sources when the returned evidence allows it.
                    - Multiple citations are allowed when needed.
                """.trimIndent(),
                parameters = {
                    val options = settings.activeSearchOptions()
                    SearchService.getService(options).parameters(options)
                },
                execute = {
                    val options = settings.activeSearchOptions()
                    val service = SearchService.getService(options)
                    val result = service.search(
                        params = it.jsonObject,
                        commonOptions = settings.searchCommonOptions,
                        serviceOptions = options,
                    )
                    val results =
                        JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject.let { json ->
                            val map = json.toMutableMap()
                            map["items"] =
                                JsonArray(map["items"]!!.jsonArray.mapIndexed { index, item ->
                                    JsonObject(item.jsonObject.toMutableMap().apply {
                                        put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                                        put("index", JsonPrimitive(index + 1))
                                    })
                                })
                            JsonObject(map)
                        }
                    listOf(UIMessagePart.Text(results.toString()))
                }
            )
        )

        val options = settings.activeSearchOptions()
        val service = SearchService.getService(options)
        if (service.scrapingParameters(options) != null) {
            add(
                Tool(
                    name = "scrape_web",
                    description = """
                        Read a specific URL for detailed page content after search has identified it.
                        Use this when a source snippet is insufficient or the user explicitly asks about a page.
                        Do not invent content when the page cannot be read.
                    """.trimIndent(),
                    parameters = {
                        val scrapeOptions = settings.activeSearchOptions()
                        SearchService.getService(scrapeOptions).scrapingParameters(scrapeOptions)
                    },
                    execute = {
                        val scrapeOptions = settings.activeSearchOptions()
                        val scrapeService = SearchService.getService(scrapeOptions)
                        val result = scrapeService.scrape(
                            params = it.jsonObject,
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = scrapeOptions,
                        )
                        val payload = JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject
                        listOf(UIMessagePart.Text(payload.toString()))
                    }
                )
            )
        }
    }
}
