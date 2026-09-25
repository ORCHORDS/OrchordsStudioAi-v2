package com.orchords.orchordsai.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCatalogChangeCapabilityTest {
    @Test
    fun `tool list change refresh requires explicit advertised capability`() {
        assertFalse(supportsToolListChanges(null))
        assertFalse(supportsToolListChanges(ServerCapabilities()))
        assertFalse(
            supportsToolListChanges(
                ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
            )
        )
        assertTrue(
            supportsToolListChanges(
                ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
            )
        )
    }
}
