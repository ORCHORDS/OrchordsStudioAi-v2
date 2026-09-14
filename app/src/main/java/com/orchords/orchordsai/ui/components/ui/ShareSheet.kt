package com.orchords.orchordsai.ui.components.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orchords.ai.provider.ProviderSetting
import me.orchid.hugeicons.HugeIcons
import me.orchid.hugeicons.stroke.Share03
import com.orchords.orchordsai.utils.JsonInstant
import kotlin.io.encoding.Base64

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    if (state.isShow) {
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Share your LLM models", style = MaterialTheme.typography.titleLarge)

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                state.currentProvider?.encodeForShare() ?: ""
                            )
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(HugeIcons.Share03, null)
                    }
                }

                QRCode(
                    value = state.currentProvider?.encodeForShare() ?: "",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }
    }
}

fun ProviderSetting.encodeForShare(): String {
    // #428 marked `apiKey` as `@Transient` on `ProviderSetting`, so the normal
    // JsonInstant path drops it. For the share-sheet we re-attach it in a
    // side-car object: the on-the-wire payload keeps the standard polymorphic
    // ProviderSetting envelope plus a `apiKey` field. We do NOT keep the key
    // anywhere else — the share-sheet string is the only on-disk carrier of
    // this credential and it stays in the user's clipboard for that one share.
    val stripped = JsonInstant.encodeToString(this.copyProvider(models = emptyList()))
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(stripped).let {
        (it as kotlinx.serialization.json.JsonObject).toMutableMap()
    }
    obj["apiKey"] = kotlinx.serialization.json.JsonPrimitive(apiKey)
    val withKey = kotlinx.serialization.json.JsonObject(obj)
    val withKeyJson = kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        withKey,
    )
    return buildString {
        append("ai-provider:")
        append("v1:")
        append(Base64.encode(withKeyJson.encodeToByteArray()))
    }
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }

    val base64Str = value.removePrefix("ai-provider:v1:")

    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()

    val obj = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr) as kotlinx.serialization.json.JsonObject
    val keyElement = obj["apiKey"]
    val key = keyElement?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
    val withoutKey = kotlinx.serialization.json.JsonObject(obj.filterKeys { it != "apiKey" })
    val withoutKeyJson = kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        withoutKey,
    )
    val decoded = JsonInstant.decodeFromString<ProviderSetting>(withoutKeyJson)
    return decoded.withApiKey(key)
}

private fun ProviderSetting.withApiKey(value: String): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> this.also { this.apiKey = value }
    is ProviderSetting.Google -> this.also { this.apiKey = value }
    is ProviderSetting.Claude -> this.also { this.apiKey = value }
}

class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState {
    return ShareSheetState()
}
