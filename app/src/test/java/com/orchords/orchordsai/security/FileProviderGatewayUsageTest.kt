package com.orchords.orchordsai.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileProviderGatewayUsageTest {
    @Test
    fun `production FileProvider URI creation stays behind authorized gateway`() {
        val moduleDir = File(".").canonicalFile
        val sourceRoot = moduleDir.resolve("src/main/java").canonicalFile
        require(sourceRoot.isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }

        val gateway = sourceRoot
            .resolve("com/orchords/orchordsai/data/files/AuthorizedShareUris.kt")
            .canonicalFile
        require(gateway.isFile) { "Authorized FileProvider gateway is missing" }

        val bypasses = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.canonicalFile != gateway }
            .filter { it.readText().contains("FileProvider.getUriForFile") }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Direct FileProvider URI creation must stay inside AuthorizedShareUris.kt: $bypasses",
            bypasses.isEmpty(),
        )
    }
}
