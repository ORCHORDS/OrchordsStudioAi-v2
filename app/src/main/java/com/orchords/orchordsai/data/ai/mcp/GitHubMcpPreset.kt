package com.orchords.orchordsai.data.ai.mcp

import java.net.URI

const val GITHUB_MCP_REMOTE_ENDPOINT = "https://api.githubcopilot.com/mcp/"
private const val GITHUB_MCP_REMOTE_HOST = "api.githubcopilot.com"

/**
 * Safe first-use configuration for GitHub's official remote MCP server.
 *
 * PAT auth is represented by an empty Authorization value that the user fills
 * in through the existing masked-header editor. The SettingsStore MCP secret
 * boundary moves that value into Android Keystore-backed storage before the
 * config is persisted. Remote OAuth is intentionally not preconfigured here:
 * GitHub requires each MCP host to register its own GitHub/OAuth App.
 */
fun githubMcpPreset(): McpServerConfig.StreamableHTTPServer =
    McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(
            name = "GitHub",
            headers = listOf(
                "Authorization" to "",
                "X-MCP-Toolsets" to "repos,issues,pull_requests",
                "X-MCP-Readonly" to "true",
                "X-MCP-Lockdown" to "true",
            ),
        ),
        url = GITHUB_MCP_REMOTE_ENDPOINT,
    )

internal fun isOfficialGitHubMcpRemote(config: McpServerConfig): Boolean =
    runCatching { URI(config.serverUrl).host?.equals(GITHUB_MCP_REMOTE_HOST, ignoreCase = true) == true }
        .getOrDefault(false)

internal fun isGitHubMcpReadOnly(config: McpServerConfig): Boolean {
    if (!isOfficialGitHubMcpRemote(config)) return false
    val path = runCatching { URI(config.serverUrl).path.orEmpty().lowercase() }.getOrDefault("")
    if (path.split('/').any { it == "readonly" }) return true
    val value = config.commonOptions.headers
        .lastOrNull { (name, _) -> name.equals("X-MCP-Readonly", ignoreCase = true) }
        ?.second
        ?.trim()
        ?.lowercase()
        ?: return false
    return value !in setOf("", "false", "f", "no", "n", "0", "off")
}

/**
 * Do not trust server-supplied ToolAnnotations as an authorization boundary.
 * Our own GitHub read-only configuration is deterministic: when it is off,
 * every newly discovered GitHub tool starts approval-required.
 */
internal fun githubNewToolsNeedApproval(config: McpServerConfig): Boolean =
    isOfficialGitHubMcpRemote(config) && !isGitHubMcpReadOnly(config)
