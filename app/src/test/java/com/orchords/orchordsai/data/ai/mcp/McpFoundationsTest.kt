package com.orchords.orchordsai.data.ai.mcp

import kotlinx.coroutines.test.runTest
import org.junit.Test

class McpFoundationsTest {
    @Test
    fun `catalog policy satisfies bounded pagination contracts`() = runTest {
        runMcpFoundationContracts()
    }
}
