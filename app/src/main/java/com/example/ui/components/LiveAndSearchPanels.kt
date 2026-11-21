package com.example.ui.components

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.example.ai.GroundedSearchResult
import com.example.data.remote.FirestoreResearchNote
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.VerifiedEmerald

data class LiveTranscriptEntry(
    val id: String,
    val speakerLabel: String,
    val isUser: Boolean,
    val text: String,
)

data class LiveVoiceUiState(
    val isConnecting: Boolean = false,
    val isActive: Boolean = false,
    val statusText: String = "ዝግጁ (gemini-3.8-live)",
    val transcripts: List<LiveTranscriptEntry> = emptyList(),
    val error: String? = null,
)

sealed interface SearchGroundingUiState {
    data object Idle : SearchGroundingUiState
    data object Loading : SearchGroundingUiState
    data class Success(val result: GroundedSearchResult) : SearchGroundingUiState
    data class Error(val message: String) : SearchGroundingUiState
}

@Composable
fun LiveVoiceConversationPanel(
    state: LiveVoiceUiState,
    hasActiveCaseContext: Boolean,
    onStartVoiceSession: () -> Unit,
    onStopVoiceSession: () -> Unit,
    onSendCaseSummaryToVoice: () -> Unit,
    onSaveTranscriptToCloud: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (state.isActive) VerifiedEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isActive) Icons.Default.GraphicEq else Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (state.isActive) VerifiedEmerald else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ቀጥታ የድምጽ ውይይት (Live API)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isActive) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "ከረዳት ዳኛ ጋር በቀጥታ በድምጽ ይነጋገሩ፤ የመዝገቡን ጭብጥና ህጋዊ ነጥቦች በንግግር መወያየት እና ማቋረጥ ይችላሉ።",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!state.error.isNullOrBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = AlertCrimson.copy(alpha = 0.12f))) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AlertCrimson, modifier = Modifier.size(18.dp))
                            Text(state.error, style = MaterialTheme.typography.bodySmall, color = AlertCrimson)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!state.isActive) {
                        Button(
                            onClick = onStartVoiceSession,
                            enabled = !state.isConnecting,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("live_voice_start_button"),
                        ) {
                            if (state.isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("በመገናኘት ላይ...")
                            } else {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("የድምጽ ውይይት ጀምር")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStopVoiceSession,
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("live_voice_stop_button"),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ውይይቱን አቁም")
                        }
                        if (hasActiveCaseContext) {
                            OutlinedButton(
                                onClick = onSendCaseSummaryToVoice,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("መዝገብ ላክ")
                            }
                        }
                    }
                }
            }
        }

        if (state.transcripts.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("የቀጥታ የንግግር ጽሁፍ (Live Transcript)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                val combined = state.transcripts.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
                                onSaveTranscriptToCloud(combined)
                            },
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("አስቀምጥ")
                        }
                    }
                    HorizontalDivider()
                    state.transcripts.takeLast(20).forEach { entry ->
                        Surface(
                            color = if (entry.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    entry.speakerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(entry.text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegalSearchGroundingPanel(
    searchState: SearchGroundingUiState,
    savedNotes: List<FirestoreResearchNote>,
    onRunSearch: (String) -> Unit,
    onSaveSearchNote: (GroundedSearchResult) -> Unit,
    onDeleteSavedNote: (String) -> Unit,
    onCopyToDraftChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var queryInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "የህግ ምርምር በGoogle Search (gemini-4-hydrogen)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = {
                            Text(
                                "አዋጅ፣ የሰበር ውሳኔ ወይም የህግ አንቀጽ ይጠይቁ...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (queryInput.isNotBlank() && searchState !is SearchGroundingUiState.Loading) {
                                    focusManager.clearFocus()
                                    onRunSearch(queryInput)
                                }
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("legal_search_input"),
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onRunSearch(queryInput)
                        },
                        enabled = queryInput.isNotBlank() && searchState !is SearchGroundingUiState.Loading,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("legal_search_submit_button"),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "ፈልግ")
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "የፌዴራል ጠቅላይ ፍ/ቤት ሰበር ችሎት የጣልቃ ገብነት ውሳኔ",
                        "የፍትሐብሔር ሥነ ሥርዓት ሕግ አንቀጽ 41 እና 244",
                        "የንግድ ማህበር ህግ የድርሻ ኃላፊነት",
                    ).forEach { sample ->
                        AssistChip(
                            onClick = {
                                queryInput = sample
                                onRunSearch(sample)
                            },
                            label = { Text(sample, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }

        when (searchState) {
            is SearchGroundingUiState.Idle -> Unit
            is SearchGroundingUiState.Loading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text("በGoogle Search ትክክለኛ የህግ ምንጮችን በመፈለግ ላይ...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is SearchGroundingUiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = AlertCrimson.copy(alpha = 0.12f))) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertCrimson)
                        Text(searchState.message, style = MaterialTheme.typography.bodySmall, color = AlertCrimson)
                    }
                }
            }
            is SearchGroundingUiState.Success -> {
                val res = searchState.result
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().testTag("legal_search_result_card"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("የምርምር ውጤት", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(res.answerText, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!res.searchEntryPointHtml.isNullOrBlank()) {
                            HorizontalDivider()
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        settings.domStorageEnabled = false
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val url = request?.url ?: return false
                                                runCatching {
                                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                                }
                                                return true
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    webView.loadDataWithBaseURL(
                                        "https://www.google.com",
                                        res.searchEntryPointHtml,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp, max = 140.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }

                        if (res.sources.isNotEmpty()) {
                            Text("ምንጮች (Sources)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                res.sources.forEach { src ->
                                    AssistChip(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, src.uri.toUri()))
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = {
                                            Text(
                                                src.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSaveSearchNote(res) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("save_search_note_button"),
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ወደ Cloud ማስታወሻ አስቀምጥ")
                            }
                            OutlinedButton(
                                onClick = { onCopyToDraftChat("ይህን የህግ ምርምር ነጥብ በረቂቁ ውስጥ አካትት:\n${res.answerText.take(1200)}") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("ወደ ውይይት ላክ")
                            }
                        }
                    }
                }
            }
        }

        // Saved Cloud Research Notes (Firestore synced)
        if (savedNotes.isNotEmpty()) {
            Text(
                "በCloud Firestore የተቀመጡ ማስታወሻዎች (${savedNotes.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            savedNotes.forEach { note ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    note.resolvedType().amharicLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    note.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDeleteSavedNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "ማስታወሻ አጥፋ", tint = AlertCrimson)
                            }
                        }
                        Text(
                            note.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}
