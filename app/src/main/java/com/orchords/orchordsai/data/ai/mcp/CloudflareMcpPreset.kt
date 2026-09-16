package com.orchords.orchordsai.data.ai.mcp

import java.net.URI

const val CLOUDFLARE_MCP_REMOTE_ENDPOINT = "https://mcp.cloudflare.com/mcp"
private const val CLOUDFLARE_MCP_REMOTE_HOST = "mcp.cloudflare.com"
private const val HEADER_AUTHORIZATION = "Authorization"

/**
 * First-class Cloudflare API MCP preset.
 *
 * Cloudflare's managed API MCP server supports MCP OAuth for interactive users
 * and bearer API tokens for automation. The default preset deliberately omits
 * Authorization so the existing MCP OAuth discovery/PKCE flow can run after a
 * 401 challenge. If the user chooses token mode, the dedicated field stores the
 * Authorization value through the existing encrypted MCP-header secret boundary.
 */
fun cloudflareMcpPreset(): McpServerConfig.StreamableHTTPServer =
    McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(name = "Cloudflare"),
        url = CLOUDFLARE_MCP_REMOTE_ENDPOINT,
    )

internal fun isOfficialCloudflareMcpRemote(config: McpServerConfig): Boolean =
    runCatching {
        val uri = URI(config.serverUrl)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.equals(CLOUDFLARE_MCP_REMOTE_HOST, ignoreCase = true) == true &&
            uri.path.trimEnd('/') == "/mcp"
    }.getOrDefault(false)

internal fun isCloudflareManagedHeader(name: String): Boolean =
    name.equals(HEADER_AUTHORIZATION, ignoreCase = true)

internal fun cloudflareMcpApiToken(config: McpServerConfig): String {
    if (!isOfficialCloudflareMcpRemote(config)) return ""
    val value = config.commonOptions.headers
        .lastOrNull { (name, _) -> name.equals(HEADER_AUTHORIZATION, ignoreCase = true) }
        ?.second
        ?.trim()
        .orEmpty()
    return value.removePrefix("Bearer ").removePrefix("bearer ").trim()
}

internal fun hasCloudflareMcpAuthentication(config: McpServerConfig): Boolean =
    cloudflareMcpApiToken(config).isNotBlank() || config.commonOptions.oauth?.isAuthorized == true

internal fun McpServerConfig.withCloudflareMcpApiToken(token: String): McpServerConfig {
    if (!isOfficialCloudflareMcpRemote(this)) return this
    val normalized = token.trim()
    val headers = commonOptions.headers
        .filterNot { (name, _) -> name.equals(HEADER_AUTHORIZATION, ignoreCase = true) }
        .let { current ->
            if (normalized.isBlank()) current else current + (HEADER_AUTHORIZATION to "Bearer $normalized")
        }
    return clone(commonOptions = commonOptions.copy(headers = headers))
}

internal fun McpServerConfig.withCloudflareMcpDisconnected(): McpServerConfig {
    if (!isOfficialCloudflareMcpRemote(this)) return this
    val withoutToken = withCloudflareMcpApiToken("")
    return withoutToken.clone(
        commonOptions = withoutToken.commonOptions.copy(oauth = null),
    )
}

/**
 * Cloudflare's Code Mode MCP server exposes search + execute over the full API.
 * execute can mutate account state, so newly discovered Cloudflare tools always
 * start approval-required. Server annotations are not treated as an auth boundary.
 */
internal fun cloudflareNewToolsNeedApproval(config: McpServerConfig): Boolean =
    isOfficialCloudflareMcpRemote(config)
