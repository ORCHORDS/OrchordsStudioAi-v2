package com.orchords.orchordsai.service.assistant

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoleBoundaryPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `manifest exposes only the documented voice interaction boundary`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val metadata = source("app/src/main/res/xml/voice_interaction_service.xml")

        assertTrue(manifest.contains("android.permission.BIND_VOICE_INTERACTION"))
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"))
        assertTrue(manifest.contains("android.voice_interaction"))
        assertTrue(manifest.contains("android:process=\":voice_interaction\""))
        assertTrue(manifest.contains("android:process=\":voice_session\""))
        assertTrue(metadata.contains("android:supportsAssist=\"false\""))
        assertTrue(metadata.contains("android:supportsLaunchVoiceAssistFromKeyguard=\"false\""))
    }

    @Test
    fun `always running service stays dependency free and inert`() {
        val service = source("app/src/main/java/com/orchords/orchordsai/service/assistant/OrchordsVoiceInteractionServices.kt")

        assertTrue(service.contains("class OrchordsVoiceInteractionService : VoiceInteractionService()"))
        assertFalse(service.contains("ChatService"))
        assertFalse(service.contains("GenerationHandler"))
        assertFalse(service.contains("RECORD_AUDIO"))
        assertFalse(service.contains("ContentResolver"))
    }

    @Test
    fun `assistant role gateway uses official role contract`() {
        val gateway = source("app/src/main/java/com/orchords/orchordsai/service/assistant/AssistantRoleGateway.kt")
        assertTrue(gateway.contains("RoleManager.ROLE_ASSISTANT"))
        assertTrue(gateway.contains("createRequestRoleIntent"))
        assertTrue(gateway.contains("isRoleHeld"))
    }
}
