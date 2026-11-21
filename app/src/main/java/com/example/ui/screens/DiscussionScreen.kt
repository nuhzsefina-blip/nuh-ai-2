package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.R
import com.example.ai.GroundedSearchResult
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRole
import com.example.data.local.DraftSection
import com.example.data.remote.FirestoreResearchNote
import com.example.ui.components.LegalSearchGroundingPanel
import com.example.ui.components.LiveVoiceConversationPanel
import com.example.ui.components.LiveVoiceUiState
import com.example.ui.components.SearchGroundingUiState
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.DiffAddedBg
import com.example.ui.theme.DiffRemovedBg
import com.example.ui.theme.VerifiedEmerald
import com.example.util.DiffType
import com.example.util.JudicialDocumentFormatter
import com.example.util.TextDiffUtil

enum class DiscussionMode(val label: String) {
    TEXT_CHAT("ጽሁፍ ውይይት"),
    LIVE_VOICE("የድምጽ ውይይት"),
    LEGAL_SEARCH("የህግ ምርምር"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscussionScreen(
    activeCase: CaseEntity,
    messages: List<ChatMessageEntity>,
    isSending: Boolean,
    streamingReply: String? = null,
    chatError: String? = null,
    injectedChatDraft: String? = null,
    liveVoiceState: LiveVoiceUiState = LiveVoiceUiState(),
    searchGroundingState: SearchGroundingUiState = SearchGroundingUiState.Idle,
    savedResearchNotes: List<FirestoreResearchNote> = emptyList(),
    onConsumeInjectedChatDraft: () -> String? = { null },
    onSendRevision: (String, DraftSection) -> Unit,
    onAcceptProposal: (ChatMessageEntity) -> Unit,
    onDiscardProposal: (ChatMessageEntity) -> Unit,
    onRetryLastChat: () -> Unit = {},
    onDismissChatError: () -> Unit = {},
    onSwitchToFastModel: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLaunchChatAudioTranscribe: () -> Unit = {},
    onStartLiveVoice: () -> Unit = {},
    onStopLiveVoice: () -> Unit = {},
    onSendCaseContextToLive: () -> Unit = {},
    onSaveLiveTranscriptNote: (String) -> Unit = {},
    onRunGroundedSearch: (String) -> Unit = {},
    onSaveSearchNote: (GroundedSearchResult) -> Unit = {},
    onDeleteSavedNote: (String) -> Unit = {},
    headerVisibleFraction: Float = 1f,
    onExpandTopChrome: () -> Unit = {},
) {
    var mode by rememberSaveable { mutableStateOf(DiscussionMode.TEXT_CHAT) }
    var inputText by remember { mutableStateOf("") }
    val targetSection = DraftSection.ALL
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isReadOnly = activeCase.status == CaseStatus.FINALIZED

    LaunchedEffect(injectedChatDraft) {
        if (!injectedChatDraft.isNullOrBlank()) {
            val consumed = onConsumeInjectedChatDraft()
            if (!consumed.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) consumed else "$inputText\n$consumed"
                mode = DiscussionMode.TEXT_CHAT
            }
        }
    }

    var lastMessageCount by remember { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size, isSending, chatError, mode) {
        if (mode == DiscussionMode.TEXT_CHAT) {
            val total = listState.layoutInfo.totalItemsCount
            val shouldScroll = messages.size > lastMessageCount || (!listState.canScrollForward && (isSending || chatError != null))
            lastMessageCount = messages.size
            if (shouldScroll && total > 0) {
                listState.animateScrollToItem(total - 1)
            }
        }
    }

    val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 700.dp).fillMaxSize()) {
            // Mode Switcher: Text Chat | Live Voice (gemini-3.8-live) | Legal Search (gemini-4-hydrogen)
            val modeRowFraction = if (mode == DiscussionMode.TEXT_CHAT) headerVisibleFraction.coerceIn(0f, 1f) else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val h = (placeable.height * modeRowFraction).roundToInt()
                        layout(placeable.width, h) {
                            placeable.placeRelative(0, h - placeable.height)
                        }
                    }
            ) {
                Column {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DiscussionMode.entries.forEachIndexed { index, item ->
                            val modeLabel = when (item) {
                                DiscussionMode.TEXT_CHAT -> item.label
                                DiscussionMode.LIVE_VOICE -> stringResource(R.string.live_voice_tab)
                                DiscussionMode.LEGAL_SEARCH -> stringResource(R.string.legal_search_tab)
                            }
                            SegmentedButton(
                                selected = mode == item,
                                onClick = {
                                    onExpandTopChrome()
                                    mode = item
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = DiscussionMode.entries.size),
                                modifier = Modifier.testTag("discussion_mode_${item.name.lowercase()}"),
                            ) {
                                Text(
                                    modeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }

            when (mode) {
                DiscussionMode.LIVE_VOICE -> {
                    LiveVoiceConversationPanel(
                        state = liveVoiceState,
                        hasActiveCaseContext = JudicialDocumentFormatter.hasDraftContent(activeCase) || activeCase.caseNumber.isNotBlank(),
                        onStartVoiceSession = onStartLiveVoice,
                        onStopVoiceSession = onStopLiveVoice,
                        onSendCaseSummaryToVoice = onSendCaseContextToLive,
                        onSaveTranscriptToCloud = onSaveLiveTranscriptNote,
                        modifier = Modifier.weight(1f),
                    )
                }

                DiscussionMode.LEGAL_SEARCH -> {
                    LegalSearchGroundingPanel(
                        searchState = searchGroundingState,
                        savedNotes = savedResearchNotes,
                        onRunSearch = onRunGroundedSearch,
                        onSaveSearchNote = onSaveSearchNote,
                        onDeleteSavedNote = onDeleteSavedNote,
                        onCopyToDraftChat = { prompt ->
                            inputText = prompt
                            mode = DiscussionMode.TEXT_CHAT
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                DiscussionMode.TEXT_CHAT -> {
                    val quickSuggestions = remember {
                        listOf(
                            "ጭብጡን በፍርዱ ውስጥ አጠናክር",
                            "ወጪ እና ኪሳራ ክፍሉን አሳጥር",
                            "ህጋዊ አንቀጾችን አረጋግጥና አስተካክል",
                        )
                    }

                    // Chat History + Quick Suggestions + Diff Cards
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "quick_suggestions_header") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("ፈጣን የውይይት መመሪያዎች:", style = MaterialTheme.typography.labelLarge)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        quickSuggestions.forEach { prompt ->
                                            AssistChip(
                                                onClick = { inputText = prompt },
                                                enabled = !isReadOnly && !isSending,
                                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
                                            )
                                        }
                                    }
                                }
                            }

                            if (messages.isEmpty() && !isSending && streamingReply == null && chatError == null) {
                            item(key = "empty_chat") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("በውይይት ረቂቁን ያሻሽሉ", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(
                                            "በተሰራው ረቂቅ ላይ የሚጨመር፣ የሚቀነስ ወይም የሚሻሻል ሀሳብ ካለ እዚህ ይጻፉ ወይም በማይክሮፎን ይናገሩ። AI የተጠየቀውን ክፍል አሻሽሎ ልዩነቱን (Diff) በቀለም ያሳየዎታል።",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(messages, key = { it.id }) { msg ->
                                when (msg.role) {
                                    ChatRole.SUMMARY -> {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(msg.content, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    ChatRole.USER -> {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                                modifier = Modifier.widthIn(max = 520.dp),
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        msg.targetSection.amharicLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                                                    )
                                                    Text(
                                                        msg.content,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    ChatRole.ASSISTANT -> {
                                        AssistantDiffMessageCard(
                                            message = msg,
                                            isReadOnly = isReadOnly,
                                            onAccept = { onAcceptProposal(msg) },
                                            onDiscard = { onDiscardProposal(msg) },
                                        )
                                    }
                                }
                            }
                        }

                        if (isSending || streamingReply != null) {
                            item(key = "streaming_assistant") {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (streamingReply.isNullOrBlank()) "ረዳት ዳኛ እያሰበ ነው..." else "ረዳት ዳኛ በመጻፍ ላይ...",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (!streamingReply.isNullOrBlank()) {
                                            Text(streamingReply, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        if (!chatError.isNullOrBlank()) {
                            item(key = "chat_error_card") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("chat_error_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = AlertCrimson,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = chatError,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Button(
                                                onClick = onRetryLastChat,
                                                modifier = Modifier.testTag("chat_retry_button"),
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("ደግመህ ሞክር")
                                            }
                                            OutlinedButton(onClick = onSwitchToFastModel) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("ፈጣን ሞዴል (flash-lite)")
                                            }
                                            OutlinedButton(onClick = onOpenSettings) {
                                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("API ቁልፍ / Settings")
                                            }
                                            OutlinedButton(onClick = onDismissChatError) {
                                                Text("ዝጋ")
                                            }
                                        }
                                    }
                                }
                            }
                            }
                            item(key = "chat_bottom_anchor") {
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        if (canScrollUp || canScrollDown) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 4.dp, bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (canScrollUp) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            onExpandTopChrome()
                                            coroutineScope.launch { listState.animateScrollToItem(0) }
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.testTag("chat_scroll_top_button"),
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ወደ ላይ ሂድ")
                                    }
                                }
                                if (canScrollDown) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val total = listState.layoutInfo.totalItemsCount
                                                if (total > 0) listState.animateScrollToItem(total - 1)
                                            }
                                        },
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.testTag("chat_scroll_bottom_button"),
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "ወደ ታች ሂድ")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Input Row with Gemini Audio Transcription Mic + Send
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        if (isReadOnly) "መዝገቡ ተቆልፏል" else "የማሻሻያ መመሪያ ይጻፉ...",
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
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                ),
                                enabled = !isReadOnly && !isSending,
                                minLines = 1,
                                maxLines = 4,
                                trailingIcon = {
                                    IconButton(
                                        onClick = onLaunchChatAudioTranscribe,
                                        enabled = !isReadOnly && !isSending,
                                        modifier = Modifier.testTag("chat_mic_transcribe_button"),
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "በድምጽ ወደ ጽሁፍ ቀይር")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_revision_input"),
                            )
                            FilledIconButton(
                                onClick = {
                                    val txt = inputText
                                    inputText = ""
                                    onSendRevision(txt, targetSection)
                                },
                                enabled = !isReadOnly && !isSending && inputText.isNotBlank(),
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .size(50.dp)
                                    .testTag("chat_send_button"),
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ላክ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantDiffMessageCard(
    message: ChatMessageEntity,
    isReadOnly: Boolean,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium)

            val original = message.proposedOriginalText
            val revised = message.proposedRevisedText
            if (!revised.isNullOrBlank() && original != null) {
                HorizontalDivider()
                Text(
                    "የለውጥ ንጽጽር (${message.proposedSection?.amharicLabel ?: ""}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val diffLines = remember(original, revised) {
                    TextDiffUtil.computeLineDiff(original, revised)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    diffLines.take(40).forEach { line ->
                        when (line.type) {
                            DiffType.ADDED -> Text(
                                "+ ${line.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VerifiedEmerald,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().background(DiffAddedBg).padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            DiffType.REMOVED -> Text(
                                "- ${line.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.fillMaxWidth().background(DiffRemovedBg).padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            DiffType.UNCHANGED -> if (line.text.isNotBlank()) {
                                Text(line.text, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }

                when {
                    message.isApplied -> {
                        Text("ለውጡ ተቀባይነት አግኝቶ በረቂቁ ውስጥ ተካቷል።", style = MaterialTheme.typography.labelMedium, color = VerifiedEmerald)
                    }
                    message.isDiscarded -> {
                        Text("ለውጡ ተሰርዟል።", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onAccept,
                                enabled = !isReadOnly,
                                colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                                modifier = Modifier.weight(1f).testTag("accept_diff_button"),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ተቀበል")
                            }
                            OutlinedButton(
                                onClick = onDiscard,
                                modifier = Modifier.weight(1f).testTag("discard_diff_button"),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ሰርዝ")
                            }
                        }
                    }
                }
            }
        }
    }
}
