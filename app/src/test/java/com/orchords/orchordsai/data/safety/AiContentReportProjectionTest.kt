package com.orchords.orchordsai.data.safety

import com.orchords.ai.core.MessageRole
import com.orchords.ai.ui.ToolApprovalState
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContentReportProjectionTest {
    @Test
    fun `report projection includes rendered text and excludes reasoning tools and attachments`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("Visible assistant answer"),
                UIMessagePart.Reasoning("private-reasoning-sentinel"),
                UIMessagePart.Image("file:///private/image-sentinel.png"),
                UIMessagePart.Document(
                    url = "file:///private/document-sentinel.txt",
                    fileName = "document-sentinel.txt",
                ),
                UIMessagePart.Tool(
                    toolCallId = "tool-1",
                    toolName = "secret_tool",
                    input = "{\"secret\":\"tool-input-sentinel\"}",
                    output = listOf(UIMessagePart.Text("tool-output-sentinel")),
                    approvalState = ToolApprovalState.Approved,
                ),
                UIMessagePart.Text("Second visible paragraph"),
            ),
        )

        val output = visibleAssistantOutputForReport(message)

        assertEquals("Visible assistant answer\n\nSecond visible paragraph", output)
        assertFalse(output.contains("private-reasoning-sentinel"))
        assertFalse(output.contains("image-sentinel"))
        assertFalse(output.contains("document-sentinel"))
        assertFalse(output.contains("tool-input-sentinel"))
        assertFalse(output.contains("tool-output-sentinel"))
    }

    @Test
    fun `payload bounds note metadata and report id`() {
        val payload = buildAiContentReportPayload(
            message = UIMessage.assistant("visible"),
            category = "harmful",
            note = "n".repeat(2000),
            model = "m".repeat(400),
            provider = "p".repeat(400),
            reportId = "r".repeat(400),
        )

        assertEquals(1200, payload.note.length)
        assertEquals(160, payload.model.length)
        assertEquals(160, payload.provider.length)
        assertEquals(160, payload.reportId.length)
        assertEquals("visible", payload.output)
    }

    @Test
    fun `large rendered output is bounded before network submission`() {
        val payload = buildAiContentReportPayload(
            message = UIMessage.assistant("x".repeat(7000)),
            category = "other",
            note = "",
            model = "",
            provider = "",
        )

        assertEquals(6000, payload.output.length)
        assertTrue(payload.output.all { it == 'x' })
    }
}
