package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.data.local.CaseEntity
import com.example.data.local.QualityRating
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.ResearchNoteType
import com.example.pdf.JudicialPrintHelper
import com.example.security.BiometricAuthManager
import com.example.security.GoogleDriveAuthManager
import com.example.security.SecurePrefsManager
import com.example.security.configureAppCheck
import com.example.ui.components.ArchiveSecuritySheet
import com.example.ui.components.AudioTranscribeSheet
import com.example.ui.components.BadRatingDialog
import com.example.ui.components.DriveSyncSheet
import com.example.ui.components.EthiopianDatePickerDialog
import com.example.ui.components.EthiopianLawReferenceSheet
import com.example.ui.components.FullScreenDocumentEditorDialog
import com.example.ui.components.PdfExportOptionsDialog
import com.example.ui.components.PdfVerificationDialog
import com.example.ui.components.PinPromptDialog
import com.example.ui.components.SettingsAndKeySheet
import com.example.ui.components.TranscribeTarget
import com.example.ui.components.VersionHistorySheet
import com.example.util.EthiopianCalendarUtil
import com.example.util.JudicialDocumentFormatter
import com.example.util.PastJudgmentParser
import com.example.ui.screens.ArchivesScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiscussionScreen
import com.example.ui.screens.DraftBuilderScreen
import com.example.ui.theme.RedatDagnaTheme
import com.example.ui.viewmodel.JudicialViewModel
import com.example.ui.viewmodel.MainMenuTab
import com.example.ui.viewmodel.WorkSubTab
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.File

private enum class PendingMicPurpose {
    LIVE_VOICE,
    AUDIO_TRANSCRIBE,
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val prefs = remember { SecurePrefsManager(applicationContext) }
            val globalSettings by prefs.settings.collectAsStateWithLifecycle()

            LaunchedEffect(globalSettings.flagSecureEnabled) {
                if (globalSettings.flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val darkTheme = globalSettings.darkThemeOverride
                ?: androidx.compose.foundation.isSystemInDarkTheme()

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
            }

            RedatDagnaTheme(darkTheme = darkTheme) {
                val authUser by GoogleDriveAuthManager.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
                val user = authUser

                if (user == null) {
                    // Unauthenticated session: Render Google Sign-In UI only.
                    // Feature ViewModels & Firestore listeners are NOT instantiated here.
                    AuthScreen(activityContext = this@MainActivity)
                } else {
                    // Authenticated session: Key composition & ViewModel by uid
                    key(user.uid) {
                        val judicialViewModel: JudicialViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY]) {
                                        "APPLICATION_KEY missing from CreationExtras"
                                    }
                                    val dbId = app.getString(R.string.firestore_database_id)
                                    val firestoreDb = FirebaseFirestore.getInstance(dbId)
                                    JudicialViewModel(
                                        appContext = app.applicationContext,
                                        firestoreRepo = FirestoreSyncRepository(firestoreDb),
                                        currentUserId = user.uid,
                                    )
                                }
                            }
                        )

                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner, judicialViewModel) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    judicialViewModel.checkAutoLockTimeout()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        RedatDagnaApp(
                            activity = this@MainActivity,
                            viewModel = judicialViewModel,
                            signedInEmail = user.email.orEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RedatDagnaApp(
    activity: FragmentActivity,
    viewModel: JudicialViewModel,
    signedInEmail: String,
) {
    val mainTab by viewModel.mainTab.collectAsStateWithLifecycle()
    val workSubTab by viewModel.workSubTab.collectAsStateWithLifecycle()
    val isArchivesUnlocked by viewModel.isArchivesUnlocked.collectAsStateWithLifecycle()
    val activeCase by viewModel.activeCase.collectAsStateWithLifecycle()
    val recentWorkCases by viewModel.allWorkCases.collectAsStateWithLifecycle()
    val plaintiffAttachments by viewModel.plaintiffAttachments.collectAsStateWithLifecycle()
    val defendantAttachments by viewModel.defendantAttachments.collectAsStateWithLifecycle()
    val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()
    val isChatSending by viewModel.isChatSending.collectAsStateWithLifecycle()
    val streamingChatReply by viewModel.streamingChatReply.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val versions by viewModel.activeCaseVersions.collectAsStateWithLifecycle()
    val chatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.archiveSearchQuery.collectAsStateWithLifecycle()
    val ratingFilter by viewModel.archiveRatingFilter.collectAsStateWithLifecycle()
    val typeFilter by viewModel.archiveTypeFilter.collectAsStateWithLifecycle()
    val archivePinnedOnly by viewModel.archivePinnedOnly.collectAsStateWithLifecycle()
    val archiveSubjectFilter by viewModel.archiveSubjectFilter.collectAsStateWithLifecycle()
    val pagedArchives = viewModel.archivedPagedCases.collectAsLazyPagingItems()
    val latestPdfResult by viewModel.latestPdfResult.collectAsStateWithLifecycle()
    val isPdfGenerating by viewModel.isPdfGenerating.collectAsStateWithLifecycle()
    val latestDocxResult by viewModel.latestDocxResult.collectAsStateWithLifecycle()
    val isDocxGenerating by viewModel.isDocxGenerating.collectAsStateWithLifecycle()
    val pdfVerificationResult by viewModel.pdfVerificationResult.collectAsStateWithLifecycle()
    val isDriveBusy by viewModel.isDriveBusy.collectAsStateWithLifecycle()
    val driveError by viewModel.driveError.collectAsStateWithLifecycle()
    val driveBackups by viewModel.driveBackups.collectAsStateWithLifecycle()
    val pendingDriveResolution by viewModel.pendingDriveResolution.collectAsStateWithLifecycle()

    // Live API, Search Grounding, Saved Cloud Notes, and Audio Transcription states
    val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
    val searchGroundingState by viewModel.searchGroundingState.collectAsStateWithLifecycle()
    val savedResearchNotes by viewModel.savedResearchNotes.collectAsStateWithLifecycle()
    val audioTranscribeState by viewModel.audioTranscribeState.collectAsStateWithLifecycle()
    val pendingChatDraftInjection by viewModel.pendingChatDraftInjection.collectAsStateWithLifecycle()
    val archiveImportState by viewModel.archiveImportState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val micRationale = stringResource(R.string.mic_permission_rationale)

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    BackHandler(enabled = mainTab == MainMenuTab.ARCHIVES || workSubTab == WorkSubTab.DISCUSSION) {
        if (mainTab == MainMenuTab.ARCHIVES) {
            viewModel.selectMainTab(MainMenuTab.CASE_AND_DISCUSSION)
        } else if (workSubTab == WorkSubTab.DISCUSSION) {
            viewModel.selectWorkSubTab(WorkSubTab.DRAFT_BUILDER)
        }
    }

    // Modal sheets & dialogs
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDriveSyncSheet by remember { mutableStateOf(false) }
    var showSecuritySheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showBadRatingDialog by remember { mutableStateOf(false) }
    var showVersionHistory by remember { mutableStateOf(false) }
    var pdfDialogCase by remember { mutableStateOf<CaseEntity?>(null) }
    var showPinPromptForUnlock by remember { mutableStateOf(false) }
    var pendingReopenCase by remember { mutableStateOf<CaseEntity?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showFullScreenEditor by remember { mutableStateOf(false) }
    var showLawReferenceSheet by remember { mutableStateOf(false) }
    var showAdjournmentDatePicker by remember { mutableStateOf(false) }

    // Audio Transcription Sheet & Microphone permission tracking
    var showAudioTranscribeSheet by remember { mutableStateOf(false) }
    var transcribeTarget by remember { mutableStateOf(TranscribeTarget.SPECIAL_INSTRUCTIONS) }
    var pendingMicPurpose by remember { mutableStateOf<PendingMicPurpose?>(null) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val purpose = pendingMicPurpose
        pendingMicPurpose = null
        if (granted) {
            when (purpose) {
                PendingMicPurpose.LIVE_VOICE -> viewModel.startLiveVoiceConversation()
                PendingMicPurpose.AUDIO_TRANSCRIBE -> {
                    viewModel.resetAudioTranscribe()
                    showAudioTranscribeSheet = true
                    viewModel.startAudioRecording()
                }
                null -> Unit
            }
        } else {
            uiScope.launch {
                snackbarHostState.showSnackbar(micRationale)
            }
        }
    }

    fun ensureAudioPermissionAndRun(purpose: PendingMicPurpose, onGranted: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted()
        } else {
            pendingMicPurpose = purpose
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Attachment target tracking
    var targetIsPlaintiff by remember { mutableStateOf(true) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Photo Picker (Zero permission per Play Policy)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        viewModel.addAttachments(uris, targetIsPlaintiff)
    }

    // Document Picker (PDF / Word / Images via SAF)
    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addAttachments(uris, targetIsPlaintiff)
    }

    // Archive Past Judgments Multi-File Uploaders (SAF + Photo Picker, zero storage permissions)
    val archiveDocsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importArchiveReferenceFiles(uris)
        }
    }

    val archivePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importArchiveReferenceFiles(uris)
        }
    }

    // Camera capture via FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.addAttachments(listOf(uri), targetIsPlaintiff)
        }
    }

    // Drive SAF folder picker
    val driveTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.connectDriveFolder(uri)
    }

    // Encrypted backup export / import
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.exportEncryptedBackupToUri(uri)
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreEncryptedBackupFromUri(uri)
    }

    // PDF Tamper Verification picker
    val verifyPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.verifyPdfUri(uri)
    }

    // Google Drive OAuth consent / resolution launcher
    val driveAuthResolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onDriveResolutionResult(activity, activityResult.data)
    }

    LaunchedEffect(pendingDriveResolution) {
        val intentSender = pendingDriveResolution ?: return@LaunchedEffect
        viewModel.clearPendingDriveResolution()
        driveAuthResolutionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    fun launchCamera(isPlaintiff: Boolean) {
        targetIsPlaintiff = isPlaintiff
        val dir = File(activity.cacheDir, "captures").apply { mkdirs() }
        val photoFile = File.createTempFile("doc_capture_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun requestUnlockWithBiometricOrPin(onAuthorized: () -> Unit) {
        if (settings.biometricEnabled && BiometricAuthManager.canAuthenticate(activity)) {
            BiometricAuthManager.authenticate(
                activity = activity,
                title = "የዳኛ ማረጋገጫ",
                subtitle = "መዝገቡን ለመክፈት የጣት አሻራ ወይም PIN ይጠቀሙ",
                onSuccess = onAuthorized,
                onFallbackPin = {
                    if (settings.hasPinSet) showPinPromptForUnlock = true
                    else onAuthorized()
                },
                onError = { _ ->
                    if (settings.hasPinSet) showPinPromptForUnlock = true
                },
            )
        } else if (settings.hasPinSet) {
            showPinPromptForUnlock = true
        } else {
            onAuthorized()
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)
    val visibleTopFraction = (1f - scrollBehavior.state.collapsedFraction).coerceIn(0f, 1f)

    LaunchedEffect(mainTab, workSubTab) {
        topBarState.heightOffset = 0f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Column {
                            Text(
                                "ረዳት ዳኛ",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.court_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showLawReferenceSheet = true },
                            modifier = Modifier.testTag("top_law_reference_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = stringResource(R.string.cd_law_reference),
                            )
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("top_key_button"),
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = stringResource(R.string.cd_api_key_settings),
                            )
                        }
                        IconButton(
                            onClick = {
                                showDriveSyncSheet = true
                                viewModel.refreshDriveBackups(activity)
                            },
                            modifier = Modifier.testTag("top_cloud_button"),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (pendingSyncCount > 0) {
                                        Badge { Text(pendingSyncCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    if (settings.driveApiAuthorized && !settings.googleAccountEmail.isNullOrBlank()) {
                                        Icons.Default.CloudDone
                                    } else {
                                        Icons.Default.CloudSync
                                    },
                                    contentDescription = stringResource(R.string.cd_drive_sync),
                                    tint = if (settings.driveApiAuthorized && !settings.googleAccountEmail.isNullOrBlank()) {
                                        com.example.ui.theme.VerifiedEmerald
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = { showSecuritySheet = true },
                            modifier = Modifier.testTag("top_lock_button"),
                        ) {
                            Icon(
                                if (isArchivesUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = stringResource(R.string.cd_archive_lock),
                            )
                        }
                        IconButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.testTag("top_sign_out_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "ከGoogle መለያ ውጣ",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail && !isImeVisible) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = mainTab == MainMenuTab.CASE_AND_DISCUSSION,
                            onClick = { viewModel.selectMainTab(MainMenuTab.CASE_AND_DISCUSSION) },
                            icon = { Icon(Icons.Default.Gavel, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_case_and_discussion), maxLines = 1) },
                            modifier = Modifier.testTag("nav_case_and_discussion"),
                        )
                        NavigationBarItem(
                            selected = mainTab == MainMenuTab.ARCHIVES,
                            onClick = { viewModel.selectMainTab(MainMenuTab.ARCHIVES) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (archivedCount > 0) Badge { Text(archivedCount.toString()) }
                                    }
                                ) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(R.string.tab_archives), maxLines = 1) },
                            modifier = Modifier.testTag("nav_archives"),
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = mainTab == MainMenuTab.CASE_AND_DISCUSSION,
                            onClick = { viewModel.selectMainTab(MainMenuTab.CASE_AND_DISCUSSION) },
                            icon = { Icon(Icons.Default.Gavel, contentDescription = null) },
                            label = { Text(stringResource(R.string.tab_case_and_discussion)) },
                        )
                        NavigationRailItem(
                            selected = mainTab == MainMenuTab.ARCHIVES,
                            onClick = { viewModel.selectMainTab(MainMenuTab.ARCHIVES) },
                            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                            label = { Text("${stringResource(R.string.tab_archives)} ($archivedCount)") },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (mainTab) {
                        MainMenuTab.CASE_AND_DISCUSSION -> {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .clipToBounds()
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val currentHeight = (placeable.height * visibleTopFraction).roundToInt()
                                        layout(placeable.width, currentHeight) {
                                            placeable.placeRelative(0, currentHeight - placeable.height)
                                        }
                                    }
                            ) {
                                TabRow(selectedTabIndex = workSubTab.ordinal) {
                                    WorkSubTab.entries.forEach { subTab ->
                                        val subTabLabel = when (subTab) {
                                            WorkSubTab.DRAFT_BUILDER -> stringResource(R.string.subtab_draft_builder)
                                            WorkSubTab.DISCUSSION -> stringResource(R.string.subtab_discussion)
                                        }
                                        Tab(
                                            selected = workSubTab == subTab,
                                            onClick = { viewModel.selectWorkSubTab(subTab) },
                                            text = { Text(subTabLabel, maxLines = 1) },
                                            modifier = Modifier.testTag("subtab_${subTab.name.lowercase()}"),
                                        )
                                    }
                                }
                            }

                            when (workSubTab) {
                                WorkSubTab.DRAFT_BUILDER -> {
                                    DraftBuilderScreen(
                                        activeCase = activeCase,
                                        recentWorkCases = recentWorkCases,
                                        plaintiffAttachments = plaintiffAttachments,
                                        defendantAttachments = defendantAttachments,
                                        pipelineState = pipelineState,
                                        onStartNewCase = viewModel::startNewCase,
                                        onSelectRecentCase = viewModel::openExistingCase,
                                        onCaseTypeChange = viewModel::updateCaseType,
                                        onMetadataChange = viewModel::updateActiveCaseMetadata,
                                        onOpenDatePicker = { showDatePicker = true },
                                        onPickPhotos = { isP ->
                                            targetIsPlaintiff = isP
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onPickDocuments = { isP ->
                                            targetIsPlaintiff = isP
                                            docPickerLauncher.launch(
                                                arrayOf(
                                                    "application/pdf",
                                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                    "image/*"
                                                )
                                            )
                                        },
                                        onCaptureCamera = ::launchCamera,
                                        onRemoveAttachment = viewModel::removeAttachment,
                                        onLaunchVoiceInstructions = {
                                            transcribeTarget = TranscribeTarget.SPECIAL_INSTRUCTIONS
                                            ensureAudioPermissionAndRun(PendingMicPurpose.AUDIO_TRANSCRIBE) {
                                                viewModel.resetAudioTranscribe()
                                                showAudioTranscribeSheet = true
                                                viewModel.startAudioRecording()
                                            }
                                        },
                                        onStartOcr = viewModel::startStage1Ocr,
                                        onUpdateOcrTexts = viewModel::updateOcrReviewTexts,
                                        onConfirmOcrAndDraft = viewModel::confirmOcrAndGenerateDraft,
                                        onCancelPipeline = viewModel::cancelAiJob,
                                        onUpdateUnifiedDocument = viewModel::updateUnifiedDocumentManually,
                                        onSaveManualVersion = viewModel::saveManualVersionSnapshot,
                                        onOpenVersionHistory = { showVersionHistory = true },
                                        onRateGoodOrExcellent = { rating -> viewModel.rateDraft(rating) },
                                        onOpenBadRatingDialog = { showBadRatingDialog = true },
                                        onFinalizeToArchive = viewModel::finalizeToArchive,
                                        onOpenPdfExport = {
                                            viewModel.dismissLatestPdfResult()
                                            viewModel.dismissLatestDocxResult()
                                            pdfDialogCase = activeCase
                                        },
                                        onOpenSettings = { showSettingsSheet = true },
                                        activeSubjectTag = PastJudgmentParser.resolveMapValue(settings.subjectTags, activeCase),
                                        activeAdjournmentDate = PastJudgmentParser.resolveMapValue(settings.adjournmentDates, activeCase),
                                        onAutoFillFromOcr = { viewModel.autoFillMetadataFromOcr(overwriteExisting = true) },
                                        onApplyPreset = viewModel::applyJudicialPreset,
                                        onSelectSubjectTag = { tag -> viewModel.setCaseSubjectTag(activeCase, tag) },
                                        onOpenAdjournmentPicker = { showAdjournmentDatePicker = true },
                                        onQuickAdjournmentDays = { days ->
                                            val (etNext, _) = EthiopianCalendarUtil.addDaysToEthiopian(activeCase.ethiopianDate, days)
                                            viewModel.setCaseAdjournmentDate(activeCase, etNext)
                                        },
                                        onClearAdjournment = { viewModel.setCaseAdjournmentDate(activeCase, null) },
                                        onOpenFullScreenEditor = { showFullScreenEditor = true },
                                        onOpenLawReference = { showLawReferenceSheet = true },
                                        onExpandTopChrome = { topBarState.heightOffset = 0f },
                                    )
                                }
                                WorkSubTab.DISCUSSION -> {
                                    DiscussionScreen(
                                        activeCase = activeCase,
                                        messages = chatMessages,
                                        isSending = isChatSending,
                                        streamingReply = streamingChatReply,
                                        chatError = chatError,
                                        injectedChatDraft = pendingChatDraftInjection,
                                        liveVoiceState = liveVoiceState,
                                        searchGroundingState = searchGroundingState,
                                        savedResearchNotes = savedResearchNotes,
                                        onConsumeInjectedChatDraft = viewModel::consumeInjectedChatDraft,
                                        onSendRevision = viewModel::sendChatRevision,
                                        onAcceptProposal = viewModel::acceptChatProposal,
                                        onDiscardProposal = viewModel::discardChatProposal,
                                        onRetryLastChat = viewModel::retryLastChatMessage,
                                        onDismissChatError = viewModel::dismissChatError,
                                        onSwitchToFastModel = viewModel::switchToFastLiteModel,
                                        onOpenSettings = { showSettingsSheet = true },
                                        onLaunchChatAudioTranscribe = {
                                            transcribeTarget = TranscribeTarget.DISCUSSION_INPUT
                                            ensureAudioPermissionAndRun(PendingMicPurpose.AUDIO_TRANSCRIBE) {
                                                viewModel.resetAudioTranscribe()
                                                showAudioTranscribeSheet = true
                                                viewModel.startAudioRecording()
                                            }
                                        },
                                        onStartLiveVoice = {
                                            ensureAudioPermissionAndRun(PendingMicPurpose.LIVE_VOICE) {
                                                viewModel.startLiveVoiceConversation()
                                            }
                                        },
                                        onStopLiveVoice = viewModel::stopLiveVoiceConversation,
                                        onSendCaseContextToLive = viewModel::sendActiveCaseContextToLive,
                                        onSaveLiveTranscriptNote = { combined ->
                                            viewModel.saveVoiceOrTranscriptNoteToCloud(
                                                noteType = ResearchNoteType.LIVE_VOICE,
                                                title = "ቀጥታ የድምጽ ውይይት (${activeCase.caseNumber.ifBlank { "አጠቃላይ" }})",
                                                content = combined,
                                            )
                                        },
                                        onRunGroundedSearch = viewModel::runGroundedLegalSearch,
                                        onSaveSearchNote = viewModel::saveGroundedSearchToCloud,
                                        onDeleteSavedNote = viewModel::deleteCloudResearchNote,
                                        headerVisibleFraction = visibleTopFraction,
                                        onExpandTopChrome = { topBarState.heightOffset = 0f },
                                    )
                                }
                            }
                        }

                        MainMenuTab.ARCHIVES -> {
                            ArchivesScreen(
                                isUnlocked = isArchivesUnlocked,
                                hasPinSet = settings.hasPinSet,
                                biometricEnabled = settings.biometricEnabled,
                                searchQuery = searchQuery,
                                ratingFilter = ratingFilter,
                                typeFilter = typeFilter,
                                pagedCases = pagedArchives,
                                importState = archiveImportState,
                                onUnlockBiometric = {
                                    requestUnlockWithBiometricOrPin { viewModel.unlockArchives() }
                                },
                                onUnlockWithPin = { showPinPromptForUnlock = true },
                                onUnlockWithoutCredentials = { viewModel.unlockArchives() },
                                onSearchChange = viewModel::setArchiveSearch,
                                onRatingFilterChange = viewModel::setArchiveRatingFilter,
                                onTypeFilterChange = viewModel::setArchiveTypeFilter,
                                onOpenCase = viewModel::openExistingCase,
                                onExportPdf = { c ->
                                    viewModel.dismissLatestPdfResult()
                                    viewModel.dismissLatestDocxResult()
                                    pdfDialogCase = c
                                },
                                onReopenForRevision = { c ->
                                    pendingReopenCase = c
                                    requestUnlockWithBiometricOrPin {
                                        viewModel.unlockFinalizedCaseForRevision(c)
                                        pendingReopenCase = null
                                    }
                                },
                                onVerifyPdfClick = {
                                    verifyPdfLauncher.launch(arrayOf("application/pdf"))
                                },
                                onUploadReferenceFiles = {
                                    archiveDocsLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/plain",
                                            "image/*"
                                        )
                                    )
                                },
                                onUploadReferencePhotos = {
                                    archivePhotosLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onCancelImport = viewModel::cancelArchiveImport,
                                onDismissImportStatus = viewModel::dismissArchiveImportStatus,
                                settingsPinnedKeys = settings.pinnedStyleKeys,
                                settingsSubjectTags = settings.subjectTags,
                                settingsAdjournmentDates = settings.adjournmentDates,
                                pinnedOnlyFilter = archivePinnedOnly,
                                subjectFilter = archiveSubjectFilter,
                                onPinnedOnlyFilterChange = viewModel::setArchivePinnedOnly,
                                onSubjectFilterChange = viewModel::setArchiveSubjectFilter,
                                onTogglePin = viewModel::togglePinStyleCase,
                                onSetSubjectTag = { c, tag -> viewModel.setCaseSubjectTag(c, tag) },
                                onRenameCase = viewModel::renameArchivedCase,
                                onDeleteCase = viewModel::deleteArchivedCase,
                                onExpandTopChrome = { topBarState.heightOffset = 0f },
                            )
                        }
                    }
                }
            }
        }
    }

    // Audio Transcription Sheet (gemini-3.5-transcribe)
    if (showAudioTranscribeSheet) {
        AudioTranscribeSheet(
            state = audioTranscribeState,
            target = transcribeTarget,
            onDismiss = {
                viewModel.resetAudioTranscribe()
                showAudioTranscribeSheet = false
            },
            onStartRecording = {
                ensureAudioPermissionAndRun(PendingMicPurpose.AUDIO_TRANSCRIBE) {
                    viewModel.startAudioRecording()
                }
            },
            onStopAndTranscribe = viewModel::stopAudioAndTranscribe,
            onUpdateTranscript = viewModel::updateTranscribedDraft,
            onApplyTranscript = { text ->
                when (transcribeTarget) {
                    TranscribeTarget.SPECIAL_INSTRUCTIONS -> {
                        viewModel.updateActiveCaseMetadata {
                            val combined = if (it.specialInstructions.isBlank()) text else "${it.specialInstructions}\n$text"
                            it.copy(specialInstructions = combined)
                        }
                    }
                    TranscribeTarget.DISCUSSION_INPUT -> {
                        viewModel.injectSearchPromptIntoChat(text)
                    }
                }
                viewModel.resetAudioTranscribe()
                showAudioTranscribeSheet = false
            },
            onSaveToCloudNotes = { text ->
                viewModel.saveVoiceOrTranscriptNoteToCloud(
                    noteType = ResearchNoteType.TRANSCRIPT,
                    title = "የድምጽ ቅጂ ጽሁፍ (${activeCase.caseNumber.ifBlank { "ማስታወሻ" }})",
                    content = text,
                )
            },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("ከGoogle መለያ መውጣት") },
            text = {
                Text(
                    if (signedInEmail.isNotBlank()) "በ $signedInEmail ገብተዋል። ከመለያው መውጣት ይፈልጋሉ?"
                    else "ከGoogle መለያዎ መውጣት ይፈልጋሉ?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOutGoogle(activity)
                    }
                ) {
                    Text("ውጣ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("ተመለስ")
                }
            },
        )
    }

    // Sheets & Dialogs
    if (showSettingsSheet) {
        val rawKey = viewModel.prefs.getCustomApiKey()
        val masked = if (rawKey.isNullOrBlank()) "" else "••••${rawKey.takeLast(4)}"
        SettingsAndKeySheet(
            settings = settings,
            currentApiKeyMask = masked,
            onDismiss = { showSettingsSheet = false },
            onSaveApiKey = { viewModel.prefs.setCustomApiKey(it) },
            onSaveModels = { ocr, draft -> viewModel.prefs.setModels(ocr, draft) },
            onToggleFlagSecure = { viewModel.prefs.setFlagSecure(it) },
            onTogglePiiMasking = { viewModel.prefs.setPiiMasking(it) },
            onAutoLockChange = { viewModel.prefs.setAutoLockMinutes(it) },
            onThemeOverrideChange = { viewModel.prefs.setDarkThemeOverride(it) },
        )
    }

    if (showDriveSyncSheet) {
        DriveSyncSheet(
            settings = settings,
            pendingSyncCount = pendingSyncCount,
            isDriveBusy = isDriveBusy,
            driveError = driveError,
            driveBackups = driveBackups,
            onDismiss = { showDriveSyncSheet = false },
            onGoogleSignIn = { viewModel.signInWithGoogleForDrive(activity) },
            onAuthorizeDrive = { viewModel.authorizeGoogleDrive(activity) },
            onCloudDriveSyncNow = { viewModel.syncToGoogleDriveNow(activity) },
            onRefreshCloudBackups = { viewModel.refreshDriveBackups(activity) },
            onRestoreCloudBackup = { fileId -> viewModel.restoreFromGoogleDriveFile(activity, fileId) },
            onGoogleSignOut = {
                showDriveSyncSheet = false
                viewModel.signOutGoogle(activity)
            },
            onSelectDriveFolder = { driveTreeLauncher.launch(null) },
            onSyncNow = viewModel::triggerDriveSyncNow,
            onExportEncryptedBackup = {
                exportBackupLauncher.launch("redat_dagna_backup_${System.currentTimeMillis()}.rdenc")
            },
            onRestoreEncryptedBackup = {
                restoreBackupLauncher.launch(arrayOf("*/*"))
            },
        )
    }

    if (showSecuritySheet) {
        ArchiveSecuritySheet(
            settings = settings,
            isArchivesUnlocked = isArchivesUnlocked,
            onDismiss = { showSecuritySheet = false },
            onToggleArchiveLock = {
                viewModel.prefs.setArchiveLock(it)
                if (!it) viewModel.unlockArchives()
            },
            onToggleBiometric = { viewModel.prefs.setBiometricEnabled(it) },
            onSetPin = { viewModel.prefs.setPin(it) },
            onLockNow = {
                viewModel.lockArchivesNow()
                showSecuritySheet = false
            },
            onVerifyPdfClick = { verifyPdfLauncher.launch(arrayOf("application/pdf")) },
        )
    }

    if (showDatePicker) {
        EthiopianDatePickerDialog(
            initialDate = activeCase.ethiopianDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { et, greg ->
                viewModel.updateActiveCaseMetadata { it.copy(ethiopianDate = et, gregorianDate = greg) }
                showDatePicker = false
            },
        )
    }

    if (showBadRatingDialog) {
        BadRatingDialog(
            onDismiss = { showBadRatingDialog = false },
            onSubmit = { cat, note ->
                viewModel.rateDraft(QualityRating.BAD, cat, note)
                showBadRatingDialog = false
            },
        )
    }

    if (showVersionHistory) {
        VersionHistorySheet(
            versions = versions,
            onDismiss = { showVersionHistory = false },
            onRestore = { ver ->
                viewModel.restoreVersion(ver)
                showVersionHistory = false
            },
        )
    }

    pdfDialogCase?.let { targetCase ->
        PdfExportOptionsDialog(
            caseNumber = targetCase.caseNumber,
            isGenerating = isPdfGenerating,
            latestResult = latestPdfResult,
            onDismiss = {
                pdfDialogCase = null
                viewModel.dismissLatestPdfResult()
                viewModel.dismissLatestDocxResult()
            },
            onGenerate = { isDraft -> viewModel.generatePdf(targetCase, isDraft) },
            onSharePdf = { res ->
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", res.file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "የፍርድ ቤት PDF አጋራ"))
            },
            latestDocxResult = latestDocxResult,
            isDocxGenerating = isDocxGenerating,
            onGenerateDocx = { isDraft -> viewModel.generateDocx(targetCase, isDraft) },
            onShareDocx = { res ->
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", res.file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "የፍርድ ቤት Word (.docx) አጋራ"))
            },
            onPrintPdf = { res ->
                JudicialPrintHelper.printPdfFile(
                    context = activity,
                    pdfFile = res.file,
                    jobTitle = "RedatDagna_${targetCase.caseNumber.ifBlank { "Case" }}",
                )
            },
        )
    }

    if (showFullScreenEditor) {
        FullScreenDocumentEditorDialog(
            caseNumber = activeCase.caseNumber,
            initialText = JudicialDocumentFormatter.composeUnifiedDocument(activeCase),
            onDismiss = { showFullScreenEditor = false },
            onSaveAndClose = { updatedText ->
                viewModel.updateUnifiedDocumentManually(updatedText)
                showFullScreenEditor = false
            },
        )
    }

    if (showLawReferenceSheet) {
        EthiopianLawReferenceSheet(
            onDismiss = { showLawReferenceSheet = false },
            onInsertCitation = { citation ->
                if (mainTab == MainMenuTab.CASE_AND_DISCUSSION && workSubTab == WorkSubTab.DISCUSSION) {
                    viewModel.injectSearchPromptIntoChat("በ$citation መሰረት ረቂቁን አጠናክር")
                } else {
                    viewModel.updateActiveCaseMetadata { cur ->
                        val next = if (cur.specialInstructions.isBlank()) citation else "${cur.specialInstructions}\n$citation"
                        cur.copy(specialInstructions = next)
                    }
                }
                showLawReferenceSheet = false
            },
        )
    }

    if (showAdjournmentDatePicker) {
        val currentAdj = PastJudgmentParser.resolveMapValue(settings.adjournmentDates, activeCase)
        EthiopianDatePickerDialog(
            initialDate = currentAdj ?: activeCase.ethiopianDate,
            onDismiss = { showAdjournmentDatePicker = false },
            onConfirm = { et, _ ->
                viewModel.setCaseAdjournmentDate(activeCase, et)
                showAdjournmentDatePicker = false
            },
        )
    }

    pdfVerificationResult?.let { vResult ->
        PdfVerificationDialog(
            result = vResult,
            onDismiss = viewModel::dismissVerificationResult,
        )
    }

    if (showPinPromptForUnlock) {
        PinPromptDialog(
            title = if (pendingReopenCase != null) "የተቆለፈ መዝገብን ለማሻሻል PIN ያስገቡ" else "Archives ለመክፈት PIN ያስገቡ",
            onDismiss = {
                showPinPromptForUnlock = false
                pendingReopenCase = null
            },
            onVerifyPin = { viewModel.prefs.verifyPin(it) },
            onSuccess = {
                showPinPromptForUnlock = false
                val reopen = pendingReopenCase
                if (reopen != null) {
                    viewModel.unlockFinalizedCaseForRevision(reopen)
                    pendingReopenCase = null
                } else {
                    viewModel.unlockArchives()
                }
            },
        )
    }
}
