package com.orchords.orchordsai.docs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins public README release/documentation links to the current repository contract. */
class ReadmeRepositoryLinksPolicyTest {

    private fun readme(): String {
        val appModule = File(".").canonicalFile
        val repoRoot = appModule.parentFile.canonicalFile
        val readme = repoRoot.resolve("README.md")
        require(readme.isFile) {
            "Expected repository README.md at ${readme.path}; app unit tests must run from the app module"
        }
        return readme.readText()
    }

    @Test
    fun `README points contributors at current release and build documentation`() {
        val readme = readme()

        assertTrue(
            "README must link the current build documentation",
            readme.contains("[Building ORCHORDS AI](docs/BUILDING.md)"),
        )
        assertTrue(
            "README must link the current release documentation",
            readme.contains("[Releasing ORCHORDS AI](docs/RELEASING.md)"),
        )
        assertFalse(
            "README must not advertise the retired Daily Build workflow",
            readme.contains("daily-build.yml"),
        )
        assertFalse(
            "README must not reference the legacy ORCHORDS/OrchordsAI repository path",
            readme.contains("ORCHORDS/OrchordsAI"),
        )
    }
}
