package com.orchords.orchordsai.service.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

enum class AssistantRoleState {
    UNSUPPORTED,
    AVAILABLE_NOT_HELD,
    HELD,
}

/** Official Android assistant-role boundary. Role grant always remains a system/user decision. */
object AssistantRoleGateway {
    fun state(context: Context): AssistantRoleState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return AssistantRoleState.UNSUPPORTED
        val manager = context.getSystemService(RoleManager::class.java)
        if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            return AssistantRoleState.UNSUPPORTED
        }
        return if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            AssistantRoleState.HELD
        } else {
            AssistantRoleState.AVAILABLE_NOT_HELD
        }
    }

    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val manager = context.getSystemService(RoleManager::class.java)
        if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}
