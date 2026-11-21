package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class DocumentSectionAnchor(
    val label: String,
    val index: Int,
)

private val AMHARIC_LEGAL_TOKENS = listOf(
    "።", "፣", "፤", "፦", ":-", "‹‹", "››", "ዓ.ም", "ፍ/ብ/ሕ/ቁ", "ፍ/ብ/ሥ/ሥ/ሕ/ቁ",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDocumentEditorDialog(
    caseNumber: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSaveAndClose: (String) -> Unit,
) {
    var value by remember(initialText) {
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
    }
    val undoStack = remember { mutableStateListOf<String>() }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    val editorFocusRequester = remember { FocusRequester() }

    fun pushUndoAndUpdate(next: TextFieldValue) {
        if (next.text != value.text) {
            if (undoStack.size > 25) undoStack.removeAt(0)
            undoStack.add(value.text)
        }
        value = next
    }

    fun insertAtCursor(snippet: String) {
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(0, text.length)
        val updated = text.substring(0, start) + snippet + text.substring(end)
        val nextCursor = start + snippet.length
        pushUndoAndUpdate(TextFieldValue(text = updated, selection = TextRange(nextCursor)))
        editorFocusRequester.requestFocus()
    }

    val wordCount = remember(value.text) {
        value.text.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
    }
    val charCount = value.text.length

    val anchors = remember(value.text) {
        val candidates = listOf(
            "ራስጌ" to "የመ/ቁ",
            "መግቢያ" to "መዝገቡ የተቀጠረው",
            "ፍ ር ድ" to "ፍ ር ድ",
            "ብ ይ ን" to "ብ ይ ን",
            "ጭብጥ" to "ጭብጥ",
            "ው ሳ ኔ" to "ው ሳ ኔ",
            "ት ዕ ዛ ዝ" to "ት ዕ ዛ ዝ",
        )
        candidates.mapNotNull { (label, marker) ->
            val idx = value.text.indexOf(marker)
            if (idx >= 0) DocumentSectionAnchor(label, idx) else null
        }
    }

    val matchCount = remember(value.text, findQuery) {
        if (findQuery.isBlank()) 0
        else Regex(Regex.escape(findQuery)).findAll(value.text).count()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("fullscreen_editor_close")) {
                            Icon(Icons.Default.Close, contentDescription = "ዝጋ")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                "ሙሉ ስክሪን ማረሚያ (መ/ቁ ${caseNumber.ifBlank { "-" }})",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "ቃላት: $wordCount • ፊደላት: $charCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    val prev = undoStack.removeAt(undoStack.lastIndex)
                                    value = TextFieldValue(prev, TextRange(prev.length))
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.testTag("fullscreen_editor_undo"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "ወደ ኋላ መልስ")
                        }
                        IconButton(
                            onClick = { showFindReplace = !showFindReplace },
                            modifier = Modifier.testTag("fullscreen_editor_find_toggle"),
                        ) {
                            Icon(
                                Icons.Default.FindReplace,
                                contentDescription = "ፈልግና ቀይር",
                                tint = if (showFindReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Button(
                            onClick = { onSaveAndClose(value.text) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("fullscreen_editor_save"),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("አስቀምጥ")
                        }
                    },
                )

                AnimatedVisibility(visible = showFindReplace) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = findQuery,
                                    onValueChange = { findQuery = it },
                                    label = { Text("ፈልግ (${matchCount})") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.weight(1f).testTag("find_query_input"),
                                )
                                OutlinedTextField(
                                    value = replaceWith,
                                    onValueChange = { replaceWith = it },
                                    label = { Text("በዚህ ቀይር") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.weight(1f).testTag("replace_with_input"),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (findQuery.isNotBlank()) {
                                            val from = value.selection.max.coerceIn(0, value.text.length)
                                            val idx = value.text.indexOf(findQuery, startIndex = from)
                                                .let { if (it >= 0) it else value.text.indexOf(findQuery) }
                                            if (idx >= 0) {
                                                value = value.copy(selection = TextRange(idx, idx + findQuery.length))
                                                editorFocusRequester.requestFocus()
                                            }
                                        }
                                    },
                                    enabled = matchCount > 0,
                                ) { Text("ቀጣዩን አሳይ") }
                                OutlinedButton(
                                    onClick = {
                                        if (findQuery.isNotBlank()) {
                                            val idx = value.text.indexOf(findQuery)
                                            if (idx >= 0) {
                                                val replaced = value.text.replaceFirst(findQuery, replaceWith)
                                                pushUndoAndUpdate(
                                                    TextFieldValue(replaced, TextRange(idx + replaceWith.length))
                                                )
                                            }
                                        }
                                    },
                                    enabled = matchCount > 0,
                                ) { Text("አንዱን ቀይር") }
                                Button(
                                    onClick = {
                                        if (findQuery.isNotBlank() && matchCount > 0) {
                                            val replaced = value.text.replace(findQuery, replaceWith)
                                            pushUndoAndUpdate(
                                                TextFieldValue(replaced, TextRange(replaced.length))
                                            )
                                        }
                                    },
                                    enabled = matchCount > 0,
                                    modifier = Modifier.testTag("replace_all_button"),
                                ) { Text("ሁሉንም ቀይር") }
                            }
                        }
                    }
                }

                // Section Outline Quick Jump
                if (anchors.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(anchors, key = { it.label }) { anchor ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    value = value.copy(selection = TextRange(anchor.index))
                                    editorFocusRequester.requestFocus()
                                },
                                label = { Text("↳ ${anchor.label}", style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                }

                // Ethiopian Legal Punctuation Quick-Insert Bar
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(AMHARIC_LEGAL_TOKENS) { token ->
                        AssistChip(
                            onClick = { insertAtCursor(token) },
                            label = { Text(token, fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = value,
                    onValueChange = { pushUndoAndUpdate(it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .focusRequester(editorFocusRequester)
                        .testTag("fullscreen_editor_textfield"),
                )
            }
        }
    }
}
