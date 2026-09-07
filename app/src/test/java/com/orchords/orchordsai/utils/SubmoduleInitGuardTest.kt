package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the contract that a clean `git clone` (without `--recurse-submodules`) cannot
 * silently fall through into a confusing unresolved-reference cascade in
 * `material3:compileDebugKotlin`. It does so by pinning both the
 * `material-color-utilities` source file the runtime needs and the human-readable
 * `check(...)` message that Gradle will surface when the submodule is missing.
 */
class SubmoduleInitGuardTest {

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        // Walk up until we find the sibling `material3/build.gradle.kts`. The :app
        // unit-test working directory is the `:app` module, so we need to climb.
        repeat(8) {
            if (dir.resolve("material3/build.gradle.kts").isFile) return dir
            val parent = dir.parentFile ?: return dir
            dir = parent
        }
        return dir
    }

    @Test
    fun `material-color-utilities submodule is initialised`() {
        val root = repoRoot()
        val dynamicScheme = root.resolve("material3/material-color-utilities/kotlin/dynamiccolor/DynamicScheme.kt")
        assertTrue(
            "Expected submodule file at $dynamicScheme. " +
                "Run `git submodule update --init --recursive` from the repo root.",
            dynamicScheme.isFile,
        )
    }

    @Test
    fun `material3 Gradle script guards submodule init with an actionable message`() {
        val root = repoRoot()
        val script = root.resolve("material3/build.gradle.kts").readText()
        assertTrue(
            "material3/build.gradle.kts must call check(...) on the submodule directory",
            script.contains("check(") && script.contains("material-color-utilities"),
        )
        assertTrue(
            "Guard message must instruct the contributor to run `git submodule update --init --recursive`",
            script.contains("git submodule update --init --recursive"),
        )
        assertTrue(
            "Guard message must point to the expected DynamicScheme.kt file",
            script.contains("kotlin/dynamiccolor/DynamicScheme.kt"),
        )
    }

    @Test
    fun `contributing doc requires recursive submodule clone`() {
        val root = repoRoot()
        val contributing = root.resolve("CONTRIBUTING.md").readText()
        assertTrue(
            "CONTRIBUTING.md must document `git clone --recurse-submodules`",
            contributing.contains("--recurse-submodules") &&
                contributing.contains("git submodule update --init --recursive"),
        )
        val building = root.resolve("docs/BUILDING.md").readText()
        assertEquals(
            "docs/BUILDING.md must keep contributor copy aligned with CONTRIBUTING.md",
            true,
            building.contains("--recurse-submodules") &&
                building.contains("git submodule update --init --recursive"),
        )
    }
}
