package com.orchords.orchordsai.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetworkSecurityConfigTest {
    private val configPath = "src/main/res/xml/network_security_config.xml"
    private val allowedLoopbackHosts = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")

    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main/res").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = moduleDir.resolve(relative).canonicalFile
        require(file.toPath().startsWith(moduleDir.toPath())) { "Path escapes app module: $relative" }
        return file
    }

    private fun source(relative: String): String = moduleFile(relative).readText()

    private fun extractDomainBlocks(xml: String): List<Pair<Boolean, List<String>>> {
        val blocks = mutableListOf<Pair<Boolean, List<String>>>()
        val blockRegex = Regex("<domain-config[^>]*>", RegexOption.IGNORE_CASE)
        blockRegex.findAll(xml).forEach { match ->
            val openTag = match.value
            val start = match.range.last + 1
            val closeIdx = xml.indexOf("</domain-config>", start, ignoreCase = true)
            require(closeIdx >= 0) { "Unterminated <domain-config> block: $openTag" }
            val inner = xml.substring(start, closeIdx)
            val cleartextAllowed =
                Regex("cleartextTrafficPermitted\\s*=\\s*\"true\"", RegexOption.IGNORE_CASE)
                    .containsMatchIn(openTag)
            val hosts = Regex("<domain[^>]*>([^<]+)</domain>", RegexOption.IGNORE_CASE)
                .findAll(inner)
                .map { it.groupValues[1].trim() }
                .toList()
            blocks.add(cleartextAllowed to hosts)
        }
        return blocks
    }

    @Test
    fun `base-config forbids cleartext traffic application-wide`() {
        val xml = source(configPath)
        val baseConfig = Regex("<base-config[^>]*>", RegexOption.IGNORE_CASE)
            .find(xml) ?: error("Missing <base-config> in network_security_config.xml")
        val cleartextAllowed = Regex("cleartextTrafficPermitted\\s*=\\s*\"true\"", RegexOption.IGNORE_CASE)
            .containsMatchIn(baseConfig.value)
        assertFalse(
            "Base <base-config> must set cleartextTrafficPermitted=\"false\" but got: ${baseConfig.value}",
            cleartextAllowed,
        )
    }

    @Test
    fun `cleartext is enabled only for documented loopback hosts`() {
        val xml = source(configPath)
        val blocks = extractDomainBlocks(xml)
        val cleartextBlocks = blocks.filter { it.first }
        assertTrue(
            "Expected at least one cleartext-permitted <domain-config> block",
            cleartextBlocks.isNotEmpty(),
        )
        cleartextBlocks.forEach { (_, hosts) ->
            hosts.forEach { host ->
                assertTrue(
                    "Unexpected cleartext host outside loopback allow-list: $host",
                    host in allowedLoopbackHosts,
                )
            }
        }
    }

    @Test
    fun `no cleartext block allows non-loopback subdomains or wildcard hosts`() {
        val xml = source(configPath)
        val blocks = extractDomainBlocks(xml)
        blocks.forEach { (_, hosts) ->
            hosts.forEach { host ->
                assertFalse(
                    "Wildcard host not permitted: $host",
                    host.contains('*') || host.contains('?'),
                )
                assertFalse(
                    "CIDR-style host not permitted in this allow-list: $host",
                    host.contains('/'),
                )
                assertTrue(
                    "Host must be one of the documented loopback literals: $host",
                    host in allowedLoopbackHosts,
                )
            }
        }
    }

    @Test
    fun `usesCleartextTraffic is removed from AndroidManifest`() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertFalse(
            "usesCleartextTraffic is no longer needed once network_security_config is wired",
            manifest.contains("android:usesCleartextTraffic"),
        )
    }

    @Test
    fun `application element references network_security_config resource`() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(
            "AndroidManifest must reference android:networkSecurityConfig",
            manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""),
        )
    }

    @Test
    fun `loopback allow-list covers the WebServerService bind addresses`() {
        // WebServerService binds 127.0.0.1 and IPv6 ::1 by default; emulator users rely on 10.0.2.2.
        val xml = source(configPath)
        val blocks = extractDomainBlocks(xml)
        val allHosts = blocks.filter { it.first }
            .flatMap { it.second }
            .toSet()
        listOf("127.0.0.1", "::1", "10.0.2.2").forEach { host ->
            assertTrue(
                "Network security config must allow cleartext for local bind address: $host",
                allHosts.contains(host),
            )
        }
        assertEquals(
            "Loopback allow-list should not grow without policy review",
            allowedLoopbackHosts,
            allHosts,
        )
    }
}
