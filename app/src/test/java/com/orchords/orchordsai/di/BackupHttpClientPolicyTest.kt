package com.orchords.orchordsai.di

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHttpClientPolicyTest {
    @Test
    fun `backup transports use a dedicated no-redirect client`() {
        val source = File("src/main/java/com/orchords/orchordsai/di/DataSourceModule.kt").readText()
        val backupDefinition = source.substringAfter("single<HttpClient>(BACKUP_HTTP_CLIENT)")
            .substringBefore("single<HttpClient> {")

        assertTrue(backupDefinition.contains("followRedirects = false"))
        assertTrue(backupDefinition.contains("followSslRedirects(false)"))
        assertTrue(backupDefinition.contains("followRedirects(false)"))
        assertEquals(2, source.windowed("httpClient = get(BACKUP_HTTP_CLIENT)".length)
            .count { it == "httpClient = get(BACKUP_HTTP_CLIENT)" })
        assertTrue(source.contains("followSslRedirects(true)"))
        assertTrue(source.contains("followRedirects(true)"))
    }
}
