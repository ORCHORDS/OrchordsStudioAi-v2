package com.orchords.orchordsai.ui.pages.setting

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConnectionPersistencePolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `connection tester stays visible but never tests unsaved gateway credentials`() {
        val page = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/SettingProviderDetailPage.kt")

        assertTrue(page.contains("val hasUnsavedCredential = apiKey != provider.apiKey"))
        assertTrue(page.contains("ProviderConnectionTester("))
        assertTrue(page.contains("internalProvider = provider"))
        assertTrue(page.contains("enabled = !hasUnsavedCredential"))
        assertTrue(page.contains("Save API key before testing"))
        assertFalse(page.contains("ProviderConnectionTester(internalProvider = candidate"))
    }

    @Test
    fun `streaming connection test cannot report success with no streamed text`() {
        val tester = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/components/ProviderConnectionTester.kt")

        assertTrue(tester.contains("enabled: Boolean = true"))
        assertTrue(tester.contains("IconButton("))
        assertTrue(tester.contains("enabled = enabled"))
        assertTrue(tester.contains("check(streamingText.isNotBlank())"))
    }

    @Test
    fun `tool connection test cannot report success when no tool call was returned`() {
        val tester = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/components/ProviderConnectionTester.kt")

        assertTrue(tester.contains("check(toolCall != null)"))
        assertTrue(tester.contains("Tool-call test completed without a tool call"))
    }

    @Test
    fun `save success is emitted only after settings persistence completes`() {
        val page = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/SettingProviderDetailPage.kt")
        val vm = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/SettingVM.kt")

        assertTrue(vm.contains("onSuccess: () -> Unit = {}"))
        assertTrue(vm.contains("settingsStore.update(settings)"))
        assertTrue(vm.contains("onSuccess()"))
        assertTrue(page.contains("vm.updateSettings("))
        assertTrue(page.contains("onSuccess = {"))
        assertTrue(page.contains("toaster.show(saveSuccess"))
    }
}
