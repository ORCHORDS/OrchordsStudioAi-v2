package com.orchords.orchordsai.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

private const val TOOL_CONTRACT_FINGERPRINT_VERSION = "mcp-tool-contract-v1"
private const val CATALOG_FINGERPRINT_VERSION = "mcp-tool-catalog-v1"

/**
 * Fingerprints only fields that can change invocation validity or side-effect expectations.
 * Display text, icons and protocol _meta are intentionally excluded from authorization state.
 */
internal fun mcpToolContractFingerprint(tool: Tool): String {
    val contract = buildJsonObject {
        put("version", TOOL_CONTRACT_FINGERPRINT_VERSION)
        put("name", tool.name)
        put("inputSchema", tool.inputSchema.toPersistedSchema())
        tool.outputSchema?.let { put("outputSchema", it.toPersistedSchema()) }
        tool.annotations?.let { annotations ->
            put("effectHints", buildJsonObject {
                annotations.readOnlyHint?.let { put("readOnly", it) }
                annotations.destructiveHint?.let { put("destructive", it) }
                annotations.idempotentHint?.let { put("idempotent", it) }
                annotations.openWorldHint?.let { put("openWorld", it) }
            })
        }
        tool.execution?.taskSupport?.let { put("taskSupport", it.name) }
    }
    return sha256Fingerprint(canonicalJson(contract))
}

/** Order-independent revision for one complete, bounded tool catalog. */
internal fun mcpCatalogRevision(tools: List<Tool>): String {
    val contracts = tools
        .map { tool -> tool.name to mcpToolContractFingerprint(tool) }
        .sortedBy { it.first }
        .joinToString(separator = "\n") { (name, fingerprint) -> "$name\u0000$fingerprint" }
    return sha256Fingerprint("$CATALOG_FINGERPRINT_VERSION\n$contracts")
}

internal fun ToolSchema.toPersistedSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    schema?.let { put("\$schema", it) }
    properties?.let { put("properties", it) }
    required?.let { names ->
        put("required", JsonArray(names.distinct().sorted().map(::JsonPrimitive)))
    }
    defs?.let { put("\$defs", it) }
}

private fun sha256Fingerprint(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return buildString(7 + digest.size * 2) {
        append("sha256:")
        digest.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) }
    }
}

private fun canonicalJson(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            JsonPrimitive(key).toString() + ":" + canonicalJson(value)
        }
    is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::canonicalJson)
    else -> element.toString()
}
