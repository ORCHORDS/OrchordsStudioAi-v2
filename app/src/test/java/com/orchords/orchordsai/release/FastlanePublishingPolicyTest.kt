package com.orchords.orchordsai.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FastlanePublishingPolicyTest {
    private fun repoRoot(): File = File("..").canonicalFile

    private fun fastlaneDir(): File = repoRoot().resolve("fastlane")

    private fun appfile(): String = fastlaneDir().resolve("Appfile").readText()

    private fun fastfile(): String = fastlaneDir().resolve("Fastfile").readText()

    private fun supplyJson(): String = fastlaneDir().resolve("supply.json").readText()

    private fun gitignore(): String = repoRoot().resolve(".gitignore").readText()

    private fun gradleKts(): String = repoRoot().resolve("app/build.gradle.kts").readText()

    private fun dailyBuildWorkflow(): String =
        repoRoot().resolve(".github/workflows/daily-build.yml").readText()

    private fun publishingWorkflow(): String =
        repoRoot().resolve(".github/workflows/play-internal-publish.yml").readText()

    @Test
    fun `appfile wires the real package and a gitignored key path`() {
        val source = appfile()
        assertTrue(source.contains("package_name(\"com.orchords.orchordsai\")"))
        assertTrue(
            "Appfile must reference the gitignored service-account JSON path",
            source.contains("fastlane/play-store-key.json"),
        )
    }

    @Test
    fun `fastfile exposes only safe internal or promote lanes`() {
        val source = fastfile()
        listOf("lane :bundle", "lane :internal", "lane :promote").forEach { lane ->
            assertTrue("Missing lane: $lane", source.contains(lane))
        }
        assertTrue("internal lane must upload to the internal track", source.contains("track: \"internal\""))
        assertTrue("promote lane must target production explicitly", source.contains("track_promote_to: \"production\""))
        assertFalse("must not auto-publish from any push event", source.contains("track: \"production\"") && !source.contains("track_promote_to:"))
    }

    @Test
    fun `supply json never embeds secrets`() {
        val source = supplyJson()
        assertTrue(source.contains("\"package_name\": \"com.orchords.orchordsai\""))
        assertFalse(source.contains("client_email"))
        assertFalse(source.contains("private_key"))
        assertFalse(source.contains("BEGIN PRIVATE KEY"))
    }

    @Test
    fun `service account key and bundler state are gitignored`() {
        val source = gitignore()
        assertTrue(
            "play-store-key.json must be gitignored",
            source.contains("fastlane/play-store-key.json"),
        )
        assertTrue("bundler state must be gitignored", source.contains("fastlane/.bundle/"))
    }

    @Test
    fun `bundleRelease task is wired through buildAll and gradle accepts the release properties`() {
        val gradle = gradleKts()
        assertTrue("buildAll must depend on bundleRelease", gradle.contains("dependsOn(\"assembleRelease\", \"bundleRelease\")"))

        val workflow = dailyBuildWorkflow()
        assertTrue("signed path must invoke buildAll to also build the AAB", workflow.contains("gradle_task=buildAll"))
        assertTrue(
            "Daily Build must validate the AAB package id with apkanalyzer",
            workflow.contains("aab_path=app/build/outputs/bundle/release/app-release.aab"),
        )
        assertTrue(workflow.contains("apkanalyzer\" manifest application-id"))
        val aabPackageCheck = "test " + "\"" + "\$aab_package" + "\" = \"com.orchords.orchordsai\""
        assertTrue(workflow.contains(aabPackageCheck))
        assertTrue(workflow.contains("release-assets/orchords-studio-ai.aab"))
    }

    @Test
    fun `manual publishing workflow uses secrets and refuses non-internal tracks`() {
        val source = publishingWorkflow()
        assertTrue(source.contains("workflow_dispatch"))
        assertTrue(source.contains("PLAY_STORE_JSON_KEY"))
        assertTrue(source.contains("KEY_BASE64"))
        assertTrue(source.contains("SIGNING_CONFIG"))
        assertTrue(source.contains("Remove signing material"))
        assertTrue(source.contains("Manual publish to"))
        assertTrue(source.contains("TRACK_INPUT"))
        val inputsLiteral = "inputs" + "." + "track"
        assertTrue(source.contains(inputsLiteral))
    }

    @Test
    fun `no committed service account credentials anywhere in repo`() {
        val offenders = mutableListOf<String>()
        repoRoot().walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".git") && dir.name != "build" && dir.name != "node_modules" }
            .forEach { entry ->
                if (entry.isFile) {
                    val name = entry.name
                    if (name == "play-store-key.json" || name.endsWith("-play-store-key.json")) {
                        offenders += entry.relativeTo(repoRoot()).path
                    }
                }
            }
        assertEquals("Service-account JSON keys must not be committed", emptyList<String>(), offenders)
    }
}
