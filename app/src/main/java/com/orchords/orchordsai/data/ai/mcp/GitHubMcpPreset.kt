package com.orchords.orchordsai.data.ai.mcp

const val GITHUB_MCP_REMOTE_ENDPOINT = "https://api.githubcopilot.com/mcp/"

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
