package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.core.net.toUri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.example.data.local.CaseType
import com.example.data.local.QualityRating
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JudicialGold
import com.example.ui.theme.JudicialNavyDark
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AttachmentTarget
import com.example.ui.viewmodel.PipelineState
import com.example.util.AttachmentKind
import com.example.util.DocumentAttachment
import com.example.util.JudicialDocumentFormatter
import com.example.util.JudicialPreset
import com.example.util.JudicialPresets
import com.example.util.PastJudgmentParser

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DraftBuilderScreen(
    activeCase: CaseEntity,
    recentWorkCases: List<CaseEntity>,
    plaintiffAttachments: List<DocumentAttachment>,
    defendantAttachments: List<DocumentAttachment>,
    judgeInstructionAttachments: List<DocumentAttachment> = emptyList(),
    pipelineState: PipelineState,
    onStartNewCase: () -> Unit,
    onSelectRecentCase: (CaseEntity) -> Unit,
    onCaseTypeChange: (CaseType) -> Unit,
    onMetadataChange: ((CaseEntity) -> CaseEntity) -> Unit,
    onOpenDatePicker: () -> Unit,
    onPickPhotos: (AttachmentTarget) -> Unit,
    onPickDocuments: (AttachmentTarget) -> Unit,
    onCaptureCamera: (AttachmentTarget) -> Unit,
    onRemoveAttachment: (String, AttachmentTarget) -> Unit,
    onExtractJudgeInstructionAttachments: () -> Unit = {},
    onLaunchVoiceInstructions: () -> Unit,
    onStartOcr: () -> Unit,
    onUpdateOcrTexts: (String, String) -> Unit,
    onConfirmOcrAndDraft: () -> Unit,
    onCancelPipeline: () -> Unit,
    onUpdateUnifiedDocument: (String) -> Unit,
    onSaveManualVersion: () -> Unit,
    onOpenVersionHistory: () -> Unit,
    onRateGoodOrExcellent: (QualityRating) -> Unit,
    onOpenBadRatingDialog: () -> Unit,
    onFinalizeToArchive: () -> Unit,
    onOpenPdfExport: () -> Unit,
    onOpenSettings: () -> Unit = {},
    activeSubjectTag: String? = null,
    activeAdjournmentDate: String? = null,
    onAutoFillFromOcr: () -> Unit = {},
    onApplyPreset: (JudicialPreset, Boolean) -> Unit = { _, _ -> },
    onSelectSubjectTag: (String?) -> Unit = {},
    onOpenAdjournmentPicker: () -> Unit = {},
    onQuickAdjournmentDays: (Int) -> Unit = {},
    onClearAdjournment: () -> Unit = {},
    onOpenFullScreenEditor: () -> Unit = {},
    onOpenLawReference: () -> Unit = {},
    onTransferToDiscussion: () -> Unit = {},
    onExpandTopChrome: () -> Unit = {},
) {
    val isReadOnly = activeCase.status == CaseStatus.FINALIZED
    var showMetadataForm by remember { mutableStateOf(true) }
    var showJudgeNotes by remember { mutableStateOf(true) }
    var isEditingUnified by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var selectedPresetForConfirm by remember { mutableStateOf<JudicialPreset?>(null) }
    val clipboardManager = LocalClipboardManager.current

    selectedPresetForConfirm?.let { preset ->
        AlertDialog(
            onDismissRequest = { selectedPresetForConfirm = null },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(preset.titleAmharic) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preset.subtitleAmharic, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (preset.starterTemplate.isNotBlank()) {
                            "ለAI ልዩ መመሪያ ብቻ ይግባ ወይስ የመነሻ የፍርድ ቤት ቅጹም በረቂቅ ቦታ ላይ ይቀመጥ?"
                        } else {
                            "የ'${preset.titleAmharic}' የውሳኔ/ብይን መመሪያ እና የጉዳይ ዘርፍ (${preset.subjectTag}) ይገባል።"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyPreset(preset, preset.starterTemplate.isNotBlank())
                        selectedPresetForConfirm = null
                    }
                ) {
                    Text(if (preset.starterTemplate.isNotBlank()) "መመሪያ + መነሻ ቅጽ" else "መመሪያ አስገባ")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (preset.starterTemplate.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                onApplyPreset(preset, false)
                                selectedPresetForConfirm = null
                            }
                        ) { Text("መመሪያ ብቻ") }
                    }
                    TextButton(onClick = { selectedPresetForConfirm = null }) { Text("ተመለስ") }
                }
            },
        )
    }

    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
    val instructionsBringIntoView = remember { BringIntoViewRequester() }
    val editorBringIntoView = remember { BringIntoViewRequester() }
    val drafts = remember(recentWorkCases) {
        recentWorkCases.filter { it.status != CaseStatus.FINALIZED }.take(4)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero Banner + Active Case Controls
            item(key = "draft_hero_banner") {
            Box(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_court_banner),
                    contentDescription = stringResource(R.string.cd_court_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    JudicialNavyDark.copy(alpha = 0.92f),
                                    JudicialNavyDark.copy(alpha = 0.55f),
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "የሐረሪ ክልል ከፍተኛ ፍርድ ቤት",
                            style = MaterialTheme.typography.labelLarge,
                            color = JudicialGold,
                        )
                        Text(
                            if (activeCase.caseNumber.isNotBlank()) {
                                "የመ/ቁ:- ${activeCase.caseNumber} (${activeCase.caseType.amharicLabel})"
                            } else {
                                "አዲስ የውሳኔ/ብይን መዝገብ"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "${activeCase.ethiopianDate} ዓ.ም • ${activeCase.gregorianDate} • v${activeCase.currentVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isReadOnly) {
                            Surface(
                                color = VerifiedEmerald,
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Read-Only", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .clickable { onStartNewCase() }
                                .testTag("new_case_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("አዲስ መዝገብ", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            }

            // Quick recent draft switcher if user has saved drafts
            if (drafts.isNotEmpty()) {
                item(key = "draft_recent_chips") {
                    FlowRow(
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        drafts.forEach { d ->
                            FilterChip(
                                selected = d.id == activeCase.id,
                                onClick = { onSelectRecentCase(d) },
                                label = {
                                    Text(
                                        if (d.caseNumber.isNotBlank()) "መ/ቁ ${d.caseNumber}" else "ረቂቅ #${d.id}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Pipeline Status / Progress Card
            if (pipelineState !is PipelineState.Idle && pipelineState !is PipelineState.AwaitingOcrReview) {
                item(key = "draft_pipeline_card") {
                    Column(
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (pipelineState) {
                            is PipelineState.ProcessingImages -> {
                                PipelineProgressCard(
                                    stageText = pipelineState.message,
                                    stepIndex = 0,
                                    onCancel = onCancelPipeline,
                                )
                            }
                            is PipelineState.ReadingOcr -> {
                                PipelineProgressCard(
                                    stageText = pipelineState.message,
                                    stepIndex = 1,
                                    onCancel = onCancelPipeline,
                                )
                            }
                            is PipelineState.StreamingDraft -> {
                                PipelineProgressCard(
                                    stageText = pipelineState.stageLabel,
                                    stepIndex = if (pipelineState.stageLabel.contains("ጭብጥ")) 2 else 3,
                                    onCancel = onCancelPipeline,
                                )
                                StreamingLivePreviewCard(pipelineState = pipelineState)
                            }
                            is PipelineState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth().testTag("pipeline_error_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = AlertCrimson)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                pipelineState.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = onStartOcr) {
                                                Text("ደግመህ ሞክር")
                                            }
                                            OutlinedButton(onClick = onOpenSettings) {
                                                Text("ሞዴል / API ቁልፍ")
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }

            // OCR Review & Edit Gate (Before Stage 2 Drafting)
            if (pipelineState is PipelineState.AwaitingOcrReview) {
                item(key = "draft_ocr_review") {
                    Box(modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
                        OcrReviewCard(
                            plaintiffText = pipelineState.plaintiffText,
                            defendantText = pipelineState.defendantText,
                            statsSummary = pipelineState.statsSummary,
                            onTextsChange = onUpdateOcrTexts,
                            onAutoFillMetadata = onAutoFillFromOcr,
                            onConfirmAndDraft = onConfirmOcrAndDraft,
                        )
                    }
                }
            }

            // Common High Court Ruling Presets (ፈጣን የብይን እና የውሳኔ ቅጾች)
            if (!isReadOnly) {
                item(key = "draft_presets_row") {
                    Column(
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "ፈጣን የብይን እና የውሳኔ ቅጾች (Presets)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            AssistChip(
                                onClick = onOpenLawReference,
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text("የህግ ማጣቀሻ", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("draft_law_reference_chip"),
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(JudicialPresets.allPresets, key = { it.id }) { preset ->
                                AssistChip(
                                    onClick = { selectedPresetForConfirm = preset },
                                    label = { Text(preset.titleAmharic, maxLines = 1) },
                                    modifier = Modifier.testTag("preset_chip_${preset.id}"),
                                )
                            }
                        }
                    }
                }
            }

            // Case Metadata Card
            item(key = "draft_metadata_card") {
            ElevatedCard(
                modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMetadataForm = !showMetadataForm },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("የመዝገብ መረጃ እና የውሳኔ አይነት", style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(
                            if (showMetadataForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "ዘርጋ/ሰብስብ",
                        )
                    }

                    AnimatedVisibility(visible = showMetadataForm) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                CaseType.entries.forEachIndexed { idx, type ->
                                    SegmentedButton(
                                        selected = activeCase.caseType == type,
                                        onClick = { if (!isReadOnly) onCaseTypeChange(type) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = CaseType.entries.size),
                                        enabled = !isReadOnly,
                                        modifier = Modifier.testTag("case_type_${type.name.lowercase()}"),
                                    ) {
                                        Text(type.amharicLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                val (caseNumText, onCaseNumTextChange, flushCaseNum) = rememberBufferedText(
                                    externalValue = activeCase.caseNumber,
                                    resetKey = activeCase.id,
                                    onCommit = { v -> onMetadataChange { it.copy(caseNumber = v) } },
                                )
                                OutlinedTextField(
                                    value = caseNumText,
                                    onValueChange = onCaseNumTextChange,
                                    label = { Text("የመ/ቁ:-") },
                                    placeholder = { Text("31513") },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    singleLine = true,
                                    enabled = !isReadOnly,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { if (!it.isFocused) flushCaseNum() }
                                        .testTag("case_number_input"),
                                )
                                OutlinedTextField(
                                    value = activeCase.ethiopianDate,
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("ቀን (ዓ.ም)") },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    supportingText = { Text(activeCase.gregorianDate, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        IconButton(onClick = { if (!isReadOnly) onOpenDatePicker() }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "የኢትዮጵያ ቀን ምረጥ")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clickable(enabled = !isReadOnly) { onOpenDatePicker() },
                                )
                            }

                            val (judgesText, onJudgesTextChange, flushJudges) = rememberBufferedText(
                                externalValue = activeCase.judges,
                                resetKey = activeCase.id,
                                onCommit = { v -> onMetadataChange { it.copy(judges = v) } },
                            )
                            OutlinedTextField(
                                value = judgesText,
                                onValueChange = onJudgesTextChange,
                                label = { Text(if (activeCase.caseType == CaseType.APPEAL) "ዳኞች (በየመስመሩ)" else "ዳኛ") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                minLines = if (activeCase.caseType == CaseType.APPEAL) 2 else 1,
                                maxLines = 4,
                                enabled = !isReadOnly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) flushJudges() },
                            )

                            val (plaintiffText, onPlaintiffTextChange, flushPlaintiff) = rememberBufferedText(
                                externalValue = activeCase.plaintiffName,
                                resetKey = activeCase.id,
                                onCommit = { v -> onMetadataChange { it.copy(plaintiffName = v) } },
                            )
                            val (pCounselText, onPCounselTextChange, flushPCounsel) = rememberBufferedText(
                                externalValue = activeCase.plaintiffCounsel,
                                resetKey = activeCase.id,
                                onCommit = { v -> onMetadataChange { it.copy(plaintiffCounsel = v) } },
                            )
                            val (defendantText, onDefendantTextChange, flushDefendant) = rememberBufferedText(
                                externalValue = activeCase.defendantName,
                                resetKey = activeCase.id,
                                onCommit = { v -> onMetadataChange { it.copy(defendantName = v) } },
                            )
                            val (dCounselText, onDCounselTextChange, flushDCounsel) = rememberBufferedText(
                                externalValue = activeCase.defendantCounsel,
                                resetKey = activeCase.id,
                                onCommit = { v -> onMetadataChange { it.copy(defendantCounsel = v) } },
                            )

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val stackParties = maxWidth < 460.dp
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (stackParties) {
                                        OutlinedTextField(
                                            value = plaintiffText,
                                            onValueChange = onPlaintiffTextChange,
                                            label = { Text(if (activeCase.caseType == CaseType.APPEAL) "ይግባኝ ባይ" else "ከሳሽ/ከሳሾች") },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                            maxLines = 3,
                                            enabled = !isReadOnly,
                                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushPlaintiff() },
                                        )
                                        OutlinedTextField(
                                            value = pCounselText,
                                            onValueChange = onPCounselTextChange,
                                            label = { Text("ጠበቃ") },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                            maxLines = 2,
                                            enabled = !isReadOnly,
                                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushPCounsel() },
                                        )
                                        OutlinedTextField(
                                            value = defendantText,
                                            onValueChange = onDefendantTextChange,
                                            label = { Text(if (activeCase.caseType == CaseType.APPEAL) "መልስ ሰጭ" else "ተከሳሽ/ተከሳሾች") },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                            maxLines = 3,
                                            enabled = !isReadOnly,
                                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushDefendant() },
                                        )
                                        OutlinedTextField(
                                            value = dCounselText,
                                            onValueChange = onDCounselTextChange,
                                            label = { Text("ጠበቃ / ነገረፈጅ") },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                            maxLines = 2,
                                            enabled = !isReadOnly,
                                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushDCounsel() },
                                        )
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedTextField(
                                                value = plaintiffText,
                                                onValueChange = onPlaintiffTextChange,
                                                label = { Text(if (activeCase.caseType == CaseType.APPEAL) "ይግባኝ ባይ" else "ከሳሽ/ከሳሾች") },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                maxLines = 3,
                                                enabled = !isReadOnly,
                                                modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) flushPlaintiff() },
                                            )
                                            OutlinedTextField(
                                                value = pCounselText,
                                                onValueChange = onPCounselTextChange,
                                                label = { Text("ጠበቃ") },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                maxLines = 2,
                                                enabled = !isReadOnly,
                                                modifier = Modifier.weight(0.85f).onFocusChanged { if (!it.isFocused) flushPCounsel() },
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedTextField(
                                                value = defendantText,
                                                onValueChange = onDefendantTextChange,
                                                label = { Text(if (activeCase.caseType == CaseType.APPEAL) "መልስ ሰጭ" else "ተከሳሽ/ተከሳሾች") },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                maxLines = 3,
                                                enabled = !isReadOnly,
                                                modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) flushDefendant() },
                                            )
                                            OutlinedTextField(
                                                value = dCounselText,
                                                onValueChange = onDCounselTextChange,
                                                label = { Text("ጠበቃ / ነገረፈጅ") },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                maxLines = 2,
                                                enabled = !isReadOnly,
                                                modifier = Modifier.weight(0.85f).onFocusChanged { if (!it.isFocused) flushDCounsel() },
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Subject Tag (የጉዳይ ዘርፍ)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "የጉዳይ ዘርፍ (ለተመሳሳይ ጉዳይ ፍለጋ እና ማጣቀሻ)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    PastJudgmentParser.SUBJECT_TAGS.forEach { tag ->
                                        val isSelected = activeSubjectTag == tag
                                        FilterChip(
                                            selected = isSelected,
                                            enabled = !isReadOnly,
                                            onClick = { onSelectSubjectTag(if (isSelected) null else tag) },
                                            label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                            modifier = Modifier.testTag("subject_tag_$tag"),
                                        )
                                    }
                                }
                            }

                            // Adjournment Date Tracker (የቀጠሮ ቀን)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Event,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (!activeAdjournmentDate.isNullOrBlank()) {
                                                    "የቀጠሮ ቀን: $activeAdjournmentDate ዓ.ም"
                                                } else {
                                                    "ቀጣይ የችሎት ቀጠሮ ቀን (አማራጭ)"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        if (!activeAdjournmentDate.isNullOrBlank() && !isReadOnly) {
                                            IconButton(
                                                onClick = onClearAdjournment,
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "ቀጠሮ አጽዳ", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    if (!isReadOnly) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AssistChip(
                                                onClick = onOpenAdjournmentPicker,
                                                label = { Text("ቀን ምረጥ") },
                                                modifier = Modifier.testTag("pick_adjournment_date_button"),
                                            )
                                            AssistChip(
                                                onClick = { onQuickAdjournmentDays(7) },
                                                label = { Text("+7 ቀን") },
                                            )
                                            AssistChip(
                                                onClick = { onQuickAdjournmentDays(15) },
                                                label = { Text("+15 ቀን") },
                                            )
                                            AssistChip(
                                                onClick = { onQuickAdjournmentDays(30) },
                                                label = { Text("+30 ቀን") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            // Section 1: Plaintiff Claim Photos & Docs
            item(key = "draft_plaintiff_upload") {
                Box(modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
                    DocumentUploadCard(
                        title = "1. የከሳሽ ክስ ፎቶዎች እና ሰነዶች",
                        subtitle = "ፎቶ፣ PDF ወይም Word (.docx) — ብዙ ሰነዶች መምረጥ ይችላሉ",
                        attachments = plaintiffAttachments,
                        isReadOnly = isReadOnly,
                        pickPhotosTag = "plaintiff_pick_photos",
                        onPickPhotos = { onPickPhotos(AttachmentTarget.PLAINTIFF) },
                        onPickDocuments = { onPickDocuments(AttachmentTarget.PLAINTIFF) },
                        onCaptureCamera = { onCaptureCamera(AttachmentTarget.PLAINTIFF) },
                        onRemoveAttachment = { id -> onRemoveAttachment(id, AttachmentTarget.PLAINTIFF) },
                    )
                }
            }

            // Section 2: Defendant Defense Photos & Docs
            item(key = "draft_defendant_upload") {
                Box(modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
                    DocumentUploadCard(
                        title = "2. የተከሳሽ መልስ/መከላከያ ፎቶዎች እና ሰነዶች",
                        subtitle = "ፎቶ፣ PDF ወይም Word (.docx) — ብዙ ሰነዶች መምረጥ ይችላሉ",
                        attachments = defendantAttachments,
                        isReadOnly = isReadOnly,
                        pickPhotosTag = "defendant_pick_photos",
                        onPickPhotos = { onPickPhotos(AttachmentTarget.DEFENDANT) },
                        onPickDocuments = { onPickDocuments(AttachmentTarget.DEFENDANT) },
                        onCaptureCamera = { onCaptureCamera(AttachmentTarget.DEFENDANT) },
                        onRemoveAttachment = { id -> onRemoveAttachment(id, AttachmentTarget.DEFENDANT) },
                    )
                }
            }

            // Section 3: Judge Special Instructions, Witness Testimony & Court Evidence Photos/Docs
            item(key = "draft_special_instructions") {
                val (instructionsText, onInstructionsChange, flushInstructions) = rememberBufferedText(
                    externalValue = activeCase.specialInstructions,
                    resetKey = activeCase.id,
                    onCommit = { v -> onMetadataChange { it.copy(specialInstructions = v) } },
                )
                ElevatedCard(
                    modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "3. የዳኛው ልዩ መመሪያ፣ የምስክር ቃል እና የፍርድ ቤት ማስረጃ",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "በጽሁፍ፣ በድምጽ ወይም የምስክር ቃል / በፍርድ ቤት የቀረበ ማስረጃ ፎቶዎችን (ብዙ ፎቶዎችን) ያያይዙ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = instructionsText,
                            onValueChange = onInstructionsChange,
                            label = { Text("የዳኛው ልዩ መመሪያ (በጽሁፍ)") },
                            placeholder = { Text("ለምሳሌ፡ የምስክር ቃሉን ወይም የመጀመሪያ ደረጃ መቃወሚያውን ብቻ አተኩር...") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            trailingIcon = {
                                IconButton(onClick = onLaunchVoiceInstructions, enabled = !isReadOnly) {
                                    Icon(Icons.Default.Mic, contentDescription = "በድምጽ መመሪያ አስገባ")
                                }
                            },
                            minLines = 2,
                            maxLines = 6,
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(instructionsBringIntoView)
                                .onFocusChanged { if (!it.isFocused) flushInstructions() }
                                .onFocusEvent { focusState ->
                                    if (focusState.isFocused) {
                                        uiScope.launch {
                                            delay(220)
                                            instructionsBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                                .testTag("special_instructions_input"),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { onPickPhotos(AttachmentTarget.JUDGE_INSTRUCTIONS) },
                                enabled = !isReadOnly,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                                    .testTag("judge_instructions_pick_photos"),
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ፎቶዎች", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onPickDocuments(AttachmentTarget.JUDGE_INSTRUCTIONS) },
                                enabled = !isReadOnly,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                                    .testTag("judge_instructions_pick_docs"),
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF/Word", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onCaptureCamera(AttachmentTarget.JUDGE_INSTRUCTIONS) },
                                enabled = !isReadOnly,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("judge_instructions_capture_camera"),
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ካሜራ", maxLines = 1)
                            }
                        }

                        if (judgeInstructionAttachments.isNotEmpty()) {
                            AttachmentThumbnailRow(
                                attachments = judgeInstructionAttachments,
                                isReadOnly = isReadOnly,
                                onRemoveAttachment = { id -> onRemoveAttachment(id, AttachmentTarget.JUDGE_INSTRUCTIONS) },
                            )
                            if (!isReadOnly) {
                                OutlinedButton(
                                    onClick = {
                                        flushInstructions()
                                        onExtractJudgeInstructionAttachments()
                                    },
                                    enabled = pipelineState !is PipelineState.ReadingOcr && pipelineState !is PipelineState.StreamingDraft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("ocr_judge_instructions_button"),
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "ከፎቶ/ሰነድ ጽሁፍ አውጣ (OCR → መመሪያ)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Action CTA: Stage 1 OCR -> Review -> Draft
            item(key = "draft_generate_button") {
                Button(
                    onClick = onStartOcr,
                    enabled = !isReadOnly && pipelineState !is PipelineState.ReadingOcr && pipelineState !is PipelineState.StreamingDraft,
                    modifier = Modifier
                        .widthIn(max = 700.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_draft_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Balance, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ውሳኔ/ብይን ረቂቅ እና ጭብጥ አዘጋጅ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Empty State Tip if no draft generated yet
            if (!JudicialDocumentFormatter.hasDraftContent(activeCase)) {
                item(key = "draft_empty_tip") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("የስራ ቅደም ተከተል መመሪያ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "1. የከሳሽ፣ የተከሳሽ እና በክፍል 3 ላይ የምስክር ቃል/የፍርድ ቤት ማስረጃ ፎቶ/PDF/Word ሰነዶችን ይምረጡ።\n2. 'ውሳኔ/ብይን ረቂቅ እና ጭብጥ አዘጋጅ' ሲነኩ ፎቶዎቹ ተጨምቀው ይነበባሉ።\n3. የተነበበውን ጽሁፍ አረጋግጠው 'ረቂቅ ጀምር' ሲሉ በዳኛ ኑህ መሀመድ የአፃፃፍ ስልት አንድ ወጥ የተሟላ የፍርድ ቤት ሰነድ ሆኖ ይቀርባል። ከዚያም 'ወደ ውይይት አሸጋግር' በመንካት የሚጨመር ወይም የሚቀነስ ነጥብ ማሻሻል ይችላሉ።",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                // Unified Court Document Preview & Editor (matching Judge Nuh's 3 templates)
                item(key = "draft_unified_document_card") {
                    val unifiedDocumentText = remember(
                        activeCase.id,
                        activeCase.caseNumber,
                        activeCase.ethiopianDate,
                        activeCase.caseType,
                        activeCase.judges,
                        activeCase.plaintiffName,
                        activeCase.plaintiffCounsel,
                        activeCase.defendantName,
                        activeCase.defendantCounsel,
                        activeCase.issuesText,
                        activeCase.analysisText,
                        activeCase.decisionText,
                        activeCase.closingText,
                    ) {
                        JudicialDocumentFormatter.composeUnifiedDocument(activeCase)
                    }

                    val (localDocText, onLocalDocTextChange, flushLocalDocText) = rememberBufferedText(
                        externalValue = unifiedDocumentText,
                        resetKey = "${activeCase.id}:${activeCase.currentVersion}",
                        debounceMs = 150L,
                        onCommit = onUpdateUnifiedDocument,
                    )

                    ElevatedCard(
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("unified_document_card"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "የፍርድ ቤት ${activeCase.caseType.amharicLabel} ሰነድ",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "ስሪት v${activeCase.currentVersion} • አንድ ወጥ የፍርድ ቤት ቅጽ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            flushLocalDocText()
                                            clipboardManager.setText(AnnotatedString(localDocText))
                                            copiedFeedback = true
                                        },
                                        modifier = Modifier.testTag("copy_unified_doc_button"),
                                    ) {
                                        Icon(
                                            if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "ሰነዱን ቅዳ",
                                            tint = if (copiedFeedback) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            flushLocalDocText()
                                            onOpenVersionHistory()
                                        }
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = "የስሪት ታሪክ")
                                    }
                                    if (!isReadOnly) {
                                        IconButton(
                                            onClick = {
                                                flushLocalDocText()
                                                onSaveManualVersion()
                                            }
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = "ስሪት አስቀምጥ")
                                        }
                                    }
                                }
                            }

                            if (!isReadOnly) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    FilterChip(
                                        selected = !isEditingUnified,
                                        onClick = {
                                            flushLocalDocText()
                                            copiedFeedback = false
                                            isEditingUnified = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("የሰነድ እይታ") },
                                        modifier = Modifier.testTag("mode_preview_chip"),
                                    )
                                    FilterChip(
                                        selected = isEditingUnified,
                                        onClick = {
                                            copiedFeedback = false
                                            isEditingUnified = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("ሰነዱን አርም") },
                                        modifier = Modifier.testTag("mode_edit_chip"),
                                    )
                                    AssistChip(
                                        onClick = {
                                            flushLocalDocText()
                                            onOpenFullScreenEditor()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("ሙሉ ስክሪን") },
                                        modifier = Modifier.testTag("open_fullscreen_editor_button"),
                                    )
                                }
                            }

                            if (isEditingUnified && !isReadOnly) {
                                OutlinedTextField(
                                    value = localDocText,
                                    onValueChange = onLocalDocTextChange,
                                    label = { Text("ሙሉ የፍርድ ቤት ውሳኔ / ብይን ጽሁፍ") },
                                    supportingText = {
                                        Text(
                                            "ረጅም ጽሁፍ ሲሆን በሳጥኑ ውስጥ ወደ ላይ/ታች መንሸራተት ይችላሉ",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 25.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    minLines = 8,
                                    maxLines = 14,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(editorBringIntoView)
                                        .onFocusChanged { if (!it.isFocused) flushLocalDocText() }
                                        .onFocusEvent { focusState ->
                                            if (focusState.isFocused) {
                                                uiScope.launch {
                                                    delay(220)
                                                    editorBringIntoView.bringIntoView()
                                                }
                                            }
                                        }
                                        .testTag("editor_unified_document"),
                                )
                            } else {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                                val annotatedDocument = remember(unifiedDocumentText, primaryColor, onSurfaceColor) {
                                    buildUnifiedDocumentAnnotatedString(
                                        documentText = unifiedDocumentText,
                                        primaryColor = primaryColor,
                                        onSurfaceColor = onSurfaceColor,
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = annotatedDocument,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                lineHeight = 25.sp,
                                                color = onSurfaceColor,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 18.dp),
                                        )
                                    }
                                }

                                if (!isReadOnly) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        OutlinedButton(
                                            onClick = { isEditingUnified = true },
                                            modifier = Modifier.weight(1f).height(48.dp).testTag("enter_edit_mode_button"),
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("በቀጥታ አርም", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                flushLocalDocText()
                                                onOpenFullScreenEditor()
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp).testTag("enter_fullscreen_edit_button"),
                                        ) {
                                            Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("ሙሉ ስክሪን አርም", maxLines = 1)
                                        }
                                    }
                                }
                            }

                            if (!isReadOnly) {
                                Button(
                                    onClick = {
                                        flushLocalDocText()
                                        onTransferToDiscussion()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("transfer_to_discussion_button"),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ወደ ውይይት አሸጋግር (አሻሽል / ጨምር / ቀንስ)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // [ማስታወሻ ለዳኛ] Hallucination & Verification Guardrail Card
                if (activeCase.judgeNotesText.isNotBlank()) {
                    item(key = "draft_judge_notes_card") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showJudgeNotes = !showJudgeNotes },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                                        Spacer(Modifier.width(8.dp))
                                        Text("[ማስታወሻ ለዳኛ] — የAI ማረጋገጫ ነጥቦች", fontWeight = FontWeight.Bold)
                                    }
                                    Icon(if (showJudgeNotes) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                }
                                AnimatedVisibility(visible = showJudgeNotes) {
                                    Text(activeCase.judgeNotesText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // 3-Option Quality Grading Card (Bad / Good / Excellent)
                item(key = "draft_quality_rating_card") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("የረቂቁ ጥራት ደረጃ (Quality Grading)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = onOpenBadRatingDialog,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (activeCase.qualityRating == QualityRating.BAD) AlertCrimson.copy(alpha = 0.16f) else Color.Transparent,
                                        contentColor = AlertCrimson,
                                    ),
                                    modifier = Modifier.weight(1f).testTag("rate_bad_button"),
                                ) {
                                    Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("መጥፎ", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { onRateGoodOrExcellent(QualityRating.GOOD) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (activeCase.qualityRating == QualityRating.GOOD) VerifiedEmerald.copy(alpha = 0.14f) else Color.Transparent,
                                        contentColor = VerifiedEmerald,
                                    ),
                                    modifier = Modifier.weight(1f).testTag("rate_good_button"),
                                ) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ጥሩ", maxLines = 1)
                                }
                                Button(
                                    onClick = { onRateGoodOrExcellent(QualityRating.EXCELLENT) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeCase.qualityRating == QualityRating.EXCELLENT) JudicialGold else MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.weight(1.1f).testTag("rate_excellent_button"),
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("እጅግ ጥሩ", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Bottom One-Handed Action Row: Finalize & Lock to Archives + PDF Export
                item(key = "draft_bottom_actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                    ) {
                        if (!isReadOnly) {
                            Button(
                                onClick = onFinalizeToArchive,
                                colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                                modifier = Modifier.weight(1f).height(52.dp).testTag("finalize_archive_button"),
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("ወደ Archives ቆልፍ", maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenPdfExport,
                            modifier = Modifier.weight(1f).height(52.dp).testTag("open_pdf_export_button"),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("PDF አውጣ", maxLines = 1)
                        }
                    }
                }
            }

            item(key = "draft_bottom_spacer") {
                Spacer(Modifier.height(76.dp))
            }
        }

        if (canScrollUp || canScrollDown) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canScrollUp) {
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandTopChrome()
                            uiScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("draft_scroll_top_button"),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ወደ ላይ ሂድ")
                    }
                }
                if (canScrollDown) {
                    SmallFloatingActionButton(
                        onClick = {
                            uiScope.launch {
                                val total = listState.layoutInfo.totalItemsCount
                                if (total > 0) listState.animateScrollToItem(total - 1)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("draft_scroll_bottom_button"),
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "ወደ ታች ሂድ")
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentUploadCard(
    title: String,
    subtitle: String,
    attachments: List<DocumentAttachment>,
    isReadOnly: Boolean,
    pickPhotosTag: String,
    onPickPhotos: () -> Unit,
    onPickDocuments: () -> Unit,
    onCaptureCamera: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPickPhotos,
                    enabled = !isReadOnly,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1.2f).testTag(pickPhotosTag),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ፎቶዎች", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickDocuments,
                    enabled = !isReadOnly,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1.2f),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF/Word", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCaptureCamera,
                    enabled = !isReadOnly,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ካሜራ", maxLines = 1)
                }
            }

            if (attachments.isNotEmpty()) {
                AttachmentThumbnailRow(
                    attachments = attachments,
                    isReadOnly = isReadOnly,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnailRow(
    attachments: List<DocumentAttachment>,
    isReadOnly: Boolean,
    onRemoveAttachment: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(attachments, key = { it.id }) { att ->
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 118.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (att.kind == AttachmentKind.IMAGE) {
                    AsyncImage(
                        model = att.uriString.toUri(),
                        contentDescription = att.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(att.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                ) {
                    val badge = if (att.directExtractedText != null) "ጽሁፍ" else "${att.compressedSizeKb}KB"
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                if (!isReadOnly) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable { onRemoveAttachment(att.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "አስወግድ", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineProgressCard(
    stageText: String,
    stepIndex: Int,
    onCancel: () -> Unit,
) {
    val steps = listOf("ፎቶ እየተጨመቀ", "እየተነበበ", "ጭብጥ እየተለየ", "ረቂቅ እየተጻፈ")
    ElevatedCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stageText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_ai_button")) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("አቁም")
                }
            }
            LinearProgressIndicator(
                progress = { (stepIndex + 1) / steps.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { idx, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (idx == stepIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx <= stepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingLivePreviewCard(pipelineState: PipelineState.StreamingDraft) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ቀጥታ ረቂቅ (Streaming)...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val preview = pipelineState.parsed.analysis.ifBlank { pipelineState.rawText }
            Text(
                preview.ifBlank { "AI ምላሽ እየጠበቀ ነው..." },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            )
        }
    }
}

@Composable
private fun OcrReviewCard(
    plaintiffText: String,
    defendantText: String,
    statsSummary: String,
    onTextsChange: (String, String) -> Unit,
    onAutoFillMetadata: () -> Unit,
    onConfirmAndDraft: () -> Unit,
) {
    var localP by remember { mutableStateOf(plaintiffText) }
    var localD by remember { mutableStateOf(defendantText) }
    var lastEmittedP by remember { mutableStateOf(plaintiffText) }
    var lastEmittedD by remember { mutableStateOf(defendantText) }
    val latestCommit by rememberUpdatedState(onTextsChange)

    LaunchedEffect(plaintiffText, defendantText) {
        if (plaintiffText != lastEmittedP) {
            localP = plaintiffText
            lastEmittedP = plaintiffText
        }
        if (defendantText != lastEmittedD) {
            localD = defendantText
            lastEmittedD = defendantText
        }
    }

    LaunchedEffect(localP, localD) {
        if (localP != lastEmittedP || localD != lastEmittedD) {
            delay(160L)
            lastEmittedP = localP
            lastEmittedD = localD
            latestCommit(localP, localD)
        }
    }

    fun flushOcrTexts() {
        if (localP != lastEmittedP || localD != lastEmittedD) {
            lastEmittedP = localP
            lastEmittedD = localD
            latestCommit(localP, localD)
        }
    }

    DisposableEffect(Unit) {
        onDispose { flushOcrTexts() }
    }

    ElevatedCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = VerifiedEmerald)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ንባብ ተጠናቋል — ከመረቀቁ በፊት ያረጋግጡ", style = MaterialTheme.typography.titleMedium)
                    Text(statsSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = {
                    flushOcrTexts()
                    onAutoFillMetadata()
                },
                modifier = Modifier.fillMaxWidth().testTag("ocr_autofill_metadata_button"),
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ከOCR መረጃ ሙላ (የመ/ቁ እና ተከራካሪዎች)", maxLines = 1)
            }
            OutlinedTextField(
                value = localP,
                onValueChange = { localP = it },
                label = { Text("የከሳሽ/ይግባኝ ባይ የተነበበ ጽሁፍ (ማረም ይችላሉ)") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushOcrTexts() },
            )
            OutlinedTextField(
                value = localD,
                onValueChange = { localD = it },
                label = { Text("የተከሳሽ/መልስ ሰጪ የተነበበ ጽሁፍ (ማረም ይችላሉ)") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) flushOcrTexts() },
            )
            Button(
                onClick = {
                    flushOcrTexts()
                    onConfirmAndDraft()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_ocr_start_draft_button"),
            ) {
                Text("ንባቡን አረጋግጥ → ረቂቅ ጀምር", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class BufferedTextHandle(
    val value: String,
    val onValueChange: (String) -> Unit,
    val flush: () -> Unit,
)

@Composable
private fun rememberBufferedText(
    externalValue: String,
    resetKey: Any?,
    debounceMs: Long = 160L,
    onCommit: (String) -> Unit,
): BufferedTextHandle {
    var localValue by remember(resetKey) { mutableStateOf(externalValue) }
    var lastCommitted by remember(resetKey) { mutableStateOf(externalValue) }
    val latestCommit by rememberUpdatedState(onCommit)

    LaunchedEffect(externalValue) {
        if (externalValue != lastCommitted) {
            localValue = externalValue
            lastCommitted = externalValue
        }
    }

    LaunchedEffect(localValue) {
        if (localValue != lastCommitted) {
            delay(debounceMs)
            lastCommitted = localValue
            latestCommit(localValue)
        }
    }

    val flush: () -> Unit = remember(resetKey) {
        {
            if (localValue != lastCommitted) {
                lastCommitted = localValue
                latestCommit(localValue)
            }
        }
    }

    DisposableEffect(resetKey) {
        onDispose {
            if (localValue != lastCommitted) {
                lastCommitted = localValue
                latestCommit(localValue)
            }
        }
    }

    return BufferedTextHandle(
        value = localValue,
        onValueChange = { localValue = it },
        flush = flush,
    )
}

private fun buildUnifiedDocumentAnnotatedString(
    documentText: String,
    primaryColor: Color,
    onSurfaceColor: Color,
): AnnotatedString = buildAnnotatedString {
    val lines = documentText.split('\n')
    lines.forEachIndexed { idx, rawLine ->
        val trimmed = rawLine.trim()
        when {
            trimmed.isEmpty() -> append('\n')
            JudicialDocumentFormatter.isUnifiedHeadingLine(trimmed) -> {
                withStyle(ParagraphStyle(textAlign = TextAlign.Center)) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 17.sp,
                            color = primaryColor,
                        )
                    ) {
                        append(trimmed)
                    }
                }
            }
            JudicialDocumentFormatter.isUnifiedHeaderLabelLine(trimmed) -> {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceColor,
                    )
                ) {
                    append(trimmed)
                }
                if (idx < lines.lastIndex) append('\n')
            }
            else -> {
                append(trimmed)
                if (idx < lines.lastIndex) append('\n')
            }
        }
    }
}
