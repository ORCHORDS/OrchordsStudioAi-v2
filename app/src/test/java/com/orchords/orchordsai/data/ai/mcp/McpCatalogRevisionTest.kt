package com.orchords.orchordsai.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TaskSupport
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolExecution
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCatalogRevisionTest {
    private fun schema(
        first: Pair<String, String> = "city" to "string",
        second: Pair<String, String> = "units" to "string",
        required: List<String> = listOf(first.first, second.first),
    ) = ToolSchema(
        schema = "https://json-schema.org/draft/2020-12/schema",
        properties = buildJsonObject {
            put(first.first, buildJsonObject { put("type", first.second) })
            put(second.first, buildJsonObject { put("type", second.second) })
        },
        required = required,
        defs = buildJsonObject {
            put("Unit", buildJsonObject { put("enum", "metric") })
        },
    )

    private fun tool(
        input: ToolSchema = schema(),
        readOnly: Boolean? = true,
        taskSupport: TaskSupport? = TaskSupport.Forbidden,
    ) = Tool(
        name = "weather",
        inputSchema = input,
        outputSchema = ToolSchema(
            properties = buildJsonObject {
                put("temperature", buildJsonObject { put("type", "number") })
            },
        ),
        annotations = ToolAnnotations(readOnlyHint = readOnly),
        execution = ToolExecution(taskSupport = taskSupport),
    )

    @Test
    fun `schema object and required ordering do not change fingerprint`() {
        val left = tool(schema(
            first = "city" to "string",
            second = "units" to "string",
            required = listOf("city", "units"),
        ))
        val right = tool(schema(
            first = "units" to "string",
            second = "city" to "string",
            required = listOf("units", "city"),
        ))

        assertEquals(mcpToolContractFingerprint(left), mcpToolContractFingerprint(right))
    }

    @Test
    fun `schema effect and task declarations change fingerprint`() {
        val baseline = tool()
        assertNotEquals(
            mcpToolContractFingerprint(baseline),
            mcpToolContractFingerprint(tool(input = schema(first = "city" to "integer"))),
        )
        assertNotEquals(
            mcpToolContractFingerprint(baseline),
            mcpToolContractFingerprint(tool(readOnly = false)),
        )
        assertNotEquals(
            mcpToolContractFingerprint(baseline),
            mcpToolContractFingerprint(tool(taskSupport = TaskSupport.Required)),
        )
    }

    @Test
    fun `catalog revision is order independent but changes on membership change`() {
        val a = tool()
        val b = a.copy(name = "forecast")
        assertEquals(mcpCatalogRevision(listOf(a, b)), mcpCatalogRevision(listOf(b, a)))
        assertNotEquals(mcpCatalogRevision(listOf(a)), mcpCatalogRevision(listOf(a, b)))
    }

    @Test
    fun `existing fingerprint change requires fresh approval`() {
        val original = tool()
        val originalFingerprint = mcpToolContractFingerprint(original)
        val stored = McpTool(
            name = original.name,
            needsApproval = false,
            schemaFingerprint = originalFingerprint,
        )
        val changed = tool(readOnly = false)

        val merged = mergeTools(listOf(stored), listOf(changed)).single()

        assertTrue(merged.needsApproval)
        assertNotEquals(originalFingerprint, merged.schemaFingerprint)
    }

    @Test
    fun `legacy tool without fingerprint migrates without inventing approval`() {
        val merged = mergeTools(
            storedTools = listOf(McpTool(name = "weather", needsApproval = false)),
            serverTools = listOf(tool()),
        ).single()

        assertFalse(merged.needsApproval)
        assertTrue(merged.schemaFingerprint?.startsWith("sha256:") == true)
    }

    @Test
    fun `supported input schema document is retained alongside provider projection`() {
        val merged = mergeTools(emptyList(), listOf(tool())).single()
        val document = requireNotNull(merged.inputSchemaDocument)

        assertEquals("object", document["type"]?.jsonPrimitive?.content)
        assertEquals(
            "https://json-schema.org/draft/2020-12/schema",
            document["\$schema"]?.jsonPrimitive?.content,
        )
        assertEquals("metric", document["\$defs"]?.jsonObject
            ?.get("Unit")?.jsonObject?.get("enum")?.jsonPrimitive?.content)
    }
}
