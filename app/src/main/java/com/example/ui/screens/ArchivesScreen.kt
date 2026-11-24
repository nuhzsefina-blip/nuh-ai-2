package com.example.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.example.data.local.CaseEntity
import com.example.data.local.CaseType
import com.example.data.local.QualityRating
import com.example.data.local.SyncStatus
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JudicialGold
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.ArchiveImportState
import com.example.util.JudicialDocumentFormatter
import com.example.util.PastJudgmentParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArchivesScreen(
    isUnlocked: Boolean,
    hasPinSet: Boolean,
    biometricEnabled: Boolean,
    searchQuery: String,
    ratingFilter: QualityRating?,
    typeFilter: CaseType?,
    pagedCases: LazyPagingItems<CaseEntity>,
    importState: ArchiveImportState = ArchiveImportState(),
    onUnlockBiometric: () -> Unit,
    onUnlockWithPin: () -> Unit,
    onUnlockWithoutCredentials: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRatingFilterChange: (QualityRating?) -> Unit,
    onTypeFilterChange: (CaseType?) -> Unit,
    onOpenCase: (CaseEntity) -> Unit,
    onExportPdf: (CaseEntity) -> Unit,
    onReopenForRevision: (CaseEntity) -> Unit,
    onVerifyPdfClick: () -> Unit,
    onUploadReferenceFiles: () -> Unit = {},
    onUploadReferencePhotos: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    onDismissImportStatus: () -> Unit = {},
    settingsPinnedKeys: Set<String> = emptySet(),
    settingsSubjectTags: Map<String, String> = emptyMap(),
    settingsAdjournmentDates: Map<String, String> = emptyMap(),
    pinnedOnlyFilter: Boolean = false,
    subjectFilter: String? = null,
    onPinnedOnlyFilterChange: (Boolean) -> Unit = {},
    onSubjectFilterChange: (String?) -> Unit = {},
    onTogglePin: (CaseEntity) -> Unit = {},
    onSetSubjectTag: (CaseEntity, String?) -> Unit = { _, _ -> },
    onRenameCase: (CaseEntity, String) -> Unit = { _, _ -> },
    onDeleteCase: (CaseEntity) -> Unit = {},
    onExpandTopChrome: () -> Unit = {},
) {
    if (!isUnlocked) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            ElevatedCard(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text("የArchives መዝገብ ቤት ተቆልፏል", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "የተጠናቀቁ የፍርድ ቤት መዝገቦችን ለማየት በጣት አሻራ ወይም በPIN ያረጋግጡ።",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (biometricEnabled) {
                        Button(
                            onClick = onUnlockBiometric,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("unlock_biometric_button"),
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("በባዮሜትሪክ ክፈት")
                        }
                    }
                    if (hasPinSet) {
                        OutlinedButton(
                            onClick = onUnlockWithPin,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("unlock_pin_button"),
                        ) {
                            Text("በPIN ክፈት")
                        }
                    }
                    if (!hasPinSet && !biometricEnabled) {
                        Button(
                            onClick = onUnlockWithoutCredentials,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Archives ክፈት")
                        }
                    }
                }
            }
        }
        return
    }

    val archiveListState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val showScrollTop by remember { derivedStateOf { archiveListState.canScrollBackward } }

    var taggingCase by remember { mutableStateOf<CaseEntity?>(null) }
    var renamingCase by remember { mutableStateOf<CaseEntity?>(null) }
    var renameDraftText by remember { mutableStateOf("") }
    var deletingCase by remember { mutableStateOf<CaseEntity?>(null) }

    taggingCase?.let { target ->
        val currentTag = PastJudgmentParser.resolveMapValue(settingsSubjectTags, target)
        AlertDialog(
            onDismissRequest = { taggingCase = null },
            title = { Text("የጉዳይ ዘርፍ ምረጥ (መ/ቁ ${target.caseNumber.ifBlank { "-" }})") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PastJudgmentParser.SUBJECT_TAGS.forEach { tag ->
                        FilterChip(
                            selected = currentTag == tag,
                            onClick = {
                                onSetSubjectTag(target, if (currentTag == tag) null else tag)
                                taggingCase = null
                            },
                            label = { Text(tag) },
                        )
                    }
                }
            },
            confirmButton = {
                if (!currentTag.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onSetSubjectTag(target, null)
                            taggingCase = null
                        }
                    ) { Text("መለያውን አንሳ") }
                }
            },
            dismissButton = {
                TextButton(onClick = { taggingCase = null }) { Text("ዝጋ") }
            },
        )
    }

    renamingCase?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingCase = null },
            title = { Text("የመዝገብ ቁጥር / ስም ቀይር") },
            text = {
                OutlinedTextField(
                    value = renameDraftText,
                    onValueChange = { renameDraftText = it },
                    label = { Text("አዲስ የመ/ቁ ወይም መለያ") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().testTag("archive_rename_input"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameCase(target, renameDraftText)
                        renamingCase = null
                    },
                    enabled = renameDraftText.isNotBlank(),
                    modifier = Modifier.testTag("archive_rename_confirm"),
                ) { Text("ቀይር") }
            },
            dismissButton = {
                TextButton(onClick = { renamingCase = null }) { Text("ተመለስ") }
            },
        )
    }

    deletingCase?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingCase = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertCrimson) },
            title = { Text("መዝገቡን ከArchive ሰርዝ?") },
            text = {
                Text(
                    "የመ/ቁ '${target.caseNumber.ifBlank { "-" }}' ከስልክዎ Archive ሊሰረዝ ነው። ይህ እርምጃ አይመለስም።",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCase(target)
                        deletingCase = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson),
                    modifier = Modifier.testTag("archive_delete_confirm"),
                ) { Text("አዎ ሰርዝ") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCase = null }) { Text("ተመለስ") }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = archiveListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 72.dp),
                ) {
                    // Search + Verify PDF Bar inside LazyColumn so entire top section scrolls away freely
                    item(key = "archive_search_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchChange,
                                placeholder = {
                                    Text(
                                        "በመ/ቁ፣ በስም ወይም በጭብጥ ፈልግ (FTS4)...",
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
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { onSearchChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "ፍለጋን አጽዳ")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("archive_search_input"),
                            )
                            OutlinedButton(
                                onClick = onVerifyPdfClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("archive_verify_pdf_button"),
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = "PDF አረጋግጥ", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF አረጋግጥ", maxLines = 1)
                            }
                        }
                    }
                    // Multi-file Past Judgments Upload Card inside scroll list so full list scrolls freely
                    item(key = "archive_upload_header") {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().testTag("archive_upload_card"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = JudicialGold,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "የቀድሞ ውሳኔዎችን ጫን (Style & Reference)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "ከዚህ በፊት የሰሯቸውን ውሳኔዎች (PDF, Word, TXT, ፎቶ) በብዛት ያስቀምጡ — AI የአፃፃፍ ስልትዎን ይማራል፣ ተመሳሳይ ጉዳይ ሲኖርም ማጣቀሻ ይሆናል።",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (importState.isImporting) {
                                    val progress = if (importState.totalCount > 0) {
                                        (importState.processedCount.toFloat() / importState.totalCount.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "${importState.processedCount + 1}/${importState.totalCount} በመጫን ላይ: ${importState.currentFileName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(onClick = onCancelImport) {
                                                Text("አቁም")
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = onUploadReferenceFiles,
                                            modifier = Modifier.weight(1.4f).testTag("archive_upload_files_button"),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        ) {
                                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("ፋይሎች ጫን (ብዙ)", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = onUploadReferencePhotos,
                                            modifier = Modifier.weight(1f).testTag("archive_upload_photos_button"),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("ፎቶዎች", maxLines = 1)
                                        }
                                    }
                                }

                                val feedbackMsg = importState.error ?: importState.lastSummary
                                if (!feedbackMsg.isNullOrBlank() && !importState.isImporting) {
                                    Surface(
                                        color = if (importState.error != null) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                feedbackMsg,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = onDismissImportStatus,
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "ዝጋ", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rating, Pin, Subject & Case Type Filter Chips inside LazyColumn
                    item(key = "archive_filters_header") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = pinnedOnlyFilter,
                                onClick = { onPinnedOnlyFilterChange(!pinnedOnlyFilter) },
                                leadingIcon = {
                                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text("ዋና ናሙና") },
                                modifier = Modifier.testTag("archive_filter_pinned"),
                            )
                            FilterChip(
                                selected = ratingFilter == QualityRating.EXCELLENT,
                                onClick = {
                                    onRatingFilterChange(if (ratingFilter == QualityRating.EXCELLENT) null else QualityRating.EXCELLENT)
                                },
                                label = { Text("እጅግ ጥሩ") },
                            )
                            FilterChip(
                                selected = ratingFilter == QualityRating.GOOD,
                                onClick = {
                                    onRatingFilterChange(if (ratingFilter == QualityRating.GOOD) null else QualityRating.GOOD)
                                },
                                label = { Text("ጥሩ") },
                            )
                            FilterChip(
                                selected = ratingFilter == QualityRating.BAD,
                                onClick = {
                                    onRatingFilterChange(if (ratingFilter == QualityRating.BAD) null else QualityRating.BAD)
                                },
                                label = { Text("መጥፎ") },
                            )
                            CaseType.entries.forEach { t ->
                                FilterChip(
                                    selected = typeFilter == t,
                                    onClick = { onTypeFilterChange(if (typeFilter == t) null else t) },
                                    label = { Text(t.amharicLabel) },
                                )
                            }
                            PastJudgmentParser.SUBJECT_TAGS.forEach { tag ->
                                FilterChip(
                                    selected = subjectFilter == tag,
                                    onClick = { onSubjectFilterChange(if (subjectFilter == tag) null else tag) },
                                    label = { Text("#$tag") },
                                )
                            }
                        }
                    }

                    if (pagedCases.itemCount == 0) {
                        item(key = "archive_empty_state") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(40.dp))
                                    Text("በArchives ውስጥ የተጠናቀቀ መዝገብ የለም", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "ከላይ የቀድሞ ውሳኔዎችን ይጫኑ ወይም በ'መዝገብ እና ውይይት' ውስጥ ረቂቅ ሲጨርሱ 'ወደ Archives ቆልፍ' ይጫኑ።",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            count = pagedCases.itemCount,
                            key = pagedCases.itemKey { it.id },
                        ) { index ->
                            val item = pagedCases[index] ?: return@items
                            val itemPinned = remember(item.id, item.contentSha256, settingsPinnedKeys) {
                                PastJudgmentParser.matchesAnyKey(settingsPinnedKeys, item)
                            }
                            val itemTag = remember(item.id, item.contentSha256, settingsSubjectTags) {
                                PastJudgmentParser.resolveMapValue(settingsSubjectTags, item)
                            }
                            val itemAdjournment = remember(item.id, item.contentSha256, settingsAdjournmentDates) {
                                PastJudgmentParser.resolveMapValue(settingsAdjournmentDates, item)
                            }
                            ArchiveCaseCard(
                                caseEntity = item,
                                isPinned = itemPinned,
                                subjectTag = itemTag,
                                adjournmentDate = itemAdjournment,
                                onOpen = { onOpenCase(item) },
                                onExportPdf = { onExportPdf(item) },
                                onReopen = { onReopenForRevision(item) },
                                onTogglePin = { onTogglePin(item) },
                                onOpenTagPicker = { taggingCase = item },
                                onOpenRename = {
                                    renameDraftText = item.caseNumber
                                    renamingCase = item
                                },
                                onRequestDelete = { deletingCase = item },
                            )
                        }
                    }
                }

                if (showScrollTop) {
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandTopChrome()
                            uiScope.launch { archiveListState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 8.dp)
                            .testTag("archive_scroll_top_button"),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ወደ ላይ ሂድ")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveCaseCard(
    caseEntity: CaseEntity,
    isPinned: Boolean = false,
    subjectTag: String? = null,
    adjournmentDate: String? = null,
    onOpen: () -> Unit,
    onExportPdf: () -> Unit,
    onReopen: () -> Unit,
    onTogglePin: () -> Unit = {},
    onOpenTagPicker: () -> Unit = {},
    onOpenRename: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
) {
    val isUploadedRef = remember(caseEntity.id, caseEntity.specialInstructions, caseEntity.judgeNotesText) {
        PastJudgmentParser.isUploadedReferenceCase(caseEntity)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("archive_case_card_${caseEntity.id}"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "የመ/ቁ:- ${caseEntity.caseNumber.ifBlank { "-" }}  •  ${caseEntity.caseType.amharicLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${caseEntity.ethiopianDate} ዓ.ም  |  ስሪት v${caseEntity.currentVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Quality Rating Badge
                if (caseEntity.qualityRating != QualityRating.NONE) {
                    val (bg, icon) = when (caseEntity.qualityRating) {
                        QualityRating.EXCELLENT -> JudicialGold to Icons.Default.Star
                        QualityRating.GOOD -> VerifiedEmerald to Icons.Default.ThumbUp
                        QualityRating.BAD -> AlertCrimson to Icons.Default.ThumbDown
                        QualityRating.NONE -> Color.Gray to Icons.Default.Star
                    }
                    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(caseEntity.qualityRating.amharicLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            if (caseEntity.plaintiffName.isNotBlank() || caseEntity.defendantName.isNotBlank()) {
                Text(
                    "${caseEntity.plaintiffName}  ከ/ከ  ${caseEntity.defendantName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }

            val snippet = remember(
                caseEntity.id,
                caseEntity.updatedAt,
                caseEntity.analysisText,
                caseEntity.issuesText,
            ) {
                JudicialDocumentFormatter.extractPreviewSnippet(caseEntity)
            }
            if (snippet.isNotBlank()) {
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            // Status / Pin / Tag / Adjournment badges row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = VerifiedEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Read-Only", tint = VerifiedEmerald, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Read-Only", style = MaterialTheme.typography.labelSmall, color = VerifiedEmerald)
                    }
                }
                if (isUploadedRef) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "ናሙና / Ref",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                if (isPinned) {
                    Surface(
                        color = JudicialGold.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "ዋና ናሙና", tint = JudicialGold, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("ዋና ናሙና", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!subjectTag.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "#$subjectTag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                if (!adjournmentDate.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("ቀጠሮ: $adjournmentDate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Icon(
                    if (caseEntity.syncStatus == SyncStatus.SYNCED) Icons.Default.CloudDone else Icons.Default.Sync,
                    contentDescription = caseEntity.syncStatus.amharicLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                )
            }

            // Management Icons + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag("archive_pin_button_${caseEntity.id}"),
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (isPinned) "ከዋና ናሙና አንሳ" else "ዋና ናሙና አድርግ",
                            tint = if (isPinned) JudicialGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onOpenTagPicker,
                        modifier = Modifier.testTag("archive_tag_button_${caseEntity.id}"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = "የጉዳይ ዘርፍ ምረጥ",
                            tint = if (!subjectTag.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onOpenRename,
                        modifier = Modifier.testTag("archive_rename_button_${caseEntity.id}"),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "የመ/ቁ ቀይር",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.testTag("archive_delete_button_${caseEntity.id}"),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "መዝገብ ሰርዝ",
                            tint = AlertCrimson.copy(alpha = 0.85f),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("ክፈት", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = onReopen, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("አሻሽል", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = onExportPdf, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
