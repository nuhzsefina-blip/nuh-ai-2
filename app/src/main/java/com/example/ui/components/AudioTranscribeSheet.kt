package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.VerifiedEmerald
import java.util.Locale

data class AudioTranscribeUiState(
    val isRecording: Boolean = false,
    val amplitude: Float = 0f,
    val elapsedSeconds: Int = 0,
    val isTranscribing: Boolean = false,
    val transcribedText: String = "",
    val error: String? = null,
)

enum class TranscribeTarget {
    SPECIAL_INSTRUCTIONS,
    DISCUSSION_INPUT,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioTranscribeSheet(
    state: AudioTranscribeUiState,
    target: TranscribeTarget,
    onDismiss: () -> Unit,
    onStartRecording: () -> Unit,
    onStopAndTranscribe: () -> Unit,
    onUpdateTranscript: (String) -> Unit,
    onApplyTranscript: (String) -> Unit,
    onSaveToCloudNotes: (String) -> Unit,
) {
    var localDraft by remember(state.transcribedText) { mutableStateOf(state.transcribedText) }
    val pulseScale by animateFloatAsState(
        targetValue = if (state.isRecording) 1f + (state.amplitude * 0.35f) else 1f,
        label = "micPulse",
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!state.isRecording && !state.isTranscribing) onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.audio_transcribe_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isRecording && !state.isTranscribing,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "ዝጋ")
                }
            }

            Text(
                text = "በማይክሮፎን ይናገሩ (አማርኛ ወይም እንግሊዝኛ)፤ gemini-3.5-transcribe ወደ ጽሁፍ ይቀይረዋል።",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Recorder visualization card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (state.isRecording) AlertCrimson.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (state.isRecording) AlertCrimson else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    val mins = state.elapsedSeconds / 60
                    val secs = state.elapsedSeconds % 60
                    Text(
                        text = when {
                            state.isRecording -> String.format(Locale.US, "በመቅዳት ላይ… %02d:%02d", mins, secs)
                            state.isTranscribing -> "በgemini-3.5-transcribe ወደ ጽሁፍ በመቀየር ላይ..."
                            else -> "ለመቅዳት ከታች ያለውን ቁልፍ ይጫኑ"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (state.isRecording) {
                        LinearProgressIndicator(
                            progress = { state.amplitude.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AlertCrimson,
                        )
                    } else if (state.isTranscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (!state.isRecording) {
                            Button(
                                onClick = onStartRecording,
                                enabled = !state.isTranscribing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("transcribe_start_button"),
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("መቅዳት ጀምር")
                            }
                        } else {
                            Button(
                                onClick = onStopAndTranscribe,
                                colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("transcribe_stop_button"),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("አቁምና ወደ ጽሁፍ ቀይር")
                            }
                        }
                    }
                }
            }

            if (!state.error.isNullOrBlank()) {
                Surface(
                    color = AlertCrimson.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertCrimson, modifier = Modifier.size(18.dp))
                        Text(state.error, style = MaterialTheme.typography.bodySmall, color = AlertCrimson)
                    }
                }
            }

            if (localDraft.isNotBlank()) {
                OutlinedTextField(
                    value = localDraft,
                    onValueChange = {
                        localDraft = it
                        onUpdateTranscript(it)
                    },
                    label = { Text("የተገኘው ጽሁፍ (ማስተካከል ይቻላል)") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transcribe_result_input"),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onApplyTranscript(localDraft) },
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("transcribe_apply_button"),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (target == TranscribeTarget.SPECIAL_INSTRUCTIONS) "ወደ ልዩ መመሪያ አስገባ"
                            else "ወደ ውይይት መጻፊያ አስገባ"
                        )
                    }
                    OutlinedButton(
                        onClick = { onSaveToCloudNotes(localDraft) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ወደ Cloud ማስታወሻ አስቀምጥ")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
