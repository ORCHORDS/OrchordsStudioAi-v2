package com.orchords.orchordsai.ui.activity

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SafeModeCompositionPolicyTest {
    private fun source(): String {
        val repoRoot = File("..").canonicalFile
        val file = repoRoot.resolve(
            "app/src/main/java/com/orchords/orchordsai/ui/activity/SafeModeActivity.kt"
        )
        require(file.isFile) { "SafeModeActivity.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `safe mode provides toaster state to descendant composables`() {
        val source = source()
        assertTrue(source.contains("rememberToasterState()"))
        assertTrue(source.contains("CompositionLocalProvider("))
        assertTrue(source.contains("LocalToaster provides toastState"))
        assertTrue(source.contains("Toaster(state = toastState)"))
    }
}
