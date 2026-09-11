package com.orchords.orchordsai.ui.pages.extensions.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDnsPolicyTest {
    @Test
    fun `uses active system dns when private dns is inactive`() {
        assertEquals(
            listOf("10.0.0.53", "2001:db8::53"),
            resolveWorkspaceDnsPolicy(
                WorkspaceDnsSnapshot(
                    nameservers = listOf("10.0.0.53", "2001:db8::53"),
                    privateDnsActive = false,
                )
            )
        )
    }

    @Test
    fun `fails closed instead of downgrading active private dns`() {
        val error = assertThrows(IllegalStateException::class.java) {
            resolveWorkspaceDnsPolicy(
                WorkspaceDnsSnapshot(
                    nameservers = listOf("10.0.0.53"),
                    privateDnsActive = true,
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Private DNS"))
    }

    @Test
    fun `normalizes and deduplicates resolver addresses`() {
        assertEquals(
            listOf("10.0.0.53"),
            resolveWorkspaceDnsPolicy(
                WorkspaceDnsSnapshot(
                    nameservers = listOf("", " 10.0.0.53 ", "10.0.0.53"),
                    privateDnsActive = false,
                )
            )
        )
    }

    @Test
    fun `no active dns leaves resolver list empty`() {
        assertTrue(
            resolveWorkspaceDnsPolicy(
                WorkspaceDnsSnapshot(emptyList(), privateDnsActive = false)
            ).isEmpty()
        )
    }
}
