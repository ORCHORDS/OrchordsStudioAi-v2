package com.orchords.orchordsai.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivateRunnerToolchainPolicyTest {
    private val repoRoot = File("..").canonicalFile

    private fun source(path: String): String {
        val file = repoRoot.resolve(path)
        require(file.isFile) { "$path not found from ${repoRoot.canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `private android lanes bootstrap the same pinned toolchain`() {
        val actionPath = ".github/actions/setup-private-android-toolchain/action.yml"
        val main = source(".github/workflows/main-verification.yml")
        val release = source(".github/workflows/daily-build.yml")
        val action = source(actionPath)

        assertTrue(main.contains("uses: ./.github/actions/setup-private-android-toolchain"))
        assertTrue(release.contains("uses: ./.github/actions/setup-private-android-toolchain"))
        assertTrue(action.contains("java-version: '21'"))
        assertTrue(action.contains("node-version: '22'"))
        assertTrue(action.contains("pnpm@11"))
        assertTrue(action.contains("cmdline_version='15859902'"))
        assertTrue(action.contains("'platforms;android-37'"))
        assertTrue(action.contains("'build-tools;36.0.0'"))
        assertTrue(action.contains("'ndk;28.2.13676358'"))
        assertTrue(action.contains("'cmake;3.22.1'"))
        assertTrue(action.contains("4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"))
    }
}
