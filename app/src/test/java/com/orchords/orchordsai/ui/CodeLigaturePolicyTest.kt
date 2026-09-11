package com.orchords.orchordsai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLigaturePolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `highlighted code and workspace editor disable programming ligatures`() {
        val highlighter = source("highlight/src/main/java/com/orchords/highlight/Highlighter.kt")
        val editor = source("app/src/main/java/com/orchords/orchordsai/ui/pages/extensions/workspace/WorkspaceFileEditorPage.kt")

        assertTrue(highlighter.contains("CODE_FONT_FEATURE_SETTINGS = \"\\\"liga\\\" 0, \\\"calt\\\" 0\""))
        assertTrue(highlighter.contains("fontFeatureSettings = CODE_FONT_FEATURE_SETTINGS"))
        assertTrue(editor.contains("fontFeatureSettings = CODE_FONT_FEATURE_SETTINGS"))
    }

    @Test
    fun `web markdown code disables equivalent ligatures`() {
        val markdown = source("web-ui/app/components/markdown/markdown.tsx")
        val css = source("web-ui/app/components/markdown/code-ligatures.css")

        assertTrue(markdown.contains("import \"./code-ligatures.css\""))
        assertTrue(css.contains("font-variant-ligatures: none"))
        assertTrue(css.contains("font-feature-settings: \"liga\" 0, \"calt\" 0"))
    }
}
