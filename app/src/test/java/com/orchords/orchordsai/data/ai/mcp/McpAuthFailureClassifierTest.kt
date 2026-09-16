package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpAuthFailureClassifierTest {
    @Test
    fun `401 and invalid token are unauthorized`() {
        assertTrue(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 401 Unauthorized")))
        assertTrue(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("invalid_token")))
        assertFalse(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("HTTP 401 Unauthorized")))
    }

    @Test
    fun `403 and insufficient scope are permission denied`() {
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("HTTP 403 Forbidden")))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("insufficient_scope")))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("Resource not accessible by integration")))
        assertFalse(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 403 Forbidden")))
    }

    @Test
    fun `invalid grant refresh errors require reauthorization`() {
        assertTrue(McpAuthFailureClassifier.isInvalidGrant(IllegalStateException("invalid_grant")))
        assertTrue(McpAuthFailureClassifier.isInvalidGrant(IllegalStateException("refresh token revoked")))
        assertFalse(McpAuthFailureClassifier.isInvalidGrant(IllegalStateException("HTTP 500")))
    }

    @Test
    fun `classification scans nested causes`() {
        val error = IllegalStateException("outer", IllegalArgumentException("insufficient scope"))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(error))
    }
}
