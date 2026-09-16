package com.orchords.orchordsai.data.ai.mcp

import java.net.URI

const val GITHUB_MCP_REMOTE_ENDPOINT = "https://api.githubcopilot.com/mcp/"
private const val GITHUB_MCP_REMOTE_HOST = "api.githubcopilot.com"
private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_TOOLSETS = "X-MCP-Toolsets"
private const val HEADER_READONLY = "X-MCP-Readonly"
private const val HEADER_LOCKDOWN = "X-MCP-Lockdown"

/**
 * Safe first-use configuration for GitHub's official remote MCP server.
 *
 * PAT auth is represented by an empty Authorization value that the dedicated
 * GitHub PAT field fills. The SettingsStore MCP secret boundary moves that
 * value into Android Keystore-backed storage before the config is persisted.
 * Remote OAuth is intentionally not preconfigured here: GitHub requires each
 * MCP host to register its own GitHub/OAuth App.
 */
fun githubMcpPreset(): McpServerConfig.StreamableHTTPServer =
    McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(
            name = "GitHub",
            headers = listOf(
                HEADER_AUTHORIZATION to "",
                HEADER_TOOLSETS to "repos,issues,pull_requests",
                HEADER_READONLY to "true",
                HEADER_LOCKDOWN to "true",
            ),
        ),
        url = GITHUB_MCP_REMOTE_ENDPOINT,
    )

internal fun isOfficialGitHubMcpRemote(config: McpServerConfig): Boolean =
    runCatching { URI(config.serverUrl).host?.equals(GITHUB_MCP_REMOTE_HOST, ignoreCase = true) == true }
        .getOrDefault(false)

internal fun isGitHubManagedHeader(name: String): Boolean =
    name.equals(HEADER_AUTHORIZATION, ignoreCase = true) ||
        name.equals(HEADER_TOOLSETS, ignoreCase = true) ||
        name.equals(HEADER_READONLY, ignoreCase = true) ||
        name.equals(HEADER_LOCKDOWN, ignoreCase = true)

internal fun isGitHubMcpReadOnly(config: McpServerConfig): Boolean {
    if (!isOfficialGitHubMcpRemote(config)) return false
    val path = runCatching { URI(config.serverUrl).path.orEmpty().lowercase() }.getOrDefault("")
    if (path.split('/').any { it == "readonly" }) return true
    return githubBooleanHeader(config, HEADER_READONLY)
}

internal fun isGitHubMcpLockdown(config: McpServerConfig): Boolean =
    isOfficialGitHubMcpRemote(config) && githubBooleanHeader(config, HEADER_LOCKDOWN)

internal fun githubMcpToolsets(config: McpServerConfig): String {
    if (!isOfficialGitHubMcpRemote(config)) return ""
    return config.commonOptions.headers
        .lastOrNull { (name, _) -> name.equals(HEADER_TOOLSETS, ignoreCase = true) }
        ?.second
        ?.trim()
        .orEmpty()
}

internal fun githubMcpPat(config: McpServerConfig): String {
    if (!isOfficialGitHubMcpRemote(config)) return ""
    val value = config.commonOptions.headers
        .lastOrNull { (name, _) -> name.equals(HEADER_AUTHORIZATION, ignoreCase = true) }
        ?.second
        ?.trim()
        .orEmpty()
    return value.removePrefix("Bearer ").removePrefix("bearer ").trim()
}

internal fun hasGitHubMcpAuthentication(config: McpServerConfig): Boolean =
    githubMcpPat(config).isNotBlank() || config.commonOptions.oauth?.isAuthorized == true

internal fun McpServerConfig.withGitHubMcpPat(pat: String): McpServerConfig {
    if (!isOfficialGitHubMcpRemote(this)) return this
    val normalized = pat.trim()
    return withGitHubHeader(
        HEADER_AUTHORIZATION,
        if (normalized.isEmpty()) "" else "Bearer $normalized",
    )
}

internal fun McpServerConfig.withGitHubMcpToolsets(toolsets: String): McpServerConfig {
    if (!isOfficialGitHubMcpRemote(this)) return this
    val normalized = toolsets.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(",")
    return withGitHubHeader(HEADER_TOOLSETS, normalized)
}

internal fun McpServerConfig.withGitHubMcpDisconnected(): McpServerConfig {
    if (!isOfficialGitHubMcpRemote(this)) return this
    val withoutPat = withGitHubMcpPat("")
    return withoutPat.clone(
        commonOptions = withoutPat.commonOptions.copy(oauth = null),
    )
}

internal fun McpServerConfig.withGitHubMcpReadOnly(enabled: Boolean): McpServerConfig =
    withGitHubBooleanHeader(HEADER_READONLY, enabled)

internal fun McpServerConfig.withGitHubMcpLockdown(enabled: Boolean): McpServerConfig =
    withGitHubBooleanHeader(HEADER_LOCKDOWN, enabled)

private fun githubBooleanHeader(config: McpServerConfig, headerName: String): Boolean {
    val value = config.commonOptions.headers
        .lastOrNull { (name, _) -> name.equals(headerName, ignoreCase = true) }
        ?.second
        ?.trim()
        ?.lowercase()
        ?: return false
    return value !in setOf("", "false", "f", "no", "n", "0", "off")
}

private fun McpServerConfig.withGitHubBooleanHeader(
    headerName: String,
    enabled: Boolean,
): McpServerConfig = withGitHubHeader(headerName, enabled.toString())

private fun McpServerConfig.withGitHubHeader(
    headerName: String,
    headerValue: String,
): McpServerConfig {
    if (!isOfficialGitHubMcpRemote(this)) return this
    var found = false
    val headers = commonOptions.headers.map { (name, value) ->
        if (name.equals(headerName, ignoreCase = true)) {
            found = true
            name to headerValue
        } else {
            name to value
        }
    }.let { current ->
        if (found) current else current + (headerName to headerValue)
    }
    return clone(commonOptions = commonOptions.copy(headers = headers))
}

/**
 * Do not trust server-supplied ToolAnnotations as an authorization boundary.
 * GitHub write-enabled profiles and the Cloudflare API Code Mode surface both
 * default newly discovered tools to approval-required.
 *
 * The historical function name is kept because McpSessionRegistry already
 * calls this policy hook for every server; it now covers both first-class
 * providers without changing generic MCP behavior.
 */
internal fun githubNewToolsNeedApproval(config: McpServerConfig): Boolean =
    cloudflareNewToolsNeedApproval(config) ||
        (isOfficialGitHubMcpRemote(config) && !isGitHubMcpReadOnly(config))
