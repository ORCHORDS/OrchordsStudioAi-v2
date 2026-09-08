package com.orchords.orchordsai.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.Model
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.isEmptyUIMessage
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.Avatar
import com.orchords.orchordsai.ui.components.ui.UIAvatar
import com.orchords.orchordsai.ui.context.LocalSettings

internal fun shouldShowAssistantIdentityRow(
    role: MessageRole,
    useAssistantAvatar: Boolean,
): Boolean = role == MessageRole.ASSISTANT && useAssistantAvatar

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    if (message.role == MessageRole.USER && !message.parts.isEmptyUIMessage() && settings.displaySetting.showUserAvatar) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nickname.ifEmpty { stringResource(R.string.user_default_name) },
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
            )
            UIAvatar(
                name = nickname,
                modifier = Modifier.size(28.dp),
                value = avatar,
                loading = false,
            )
        }
    }
}

/**
 * Show an assistant identity row only when the user explicitly configured an
 * Assistant profile avatar/name. The ordinary model identity is already known
 * from the conversation/model controls and repeating the same model icon/name
 * above every response adds noise without adding speaker information.
 *
 * [model] remains in the signature for call-site compatibility; it is
 * intentionally not rendered here.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val useAssistantAvatar = assistant?.useAssistantAvatar == true

    if (!shouldShowAssistantIdentityRow(message.role, useAssistantAvatar) || assistant == null) {
        return
    }

    val showIcon = settings.displaySetting.showModelIcon
    val showName = settings.displaySetting.showModelName
    if (!showIcon && !showName) {
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (showIcon) {
            UIAvatar(
                name = assistant.name,
                modifier = Modifier.size(28.dp),
                value = assistant.avatar,
                loading = loading,
                useDefaultAssistantBranding = assistant.name.isBlank(),
            )
        }
        if (showName) {
            Text(
                text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
            )
        }
    }
}
