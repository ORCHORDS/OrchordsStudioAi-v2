package com.orchords.orchordsai.data.deviceactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActionRegistryTest {
    @Test
    fun `registry exposes unique stable action ids`() {
        val ids = DeviceActionRegistry.descriptors.map { it.id.wireId }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals("device.call.dial", DeviceActionId.DIAL_CONTACT.wireId)
        assertEquals("device.message.compose", DeviceActionId.COMPOSE_MESSAGE.wireId)
        assertEquals("device.navigation.open", DeviceActionId.NAVIGATE.wireId)
    }

    @Test
    fun `communication actions require user presence and preview`() {
        listOf(DeviceActionId.DIAL_CONTACT, DeviceActionId.COMPOSE_MESSAGE).forEach { id ->
            val descriptor = DeviceActionRegistry.require(id)
            assertTrue(descriptor.requiresUserPresence)
            assertTrue(descriptor.requiresPreview)
            assertFalse(descriptor.allowUnattendedAutomation)
        }
    }

    @Test
    fun `automation cannot bypass communication presence policy`() {
        assertEquals(
            DeviceActionDecision.USER_PRESENCE_REQUIRED,
            DeviceActionRegistry.evaluate(
                id = DeviceActionId.COMPOSE_MESSAGE,
                source = DeviceActionInvocationSource.AUTOMATION,
                userPresent = false,
                approved = false,
            ),
        )
    }

    @Test
    fun `physical smart home actions require explicit approval`() {
        val descriptor = DeviceActionRegistry.require(DeviceActionId.SMART_HOME_COMMAND)
        assertTrue(descriptor.requiresUserPresence)
        assertTrue(descriptor.requiresExplicitApproval)
        assertFalse(descriptor.allowUnattendedAutomation)
        assertEquals(
            DeviceActionDecision.APPROVAL_REQUIRED,
            DeviceActionRegistry.evaluate(
                id = DeviceActionId.SMART_HOME_COMMAND,
                source = DeviceActionInvocationSource.CHAT,
                userPresent = true,
                approved = false,
            ),
        )
        assertEquals(
            DeviceActionDecision.ALLOWED,
            DeviceActionRegistry.evaluate(
                id = DeviceActionId.SMART_HOME_COMMAND,
                source = DeviceActionInvocationSource.CHAT,
                userPresent = true,
                approved = true,
            ),
        )
    }
}
