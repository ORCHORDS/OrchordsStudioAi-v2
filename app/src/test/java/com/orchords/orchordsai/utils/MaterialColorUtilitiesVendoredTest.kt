package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locks the contract that `:material3` builds without any submodule bootstrap.
 *
 * Pins:
 *  - No Git submodule metadata (`.gitmodules`) and no Git-tracked submodule
 *    path under `material3/material-color-utilities/`. The check is on
 *    `git ls-files`, not on working-tree presence, so an untracked leftover
 *    directory on a persistent runner is not a false positive.
 *  - All required `material-color-utilities` Kotlin sources are present under
 *    `material3/src/main/java/<package>/` so the `:material3` Android library
 *    compiles against them as part of its main sourceset.
 *  - The vendored LICENSE and README exist next to the vendored sources for
 *    Apache-2.0 attribution.
 *  - Contributor-facing docs (CONTRIBUTING.md, docs/BUILDING.md) reflect that
 *    a plain `git clone` is sufficient.
 */
class MaterialColorUtilitiesVendoredTest {

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        repeat(8) {
            if (dir.resolve("material3/build.gradle.kts").isFile) return dir
            val parent = dir.parentFile ?: return dir
            dir = parent
        }
        return dir
    }

    @Test
    fun `no gitmodules and no tracked submodule path`() {
        // The semantic contract is that the Git tree no longer wires
        // `material-color-utilities` in as a submodule. Asserting against
        // `git ls-files` (rather than a directory's on-disk presence) keeps the
        // check correct on persistent self-hosted runners whose workspaces may
        // still hold an untracked directory from a prior `actions/checkout`
        // run with `clean: false`. A fresh `git clone` will not see that
        // directory at all.
        val root = repoRoot()
        assertFalse(
            "Repository must not declare a .gitmodules file (vendoring replaces the submodule). " +
                "Found at ${root.resolve(".gitmodules")}",
            root.resolve(".gitmodules").isFile,
        )
        val lsFiles = runCatching {
            val proc = ProcessBuilder("git", "ls-files")
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { output.append(it).append('\n') }
                }
            }.apply { start() }
            try {
                check(proc.waitFor(30, TimeUnit.SECONDS)) { "git ls-files timed out" }
                reader.join(5_000)
                check(proc.exitValue() == 0) { "git ls-files exited ${proc.exitValue()}" }
                output.toString()
            } finally {
                if (proc.isAlive) proc.destroyForcibly()
                reader.join(5_000)
            }
        }.getOrDefault("")
        assertFalse(
            "Git tree must not track material3/material-color-utilities as a submodule. " +
                "Found entries:\n" + lsFiles.lineSequence()
                .filter { it.startsWith("material3/material-color-utilities") }
                .joinToString("\n").ifEmpty { "(none)" },
            lsFiles.lineSequence().any { it.startsWith("material3/material-color-utilities") },
        )
    }

    @Test
    fun `all required vendored Kotlin sources are present`() {
        val root = repoRoot()
        val required = listOf(
            "dynamiccolor/ColorSpec.kt",
            "dynamiccolor/ColorSpec2021.kt",
            "dynamiccolor/ColorSpec2025.kt",
            "dynamiccolor/ColorSpec2026.kt",
            "dynamiccolor/ColorSpecs.kt",
            "dynamiccolor/ContrastCurve.kt",
            "dynamiccolor/DynamicColor.kt",
            "dynamiccolor/DynamicScheme.kt",
            "dynamiccolor/MaterialDynamicColors.kt",
            "dynamiccolor/ToneDeltaPair.kt",
            "dynamiccolor/Variant.kt",
            "hct/Cam16.kt",
            "hct/Hct.kt",
            "hct/HctSolver.kt",
            "hct/ViewingConditions.kt",
            "palettes/CorePalettes.kt",
            "palettes/TonalPalette.kt",
            "quantize/PointProvider.kt",
            "quantize/PointProviderLab.kt",
            "quantize/Quantizer.kt",
            "quantize/QuantizerCelebi.kt",
            "quantize/QuantizerMap.kt",
            "quantize/QuantizerResult.kt",
            "quantize/QuantizerWsmeans.kt",
            "quantize/QuantizerWu.kt",
            "scheme/SchemeCmf.kt",
            "scheme/SchemeContent.kt",
            "scheme/SchemeExpressive.kt",
            "scheme/SchemeFidelity.kt",
            "scheme/SchemeFruitSalad.kt",
            "scheme/SchemeMonochrome.kt",
            "scheme/SchemeNeutral.kt",
            "scheme/SchemeRainbow.kt",
            "scheme/SchemeTonalSpot.kt",
            "scheme/SchemeVibrant.kt",
            "score/Score.kt",
            "temperature/TemperatureCache.kt",
            "utils/ColorUtils.kt",
            "utils/MathUtils.kt",
            "utils/StringUtils.kt",
            "blend/Blend.kt",
            "contrast/Contrast.kt",
            "dislike/DislikeAnalyzer.kt",
        )
        val base = root.resolve("material3/src/main/java")
        for (rel in required) {
            val f = base.resolve(rel)
            assertTrue("Missing vendored source: $rel", f.isFile)
        }
        assertEquals(43, required.size)
    }

    @Test
    fun `material3 build script no longer guards a submodule`() {
        val root = repoRoot()
        val script = root.resolve("material3/build.gradle.kts").readText()
        assertFalse(
            "material3/build.gradle.kts must not include a runtime submodule guard `check(...)`",
            Regex("""check\s*\(""", RegexOption.MULTILINE).containsMatchIn(script),
        )
        assertFalse(
            "material3/build.gradle.kts must not call `git submodule update`",
            script.contains("git submodule update"),
        )
        assertFalse(
            "material3/build.gradle.kts must not add material-color-utilities as an extra source directory",
            Regex("""srcDir\s*\(\s*\"material-color-utilities"""").containsMatchIn(script),
        )
    }

    @Test
    fun `vendored license and provenance docs exist`() {
        val root = repoRoot()
        val license = root.resolve("material3/third-party/material-color-utilities/LICENSE")
        val readme = root.resolve("material3/third-party/material-color-utilities/README.md")
        assertTrue("Vendored LICENSE must exist at $license", license.isFile)
        assertTrue("Vendored provenance README must exist at $readme", readme.isFile)
        val thirdPartyNotices = root.resolve("THIRD_PARTY_NOTICES.md").readText()
        assertTrue(
            "THIRD_PARTY_NOTICES.md must reference the vendored material-color-utilities location",
            thirdPartyNotices.contains("material3/third-party/material-color-utilities/LICENSE"),
        )
    }

    @Test
    fun `contributor docs say plain git clone is sufficient`() {
        val root = repoRoot()
        val contributing = root.resolve("CONTRIBUTING.md").readText()
        val building = root.resolve("docs/BUILDING.md").readText()
        for ((label, doc) in listOf("CONTRIBUTING.md" to contributing, "docs/BUILDING.md" to building)) {
            assertFalse(
                "$label must not require `--recurse-submodules` after vendoring",
                doc.contains("--recurse-submodules"),
            )
            assertFalse(
                "$label must not require `git submodule update` after vendoring",
                doc.contains("git submodule update"),
            )
            assertTrue(
                "$label must state that a plain `git clone` is sufficient",
                doc.contains("git clone"),
            )
        }
    }
}
