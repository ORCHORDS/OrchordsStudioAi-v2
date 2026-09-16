package com.orchords.orchordsai.data.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiContentReportUiPolicyTest {
    private fun source(relative: String): String {
        val file = File("src/main/java/com/orchords/orchordsai/$relative")
        require(file.isFile) { "Missing source file: ${file.canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `assistant message sheet exposes in-app report action only for visible assistant text`() {
        val actions = source("ui/components/message/ChatMessageActions.kt")

        assertTrue(actions.contains("message.role == MessageRole.ASSISTANT && hasTextContent"))
        assertTrue(actions.contains("Report AI output"))
        assertTrue(actions.contains("showReportDialog = true"))
        assertTrue(actions.contains("AiContentReportDialog("))
    }

    @Test
    fun `report dialog discloses minimal payload and never launches external reporting`() {
        val dialog = source("ui/components/message/AiContentReportDialog.kt")

        assertTrue(dialog.contains("does not send the rest of the conversation"))
        assertTrue(dialog.contains("hidden reasoning/tool traces"))
        assertTrue(dialog.contains("Send report"))
        assertFalse(dialog.contains("Intent.ACTION_SEND"))
        assertFalse(dialog.contains("openUrl("))
        assertFalse(dialog.contains("mailto:"))
    }

    @Test
    fun `network projection uses visible text only and fixed first-party endpoint`() {
        val client = source("data/safety/AiContentReportClient.kt")

        assertTrue(client.contains("https://orchords.com/api/ai-report"))
        assertTrue(client.contains("filterIsInstance<UIMessagePart.Text>()"))
        assertFalse(client.contains("UIMessagePart.Reasoning"))
        assertFalse(client.contains("UIMessagePart.Tool("))
        assertFalse(client.contains("message.annotations"))
    }
}
