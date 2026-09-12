package com.orchords.orchordsai.buildconfig

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseR8MinifyPolicyTest {
    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory } ?: error("could not find app/src/main")

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `release build type enables R8 minification and resource shrinking`() {
        val build = source("app/build.gradle.kts")

        // Extract the release { ... } block. The next buildType closes at "        debug {".
        val releaseBlock = build
            .substringAfter("buildTypes {")
            .substringBefore("debug {")

        assertTrue(
            "release { must enable isMinifyEnabled = true so Play Store AABs are R8-shrunk",
            releaseBlock.contains("isMinifyEnabled = true")
        )
        assertTrue(
            "release { must enable isShrinkResources = true to keep AAB size in audit",
            releaseBlock.contains("isShrinkResources = true")
        )
        assertTrue(
            "release { must wire proguardFiles so reflection-based libraries stay usable",
            releaseBlock.contains("proguardFiles(")
        )
        assertTrue(
            "release { must reference the project's keep rules file",
            releaseBlock.contains("proguard-rules.pro")
        )
        assertTrue(
            "release { must use Android's optimize template (R8 baseline rules)",
            releaseBlock.contains("proguard-android-optimize")
        )
    }

    @Test
    fun `app declares a keep-rules file with reflection-based library coverage`() {
        val rules: String = source("app/proguard-rules.pro")
        assertTrue("proguard-rules.pro must exist", rules.isNotEmpty())

        // kotlinx.serialization: keep generated $serializer and synthetic Companion.
        assertTrue(
            "kotlinx.serialization keep rule must be present",
            rules.contains("kotlinx.serialization") ||
                rules.contains("serializer")
        )

        // Room: generated DAO impls rely on @Database / @Dao annotation retention
        // and on @TypeConverter lookups by name from generated code.
        assertTrue(
            "Room keep rule must be present",
            rules.contains("androidx.room") || rules.contains("Room") ||
                rules.contains("TypeConverter")
        )

        // MCP / orchords runtime: classes loaded reflectively from JSON config.
        assertTrue(
            "MCP / orchords runtime reflective keep rule must be present",
            rules.contains("mcp") || rules.contains("com.orchords.ai") ||
                rules.contains("com.orchords.orchordsai")
        )

        // JNI surface: keep names so libtermux.so and any other .so can resolve them.
        assertTrue(
            "JNI native-method keep rule must be present",
            rules.contains("native") || rules.contains("JNI")
        )
    }

    @Test
    fun `document module consumer rules keep MuPDF JNI bindings`() {
        val consumer = source("document/consumer-rules.pro")
        assertTrue(
            "MuPDF JNI bindings must be kept at consumer-rules level",
            consumer.contains("com.artifex.mupdf.fitz")
        )
    }
}
