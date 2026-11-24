package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BadReasonCategory
import com.example.data.local.CaseVersionEntity
import com.example.pdf.GeneratedDocxResult
import com.example.pdf.GeneratedPdfResult
import com.example.pdf.PdfVerificationResult
import com.example.security.AppSecuritySettings
import com.example.sync.DriveBackupFile
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber
import com.example.util.EthiopianCalendarUtil
import com.example.util.EthiopianDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAndKeySheet(
    settings: AppSecuritySettings,
    currentApiKeyMask: String,
    hasNotificationPermission: Boolean,
    onDismiss: () -> Unit,
    onSaveApiKey: (String?) -> Unit,
    onSaveModels: (String, String) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onTogglePiiMasking: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSendTestNotification: () -> Unit,
    onAutoLockChange: (Int) -> Unit,
    onThemeOverrideChange: (Boolean?) -> Unit,
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var ocrModel by remember(settings.ocrModelName) { mutableStateOf(settings.ocrModelName) }
    var draftModel by remember(settings.draftingModelName) { mutableStateOf(settings.draftingModelName) }
    var autoLockSlider by remember(settings.autoLockMinutes) { mutableFloatStateOf(settings.autoLockMinutes.toFloat()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("የAPI ቁልፍ እና AI ሞዴል ማዋቀሪያ", style = MaterialTheme.typography.titleLarge)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (settings.hasCustomApiKey) {
                            "ሁኔታ: የግል Gemini API ቁልፍ በKeystore ተቀምጧል ($currentApiKeyMask)"
                        } else {
                            "ሁኔታ: ነባሪ Firebase AI Logic (App Check) በመጠቀም ላይ — ወይም ከታች የግል API ቁልፍ ያስገቡ"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key (አማራጭ)") },
                        placeholder = { Text("AIza...") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "ቁልፍ አሳይ"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    onSaveApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            modifier = Modifier.testTag("save_api_key_button"),
                        ) {
                            Text("ቁልፍ አስቀምጥ")
                        }
                        if (settings.hasCustomApiKey) {
                            OutlinedButton(
                                onClick = { onSaveApiKey(null) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertCrimson),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("አጥፋ (Firebase ተጠቀም)")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("የAI ሞዴል ስሞች (በሁለት ደረጃ)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = ocrModel,
                onValueChange = { ocrModel = it },
                label = { Text("ደረጃ 1 - ፈጣን OCR ሞዴል") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ocr_model_input"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "gemini-3.5-flash-lite" to "gemini-3.5-flash-lite (ፈጣን)",
                    "gemini-3.8-flash" to "gemini-3.8-flash",
                ).forEach { (preset, label) ->
                    FilterChip(
                        selected = ocrModel == preset,
                        onClick = { ocrModel = preset },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = draftModel,
                onValueChange = { draftModel = it },
                label = { Text("ደረጃ 2 - ረቂቅ እና ምክንያት ሞዴል") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("draft_model_input"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "gemini-3.5-flash-lite" to "gemini-3.5-flash-lite (ፈጣን • ሰፊ ገደብ)",
                    "gemini-3.8-flash" to "gemini-3.8-flash (20/ቀን ነጻ)",
                    "gemini-4-hydrogen" to "gemini-4-hydrogen (Search & ምርምር)",
                    "gemini-4-argon" to "gemini-4-argon",
                ).forEach { (preset, label) ->
                    FilterChip(
                        selected = draftModel == preset,
                        onClick = { draftModel = preset },
                        label = { Text(label) },
                    )
                }
            }

            Button(
                onClick = { onSaveModels(ocrModel, draftModel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("የሞዴል ምርጫ አስቀምጥ")
            }

            HorizontalDivider()
            Text("ደህንነት እና ገጽታ", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("የግል መረጃ መሸፈኛ (PII Masking)", style = MaterialTheme.typography.bodyMedium)
                    Text("ወደ AI ከመላኩ በፊት ስም፣ ስልክ እና መታወቂያ ይሸፍናል", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.piiMaskingEnabled, onCheckedChange = onTogglePiiMasking)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Screenshot መከልከያ (FLAG_SECURE)", style = MaterialTheme.typography.bodyMedium)
                    Text("ስክሪን እንዳይቀረጽ ይከላከላል (ማሳሰቢያ፡ በEmulator Preview ላይ ከበራ ስክሪኑን ያጠቁራል)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.flagSecureEnabled, onCheckedChange = onToggleFlagSecure)
            }

            Column {
                Text("ራስ-ሰር መቆለፊያ ጊዜ: ${autoLockSlider.toInt()} ደቂቃ", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = autoLockSlider,
                    onValueChange = { autoLockSlider = it },
                    onValueChangeFinished = { onAutoLockChange(autoLockSlider.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.darkThemeOverride == null,
                    onClick = { onThemeOverrideChange(null) },
                    label = { Text("ስርዓት") },
                )
                FilterChip(
                    selected = settings.darkThemeOverride == false,
                    onClick = { onThemeOverrideChange(false) },
                    label = { Text("ብርሃን") },
                )
                FilterChip(
                    selected = settings.darkThemeOverride == true,
                    onClick = { onThemeOverrideChange(true) },
                    label = { Text("ጨለማ") },
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("ማሳወቂያዎች (Notifications)", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("የችሎት ቀጠሮ እና የረቂቅ ማሳወቂያዎች", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "የቀጠሮ ቀን ማስታወሻ፣ የወረፋ ረቂቅ ውሳኔ እና የGoogle Drive ምትኬ ማሳወቂያዎችን ይልካል።",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onToggleNotifications,
                    modifier = Modifier.testTag("notifications_switch"),
                )
            }

            if (!hasNotificationPermission) {
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("grant_notification_permission_button"),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("የNotification ፍቃድ ስጥ")
                }
            } else if (settings.notificationsEnabled) {
                OutlinedButton(
                    onClick = onSendTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("test_notification_button"),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("የሙከራ Notification ላክ")
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSyncSheet(
    settings: AppSecuritySettings,
    pendingSyncCount: Int,
    isDriveBusy: Boolean,
    driveError: String?,
    driveBackups: List<DriveBackupFile>,
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onAuthorizeDrive: () -> Unit,
    onCloudDriveSyncNow: () -> Unit,
    onRefreshCloudBackups: () -> Unit,
    onRestoreCloudBackup: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onSelectDriveFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
) {
    val lastSyncLabel = remember(settings.lastSyncTimestamp) {
        if (settings.lastSyncTimestamp == 0L) "ገና አልተመሳሰለም"
        else SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(settings.lastSyncTimestamp))
    }
    val isSignedIn = !settings.googleAccountEmail.isNullOrBlank()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Google Drive መግቢያ እና ምትኬ", style = MaterialTheme.typography.titleLarge)
            }

            // 1. Google Sign-In & Drive OAuth Section
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isSignedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "በGoogle መለያ ይግቡ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "መዝገቦች በAES-256-GCM ተመስጥረው ወደ Google Drive (RedatDagna_Backups) ይቀመጣሉ።",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !isDriveBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("google_sign_in_button"),
                        ) {
                            if (isDriveBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("በመግባት ላይ...")
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google / በGoogle ይግቡ")
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (!settings.googleAccountPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = settings.googleAccountPhotoUrl,
                                    contentDescription = "የዳኛ Google መለያ ፎቶ",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (settings.googleAccountName?.takeIf { it.isNotBlank() }
                                            ?: settings.googleAccountEmail.ifBlank { "ዳ" })
                                            .first()
                                            .uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.googleAccountName ?: "ዳኛ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = settings.googleAccountEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = onGoogleSignOut,
                                enabled = !isDriveBusy,
                                modifier = Modifier.testTag("google_sign_out_button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "ከGoogle መለያ ውጣ",
                                    tint = AlertCrimson,
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (settings.driveApiAuthorized) Icons.Default.VerifiedUser else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (settings.driveApiAuthorized) VerifiedEmerald else WarningAmber,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (settings.driveApiAuthorized) {
                                    "Google Drive ፍቃድ ተገናኝቷል ($pendingSyncCount ያልተመሳሰሉ)"
                                } else {
                                    "Google Drive ፍቃድ ገና አልተሰጠም"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.driveApiAuthorized) VerifiedEmerald else WarningAmber,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Text("የመጨረሻ ምትኬ: $lastSyncLabel", style = MaterialTheme.typography.bodySmall)

                        if (!settings.driveApiAuthorized) {
                            Button(
                                onClick = onAuthorizeDrive,
                                enabled = !isDriveBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("authorize_google_drive_button"),
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Google Drive ፍቃድ ስጥ (Grant Access)")
                            }
                        } else {
                            Button(
                                onClick = onCloudDriveSyncNow,
                                enabled = !isDriveBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("cloud_drive_sync_now_button"),
                            ) {
                                if (isDriveBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("ወደ Google Drive በመላክ ላይ...")
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("አሁን ወደ Google Drive አመሳስል")
                                }
                            }
                        }
                    }

                    if (!driveError.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AlertCrimson.copy(alpha = 0.12f)),
                        ) {
                            Text(
                                text = driveError,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // 2. Recent Cloud Backups on Google Drive
            if (isSignedIn && settings.driveApiAuthorized) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "በGoogle Drive ያሉ ምትኬዎች",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = onRefreshCloudBackups,
                        enabled = !isDriveBusy,
                        modifier = Modifier.testTag("refresh_drive_backups_button"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "ዝርዝሩን አድስ")
                    }
                }

                if (driveBackups.isEmpty()) {
                    Text(
                        "ገና የተላከ የDrive ምትኬ አልተገኘም። 'አሁን ወደ Google Drive አመሳስል' ይጫኑ።",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        driveBackups.take(5).forEach { file ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${(file.sizeBytes / 1024).coerceAtLeast(1)} KB • ${file.modifiedTime.take(10)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onRestoreCloudBackup(file.id) },
                                        enabled = !isDriveBusy,
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("መልስ")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 3. Offline / SAF Folder & Local Backup Options
            Text("የአካባቢ / SAF ማህደር ምትኬ (አማራጭ)", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = onSelectDriveFolder,
                modifier = Modifier.fillMaxWidth().testTag("connect_drive_folder_button"),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (settings.driveTreeUri == null) "የመሳሪያ / SAF ማህደር ምረጥ" else "SAF ማህደር ቀይር")
            }

            OutlinedButton(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().testTag("sync_now_button"),
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("በጀርባ አመሳስል (Delta Sync)")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExportEncryptedBackup, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ምትኬ አውጣ")
                }
                OutlinedButton(onClick = onRestoreEncryptedBackup, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ምትኬ መልስ")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSecuritySheet(
    settings: AppSecuritySettings,
    isArchivesUnlocked: Boolean,
    onDismiss: () -> Unit,
    onToggleArchiveLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onSetPin: (String) -> Unit,
    onLockNow: () -> Unit,
    onVerifyPdfClick: () -> Unit,
) {
    var newPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isArchivesUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text("የArchives ቁልፍ እና የPDF ማረጋገጫ", style = MaterialTheme.typography.titleLarge)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Archives በቁልፍ ይጠበቅ", style = MaterialTheme.typography.bodyMedium)
                    Text("SQLCipher 256-bit ምስጠራ + ባዮሜትሪክ/PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.archiveLockEnabled, onCheckedChange = onToggleArchiveLock)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("የጣት አሻራ / ባዮሜትሪክ ፍቀድ", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = settings.biometricEnabled, onCheckedChange = onToggleBiometric)
            }

            OutlinedTextField(
                value = newPin,
                onValueChange = {
                    if (it.length <= 8 && it.all(Char::isDigit)) {
                        newPin = it
                        pinError = null
                    }
                },
                label = { Text(if (settings.hasPinSet) "አዲስ PIN ቀይር (4-8 አሃዝ)" else "PIN አዘጋጅ (4-8 አሃዝ)") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                isError = pinError != null,
                supportingText = pinError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("set_pin_input"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (newPin.length < 4) {
                            pinError = "PIN ቢያንስ 4 አሃዝ መሆን አለበት።"
                        } else {
                            onSetPin(newPin)
                            newPin = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("PIN አስቀምጥ")
                }
                if (isArchivesUnlocked && settings.archiveLockEnabled) {
                    OutlinedButton(onClick = onLockNow) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("አሁን ቆልፍ")
                    }
                }
            }

            HorizontalDivider()
            Button(
                onClick = {
                    onDismiss()
                    onVerifyPdfClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().testTag("verify_pdf_button"),
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("PDFውን አረጋግጥ (Tamper Check)")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun EthiopianDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val parsed = remember(initialDate) {
        EthiopianCalendarUtil.parseEthiopianDate(initialDate) ?: EthiopianCalendarUtil.todayEthiopian()
    }
    var day by remember { mutableIntStateOf(parsed.day) }
    var month by remember { mutableIntStateOf(parsed.month) }
    var year by remember { mutableIntStateOf(parsed.year) }

    val maxDays = EthiopianCalendarUtil.daysInEthiopianMonth(year, month)
    if (day > maxDays) day = maxDays

    val formattedEt = EthiopianDate(day, month, year).formatNumeric()
    val formattedGreg = EthiopianCalendarUtil.ethiopianToGregorianString(formattedEt)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("የኢትዮጵያ ቀን መቁጠሪያ", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${EthiopianCalendarUtil.amharicMonthName(month)} $day, $year ዓ.ም",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("ግሪጎሪያን: $formattedGreg", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (day > 1) day-- else day = maxDays }) { Text("-") }
                    Text("ቀን: $day", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (day < maxDays) day++ else day = 1 }) { Text("+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { month = if (month > 1) month - 1 else 13 }) { Text("-") }
                    Text("ወር: ${EthiopianCalendarUtil.amharicMonthName(month)} ($month)", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { month = if (month < 13) month + 1 else 1 }) { Text("+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { year-- }) { Text("-") }
                    Text("ዓመት: $year ዓ.ም", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { year++ }) { Text("+") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(formattedEt, formattedGreg) }) { Text("ምረጥ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ሰርዝ") }
        },
    )
}

@Composable
fun BadRatingDialog(
    onDismiss: () -> Unit,
    onSubmit: (BadReasonCategory, String) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(BadReasonCategory.STYLE) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("የጥራት ማሻሻያ ምክንያት (አማራጭ)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("AI ለወደፊት ይህን ስህተት እንዳይደግም ምክንያቱን ይምረጡ:", style = MaterialTheme.typography.bodySmall)
                listOf(
                    BadReasonCategory.STYLE,
                    BadReasonCategory.LEGAL_ERROR,
                    BadReasonCategory.ISSUES,
                    BadReasonCategory.LANGUAGE,
                ).forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedCategory == cat) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedCategory = cat }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.amharicLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("አጭር ማስታወሻ (አታድርግ)") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedCategory, note) }) { Text("መዝግብ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ዝጋ") }
        },
    )
}

@Composable
fun PdfExportOptionsDialog(
    caseNumber: String,
    isGenerating: Boolean,
    latestResult: GeneratedPdfResult?,
    onDismiss: () -> Unit,
    onGenerate: (Boolean) -> Unit,
    onSharePdf: (GeneratedPdfResult) -> Unit,
    latestDocxResult: GeneratedDocxResult? = null,
    isDocxGenerating: Boolean = false,
    onGenerateDocx: (Boolean) -> Unit = {},
    onShareDocx: (GeneratedDocxResult) -> Unit = {},
    onPrintPdf: (GeneratedPdfResult) -> Unit = {},
) {
    var showSecurityNotice by remember { mutableStateOf(false) }

    if (showSecurityNotice && latestResult != null) {
        AlertDialog(
            onDismissRequest = { showSecurityNotice = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = WarningAmber) },
            title = { Text("የሚስጥራዊነት ማሳሰቢያ") },
            text = {
                Text(
                    "ይህ የፍርድ ቤት PDF ሰነድ የተከራካሪዎችን የግል መረጃ ይዟል። ለተፈቀደለት አካል ብቻ መላኩን ያረጋግጡ። ፋይሉ ከማረምና ከመገልበጥ የተቆለፈና በSHA-256 + QR የተረጋገጠ ነው።",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecurityNotice = false
                        onSharePdf(latestResult)
                    }
                ) { Text("አጋራ (Share)") }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityNotice = false }) { Text("ተመለስ") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("ሰነድ ማውጣት እና ማተም (የመ/ቁ $caseNumber)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "PDF፦ Noto Ethiopic ፎንት፣ QR ኮድ፣ SHA-256 እና መቆለፊያ ይካተታል።",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onGenerate(false) },
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f).testTag("export_clean_pdf_button"),
                    ) { Text("ንጹህ PDF") }
                    OutlinedButton(
                        onClick = { onGenerate(true) },
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f).testTag("export_draft_pdf_button"),
                    ) { Text("ረቂቅ PDF") }
                }

                if (isGenerating) {
                    Text("PDF በNoto Ethiopic እና በQR ኮድ እየተፈጠረ ነው...", style = MaterialTheme.typography.bodySmall)
                }

                if (latestResult != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("PDF ተዘጋጅቷል (${latestResult.pageCount} ገጽ)", fontWeight = FontWeight.Bold, color = VerifiedEmerald)
                            Text("ፋይል: ${latestResult.file.name}", style = MaterialTheme.typography.bodySmall)
                            Text("SHA-256: ${latestResult.fileSha256.take(24)}...", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onPrintPdf(latestResult) },
                                    modifier = Modifier.weight(1f).testTag("print_pdf_button"),
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("አትም", maxLines = 1)
                                }
                                Button(
                                    onClick = { showSecurityNotice = true },
                                    modifier = Modifier.weight(1f).testTag("share_pdf_button"),
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF አጋራ", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    "Word (.docx) — በMicrosoft Word ወይም LibreOffice የሚታረም ቅጂ፦",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onGenerateDocx(false) },
                        enabled = !isDocxGenerating,
                        modifier = Modifier.weight(1f).testTag("export_docx_button"),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Word (.docx)", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onGenerateDocx(true) },
                        enabled = !isDocxGenerating,
                        modifier = Modifier.weight(1f).testTag("export_draft_docx_button"),
                    ) {
                        Text("ረቂቅ Word", maxLines = 1)
                    }
                }

                if (isDocxGenerating) {
                    Text("የWord (.docx) ሰነድ እየተዘጋጀ ነው...", style = MaterialTheme.typography.bodySmall)
                }

                if (latestDocxResult != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Word ተዘጋጅቷል", fontWeight = FontWeight.Bold, color = VerifiedEmerald)
                                Text(
                                    latestDocxResult.file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = { onShareDocx(latestDocxResult) },
                                modifier = Modifier.testTag("share_docx_button"),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("አጋራ")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (latestResult != null && latestDocxResult == null) {
                Button(onClick = { showSecurityNotice = true }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF አጋራ")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ዝጋ") }
        },
    )
}

@Composable
fun PdfVerificationDialog(
    result: PdfVerificationResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (result.isAuthentic) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result.isAuthentic) VerifiedEmerald else AlertCrimson,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(result.statusTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(result.details, style = MaterialTheme.typography.bodyMedium)
                if (!result.matchedCaseNumber.isNullOrBlank()) {
                    Text("የመዝገብ ቁጥር: ${result.matchedCaseNumber}", fontWeight = FontWeight.Bold)
                }
                if (result.fileSha256.isNotBlank()) {
                    Text("የፋይል SHA-256:\n${result.fileSha256}", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("እሺ") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistorySheet(
    versions: List<CaseVersionEntity>,
    onDismiss: () -> Unit,
    onRestore: (CaseVersionEntity) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("የስሪት ታሪክ (Version History)", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            if (versions.isEmpty()) {
                Text("ገና የተቀመጠ ስሪት የለም።", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(versions, key = { it.id }) { ver ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("v${ver.versionNumber} — ${ver.changeSummary}", fontWeight = FontWeight.SemiBold)
                                    Text(fmt.format(Date(ver.createdAt)), style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onRestore(ver) }) { Text("መልስ") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun PinPromptDialog(
    title: String,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onSuccess: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 8 && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                label = { Text("PIN ያስገቡ") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerifyPin(pin)) {
                        onSuccess()
                    } else {
                        error = "የተሳሳተ PIN ነው።"
                    }
                }
            ) { Text("ክፈት") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ሰርዝ") }
        },
    )
}
