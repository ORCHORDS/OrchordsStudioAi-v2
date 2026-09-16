package com.orchords.orchordsai.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orchords.ai.provider.Model
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.safety.AiContentReportClient
import com.orchords.orchordsai.data.safety.AiContentReportResult
import com.orchords.orchordsai.data.safety.buildAiContentReportPayload
import com.orchords.orchordsai.data.safety.visibleAssistantOutputForReport
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class AiReportCategory(val key: String, val label: String)

private val AI_REPORT_CATEGORIES = listOf(
    AiReportCategory("harmful", "Harmful or dangerous content"),
    AiReportCategory("sexual", "Sexual or exploitative content"),
    AiReportCategory("harassment", "Harassment or bullying"),
    AiReportCategory("deception", "Deceptive or fraudulent content"),
    AiReportCategory("self_harm", "Self-harm content"),
    AiReportCategory("illegal", "Illegal or prohibited assistance"),
    AiReportCategory("privacy", "Privacy or personal-data concern"),
    AiReportCategory("other", "Other offensive content"),
)

private sealed interface AiReportUiState {
    data object Editing : AiReportUiState
    data object Submitting : AiReportUiState
    data class Sent(val reference: String) : AiReportUiState
    data class Failed(val message: String) : AiReportUiState
}

@Composable
fun AiContentReportDialog(
    message: UIMessage,
    model: Model?,
    onDismissRequest: () -> Unit,
) {
    val client = koinInject<AiContentReportClient>()
    val scope = rememberCoroutineScope()
    val output = remember(message) { visibleAssistantOutputForReport(message) }
    var category by remember { mutableStateOf<AiReportCategory?>(null) }
    var note by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AiReportUiState>(AiReportUiState.Editing) }

    val canSubmit = category != null && output.isNotBlank() && state !is AiReportUiState.Submitting

    AlertDialog(
        onDismissRequest = {
            if (state !is AiReportUiState.Submitting) onDismissRequest()
        },
        title = {
            Text(
                when (state) {
                    is AiReportUiState.Sent -> "Report sent"
                    else -> "Report AI output"
                }
            )
        },
        text = {
            when (val current = state) {
                is AiReportUiState.Sent -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Thank you. ORCHORDS received this report.")
                        Text(
                            "Reference: ${current.reference}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "This sends only the visible assistant text below, the category, your optional note, model information, and app version to ORCHORDS. It does not send the rest of the conversation, attachments, credentials, local file paths, or hidden reasoning/tool traces.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text("Category", style = MaterialTheme.typography.titleSmall)
                        AI_REPORT_CATEGORIES.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = category == option,
                                    onClick = {
                                        category = option
                                        if (state is AiReportUiState.Failed) state = AiReportUiState.Editing
                                    },
                                )
                                Text(option.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = {
                                note = it.take(1200)
                                if (state is AiReportUiState.Failed) state = AiReportUiState.Editing
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Optional note") },
                            supportingText = { Text("${note.length}/1200") },
                            minLines = 2,
                            maxLines = 5,
                        )

                        Text("Reported output", style = MaterialTheme.typography.titleSmall)
                        Text(
                            output.ifBlank { "No visible assistant text is available to report." },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                        )

                        if (current is AiReportUiState.Failed) {
                            Text(
                                current.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is AiReportUiState.Sent -> {
                    TextButton(onClick = onDismissRequest) { Text("Close") }
                }

                is AiReportUiState.Submitting -> {
                    TextButton(onClick = {}, enabled = false) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Sending…")
                    }
                }

                else -> {
                    TextButton(
                        enabled = canSubmit,
                        onClick = {
                            val selected = category ?: return@TextButton
                            state = AiReportUiState.Submitting
                            scope.launch {
                                val payload = buildAiContentReportPayload(
                                    message = message,
                                    category = selected.key,
                                    note = note,
                                    model = model?.modelId?.ifBlank { model.displayName }.orEmpty(),
                                    provider = "",
                                )
                                state = when (val result = client.submit(payload)) {
                                    is AiContentReportResult.Success -> AiReportUiState.Sent(result.reference)
                                    is AiContentReportResult.RateLimited -> {
                                        val suffix = result.retryAfterSeconds?.let { " Try again in about $it seconds." }.orEmpty()
                                        AiReportUiState.Failed("Too many reports were submitted from this connection.$suffix")
                                    }
                                    AiContentReportResult.Rejected -> AiReportUiState.Failed(
                                        "The report could not be accepted. Review the category and note, then try again."
                                    )
                                    AiContentReportResult.Unavailable -> AiReportUiState.Failed(
                                        "Reporting is temporarily unavailable. Please try again shortly."
                                    )
                                }
                            }
                        },
                    ) {
                        Text("Send report")
                    }
                }
            }
        },
        dismissButton = {
            if (state !is AiReportUiState.Sent && state !is AiReportUiState.Submitting) {
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        },
    )
}
