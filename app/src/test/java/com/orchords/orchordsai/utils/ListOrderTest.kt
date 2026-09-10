package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ListOrderTest {
    @Test
    fun `moveListItem moves one item without mutating source`() {
        val source = listOf("alpha", "beta", "gamma")

        val moved = moveListItem(source, 0, 2)

        assertEquals(listOf("beta", "gamma", "alpha"), moved)
        assertEquals(listOf("alpha", "beta", "gamma"), source)
    }

    @Test
    fun `moveListItem returns original list when position is unchanged`() {
        val source = listOf("alpha", "beta")

        assertSame(source, moveListItem(source, 1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `moveListItem rejects invalid source index`() {
        moveListItem(listOf("alpha"), -1, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `moveListItem rejects invalid destination index`() {
        moveListItem(listOf("alpha"), 0, 1)
    }
}
