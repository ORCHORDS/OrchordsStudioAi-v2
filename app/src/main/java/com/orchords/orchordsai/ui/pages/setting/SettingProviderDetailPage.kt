package com.orchords.orchordsai.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.datastore.ORCHORDS_GATEWAY_BASE_URL
import com.orchords.orchordsai.data.datastore.ORCHORDS_MODEL_ID
import com.orchords.orchordsai.ui.components.nav.BackButton
import com.orchords.orchordsai.ui.components.ui.AutoAIIcon
import com.orchords.orchordsai.ui.context.LocalToaster
import com.orchords.orchordsai.ui.pages.setting.components.ProviderConnectionTester
import com.orchords.orchordsai.ui.theme.CustomColors
import me.orchid.hugeicons.HugeIcons
import me.orchid.hugeicons.stroke.View
import me.orchid.hugeicons.stroke.ViewOff
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

/**
 * Configuration for the single supported first-party Orchords gateway.
 *
 * The gateway origin and model identity are product constants, not editable
 * provider/model selectors. Only the protected gateway credential is user
 * configurable here.
 */
@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val provider = settings.providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .singleOrNull { it.id == id }
        ?: return
    val toaster = LocalToaster.current
    val saveSuccess = stringResource(R.string.setting_provider_page_save_success)
    var apiKey by remember(provider.id, provider.apiKey) { mutableStateOf(provider.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AutoAIIcon(
                            name = ORCHORDS_MODEL_ID,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(provider.name)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = ORCHORDS_MODEL_ID,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Gateway",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = ORCHORDS_GATEWAY_BASE_URL,
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                            contentDescription = null,
                        )
                    }
                },
            )

            val candidate = provider.copy(apiKey = apiKey)
            val hasUnsavedCredential = apiKey != provider.apiKey
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasUnsavedCredential) {
                    ProviderConnectionTester(internalProvider = provider)
                }
                Button(
                    onClick = {
                        vm.updateSettings(
                            settings = settings.copy(
                                providers = listOf(candidate),
                            ),
                            onSuccess = {
                                toaster.show(saveSuccess, type = ToastType.Success)
                            },
                        )
                    },
                    enabled = hasUnsavedCredential,
                ) {
                    Text(stringResource(R.string.setting_provider_page_save))
                }
            }
        }
    }
}
