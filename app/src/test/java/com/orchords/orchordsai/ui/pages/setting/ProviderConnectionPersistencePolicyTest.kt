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
    fun `connection tester never tests unsaved gateway credentials`() {
        val page = source("app/src/main/java/com/orchords/orchordsai/ui/pages/setting/SettingProviderDetailPage.kt")

        assertTrue(page.contains("val hasUnsavedCredential = apiKey != provider.apiKey"))
        assertTrue(page.contains("if (!hasUnsavedCredential)"))
        assertTrue(page.contains("ProviderConnectionTester(internalProvider = provider)"))
        assertFalse(page.contains("ProviderConnectionTester(internalProvider = candidate)"))
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
