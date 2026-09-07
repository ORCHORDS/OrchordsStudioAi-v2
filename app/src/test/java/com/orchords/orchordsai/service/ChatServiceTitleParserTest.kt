package com.orchords.orchordsai.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract for [pickFirstTitleLine] used by [ChatService.generateTitle].
 *
 * Background: the title prompt instructs the model to reply with a single title, but small
 * models frequently return a numbered list of candidate titles ("1. Foo\n2. Bar…"). When
 * that happens the previous implementation stored the whole blob as the chat title,
 * surfacing the numbered list verbatim. This test pins the parsing behavior.
 */
class ChatServiceTitleParserTest {
    @Test
    fun `numbered list of candidates returns the first entry`() {
        val raw = """
            1. Ping Pong
            2. Greeting Exchange
            3. Friendly Reply
        """.trimIndent()
        assertEquals("Ping Pong", pickFirstTitleLine(raw))
    }

    @Test
    fun `bullet list returns the first entry`() {
        val raw = """
            - Hello There
            * Greetings
            • Hi
        """.trimIndent()
        assertEquals("Hello There", pickFirstTitleLine(raw))
    }

    @Test
    fun `single line is returned unchanged`() {
        assertEquals("Weather in Tokyo", pickFirstTitleLine("Weather in Tokyo"))
    }

    @Test
    fun `markdown code fence is stripped before parsing`() {
        val raw = """
            ```
            Title in a fence
            ```
        """.trimIndent()
        assertEquals("Title in a fence", pickFirstTitleLine(raw))
    }

    @Test
    fun `truncates at max length and trims trailing punctuation`() {
        val raw = "This is a deliberately overlong title candidate that should be cut short."
        val out = pickFirstTitleLine(raw)
        assertTrue("must be trimmed under max length", out.length <= 50)
        // substring(0,50).trim() drops the trailing space → 49 chars, then trimEnd('.') drops the period.
        assertEquals(
            "This is a deliberately overlong title candidate th",
            out,
        )
    }

    @Test
    fun `returns empty string for blank input`() {
        assertEquals("", pickFirstTitleLine(""))
        assertEquals("", pickFirstTitleLine("   \n\n  "))
        assertEquals("", pickFirstTitleLine("```\n```"))
    }

    @Test
    fun `chinese punctuation is trimmed`() {
        assertEquals("你好世界", pickFirstTitleLine("你好世界。"))
    }
}
