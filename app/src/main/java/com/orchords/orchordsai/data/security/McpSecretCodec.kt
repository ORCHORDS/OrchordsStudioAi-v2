package com.orchords.orchordsai.data.security

import com.orchords.orchordsai.data.ai.mcp.McpServerConfig
import java.security.MessageDigest
import kotlin.uuid.Uuid

object McpSecretKey {
    private fun prefix(serverId: Uuid): String = "mcp.$serverId"

    fun oauthClientSecret(serverId: Uuid): String = "${prefix(serverId)}.oauth.client_secret"
    fun oauthAccessToken(serverId: Uuid): String = "${prefix(serverId)}.oauth.access_token"
    fun oauthRefreshToken(serverId: Uuid): String = "${prefix(serverId)}.oauth.refresh_token"

    fun headerValue(serverId: Uuid, index: Int, name: String): String {
        val normalized = name.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${prefix(serverId)}.header.$index.$digest"
    }
}

/**
 * Keeps MCP authentication material out of the Settings DataStore while
 * preserving ordinary MCP metadata there. All user-supplied header values are
 * treated as secret: custom MCP servers frequently use non-standard API-key
 * headers, so trying to guess which names are sensitive is unsafe.
 */
object McpSecretCodec {
    fun redactServersForWrite(
        previousServers: List<McpServerConfig>,
        servers: List<McpServerConfig>,
        store: SecondarySecretBackend,
    ): List<McpServerConfig>? {
        val values = linkedMapOf<String, String>()
        servers.forEach { server -> collectNonBlankSecrets(server, values) }

        val previousKeys = previousServers.flatMapTo(linkedSetOf()) { potentialSecretKeys(it) }
        val removals = previousKeys - values.keys

        if (!store.isAvailable()) {
            if (values.isNotEmpty()) return null
            if (previousServers != servers && previousKeys.isNotEmpty()) return null
        }

        if ((values.isNotEmpty() || removals.isNotEmpty()) && !store.replace(values, removals)) {
            return null
        }

        return servers.map(::redactServer)
    }

    fun hydrateServersFromStore(
        servers: List<McpServerConfig>,
        store: SecondarySecretBackend,
    ): List<McpServerConfig> = servers.map { server ->
        val oauth = server.commonOptions.oauth?.let { state ->
            state.copy(
                clientSecret = store.get(McpSecretKey.oauthClientSecret(server.id)) ?: state.clientSecret,
                accessToken = store.get(McpSecretKey.oauthAccessToken(server.id)) ?: state.accessToken,
                refreshToken = store.get(McpSecretKey.oauthRefreshToken(server.id)) ?: state.refreshToken,
            )
        }
        val headers = server.commonOptions.headers.mapIndexed { index, (name, value) ->
            name to (store.get(McpSecretKey.headerValue(server.id, index, name)) ?: value)
        }
        server.clone(commonOptions = server.commonOptions.copy(oauth = oauth, headers = headers))
    }

    private fun collectNonBlankSecrets(
        server: McpServerConfig,
        destination: MutableMap<String, String>,
    ) {
        server.commonOptions.oauth?.let { oauth ->
            oauth.clientSecret?.takeIf(String::isNotBlank)?.let {
                destination[McpSecretKey.oauthClientSecret(server.id)] = it
            }
            oauth.accessToken?.takeIf(String::isNotBlank)?.let {
                destination[McpSecretKey.oauthAccessToken(server.id)] = it
            }
            oauth.refreshToken?.takeIf(String::isNotBlank)?.let {
                destination[McpSecretKey.oauthRefreshToken(server.id)] = it
            }
        }
        server.commonOptions.headers.forEachIndexed { index, (name, value) ->
            if (value.isNotBlank()) {
                destination[McpSecretKey.headerValue(server.id, index, name)] = value
            }
        }
    }

    private fun potentialSecretKeys(server: McpServerConfig): Set<String> = buildSet {
        if (server.commonOptions.oauth != null) {
            add(McpSecretKey.oauthClientSecret(server.id))
            add(McpSecretKey.oauthAccessToken(server.id))
            add(McpSecretKey.oauthRefreshToken(server.id))
        }
        server.commonOptions.headers.forEachIndexed { index, (name, _) ->
            add(McpSecretKey.headerValue(server.id, index, name))
        }
    }

    private fun redactServer(server: McpServerConfig): McpServerConfig {
        val oauth = server.commonOptions.oauth?.copy(
            clientSecret = null,
            accessToken = null,
            refreshToken = null,
        )
        val headers = server.commonOptions.headers.map { (name, _) -> name to "" }
        return server.clone(commonOptions = server.commonOptions.copy(oauth = oauth, headers = headers))
    }
}
