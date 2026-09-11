package com.orchords.orchordsai.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexSafeSegmenterTest {
    @Test
    fun `scripts groups commands and incomplete input stay atomic`() {
        assertEquals(listOf("x^{n+1}"), segmentLatexForWrapping("x^{n+1}"))
        assertEquals(listOf("a_{i-1}", "+b"), segmentLatexForWrapping("a_{i-1}+b"))
        assertEquals(listOf("x^{a_{i+1}+b}", "+c"), segmentLatexForWrapping("x^{a_{i+1}+b}+c"))
        assertEquals(listOf("x^+ ", "+ y^-"), segmentLatexForWrapping("x^+ + y^-"))
        assertEquals(listOf("\\frac{a+b}{c-d}", "=z"), segmentLatexForWrapping("\\frac{a+b}{c-d}=z"))
        assertEquals(listOf("\\sqrt[n+1]{x+y}", "+z"), segmentLatexForWrapping("\\sqrt[n+1]{x+y}+z"))
        assertEquals(listOf("\\text{cost + tax}", "=x"), segmentLatexForWrapping("\\text{cost + tax}=x"))
        assertEquals(listOf("x^{n+1"), segmentLatexForWrapping("x^{n+1"))
    }

    @Test
    fun `only top level operators become wrap points`() {
        assertEquals(listOf("a", "+b", "+c"), segmentLatexForWrapping("a+b+c"))
        assertEquals(
            listOf("\\begin{matrix}a+b&c-d\\end{matrix}+z"),
            segmentLatexForWrapping("\\begin{matrix}a+b&c-d\\end{matrix}+z"),
        )
    }
}
