package com.orchords.orchordsai.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolSchemaSnapshotTest {
    // Regression coverage for architecture issue #23.
    @Test
    fun `catalog refresh preserves declared output schema and local policy`() {
        val serverTool = Tool(
            name = "weather",
            description = "Returns current weather",
            inputSchema = ToolSchema(
                schema = "https://json-schema.org/draft/2020-12/schema",
                properties = buildJsonObject {
                    put("city", buildJsonObject {
                        put("\$ref", "#/\$defs/City")
                    })
                },
                required = listOf("city"),
                defs = buildJsonObject {
                    put("City", buildJsonObject { put("type", "string") })
                },
            ),
            outputSchema = ToolSchema(
                schema = "https://json-schema.org/draft/2020-12/schema",
                properties = buildJsonObject {
                    put("temperature", buildJsonObject { put("type", "number") })
                },
                required = listOf("temperature"),
                defs = buildJsonObject {
                    put("Unit", buildJsonObject { put("type", "string") })
                },
            ),
        )
        val stored = McpTool(
            name = "weather",
            enable = false,
            needsApproval = true,
        )

        val merged = mergeTools(
            storedTools = listOf(stored),
            serverTools = listOf(serverTool),
        ).single()

        assertFalse(merged.enable)
        assertTrue(merged.needsApproval)

        val input = merged.inputSchema as com.orchords.ai.core.InputSchema.Obj
        assertEquals("https://json-schema.org/draft/2020-12/schema", input.schema)
        assertEquals(
            "#/\$defs/City",
            input.properties["city"]?.jsonObject?.get("\$ref")?.jsonPrimitive?.content,
        )
        assertEquals(
            "string",
            input.defs?.get("City")?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )

        val schema = requireNotNull(merged.outputSchema)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)
        assertEquals(
            "https://json-schema.org/draft/2020-12/schema",
            schema["\$schema"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "number",
            schema["properties"]
                ?.jsonObject
                ?.get("temperature")
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "temperature",
            schema["required"]?.jsonArray?.single()?.jsonPrimitive?.content,
        )
        assertEquals(
            "string",
            schema["\$defs"]
                ?.jsonObject
                ?.get("Unit")
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
    }
}
