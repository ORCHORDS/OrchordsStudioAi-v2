package com.orchords.orchordsai.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeTypographyPolicyTest {
    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory } ?: error("could not find app/src/main")

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `shared code typography helper disables programming ligatures`() {
        val type = source("app/src/main/java/com/orchords/orchordsai/ui/theme/Type.kt")

        // Raw source contains Kotlin string escapes; assert on the literal source text.
        assertTrue(
            "Type.kt must export CODE_FONT_FEATURE_SETTINGS disabling liga and calt (Kotlin source-escaped)",
            type.contains("CODE_FONT_FEATURE_SETTINGS = \"\\\"liga\\\" 0, \\\"calt\\\" 0\"")
        )
        assertTrue(
            "Type.kt must expose a TextStyle.applyCodeFontFamily() helper",
            type.contains("fun TextStyle.applyCodeFontFamily(): TextStyle")
        )
        assertTrue(
            "applyCodeFontFamily must set fontFamily to JetbrainsMono",
            type.contains("fontFamily = JetbrainsMono")
        )
        assertTrue(
            "applyCodeFontFamily must apply CODE_FONT_FEATURE_SETTINGS",
            type.contains("fontFeatureSettings = CODE_FONT_FEATURE_SETTINGS")
        )
    }

    @Test
    fun `code-block line numbers and language label apply the no-ligature policy`() {
        val block = source("app/src/main/java/com/orchords/orchordsai/ui/components/richtext/HighlightCodeBlock.kt")

        assertTrue(
            "HighlightCodeBlock must import applyCodeFontFamily",
            block.contains("import com.orchords.orchordsai.ui.theme.applyCodeFontFamily")
        )

        // Strip the CodeHighlightText call sites — those carry the policy via
        // com.orchords.highlight.CODE_FONT_FEATURE_SETTINGS internally and don't
        // need the helper. We only assert on direct Text(...) callsites that
        // render line numbers and the language label.
        val codeHighlightTextBlocks = Regex(
            "CodeHighlightText\\([\\s\\S]*?\\n\\s*\\)",
        ).findAll(block).toList()
        val withoutCodeHighlight = codeHighlightTextBlocks.fold(block) { acc, m ->
            acc.replace(m.value, "")
        }

        val textBlocks = Regex(
            "\\bText\\([\\s\\S]*?\\n\\s*\\)",
        ).findAll(withoutCodeHighlight).toList()

        val codeSurfaces = textBlocks.filter { it.value.contains("fontFamily = JetbrainsMono") }
        assertTrue(
            "Expected at least three direct Text(...) callsites using JetbrainsMono (line numbers + language label); got ${codeSurfaces.size}",
            codeSurfaces.size >= 3
        )
        codeSurfaces.forEach { match ->
            assertTrue(
                "Every JetbrainsMono Text() must apply the no-ligature policy via applyCodeFontFamily; offending call: ${match.value}",
                match.value.contains("applyCodeFontFamily")
            )
        }
    }

    @Test
    fun `highlight module keeps internal ligature suppression in CodeHighlightText`() {
        val highlighter = source("highlight/src/main/java/com/orchords/highlight/Highlighter.kt")

        assertTrue(
            "CodeHighlightText must apply CODE_FONT_FEATURE_SETTINGS internally",
            highlighter.contains("TextStyle(fontFeatureSettings = CODE_FONT_FEATURE_SETTINGS)")
        )
        assertTrue(
            "highlight module must declare CODE_FONT_FEATURE_SETTINGS constant",
            highlighter.contains("const val CODE_FONT_FEATURE_SETTINGS")
        )
        // Raw Kotlin source contains string escapes for the embedded quotes.
        assertTrue(
            "highlight module ligature suppression must disable liga and calt (Kotlin source-escaped)",
            highlighter.contains("\"\\\"liga\\\" 0, \\\"calt\\\" 0\"")
        )
    }
}
