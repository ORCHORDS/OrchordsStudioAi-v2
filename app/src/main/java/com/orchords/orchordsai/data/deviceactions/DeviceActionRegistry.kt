package com.orchords.orchordsai.data.deviceactions

enum class DeviceActionId(val wireId: String) {
    DIAL_CONTACT("device.call.dial"),
    COMPOSE_MESSAGE("device.message.compose"),
    NAVIGATE("device.navigation.open"),
    OPEN_APP("device.app.open"),
    OPEN_SAFE_SETTINGS("device.settings.open_safe"),
    FLASHLIGHT_SET("device.flashlight.set"),
    MEDIA_CONTROL("device.media.control"),
    SMART_HOME_COMMAND("device.smart_home.command"),
}

enum class DeviceActionInvocationSource {
    CHAT,
    VOICE,
    WORK,
    AUTOMATION,
}

enum class DeviceActionRisk {
    LOW,
    CONSEQUENTIAL,
    PHYSICAL,
}

enum class DeviceActionDecision {
    ALLOWED,
    USER_PRESENCE_REQUIRED,
    APPROVAL_REQUIRED,
    AUTOMATION_NOT_ALLOWED,
}

data class DeviceActionDescriptor(
    val id: DeviceActionId,
    val risk: DeviceActionRisk,
    val requiresUserPresence: Boolean,
    val requiresPreview: Boolean,
    val requiresExplicitApproval: Boolean,
    val allowUnattendedAutomation: Boolean,
)

object DeviceActionRegistry {
    val descriptors: List<DeviceActionDescriptor> = listOf(
        DeviceActionDescriptor(DeviceActionId.DIAL_CONTACT, DeviceActionRisk.CONSEQUENTIAL, true, true, false, false),
        DeviceActionDescriptor(DeviceActionId.COMPOSE_MESSAGE, DeviceActionRisk.CONSEQUENTIAL, true, true, false, false),
        DeviceActionDescriptor(DeviceActionId.NAVIGATE, DeviceActionRisk.LOW, false, false, false, true),
        DeviceActionDescriptor(DeviceActionId.OPEN_APP, DeviceActionRisk.LOW, false, false, false, true),
        DeviceActionDescriptor(DeviceActionId.OPEN_SAFE_SETTINGS, DeviceActionRisk.LOW, false, false, false, true),
        DeviceActionDescriptor(DeviceActionId.FLASHLIGHT_SET, DeviceActionRisk.LOW, false, false, false, true),
        DeviceActionDescriptor(DeviceActionId.MEDIA_CONTROL, DeviceActionRisk.LOW, false, false, false, true),
        DeviceActionDescriptor(DeviceActionId.SMART_HOME_COMMAND, DeviceActionRisk.PHYSICAL, true, false, true, false),
    )

    private val byId = descriptors.associateBy { it.id }

    init {
        check(byId.size == descriptors.size) { "Device action IDs must be unique" }
    }

    fun require(id: DeviceActionId): DeviceActionDescriptor =
        requireNotNull(byId[id]) { "Unregistered device action: ${id.wireId}" }

    fun evaluate(
        id: DeviceActionId,
        source: DeviceActionInvocationSource,
        userPresent: Boolean,
        approved: Boolean,
    ): DeviceActionDecision {
        val descriptor = require(id)
        if (descriptor.requiresUserPresence && !userPresent) {
            return DeviceActionDecision.USER_PRESENCE_REQUIRED
        }
        if (source == DeviceActionInvocationSource.AUTOMATION && !descriptor.allowUnattendedAutomation) {
            return DeviceActionDecision.AUTOMATION_NOT_ALLOWED
        }
        if (descriptor.requiresExplicitApproval && !approved) {
            return DeviceActionDecision.APPROVAL_REQUIRED
        }
        return DeviceActionDecision.ALLOWED
    }
}
