package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.example.ai.ChatStreamEvent
import com.example.ai.FirebaseAiFeatures
import com.example.ai.GeminiJudicialService
import com.example.ai.GroundedSearchResult
import com.example.ai.ParsedDraftSections
import com.example.ai.StreamEvent
import com.example.data.local.AppDatabase
import com.example.data.local.BadReasonCategory
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.example.data.local.CaseType
import com.example.data.local.CaseVersionEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRole
import com.example.data.local.DraftSection
import com.example.data.local.QualityRating
import com.example.data.local.SyncStatus
import com.example.data.remote.FirestoreCloudCase
import com.example.data.remote.FirestoreResearchNote
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.ResearchNoteType
import com.example.data.repository.CaseRepository
import com.example.pdf.DocxExporter
import com.example.pdf.GeneratedDocxResult
import com.example.pdf.GeneratedPdfResult
import com.example.pdf.JudicialPdfGenerator
import com.example.pdf.PdfVerificationResult
import com.example.security.AppSecuritySettings
import com.example.security.DriveAuthOutcome
import com.example.security.GoogleDriveAuthManager
import com.example.security.GoogleSignInOutcome
import com.example.security.SecurePrefsManager
import com.example.sync.DriveBackupFile
import com.example.sync.GoogleDriveApiClient
import com.example.ui.components.AudioTranscribeUiState
import com.example.ui.components.LiveTranscriptEntry
import com.example.ui.components.LiveVoiceUiState
import com.example.ui.components.SearchGroundingUiState
import com.example.util.AudioWavRecorder
import com.example.util.CryptoUtil
import com.example.util.DocumentAttachment
import com.example.util.EthiopianCalendarUtil
import com.example.util.ImageProcessor
import com.example.util.JudicialDocumentFormatter
import com.example.util.JudicialPreset
import com.example.util.NetworkUtils
import com.example.util.NotificationHelper
import com.example.util.PastJudgmentParser
import com.example.work.AdjournmentReminderWorker
import com.example.work.DriveSyncWorker
import com.example.work.OfflineDraftWorker
import com.google.firebase.Firebase
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class MainMenuTab(val label: String) {
    CASE_AND_DISCUSSION("መዝገብ እና ውይይት"),
    ARCHIVES("Archives"),
}

enum class WorkSubTab(val label: String) {
    DRAFT_BUILDER("የውሳኔ ረቂቅ ማዘጋጃ"),
    DISCUSSION("ውይይትና ማጠቃለያ"),
}

enum class AttachmentTarget {
    PLAINTIFF,
    DEFENDANT,
    JUDGE_INSTRUCTIONS,
}

sealed interface PipelineState {
    data object Idle : PipelineState
    data class ProcessingImages(val message: String) : PipelineState
    data class ReadingOcr(val message: String) : PipelineState
    data class AwaitingOcrReview(
        val plaintiffText: String,
        val defendantText: String,
        val statsSummary: String,
    ) : PipelineState

    data class StreamingDraft(
        val stageLabel: String,
        val rawText: String,
        val parsed: ParsedDraftSections,
    ) : PipelineState

    data class Error(val message: String) : PipelineState
}

data class ArchiveImportState(
    val isImporting: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val currentFileName: String = "",
    val lastSummary: String? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, PublicPreviewAPI::class)
class JudicialViewModel(
    private val appContext: Context,
    private val firestoreRepo: FirestoreSyncRepository,
    private val currentUserId: String,
) : ViewModel() {
    val prefs = SecurePrefsManager.getInstance(appContext)
    private val db = AppDatabase.getInstance(appContext, prefs)
    private val repository = CaseRepository(db.caseDao())
    private val aiService = GeminiJudicialService(prefs, repository, appContext)
    private val pdfGenerator = JudicialPdfGenerator(appContext, prefs, repository)
    private val wavRecorder = AudioWavRecorder()

    val securitySettings: StateFlow<AppSecuritySettings> = prefs.settings

    private val _mainTab = MutableStateFlow(MainMenuTab.CASE_AND_DISCUSSION)
    val mainTab: StateFlow<MainMenuTab> = _mainTab.asStateFlow()

    private val _workSubTab = MutableStateFlow(WorkSubTab.DRAFT_BUILDER)
    val workSubTab: StateFlow<WorkSubTab> = _workSubTab.asStateFlow()

    private val _isArchivesUnlocked = MutableStateFlow(!prefs.settings.value.archiveLockEnabled)
    val isArchivesUnlocked: StateFlow<Boolean> = _isArchivesUnlocked.asStateFlow()

    private val _lastActiveTimestamp = MutableStateFlow(System.currentTimeMillis())

    @Volatile
    private var isAppInForeground: Boolean = true

    private val _activeCase = MutableStateFlow(createFreshCase())
    val activeCase: StateFlow<CaseEntity> = _activeCase.asStateFlow()

    private val _plaintiffAttachments = MutableStateFlow<List<DocumentAttachment>>(emptyList())
    val plaintiffAttachments: StateFlow<List<DocumentAttachment>> = _plaintiffAttachments.asStateFlow()

    private val _defendantAttachments = MutableStateFlow<List<DocumentAttachment>>(emptyList())
    val defendantAttachments: StateFlow<List<DocumentAttachment>> = _defendantAttachments.asStateFlow()

    private val _judgeInstructionAttachments = MutableStateFlow<List<DocumentAttachment>>(emptyList())
    val judgeInstructionAttachments: StateFlow<List<DocumentAttachment>> = _judgeInstructionAttachments.asStateFlow()

    private val extractedInstructionIds = HashSet<String>()

    private val _pipelineState = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    private val _streamingChatReply = MutableStateFlow<String?>(null)
    val streamingChatReply: StateFlow<String?> = _streamingChatReply.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private var lastChatRequest: Pair<String, DraftSection>? = null

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _latestPdfResult = MutableStateFlow<GeneratedPdfResult?>(null)
    val latestPdfResult: StateFlow<GeneratedPdfResult?> = _latestPdfResult.asStateFlow()

    private val _pdfVerificationResult = MutableStateFlow<PdfVerificationResult?>(null)
    val pdfVerificationResult: StateFlow<PdfVerificationResult?> = _pdfVerificationResult.asStateFlow()

    private val _isPdfGenerating = MutableStateFlow(false)
    val isPdfGenerating: StateFlow<Boolean> = _isPdfGenerating.asStateFlow()

    private val _latestDocxResult = MutableStateFlow<GeneratedDocxResult?>(null)
    val latestDocxResult: StateFlow<GeneratedDocxResult?> = _latestDocxResult.asStateFlow()

    private val _isDocxGenerating = MutableStateFlow(false)
    val isDocxGenerating: StateFlow<Boolean> = _isDocxGenerating.asStateFlow()

    // Search & filter state for Archives
    private val _archiveSearchQuery = MutableStateFlow("")
    val archiveSearchQuery: StateFlow<String> = _archiveSearchQuery.asStateFlow()

    private val _archiveRatingFilter = MutableStateFlow<QualityRating?>(null)
    val archiveRatingFilter: StateFlow<QualityRating?> = _archiveRatingFilter.asStateFlow()

    private val _archiveTypeFilter = MutableStateFlow<CaseType?>(null)
    val archiveTypeFilter: StateFlow<CaseType?> = _archiveTypeFilter.asStateFlow()

    private val _archivePinnedOnly = MutableStateFlow(false)
    val archivePinnedOnly: StateFlow<Boolean> = _archivePinnedOnly.asStateFlow()

    private val _archiveSubjectFilter = MutableStateFlow<String?>(null)
    val archiveSubjectFilter: StateFlow<String?> = _archiveSubjectFilter.asStateFlow()

    // Live API Voice Conversation state
    private val _liveVoiceState = MutableStateFlow(LiveVoiceUiState())
    val liveVoiceState: StateFlow<LiveVoiceUiState> = _liveVoiceState.asStateFlow()
    private var activeLiveSession: LiveSession? = null
    private var liveConnectJob: Job? = null

    // Google Search Grounding state
    private val _searchGroundingState = MutableStateFlow<SearchGroundingUiState>(SearchGroundingUiState.Idle)
    val searchGroundingState: StateFlow<SearchGroundingUiState> = _searchGroundingState.asStateFlow()

    // Audio Transcription state
    private val _audioTranscribeState = MutableStateFlow(AudioTranscribeUiState())
    val audioTranscribeState: StateFlow<AudioTranscribeUiState> = _audioTranscribeState.asStateFlow()
    private var transcribeJob: Job? = null

    // Optional text injected from Search Grounding into Discussion input
    private val _pendingChatDraftInjection = MutableStateFlow<String?>(null)
    val pendingChatDraftInjection: StateFlow<String?> = _pendingChatDraftInjection.asStateFlow()

    // Event token incremented whenever the judge transfers a generated draft into Discussion
    private val _discussionTransferEvent = MutableStateFlow(0L)
    val discussionTransferEvent: StateFlow<Long> = _discussionTransferEvent.asStateFlow()

    // Multi-file Past Judgment Import state in Archives
    private val _archiveImportState = MutableStateFlow(ArchiveImportState())
    val archiveImportState: StateFlow<ArchiveImportState> = _archiveImportState.asStateFlow()
    private var archiveImportJob: Job? = null

    // Google Drive API sync state
    private val driveApiClient = GoogleDriveApiClient()
    private val _isDriveBusy = MutableStateFlow(false)
    val isDriveBusy: StateFlow<Boolean> = _isDriveBusy.asStateFlow()

    private val _driveError = MutableStateFlow<String?>(null)
    val driveError: StateFlow<String?> = _driveError.asStateFlow()

    private val _driveBackups = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    val driveBackups: StateFlow<List<DriveBackupFile>> = _driveBackups.asStateFlow()

    private val _pendingDriveResolution = MutableStateFlow<IntentSender?>(null)
    val pendingDriveResolution: StateFlow<IntentSender?> = _pendingDriveResolution.asStateFlow()

    private var pendingDriveActionAfterAuth: PendingDriveAction = PendingDriveAction.LIST_BACKUPS

    enum class PendingDriveAction {
        LIST_BACKUPS,
        SYNC_NOW,
    }

    val savedResearchNotes: StateFlow<List<FirestoreResearchNote>> = firestoreRepo
        .observeResearchNotes(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing Firestore research notes", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        NotificationHelper.ensureChannels(appContext)
        val user = Firebase.auth.currentUser
        if (user != null && user.uid == currentUserId) {
            prefs.setGoogleAccount(
                name = user.displayName ?: user.email ?: "ዳኛ",
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
            )
            viewModelScope.launch {
                firestoreRepo.upsertUserProfile(
                    userId = currentUserId,
                    displayName = user.displayName ?: user.email ?: "ዳኛ",
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                )
            }
        }

        // Two-tier Firestore cloud case observation -> reconcile missing cloud cases into encrypted Room
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepo.observeCloudCases(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing Firestore cloud cases", firestoreError ?: error)
                }
                .collect { cloudCases ->
                    reconcileCloudCasesToLocal(cloudCases)
                }
        }

        // Pipe AudioWavRecorder meter updates to AudioTranscribeUiState only while recording
        viewModelScope.launch {
            combine(wavRecorder.amplitude, wavRecorder.elapsedSeconds) { amp, secs -> amp to secs }
                .collect { (amp, secs) ->
                    if (_audioTranscribeState.value.isRecording) {
                        _audioTranscribeState.update {
                            if (it.isRecording) it.copy(amplitude = amp, elapsedSeconds = secs) else it
                        }
                    }
                }
        }
    }

    private suspend fun reconcileCloudCasesToLocal(cloudCases: List<FirestoreCloudCase>) {
        if (cloudCases.isEmpty()) return
        withContext(Dispatchers.IO) {
            val localIdentities = repository.getNonSeedCaseIdentities()
            val localHashes = HashSet<String>(localIdentities.size)
            val localCaseKeys = HashSet<String>(localIdentities.size)
            for (item in localIdentities) {
                if (item.contentSha256.isNotBlank()) localHashes.add(item.contentSha256)
                if (item.caseNumber.isNotBlank()) localCaseKeys.add("${item.caseType.name}:${item.caseNumber}")
            }

            for (remote in cloudCases) {
                val hash = remote.contentSha256
                val key = "${remote.caseType}:${remote.caseNumber}"
                val alreadyExistsLocally = (hash.isNotBlank() && hash in localHashes) ||
                    (remote.caseNumber.isNotBlank() && key in localCaseKeys)
                if (!alreadyExistsLocally && remote.unifiedDocumentText.isNotBlank()) {
                    repository.saveOrUpdateCase(remote.toLocalEntity())
                }
            }
        }
    }

    private fun mirrorCaseToFirestore(caseEntity: CaseEntity) {
        if (caseEntity.isSeedTemplate) return
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepo.syncCase(currentUserId, caseEntity)
        }
    }

    val archivedCount: StateFlow<Int> = repository.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSyncCount: StateFlow<Int> = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allWorkCases: StateFlow<List<CaseEntity>> = repository.observeAllWorkCases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedPagedCases: Flow<PagingData<CaseEntity>> = combine(
        _archiveSearchQuery.debounce(300),
        _archiveRatingFilter,
        _archiveTypeFilter,
        _archivePinnedOnly,
        _archiveSubjectFilter,
    ) { query, rating, type, pinnedOnly, subjectFilter ->
        ArchiveFilterArgs(query, rating, type, pinnedOnly, subjectFilter)
    }.flatMapLatest { args ->
        repository.getArchivedPager(args.query, args.rating, args.type).map { pagingData ->
            val currentSettings = prefs.settings.value
            if (!args.pinnedOnly && args.subjectFilter.isNullOrBlank()) {
                pagingData
            } else {
                pagingData.filter { c ->
                    val pinOk = !args.pinnedOnly || PastJudgmentParser.matchesAnyKey(currentSettings.pinnedStyleKeys, c)
                    val subjOk = args.subjectFilter.isNullOrBlank() ||
                        PastJudgmentParser.resolveMapValue(currentSettings.subjectTags, c) == args.subjectFilter
                    pinOk && subjOk
                }
            }
        }
    }.cachedIn(viewModelScope)

    private data class ArchiveFilterArgs(
        val query: String,
        val rating: QualityRating?,
        val type: CaseType?,
        val pinnedOnly: Boolean,
        val subjectFilter: String?,
    )

    val activeCaseVersions: StateFlow<List<CaseVersionEntity>> = _activeCase
        .flatMapLatest { caseEntity ->
            if (caseEntity.id == 0L) MutableStateFlow(emptyList())
            else repository.observeVersions(caseEntity.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChatMessages: StateFlow<List<ChatMessageEntity>> = _activeCase
        .flatMapLatest { caseEntity ->
            if (caseEntity.id == 0L) MutableStateFlow(emptyList())
            else repository.observeChatMessages(caseEntity.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeAiJob: Job? = null

    fun touchActivity() {
        _lastActiveTimestamp.value = System.currentTimeMillis()
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun checkAutoLockTimeout() {
        val settings = securitySettings.value
        if (!settings.archiveLockEnabled) {
            _isArchivesUnlocked.value = true
            return
        }
        val elapsedMin = (System.currentTimeMillis() - _lastActiveTimestamp.value) / 60_000L
        if (elapsedMin >= settings.autoLockMinutes) {
            _isArchivesUnlocked.value = false
        }
    }

    fun unlockArchives() {
        _isArchivesUnlocked.value = true
        touchActivity()
    }

    fun lockArchivesNow() {
        _isArchivesUnlocked.value = false
    }

    fun selectMainTab(tab: MainMenuTab) {
        touchActivity()
        _mainTab.value = tab
    }

    fun selectWorkSubTab(subTab: WorkSubTab) {
        touchActivity()
        _workSubTab.value = subTab
    }

    fun transferDraftToDiscussion(initialPrompt: String = "") {
        touchActivity()
        val current = _activeCase.value
        if (!JudicialDocumentFormatter.hasDraftContent(current)) {
            _statusBanner.value = "ወደ ውይይት ለማሸጋገር በቅድሚያ የተሰራ ውሳኔ ወይም ረቂቅ መኖር አለበት።"
            return
        }
        viewModelScope.launch {
            if (current.id == 0L && current.status != CaseStatus.FINALIZED) {
                val savedId = repository.saveOrUpdateCase(current)
                val saved = current.copy(id = savedId)
                _activeCase.value = saved
                mirrorCaseToFirestore(saved)
            } else if (current.status != CaseStatus.FINALIZED) {
                val savedId = repository.saveOrUpdateCase(current)
                val saved = current.copy(id = savedId)
                _activeCase.value = saved
            }
            if (initialPrompt.isNotBlank()) {
                _pendingChatDraftInjection.value = initialPrompt
            }
            _discussionTransferEvent.value = System.currentTimeMillis()
            _mainTab.value = MainMenuTab.CASE_AND_DISCUSSION
            _workSubTab.value = WorkSubTab.DISCUSSION
            _statusBanner.value = "ውሳኔው ወደ ውይይት ክፍል ተሸጋግሯል። የሚጨመር፣ የሚቀነስ ወይም የሚሻሻል ነጥብ ይጻፉ።"
        }
    }

    fun returnToDraftBuilder() {
        touchActivity()
        _mainTab.value = MainMenuTab.CASE_AND_DISCUSSION
        _workSubTab.value = WorkSubTab.DRAFT_BUILDER
    }

    fun startNewCase() {
        cancelAiJob()
        _activeCase.value = createFreshCase()
        _plaintiffAttachments.value = emptyList()
        _defendantAttachments.value = emptyList()
        _judgeInstructionAttachments.value = emptyList()
        extractedInstructionIds.clear()
        _pipelineState.value = PipelineState.Idle
        _workSubTab.value = WorkSubTab.DRAFT_BUILDER
        _mainTab.value = MainMenuTab.CASE_AND_DISCUSSION
    }

    fun openExistingCase(caseEntity: CaseEntity) {
        cancelAiJob()
        _activeCase.value = caseEntity
        _judgeInstructionAttachments.value = emptyList()
        extractedInstructionIds.clear()
        _pipelineState.value = PipelineState.Idle
        _workSubTab.value = WorkSubTab.DRAFT_BUILDER
        _mainTab.value = MainMenuTab.CASE_AND_DISCUSSION
    }

    fun openCaseById(caseId: Long) {
        if (caseId <= 0L) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { repository.getCase(caseId) }
            if (found != null) {
                openExistingCase(found)
                _statusBanner.value = "መ/ቁ ${found.caseNumber.ifBlank { "-" }} ተከፍቷል።"
            }
        }
    }

    fun updateActiveCaseMetadata(transform: (CaseEntity) -> CaseEntity) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        _activeCase.value = transform(current)
    }

    fun updateCaseType(type: CaseType) {
        updateActiveCaseMetadata { current ->
            val defaultJudges = when {
                type == CaseType.APPEAL && current.judges == "ኑህ መሀመድ" ->
                    "አብራሂም ረሺድ\nኑህ መሀመድ\nቲማጅ አብዱልቃድር"
                type != CaseType.APPEAL && current.judges.contains("\n") ->
                    "ኑህ መሀመድ"
                else -> current.judges
            }
            current.copy(caseType = type, judges = defaultJudges)
        }
    }

    fun addAttachments(uris: List<Uri>, target: AttachmentTarget) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _pipelineState.value = PipelineState.ProcessingImages("ፎቶ እና ሰነድ እየተጨመቀ ነው...")
            try {
                val processed = withContext(Dispatchers.Default) {
                    coroutineScope {
                        uris.map { uri ->
                            async {
                                ImageProcessor.processUri(
                                    appContext,
                                    uri,
                                    UUID.randomUUID().toString()
                                )
                            }
                        }.map { it.await() }
                    }
                }
                when (target) {
                    AttachmentTarget.PLAINTIFF -> _plaintiffAttachments.value += processed
                    AttachmentTarget.DEFENDANT -> _defendantAttachments.value += processed
                    AttachmentTarget.JUDGE_INSTRUCTIONS -> _judgeInstructionAttachments.value += processed
                }
                _pipelineState.value = PipelineState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pipelineState.value = PipelineState.Error(
                    "ፋይሉን ማዘጋጀት አልተቻለም: ${e.localizedMessage ?: ""}"
                )
            }
        }
    }

    fun removeAttachment(id: String, target: AttachmentTarget) {
        when (target) {
            AttachmentTarget.PLAINTIFF -> {
                _plaintiffAttachments.value = _plaintiffAttachments.value.filterNot { it.id == id }
            }
            AttachmentTarget.DEFENDANT -> {
                _defendantAttachments.value = _defendantAttachments.value.filterNot { it.id == id }
            }
            AttachmentTarget.JUDGE_INSTRUCTIONS -> {
                extractedInstructionIds.remove(id)
                _judgeInstructionAttachments.value = _judgeInstructionAttachments.value.filterNot { it.id == id }
            }
        }
    }

    fun extractJudgeInstructionAttachmentsToText() {
        val pendingAttachments = _judgeInstructionAttachments.value.filter { it.id !in extractedInstructionIds }
        if (pendingAttachments.isEmpty()) {
            _statusBanner.value = if (_judgeInstructionAttachments.value.isEmpty()) {
                "በቅድሚያ የምስክር ቃል ወይም የማስረጃ ፎቶ/ሰነድ ያስገቡ።"
            } else {
                "የተያያዙት ፎቶዎች/ሰነዶች ጽሁፍ ቀድሞ ወደ መመሪያው ተገልብጧል።"
            }
            return
        }
        if (!isNetworkAvailable() && pendingAttachments.any { it.processedPages.isNotEmpty() }) {
            _pipelineState.value = PipelineState.Error("አውታረ መረብ የለም። ፎቶዎችን ለማንበብ ኢንተርኔት ያስፈልጋል።")
            return
        }

        cancelAiJob()
        activeAiJob = viewModelScope.launch {
            val startMsg = if (NetworkUtils.isCellularConnection(appContext)) {
                "በMobile Data የምስክር ቃል/ማስረጃ ፎቶዎች በመነበብ ላይ..."
            } else {
                "የምስክር ቃል/ማስረጃ ፎቶዎች በመነበብ ላይ..."
            }
            _pipelineState.value = PipelineState.ReadingOcr(startMsg)
            try {
                val res = aiService.runParallelOcr(
                    pendingAttachments,
                    "የምስክር ቃል / የፍርድ ቤት ማስረጃ",
                ) { msg ->
                    _pipelineState.value = PipelineState.ReadingOcr(msg)
                }
                extractedInstructionIds.addAll(pendingAttachments.map { it.id })
                val extractedText = res.combinedText.trim()
                if (extractedText.isNotBlank()) {
                    _activeCase.update { cur ->
                        val block = "[የምስክር ቃል / በፍርድ ቤት የቀረበ ማስረጃ (ከፎቶ የተነበበ)]:\n$extractedText"
                        val nextInstructions = if (cur.specialInstructions.isBlank()) {
                            block
                        } else {
                            "${cur.specialInstructions.trimEnd()}\n\n$block"
                        }
                        cur.copy(specialInstructions = nextInstructions)
                    }
                    _statusBanner.value = "${res.totalPages} ገጽ የምስክር ቃል/ማስረጃ ወደ ልዩ መመሪያ ገብቷል።"
                } else {
                    _statusBanner.value = "ከተያያዙት ፋይሎች የሚነበብ ጽሁፍ አልተገኘም።"
                }
                _pipelineState.value = PipelineState.Idle
            } catch (e: CancellationException) {
                _pipelineState.value = PipelineState.Idle
                throw e
            } catch (e: Exception) {
                _pipelineState.value = PipelineState.Error(GeminiJudicialService.friendlyError(e))
            }
        }
    }

    fun startStage1Ocr() {
        val pList = _plaintiffAttachments.value
        val dList = _defendantAttachments.value
        val jPending = _judgeInstructionAttachments.value.filter { it.id !in extractedInstructionIds }
        val current = _activeCase.value
        if (pList.isEmpty() && dList.isEmpty() && jPending.isEmpty() &&
            current.ocrPlaintiffText.isBlank() &&
            current.ocrDefendantText.isBlank() &&
            current.specialInstructions.isBlank()
        ) {
            _pipelineState.value = PipelineState.Error("እባክዎ መጀመሪያ የከሳሽ፣ የተከሳሽ ወይም የማስረጃ ሰነድ/ፎቶ ይምረጡ።")
            return
        }

        if (pList.isEmpty() && dList.isEmpty() && jPending.isEmpty()) {
            _pipelineState.value = PipelineState.AwaitingOcrReview(
                plaintiffText = current.ocrPlaintiffText,
                defendantText = current.ocrDefendantText,
                statsSummary = "ቀድሞ የተነበበ ጽሁፍ",
            )
            return
        }

        val needsInternet = pList.any { it.processedPages.isNotEmpty() } ||
            dList.any { it.processedPages.isNotEmpty() } ||
            jPending.any { it.processedPages.isNotEmpty() }
        if (!isNetworkAvailable() && needsInternet) {
            _pipelineState.value = PipelineState.Error("አውታረ መረብ የለም። ፎቶዎችን ለማንበብ ኢንተርኔት ያስፈልጋል።")
            return
        }

        cancelAiJob()
        activeAiJob = viewModelScope.launch {
            val readingStartMsg = if (NetworkUtils.isCellularConnection(appContext)) {
                "በMobile Data ፎቶዎች በጥንቃቄ በመነበብ ላይ..."
            } else {
                "ፎቶዎች ተጨምቀው በመነበብ ላይ..."
            }
            _pipelineState.value = PipelineState.ReadingOcr(readingStartMsg)
            try {
                val (pRes, dRes, jRes) = coroutineScope {
                    val pJob = async {
                        aiService.runParallelOcr(pList, "የከሳሽ ሰነድ") { msg ->
                            _pipelineState.value = PipelineState.ReadingOcr(msg)
                        }
                    }
                    val dJob = async {
                        aiService.runParallelOcr(dList, "የተከሳሽ ሰነድ") { msg ->
                            _pipelineState.value = PipelineState.ReadingOcr(msg)
                        }
                    }
                    val jJob = async {
                        if (jPending.isEmpty()) null
                        else aiService.runParallelOcr(jPending, "የምስክር ቃል / የፍርድ ቤት ማስረጃ") { msg ->
                            _pipelineState.value = PipelineState.ReadingOcr(msg)
                        }
                    }
                    Triple(pJob.await(), dJob.await(), jJob.await())
                }
                if (jPending.isNotEmpty()) {
                    extractedInstructionIds.addAll(jPending.map { it.id })
                }

                val totalMs = maxOf(pRes.elapsedMs, dRes.elapsedMs, jRes?.elapsedMs ?: 0L)
                val totalPages = pRes.totalPages + dRes.totalPages + (jRes?.totalPages ?: 0)
                val totalCacheHits = pRes.cacheHits + dRes.cacheHits + (jRes?.cacheHits ?: 0)
                val summary = "${totalPages} ገጽ በ ${(totalMs / 1000.0).let { "%.1f".format(it) }} ሰከንድ ተነቧል (ከመሸጎጫ: $totalCacheHits)"

                val pTextCombined = pRes.combinedText.ifBlank { _activeCase.value.ocrPlaintiffText }
                val dTextCombined = dRes.combinedText.ifBlank { _activeCase.value.ocrDefendantText }
                val jTextExtracted = jRes?.combinedText?.trim().orEmpty()
                val nextInstructions = if (jTextExtracted.isNotBlank()) {
                    val block = "[የምስክር ቃል / በፍርድ ቤት የቀረበ ማስረጃ (ከፎቶ የተነበበ)]:\n$jTextExtracted"
                    val existing = _activeCase.value.specialInstructions
                    if (existing.isBlank()) block else "${existing.trimEnd()}\n\n$block"
                } else {
                    _activeCase.value.specialInstructions
                }

                val autoMerged = withContext(Dispatchers.Default) {
                    mergeOcrMetadataIntoCase(
                        current = _activeCase.value.copy(
                            ocrPlaintiffText = pTextCombined,
                            ocrDefendantText = dTextCombined,
                            specialInstructions = nextInstructions,
                            ocrTimeMs = totalMs,
                        ),
                        pText = pTextCombined,
                        dText = dTextCombined,
                        overwriteExisting = false,
                    )
                }
                val updated = autoMerged.first
                val savedId = repository.saveOrUpdateCase(updated)
                val savedCase = updated.copy(id = savedId)
                _activeCase.value = savedCase
                mirrorCaseToFirestore(savedCase)

                val filledNote = if (autoMerged.second > 0) " • ${autoMerged.second} መረጃ ተሞልቷል" else ""
                _pipelineState.value = PipelineState.AwaitingOcrReview(
                    plaintiffText = savedCase.ocrPlaintiffText,
                    defendantText = savedCase.ocrDefendantText,
                    statsSummary = summary + filledNote,
                )
            } catch (e: CancellationException) {
                _pipelineState.value = PipelineState.Idle
                throw e
            } catch (e: Exception) {
                _pipelineState.value = PipelineState.Error(GeminiJudicialService.friendlyError(e))
            }
        }
    }

    fun updateOcrReviewTexts(plaintiffText: String, defendantText: String) {
        val currentState = _pipelineState.value
        if (currentState is PipelineState.AwaitingOcrReview) {
            _pipelineState.value = currentState.copy(
                plaintiffText = plaintiffText,
                defendantText = defendantText,
            )
        }
        _activeCase.value = _activeCase.value.copy(
            ocrPlaintiffText = plaintiffText,
            ocrDefendantText = defendantText,
        )
    }

    fun autoFillMetadataFromOcr(overwriteExisting: Boolean = true) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        val (pText, dText) = when (val st = _pipelineState.value) {
            is PipelineState.AwaitingOcrReview -> st.plaintiffText to st.defendantText
            else -> current.ocrPlaintiffText to current.ocrDefendantText
        }
        if (pText.isBlank() && dText.isBlank()) {
            _statusBanner.value = "በቅድሚያ የOCR ጽሁፍ መኖር አለበት።"
            return
        }
        viewModelScope.launch {
            val (merged, count) = withContext(Dispatchers.Default) {
                mergeOcrMetadataIntoCase(current, pText, dText, overwriteExisting)
            }
            _activeCase.value = merged
            _statusBanner.value = if (count > 0) {
                "ከOCR የተገኙ $count መረጃዎች (የመ/ቁ / ተከራካሪዎች / ቀን) ተሞልተዋል።"
            } else {
                "ተጨማሪ የሚሞላ አዲስ መረጃ አልተገኘም።"
            }
        }
    }

    private fun mergeOcrMetadataIntoCase(
        current: CaseEntity,
        pText: String,
        dText: String,
        overwriteExisting: Boolean,
    ): Pair<CaseEntity, Int> {
        val parsed = PastJudgmentParser.extractFromOcr(pText, dText)
        var filledCount = 0

        val nextCaseNo = if (parsed.caseNumber.isNotBlank() && (overwriteExisting || current.caseNumber.isBlank())) {
            if (current.caseNumber != parsed.caseNumber) filledCount++
            parsed.caseNumber
        } else current.caseNumber

        val nextPlaintiff = if (parsed.plaintiffName.isNotBlank() && (overwriteExisting || current.plaintiffName.isBlank())) {
            if (current.plaintiffName != parsed.plaintiffName) filledCount++
            parsed.plaintiffName
        } else current.plaintiffName

        val nextPlaintiffCounsel = if (parsed.plaintiffCounsel.isNotBlank() && (overwriteExisting || current.plaintiffCounsel.isBlank())) {
            if (current.plaintiffCounsel != parsed.plaintiffCounsel) filledCount++
            parsed.plaintiffCounsel
        } else current.plaintiffCounsel

        val nextDefendant = if (parsed.defendantName.isNotBlank() && (overwriteExisting || current.defendantName.isBlank())) {
            if (current.defendantName != parsed.defendantName) filledCount++
            parsed.defendantName
        } else current.defendantName

        val nextDefendantCounsel = if (parsed.defendantCounsel.isNotBlank() && (overwriteExisting || current.defendantCounsel.isBlank())) {
            if (current.defendantCounsel != parsed.defendantCounsel) filledCount++
            parsed.defendantCounsel
        } else current.defendantCounsel

        val nextCaseType = if (overwriteExisting && parsed.caseType != current.caseType) {
            filledCount++
            parsed.caseType
        } else current.caseType

        val nextJudges = if (parsed.judges.isNotBlank() &&
            (overwriteExisting || current.judges == "ኑህ መሀመድ" || current.judges.isBlank()) &&
            parsed.judges != current.judges
        ) {
            filledCount++
            parsed.judges
        } else current.judges

        val nextEtDate = if (parsed.ethiopianDate.isNotBlank() && overwriteExisting && parsed.ethiopianDate != current.ethiopianDate) {
            filledCount++
            parsed.ethiopianDate
        } else current.ethiopianDate

        val nextGregDate = if (nextEtDate != current.ethiopianDate) {
            parsed.gregorianDate.ifBlank { current.gregorianDate }
        } else current.gregorianDate

        val merged = current.copy(
            caseNumber = nextCaseNo,
            caseType = nextCaseType,
            ethiopianDate = nextEtDate,
            gregorianDate = nextGregDate,
            judges = nextJudges,
            plaintiffName = nextPlaintiff,
            plaintiffCounsel = nextPlaintiffCounsel,
            defendantName = nextDefendant,
            defendantCounsel = nextDefendantCounsel,
        )
        return merged to filledCount
    }

    fun confirmOcrAndGenerateDraft() {
        val caseNow = _activeCase.value
        val (pText, dText) = when (val st = _pipelineState.value) {
            is PipelineState.AwaitingOcrReview -> st.plaintiffText to st.defendantText
            else -> caseNow.ocrPlaintiffText to caseNow.ocrDefendantText
        }

        if (pText.isBlank() && dText.isBlank() && caseNow.specialInstructions.isBlank()) {
            _pipelineState.value = PipelineState.Error("የሚተነተን ጽሁፍ የለም። እባክዎ ንባብ ያካሂዱ።")
            return
        }

        val updatedCase = caseNow.copy(
            ocrPlaintiffText = pText,
            ocrDefendantText = dText,
        )

        if (!isNetworkAvailable()) {
            viewModelScope.launch {
                val queuedCase = updatedCase.copy(
                    status = CaseStatus.QUEUED_OFFLINE,
                    syncStatus = SyncStatus.OFFLINE_QUEUED,
                )
                val id = repository.saveOrUpdateCase(queuedCase)
                val savedQueued = queuedCase.copy(id = id)
                _activeCase.value = savedQueued
                OfflineDraftWorker.enqueue(appContext)
                _pipelineState.value = PipelineState.Idle
                _statusBanner.value = "አውታረ መረብ የለም፣ ስራው ወረፋ ተይዟል፤ ሲገናኝ በራሱ ይቀጥላል።"
            }
            return
        }

        cancelAiJob()
        activeAiJob = viewModelScope.launch {
            _pipelineState.value = PipelineState.StreamingDraft(
                stageLabel = "ጭብጥ እየተለየ...",
                rawText = "",
                parsed = ParsedDraftSections("", "", "", "", ""),
            )
            try {
                val seed = repository.getSeedTemplate(updatedCase.caseType)
                val currentSettings = prefs.settings.value
                val activeTag = PastJudgmentParser.resolveMapValue(currentSettings.subjectTags, updatedCase).orEmpty()
                val queryContext = buildString {
                    append(updatedCase.specialInstructions).append(' ')
                    append(pText.take(2500)).append(' ')
                    append(dText.take(2500))
                }
                val styleAndRef = repository.selectStyleAndSimilarReferences(
                    caseType = updatedCase.caseType,
                    queryText = queryContext,
                    pinnedKeys = currentSettings.pinnedStyleKeys,
                    subjectTags = currentSettings.subjectTags,
                    targetSubjectTag = activeTag,
                )
                val bad = repository.getBadFeedbackReminders(updatedCase.caseType)

                var finalParsed: ParsedDraftSections? = null
                aiService.streamDraft(
                    caseEntity = updatedCase,
                    plaintiffText = pText,
                    defendantText = dText,
                    seedTemplate = seed,
                    fewShotExamples = styleAndRef.styleExamples,
                    badReminders = bad,
                    similarReferences = styleAndRef.similarReferences,
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Stage -> {
                            val current = _pipelineState.value as? PipelineState.StreamingDraft
                            _pipelineState.value = PipelineState.StreamingDraft(
                                stageLabel = "${event.label}...",
                                rawText = current?.rawText.orEmpty(),
                                parsed = current?.parsed ?: ParsedDraftSections("", "", "", "", ""),
                            )
                        }
                        is StreamEvent.Token -> {
                            _pipelineState.value = PipelineState.StreamingDraft(
                                stageLabel = "ረቂቅ እየተጻፈ...",
                                rawText = event.accumulatedRaw,
                                parsed = event.parsed,
                            )
                        }
                        is StreamEvent.Completed -> {
                            finalParsed = event.parsed
                        }
                    }
                }

                val parsed = finalParsed ?: throw IllegalStateException("ረቂቁ አልተጠናቀቀም።")
                val nextVer = if (!JudicialDocumentFormatter.hasDraftContent(updatedCase)) 1 else updatedCase.currentVersion + 1
                val completedCase = updatedCase.copy(
                    issuesText = parsed.issues,
                    analysisText = parsed.analysis,
                    decisionText = parsed.decision,
                    closingText = parsed.closing,
                    judgeNotesText = parsed.judgeNotes,
                    status = CaseStatus.DRAFT,
                    currentVersion = nextVer,
                )
                val savedId = repository.saveOrUpdateCase(
                    completedCase,
                    createVersionSummary = "AI የመጀመሪያ ረቂቅ (v$nextVer)"
                )
                val saved = completedCase.copy(id = savedId)
                _activeCase.value = saved
                mirrorCaseToFirestore(saved)
                _pipelineState.value = PipelineState.Idle
                _statusBanner.value = "ረቂቅ ውሳኔው ተዘጋጅቷል (v$nextVer)።"
                if (!isAppInForeground) {
                    NotificationHelper.showDraftReadyNotification(
                        context = appContext,
                        caseId = saved.id,
                        caseNumber = saved.caseNumber,
                        version = nextVer,
                        isFromOfflineQueue = false,
                    )
                }
            } catch (e: CancellationException) {
                _pipelineState.value = PipelineState.Idle
                throw e
            } catch (e: Exception) {
                _pipelineState.value = PipelineState.Error(GeminiJudicialService.friendlyError(e))
            }
        }
    }

    fun cancelAiJob() {
        activeAiJob?.cancel()
        activeAiJob = null
        if (_pipelineState.value is PipelineState.ReadingOcr ||
            _pipelineState.value is PipelineState.StreamingDraft ||
            _pipelineState.value is PipelineState.ProcessingImages
        ) {
            _pipelineState.value = PipelineState.Idle
            _statusBanner.value = "ሂደቱ ተቋርጧል።"
        }
    }

    fun updateUnifiedDocumentManually(unifiedText: String) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        _activeCase.value = current.copy(
            issuesText = "",
            analysisText = unifiedText,
            decisionText = "",
            closingText = "",
        )
    }

    fun updateDraftSectionManually(
        issues: String? = null,
        analysis: String? = null,
        decision: String? = null,
        closing: String? = null,
    ) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        _activeCase.value = current.copy(
            issuesText = issues ?: current.issuesText,
            analysisText = analysis ?: current.analysisText,
            decisionText = decision ?: current.decisionText,
            closingText = closing ?: current.closingText,
        )
    }

    fun saveManualVersionSnapshot() {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        viewModelScope.launch {
            val nextVersion = current.currentVersion + 1
            val updated = current.copy(currentVersion = nextVersion)
            val id = repository.saveOrUpdateCase(
                updated,
                createVersionSummary = "በዳኛው የተስተካከለ (v$nextVersion)"
            )
            val saved = updated.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            _statusBanner.value = "ስሪት v$nextVersion ተቀምጧል።"
        }
    }

    fun restoreVersion(version: CaseVersionEntity) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        viewModelScope.launch {
            val nextVersion = current.currentVersion + 1
            val restored = current.copy(
                issuesText = version.issuesText,
                analysisText = version.analysisText,
                decisionText = version.decisionText,
                closingText = version.closingText,
                judgeNotesText = version.judgeNotesText,
                currentVersion = nextVersion,
            )
            val id = repository.saveOrUpdateCase(
                restored,
                createVersionSummary = "ከ v${version.versionNumber} ወደ v$nextVersion ተመልሷል"
            )
            val saved = restored.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            _statusBanner.value = "ወደ ስሪት v${version.versionNumber} ተመልሷል (አዲስ v$nextVersion)።"
        }
    }

    fun rateDraft(
        rating: QualityRating,
        badCategory: BadReasonCategory = BadReasonCategory.NONE,
        badNote: String = "",
    ) {
        viewModelScope.launch {
            val current = _activeCase.value
            val updated = current.copy(
                qualityRating = rating,
                badReasonCategory = if (rating == QualityRating.BAD) badCategory else BadReasonCategory.NONE,
                badReasonNote = if (rating == QualityRating.BAD) badNote.trim() else "",
            )
            val id = repository.saveOrUpdateCase(updated)
            val saved = updated.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            _statusBanner.value = "የጥራት ደረጃ '${rating.amharicLabel}' ተመዝግቧል።"
        }
    }

    fun finalizeToArchive() {
        viewModelScope.launch {
            val current = _activeCase.value
            if (!JudicialDocumentFormatter.hasDraftContent(current)) {
                _statusBanner.value = "ወደ Archives ከመቆለፉ በፊት ረቂቅ ውሳኔ መዘጋጀት አለበት።"
                return@launch
            }
            val finalized = current.copy(
                status = CaseStatus.FINALIZED,
                syncStatus = SyncStatus.PENDING,
            )
            val id = repository.saveOrUpdateCase(
                finalized,
                createVersionSummary = "ተጠናቆ ወደ Archives ተቆልፏል (v${finalized.currentVersion})"
            )
            val saved = finalized.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            DriveSyncWorker.enqueue(appContext)
            _statusBanner.value = "መዝገቡ ተጠናቆ በArchives እና Cloud Firestore ውስጥ ተቆልፏል (Read-Only)።"
        }
    }

    fun unlockFinalizedCaseForRevision(caseEntity: CaseEntity) {
        viewModelScope.launch {
            val nextVer = caseEntity.currentVersion + 1
            val reopened = caseEntity.copy(
                status = CaseStatus.DRAFT,
                currentVersion = nextVer,
            )
            val id = repository.saveOrUpdateCase(
                reopened,
                createVersionSummary = "የተቆለፈ መዝገብ ለማሻሻያ ተከፍቷል (v$nextVer)"
            )
            val saved = reopened.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            _mainTab.value = MainMenuTab.CASE_AND_DISCUSSION
            _workSubTab.value = WorkSubTab.DRAFT_BUILDER
            _statusBanner.value = "መዝገቡ ለማሻሻያ ተከፍቷል (ስሪት v$nextVer)።"
        }
    }

    fun sendChatRevision(userRequest: String, targetSection: DraftSection) {
        val trimmed = userRequest.trim()
        if (trimmed.isBlank() || _isChatSending.value) return
        val currentCase = _activeCase.value
        if (currentCase.status == CaseStatus.FINALIZED) {
            _chatError.value = "የተቆለፈ መዝገብ በውይይት አይቀየርም፤ መጀመሪያ ለማሻሻያ ይክፈቱት።"
            return
        }
        if (!isNetworkAvailable()) {
            _chatError.value = "አውታረ መረብ የለም። ለውይይት ኢንተርኔት ያስፈልጋል።"
            return
        }

        lastChatRequest = trimmed to targetSection
        _chatError.value = null

        viewModelScope.launch {
            _isChatSending.value = true
            _streamingChatReply.value = null
            try {
                val caseId = if (currentCase.id == 0L) {
                    val savedId = repository.saveOrUpdateCase(currentCase)
                    _activeCase.value = currentCase.copy(id = savedId)
                    savedId
                } else currentCase.id

                val persistedCase = _activeCase.value.copy(id = caseId)
                repository.addChatMessage(
                    ChatMessageEntity(
                        caseId = caseId,
                        role = ChatRole.USER,
                        targetSection = targetSection,
                        content = trimmed,
                    )
                )

                val allMessages = repository.getChatMessages(caseId)
                if (allMessages.size > 10) {
                    runCatching {
                        val toSummarize = allMessages.dropLast(5)
                        val summaryText = aiService.summarizeChatHistory(
                            toSummarize.joinToString("\n") { "${it.role.name}: ${it.content}" }
                        )
                        if (summaryText.isNotBlank()) {
                            repository.replaceOldChatMessagesWithSummary(
                                idsToDelete = toSummarize.map { it.id },
                                summary = ChatMessageEntity(
                                    caseId = caseId,
                                    role = ChatRole.SUMMARY,
                                    content = "የቀድሞ ውይይት ማጠቃለያ:\n$summaryText",
                                    createdAt = toSummarize.first().createdAt,
                                )
                            )
                        }
                    }
                }

                val recentSummary = repository.getChatMessages(caseId)
                    .takeLast(5)
                    .joinToString("\n") { "${it.role.name}: ${it.content}" }

                val hasExistingDraft = JudicialDocumentFormatter.hasDraftContent(persistedCase)
                val chatQueryContext = buildString {
                    append(trimmed).append(' ')
                    append(JudicialDocumentFormatter.composeUnifiedDocument(persistedCase).take(1800))
                }
                val currentSettings = prefs.settings.value
                val activeTag = PastJudgmentParser.resolveMapValue(currentSettings.subjectTags, persistedCase).orEmpty()
                val similarRefs = withContext(Dispatchers.IO) {
                    repository.selectStyleAndSimilarReferences(
                        caseType = persistedCase.caseType,
                        queryText = chatQueryContext,
                        pinnedKeys = currentSettings.pinnedStyleKeys,
                        subjectTags = currentSettings.subjectTags,
                        targetSubjectTag = activeTag,
                    ).similarReferences
                }

                if (GeminiJudicialService.isConversationalMessage(trimmed, hasExistingDraft)) {
                    var fullReply = ""
                    aiService.streamConversationalReply(
                        caseEntity = persistedCase,
                        userMessage = trimmed,
                        recentHistorySummary = recentSummary,
                        similarReferences = similarRefs,
                    ).collect { ev ->
                        when (ev) {
                            is ChatStreamEvent.Delta -> _streamingChatReply.value = ev.accumulatedText
                            is ChatStreamEvent.Completed -> fullReply = ev.fullText
                        }
                    }
                    _streamingChatReply.value = null
                    repository.addChatMessage(
                        ChatMessageEntity(
                            caseId = caseId,
                            role = ChatRole.ASSISTANT,
                            targetSection = targetSection,
                            content = fullReply,
                            proposedOriginalText = null,
                            proposedRevisedText = null,
                            proposedSection = null,
                        )
                    )
                } else {
                    val proposal = aiService.reviseSection(
                        caseEntity = persistedCase,
                        targetSection = targetSection,
                        userRequest = trimmed,
                        recentHistorySummary = recentSummary,
                        similarReferences = similarRefs,
                    )

                    val originalText = JudicialDocumentFormatter.composeUnifiedDocument(persistedCase)

                    val replyBody = buildString {
                        append(proposal.explanation)
                        if (proposal.consistencyWarning.isNotBlank()) {
                            append("\n\n[የወጥነት ማስታወሻ]: ${proposal.consistencyWarning}")
                        }
                        if (proposal.usedFallbackModel) {
                            append("\n\n(ፈጣን ሞዴል gemini-3.5-flash-lite በራሱ ተመርጧል)")
                        }
                    }

                    repository.addChatMessage(
                        ChatMessageEntity(
                            caseId = caseId,
                            role = ChatRole.ASSISTANT,
                            targetSection = DraftSection.ALL,
                            content = replyBody,
                            proposedOriginalText = originalText,
                            proposedRevisedText = proposal.revisedText,
                            proposedSection = DraftSection.ALL,
                        )
                    )
                }
                lastChatRequest = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _streamingChatReply.value = null
                _chatError.value = GeminiJudicialService.friendlyError(e)
            } finally {
                _isChatSending.value = false
            }
        }
    }

    fun retryLastChatMessage() {
        val last = lastChatRequest ?: return
        _chatError.value = null
        sendChatRevision(last.first, last.second)
    }

    fun dismissChatError() {
        _chatError.value = null
    }

    fun switchToFastLiteModel() {
        prefs.setModels("gemini-3.5-flash-lite", "gemini-3.5-flash-lite")
        _chatError.value = null
        _statusBanner.value = "AI ሞዴሉ ወደ ፈጣኑ gemini-3.5-flash-lite ተቀይሯል።"
        if (lastChatRequest != null) {
            retryLastChatMessage()
        }
    }

    fun acceptChatProposal(message: ChatMessageEntity) {
        val revised = message.proposedRevisedText ?: return
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return

        viewModelScope.launch {
            val nextVer = current.currentVersion + 1
            val parsed = withContext(Dispatchers.Default) {
                GeminiJudicialService.parseSections(revised)
            }
            val unifiedRevised = parsed.analysis.ifBlank { revised.trim() }
            val updated = current.copy(
                issuesText = "",
                analysisText = unifiedRevised,
                decisionText = "",
                closingText = "",
                currentVersion = nextVer,
            )
            val id = repository.saveOrUpdateCase(
                updated,
                createVersionSummary = "በውይይት የተሻሻለ ሙሉ ሰነድ (v$nextVer)"
            )
            val saved = updated.copy(id = id)
            _activeCase.value = saved
            mirrorCaseToFirestore(saved)
            repository.updateChatMessage(message.copy(isApplied = true))
            _statusBanner.value = "ለውጡ ተቀባይነት አግኝቶ ስሪት v$nextVer ተቀምጧል።"
        }
    }

    fun discardChatProposal(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.updateChatMessage(message.copy(isDiscarded = true))
        }
    }

    // --- LIVE API VOICE CONVERSATIONS (gemini-3.8-live) ---

    @OptIn(PublicPreviewAPI::class)
    fun startLiveVoiceConversation() {
        if (_liveVoiceState.value.isConnecting || _liveVoiceState.value.isActive) return
        if (!isNetworkAvailable()) {
            _liveVoiceState.update { it.copy(error = "አውታረ መረብ የለም። ለቀጥታ ድምጽ ውይይት ኢንተርኔት ያስፈልጋል።") }
            return
        }

        liveConnectJob?.cancel()
        liveConnectJob = viewModelScope.launch {
            _liveVoiceState.update {
                it.copy(
                    isConnecting = true,
                    statusText = "ከgemini-3.8-live ጋር በመገናኘት ላይ...",
                    error = null,
                )
            }
            try {
                val caseSummary = buildActiveCaseVoiceSummary()
                val session = FirebaseAiFeatures.connectLiveJudicialSession(caseSummary)
                activeLiveSession = session
                _liveVoiceState.update {
                    it.copy(
                        isConnecting = false,
                        isActive = true,
                        statusText = "ቀጥታ የድምጽ ውይይት ነቅቷል — ይናገሩ…",
                    )
                }

                session.startAudioConversation(
                    transcriptHandler = { input, output ->
                        val userText = input?.text?.trim().orEmpty()
                        val modelText = output?.text?.trim().orEmpty()
                        if (userText.isNotEmpty() || modelText.isNotEmpty()) {
                            _liveVoiceState.update { cur ->
                                val next = cur.transcripts.toMutableList()
                                if (userText.isNotEmpty()) {
                                    next.add(
                                        LiveTranscriptEntry(
                                            id = UUID.randomUUID().toString(),
                                            speakerLabel = "ዳኛ",
                                            isUser = true,
                                            text = userText,
                                        )
                                    )
                                }
                                if (modelText.isNotEmpty()) {
                                    next.add(
                                        LiveTranscriptEntry(
                                            id = UUID.randomUUID().toString(),
                                            speakerLabel = "ረዳት ዳኛ (Live)",
                                            isUser = false,
                                            text = modelText,
                                        )
                                    )
                                }
                                cur.copy(transcripts = next.takeLast(30))
                            }
                        }
                    },
                    goAwayHandler = {
                        _liveVoiceState.update {
                            it.copy(
                                isActive = false,
                                statusText = "ክፍለ-ጊዜው ተጠናቋል። እንደገና መጀመር ይችላሉ።",
                            )
                        }
                    },
                    enableInterruptions = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w(TAG, "RECORD_AUDIO permission missing for Live API", e)
                _liveVoiceState.update {
                    it.copy(
                        isConnecting = false,
                        isActive = false,
                        statusText = "ዝግጁ (gemini-3.8-live)",
                        error = "የማይክሮፎን (RECORD_AUDIO) ፍቃድ አልተሰጠም።",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Live API error", e)
                _liveVoiceState.update {
                    it.copy(
                        isConnecting = false,
                        isActive = false,
                        statusText = "ዝግጁ (gemini-3.8-live)",
                        error = GeminiJudicialService.friendlyError(e),
                    )
                }
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun sendActiveCaseContextToLive() {
        val session = activeLiveSession ?: return
        val summary = buildActiveCaseVoiceSummary()
        if (summary.isBlank()) return
        viewModelScope.launch {
            runCatching {
                session.send("እባክህ የዚህን መዝገብ አውድ አጢንና አጭር አስተያየት ስጥ:\n$summary")
                _statusBanner.value = "የመዝገቡ መረጃ ወደ ቀጥታ የድምጽ ውይይቱ ተልኳል።"
            }.onFailure { e ->
                _liveVoiceState.update { it.copy(error = GeminiJudicialService.friendlyError(e)) }
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun stopLiveVoiceConversation() {
        liveConnectJob?.cancel()
        liveConnectJob = null
        val session = activeLiveSession
        activeLiveSession = null
        viewModelScope.launch(NonCancellable) {
            runCatching {
                session?.stopAudioConversation()
                session?.close()
            }
            _liveVoiceState.update {
                it.copy(
                    isConnecting = false,
                    isActive = false,
                    statusText = "ውይይቱ ቆሟል (gemini-3.8-live)",
                )
            }
        }
    }

    private fun buildActiveCaseVoiceSummary(): String {
        val c = _activeCase.value
        val doc = JudicialDocumentFormatter.composeUnifiedDocument(c)
        return buildString {
            if (c.caseNumber.isNotBlank()) append("መ/ቁ: ${c.caseNumber}\n")
            append("አይነት: ${c.caseType.amharicLabel}\n")
            if (c.plaintiffName.isNotBlank()) append("ከሳሽ: ${c.plaintiffName}\n")
            if (c.defendantName.isNotBlank()) append("ተከሳሽ: ${c.defendantName}\n")
            if (doc.isNotBlank()) append("\nረቂቅ:\n").append(doc.take(1600))
        }.trim()
    }

    // --- GOOGLE SEARCH GROUNDING (gemini-4-hydrogen) ---

    fun runGroundedLegalSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (!isNetworkAvailable()) {
            _searchGroundingState.value = SearchGroundingUiState.Error("አውታረ መረብ የለም። Google Search ለመጠቀም ኢንተርኔት ያስፈልጋል።")
            return
        }
        viewModelScope.launch {
            _searchGroundingState.value = SearchGroundingUiState.Loading
            try {
                val context = buildActiveCaseVoiceSummary()
                val res = FirebaseAiFeatures.searchLegalKnowledgeWithGrounding(trimmed, context)
                _searchGroundingState.value = SearchGroundingUiState.Success(res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchGroundingState.value = SearchGroundingUiState.Error(GeminiJudicialService.friendlyError(e))
            }
        }
    }

    fun saveGroundedSearchToCloud(result: GroundedSearchResult) {
        viewModelScope.launch {
            val saveRes = firestoreRepo.saveResearchNote(
                userId = currentUserId,
                noteType = ResearchNoteType.SEARCH,
                title = result.query.take(180),
                content = result.answerText,
                sources = result.sources.map { it.uri },
                caseNumber = _activeCase.value.caseNumber,
            )
            _statusBanner.value = if (saveRes.isSuccess) {
                "የህግ ምርምሩ በCloud Firestore ተቀምጧል።"
            } else {
                "ማስታወሻውን ማስቀመጥ አልተቻለም።"
            }
        }
    }

    fun saveVoiceOrTranscriptNoteToCloud(
        noteType: ResearchNoteType,
        title: String,
        content: String,
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val saveRes = firestoreRepo.saveResearchNote(
                userId = currentUserId,
                noteType = noteType,
                title = title,
                content = content,
                caseNumber = _activeCase.value.caseNumber,
            )
            _statusBanner.value = if (saveRes.isSuccess) {
                "ማስታወሻው በCloud Firestore ተቀምጧል።"
            } else {
                "ማስታወሻውን ማስቀመጥ አልተቻለም።"
            }
        }
    }

    fun deleteCloudResearchNote(noteId: String) {
        if (noteId.isBlank()) return
        viewModelScope.launch {
            val res = firestoreRepo.deleteResearchNote(currentUserId, noteId)
            if (res.isFailure) {
                _statusBanner.value = "ማስታወሻውን ማጥፋት አልተቻለም።"
            }
        }
    }

    fun injectSearchPromptIntoChat(prompt: String) {
        _pendingChatDraftInjection.value = prompt
    }

    fun consumeInjectedChatDraft(): String? {
        val v = _pendingChatDraftInjection.value
        _pendingChatDraftInjection.value = null
        return v
    }

    // --- MICROPHONE AUDIO TRANSCRIPTION (gemini-3.5-transcribe) ---

    fun startAudioRecording() {
        _audioTranscribeState.update {
            it.copy(isRecording = true, amplitude = 0f, elapsedSeconds = 0, error = null)
        }
        wavRecorder.start(viewModelScope) { errMsg ->
            _audioTranscribeState.update {
                it.copy(isRecording = false, error = errMsg)
            }
        }
    }

    fun stopAudioAndTranscribe() {
        transcribeJob?.cancel()
        transcribeJob = viewModelScope.launch {
            _audioTranscribeState.update {
                it.copy(isRecording = false, isTranscribing = true, error = null)
            }
            try {
                val wavBytes = wavRecorder.stopAndBuildWav()
                if (wavBytes.isEmpty()) {
                    _audioTranscribeState.update {
                        it.copy(isTranscribing = false, error = "ምንም ድምጽ አልተቀዳም።")
                    }
                    return@launch
                }
                if (!isNetworkAvailable()) {
                    _audioTranscribeState.update {
                        it.copy(isTranscribing = false, error = "አውታረ መረብ የለም።")
                    }
                    return@launch
                }
                val text = FirebaseAiFeatures.transcribeAudioWithGemini(
                    audioBytes = wavBytes,
                    mimeType = "audio/wav",
                    contextHint = _activeCase.value.caseNumber,
                )
                _audioTranscribeState.update {
                    it.copy(isTranscribing = false, transcribedText = text, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _audioTranscribeState.update {
                    it.copy(isTranscribing = false, error = GeminiJudicialService.friendlyError(e))
                }
            }
        }
    }

    fun updateTranscribedDraft(text: String) {
        _audioTranscribeState.update { it.copy(transcribedText = text) }
    }

    fun resetAudioTranscribe() {
        transcribeJob?.cancel()
        wavRecorder.cancelAndDiscard()
        _audioTranscribeState.value = AudioTranscribeUiState()
    }

    // --- PDF, ARCHIVES & GOOGLE DRIVE ---

    fun generatePdf(caseEntity: CaseEntity, includeDraftWatermark: Boolean) {
        viewModelScope.launch {
            _isPdfGenerating.value = true
            try {
                val res = pdfGenerator.generateProtectedPdf(caseEntity, includeDraftWatermark)
                val updated = caseEntity.copy(
                    pdfPath = res.file.absolutePath,
                    pdfFileSha256 = res.fileSha256,
                    contentSha256 = res.contentSha256,
                )
                val savedId = repository.saveOrUpdateCase(updated)
                if (_activeCase.value.id == caseEntity.id) {
                    _activeCase.value = updated.copy(id = savedId)
                }
                _latestPdfResult.value = res
            } catch (e: Exception) {
                _statusBanner.value = "PDF ማመንጨት አልተቻለም: ${e.localizedMessage ?: ""}"
            } finally {
                _isPdfGenerating.value = false
            }
        }
    }

    fun dismissLatestPdfResult() {
        _latestPdfResult.value = null
    }

    fun generateDocx(caseEntity: CaseEntity, includeDraftNotice: Boolean) {
        viewModelScope.launch {
            _isDocxGenerating.value = true
            try {
                val res = DocxExporter.exportDocx(appContext, caseEntity, includeDraftNotice)
                _latestDocxResult.value = res
                _statusBanner.value = "የWord (.docx) ሰነድ ተዘጋጅቷል (${res.file.name})።"
            } catch (e: Exception) {
                _statusBanner.value = "Word (.docx) ማመንጨት አልተቻለም: ${e.localizedMessage ?: ""}"
            } finally {
                _isDocxGenerating.value = false
            }
        }
    }

    fun dismissLatestDocxResult() {
        _latestDocxResult.value = null
    }

    fun applyJudicialPreset(preset: JudicialPreset, useStarterTemplate: Boolean) {
        val current = _activeCase.value
        if (current.status == CaseStatus.FINALIZED) return
        val nextInstructions = if (current.specialInstructions.isBlank()) {
            preset.aiInstruction
        } else if (!current.specialInstructions.contains(preset.aiInstruction.take(24))) {
            "${current.specialInstructions}\n${preset.aiInstruction}"
        } else {
            current.specialInstructions
        }
        val nextAnalysis = if (useStarterTemplate && current.analysisText.isBlank()) {
            preset.starterTemplate
                .replace("የመ/ቁ:- ____", "የመ/ቁ:- ${current.caseNumber.ifBlank { "____" }}")
                .replace("ቀን:- ____ ዓ.ም", "ቀን:- ${current.ethiopianDate.ifBlank { "____" }} ዓ.ም")
                .replace("ከሳሽ፤ ____", "ከሳሽ፤ ${current.plaintiffName.ifBlank { "____" }}")
                .replace("ተከሳሽ፤ ____", "ተከሳሽ፤ ${current.defendantName.ifBlank { "____" }}")
        } else current.analysisText

        val nextJudges = if (preset.caseType == CaseType.APPEAL && current.judges == "ኑህ መሀመድ") {
            "አብራሂም ረሺድ\nኑህ መሀመድ\nቲማጅ አብዱልቃድር"
        } else if (preset.caseType != CaseType.APPEAL && current.judges.contains("\n")) {
            "ኑህ መሀመድ"
        } else current.judges

        val updated = current.copy(
            caseType = preset.caseType,
            judges = nextJudges,
            specialInstructions = nextInstructions,
            issuesText = if (useStarterTemplate && current.analysisText.isBlank()) "" else current.issuesText,
            analysisText = nextAnalysis,
        )
        _activeCase.value = updated
        if (preset.subjectTag.isNotBlank()) {
            setCaseSubjectTag(updated, preset.subjectTag, silent = true)
        }
        _statusBanner.value = "'${preset.titleAmharic}' ቅጽ ተመርጧል።"
    }

    fun togglePinStyleCase(caseEntity: CaseEntity) {
        val keys = PastJudgmentParser.caseKeys(caseEntity)
        if (keys.isEmpty()) return
        val isNowPinned = prefs.togglePinnedStyleKey(keys)
        _statusBanner.value = if (isNowPinned) {
            "መ/ቁ ${caseEntity.caseNumber.ifBlank { "-" }} እንደ ዋና የአፃፃፍ ናሙና ተሰክቷል (Pinned)።"
        } else {
            "መ/ቁ ${caseEntity.caseNumber.ifBlank { "-" }} ከዋና ናሙና ተነስቷል።"
        }
    }

    fun setCaseSubjectTag(caseEntity: CaseEntity, tag: String?, silent: Boolean = false) {
        val keys = PastJudgmentParser.caseKeys(caseEntity)
        if (keys.isEmpty()) return
        prefs.setSubjectTag(keys, tag)
        if (!silent) {
            _statusBanner.value = if (tag.isNullOrBlank()) {
                "የጉዳይ ዘርፍ መለያ ተነስቷል።"
            } else {
                "የጉዳይ ዘርፍ '$tag' ተመዝግቧል።"
            }
        }
    }

    fun setCaseAdjournmentDate(caseEntity: CaseEntity, ethiopianDate: String?) {
        val keys = PastJudgmentParser.caseKeys(caseEntity)
        if (keys.isEmpty()) return
        prefs.setAdjournmentDate(keys, ethiopianDate)
        val primaryKey = keys.first()
        if (ethiopianDate.isNullOrBlank()) {
            keys.forEach { AdjournmentReminderWorker.cancel(appContext, it) }
            _statusBanner.value = "የቀጠሮ ቀን ተሰርዟል።"
        } else {
            val parties = listOfNotNull(
                caseEntity.plaintiffName.takeIf { it.isNotBlank() },
                caseEntity.defendantName.takeIf { it.isNotBlank() },
            ).joinToString(" እና ")
            val scheduled = if (prefs.settings.value.notificationsEnabled) {
                AdjournmentReminderWorker.schedule(
                    context = appContext,
                    caseKey = primaryKey,
                    caseId = caseEntity.id,
                    caseNumber = caseEntity.caseNumber,
                    partiesSummary = parties,
                    ethiopianDate = ethiopianDate.trim(),
                )
            } else false
            _statusBanner.value = if (scheduled) {
                "የቀጠሮ ቀን: $ethiopianDate ዓ.ም እና Notification ማስታወሻ ተመዝግቧል።"
            } else {
                "የቀጠሮ ቀን: $ethiopianDate ዓ.ም ተመዝግቧል።"
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        prefs.setNotificationsEnabled(enabled)
        _statusBanner.value = if (enabled) {
            "የመተግበሪያው Notification ተከፍቷል።"
        } else {
            "የመተግበሪያው Notification ታግዷል።"
        }
    }

    fun sendTestNotification(): Boolean {
        val sent = NotificationHelper.showTestNotification(appContext)
        _statusBanner.value = if (sent) {
            "የሙከራ Notification ተልኳል — የስልክዎን የማሳወቂያ ክፍል ይመልከቱ።"
        } else {
            "Notification መላክ አልተቻለም፤ እባክዎ የNotification ፍቃድ እና ማብሪያ/ማጥፊያውን ያረጋግጡ።"
        }
        return sent
    }

    fun renameArchivedCase(caseEntity: CaseEntity, newCaseNumber: String) {
        val cleaned = newCaseNumber.trim().take(70)
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val updated = caseEntity.copy(caseNumber = cleaned)
            repository.saveOrUpdateCase(updated)
            if (_activeCase.value.id == caseEntity.id) {
                _activeCase.value = updated
            }
            _statusBanner.value = "የመ/ቁ ወደ '$cleaned' ተቀይሯል።"
        }
    }

    fun deleteArchivedCase(caseEntity: CaseEntity) {
        if (caseEntity.id <= 0L) return
        viewModelScope.launch {
            val keys = PastJudgmentParser.caseKeys(caseEntity)
            repository.deleteCase(caseEntity.id)
            prefs.clearCaseExtras(keys)
            keys.forEach { AdjournmentReminderWorker.cancel(appContext, it) }
            if (_activeCase.value.id == caseEntity.id) {
                _activeCase.value = createFreshCase()
            }
            _statusBanner.value = "መዝገቡ ከArchive ተሰርዟል።"
        }
    }

    fun verifyPdfUri(uri: Uri) {
        viewModelScope.launch {
            _pdfVerificationResult.value = pdfGenerator.verifyPdf(uri)
        }
    }

    fun dismissVerificationResult() {
        _pdfVerificationResult.value = null
    }

    fun setArchiveSearch(query: String) {
        _archiveSearchQuery.value = query
    }

    fun setArchiveRatingFilter(rating: QualityRating?) {
        _archiveRatingFilter.value = rating
    }

    fun setArchiveTypeFilter(type: CaseType?) {
        _archiveTypeFilter.value = type
    }

    fun setArchivePinnedOnly(pinnedOnly: Boolean) {
        _archivePinnedOnly.value = pinnedOnly
    }

    fun setArchiveSubjectFilter(tag: String?) {
        _archiveSubjectFilter.value = tag
    }

    fun importArchiveReferenceFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _archiveImportState.value.isImporting) return
        archiveImportJob?.cancel()
        archiveImportJob = viewModelScope.launch {
            _archiveImportState.value = ArchiveImportState(
                isImporting = true,
                processedCount = 0,
                totalCount = uris.size,
                currentFileName = "",
                lastSummary = null,
                error = null,
            )

            var importedCount = 0
            var duplicateCount = 0
            var failedCount = 0
            var lastErrorMsg: String? = null

            try {
                uris.forEachIndexed { idx, uri ->
                    val att = withContext(Dispatchers.IO) {
                        ImageProcessor.processUri(appContext, uri, UUID.randomUUID().toString())
                    }
                    _archiveImportState.update {
                        it.copy(
                            processedCount = idx,
                            currentFileName = att.displayName,
                        )
                    }

                    val extractedRaw = if (!att.directExtractedText.isNullOrBlank()) {
                        att.directExtractedText.trim()
                    } else if (att.processedPages.isNotEmpty()) {
                        if (!isNetworkAvailable()) {
                            failedCount++
                            lastErrorMsg = "ስካን የተደረጉ PDF ወይም ፎቶዎችን ለማንበብ ኢንተርኔት ያስፈልጋል።"
                            return@forEachIndexed
                        }
                        runCatching {
                            val ocrRes = aiService.runParallelOcr(
                                attachments = listOf(att),
                                sideLabel = "የቀድሞ ውሳኔ",
                            )
                            ocrRes.combinedText
                                .replace(Regex("""^\[[^\]]+\]\R?""", RegexOption.MULTILINE), "")
                                .replace("\n\n---\n\n", "\n\n")
                                .trim()
                        }.getOrElse { err ->
                            if (err is CancellationException) throw err
                            failedCount++
                            lastErrorMsg = GeminiJudicialService.friendlyError(err)
                            ""
                        }
                    } else {
                        ""
                    }

                    if (extractedRaw.isBlank()) {
                        if (att.processedPages.isEmpty()) failedCount++
                        return@forEachIndexed
                    }

                    val (candidate, hash) = withContext(Dispatchers.Default) {
                        val parsedMeta = PastJudgmentParser.parseMetadata(
                            rawText = extractedRaw,
                            fallbackFileName = att.displayName,
                        )
                        val entity = CaseEntity(
                            caseNumber = parsedMeta.caseNumber,
                            caseType = parsedMeta.caseType,
                            ethiopianDate = parsedMeta.ethiopianDate,
                            gregorianDate = parsedMeta.gregorianDate,
                            judges = parsedMeta.judges,
                            plaintiffName = parsedMeta.plaintiffName,
                            defendantName = parsedMeta.defendantName,
                            specialInstructions = "${PastJudgmentParser.UPLOADED_REF_PREFIX} ${att.displayName}",
                            issuesText = "",
                            analysisText = extractedRaw,
                            decisionText = "",
                            closingText = "",
                            judgeNotesText = "${PastJudgmentParser.UPLOADED_REF_NOTE_MARKER} — Style & Reference] ከፋይል: ${att.displayName}",
                            status = CaseStatus.FINALIZED,
                            qualityRating = QualityRating.EXCELLENT,
                            currentVersion = 1,
                        )
                        entity to repository.computeCanonicalContentHash(entity)
                    }
                    val existing = withContext(Dispatchers.IO) { repository.findByHash(hash) }
                    if (existing != null) {
                        duplicateCount++
                    } else {
                        val savedId = repository.saveOrUpdateCase(
                            candidate,
                            createVersionSummary = "ከፋይል የተጫነ የቀድሞ ውሳኔ (${att.displayName})"
                        )
                        val saved = candidate.copy(id = savedId, contentSha256 = hash)
                        mirrorCaseToFirestore(saved)
                        importedCount++
                    }
                }

                if (importedCount > 0) {
                    DriveSyncWorker.enqueue(appContext)
                }

                val summaryParts = mutableListOf<String>()
                if (importedCount > 0) summaryParts.add("$importedCount ውሳኔ(ዎች) ወደ Archive ተጭነዋል")
                if (duplicateCount > 0) summaryParts.add("$duplicateCount ቀድሞ የነበረ")
                if (failedCount > 0) summaryParts.add("$failedCount አልተነበበም")
                val summary = summaryParts.joinToString(" • ").ifBlank { "ምንም አዲስ ሰነድ አልተጫነም።" }

                _archiveImportState.value = ArchiveImportState(
                    isImporting = false,
                    processedCount = uris.size,
                    totalCount = uris.size,
                    currentFileName = "",
                    lastSummary = summary,
                    error = if (importedCount == 0 && failedCount > 0) lastErrorMsg else null,
                )
                _statusBanner.value = summary
            } catch (e: CancellationException) {
                _archiveImportState.update {
                    it.copy(isImporting = false, currentFileName = "", lastSummary = "መጫኑ ተቋርጧል።")
                }
                throw e
            } catch (e: Exception) {
                val msg = GeminiJudicialService.friendlyError(e)
                _archiveImportState.update {
                    it.copy(isImporting = false, currentFileName = "", error = msg)
                }
            }
        }
    }

    fun cancelArchiveImport() {
        archiveImportJob?.cancel()
        archiveImportJob = null
        _archiveImportState.update { it.copy(isImporting = false, currentFileName = "") }
    }

    fun dismissArchiveImportStatus() {
        _archiveImportState.update { it.copy(lastSummary = null, error = null) }
    }

    fun connectDriveFolder(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.setDriveTreeUri(uri.toString())
        DriveSyncWorker.enqueue(appContext)
        _statusBanner.value = "የGoogle Drive ማህደር ተገናኝቷል፤ ምትኬ በጀርባ ይላካል።"
    }

    fun triggerDriveSyncNow() {
        DriveSyncWorker.enqueue(appContext)
        _statusBanner.value = "የተመሰጠረ ምትኬ ማመሳሰል ተጀምሯል..."
    }

    fun signInWithGoogleForDrive(activityContext: Context) {
        if (_isDriveBusy.value) return
        viewModelScope.launch {
            _isDriveBusy.value = true
            _driveError.value = null
            when (val outcome = GoogleDriveAuthManager.signInWithGoogleInteractive(activityContext)) {
                is GoogleSignInOutcome.Success -> {
                    prefs.setGoogleAccount(
                        name = outcome.profile.displayName,
                        email = outcome.profile.email,
                        photoUrl = outcome.profile.photoUrl,
                    )
                    _statusBanner.value = "በGoogle ተገብቷል: ${outcome.profile.email}"
                    _isDriveBusy.value = false
                    authorizeGoogleDrive(activityContext, PendingDriveAction.LIST_BACKUPS)
                }
                is GoogleSignInOutcome.Cancelled -> {
                    _isDriveBusy.value = false
                    _driveError.value = "የGoogle መግቢያ ተቋርጧል። እባክዎ መለያ ይምረጡ።"
                }
                is GoogleSignInOutcome.Error -> {
                    _isDriveBusy.value = false
                    _driveError.value = outcome.message
                }
            }
        }
    }

    fun authorizeGoogleDrive(
        activityContext: Context,
        afterAction: PendingDriveAction = PendingDriveAction.LIST_BACKUPS,
    ) {
        viewModelScope.launch {
            _isDriveBusy.value = true
            _driveError.value = null
            pendingDriveActionAfterAuth = afterAction
            when (val outcome = GoogleDriveAuthManager.authorizeGoogleDrive(activityContext)) {
                is DriveAuthOutcome.Authorized -> {
                    prefs.setDriveApiAuthorized(true)
                    handleAuthorizedToken(outcome.accessToken, afterAction)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    _isDriveBusy.value = false
                    _pendingDriveResolution.value = outcome.pendingIntent.intentSender
                }
                is DriveAuthOutcome.Error -> {
                    _isDriveBusy.value = false
                    _driveError.value = outcome.message
                }
            }
        }
    }

    fun clearPendingDriveResolution() {
        _pendingDriveResolution.value = null
    }

    fun onDriveResolutionResult(activityContext: Context, resultData: Intent?) {
        _pendingDriveResolution.value = null
        viewModelScope.launch {
            _isDriveBusy.value = true
            _driveError.value = null
            when (val outcome = GoogleDriveAuthManager.extractDriveTokenFromIntent(activityContext, resultData)) {
                is DriveAuthOutcome.Authorized -> {
                    prefs.setDriveApiAuthorized(true)
                    _statusBanner.value = "የGoogle Drive ፍቃድ ተሰጥቷል።"
                    handleAuthorizedToken(outcome.accessToken, pendingDriveActionAfterAuth)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    _isDriveBusy.value = false
                    _pendingDriveResolution.value = outcome.pendingIntent.intentSender
                }
                is DriveAuthOutcome.Error -> {
                    _isDriveBusy.value = false
                    _driveError.value = outcome.message
                }
            }
        }
    }

    fun syncToGoogleDriveNow(activityContext: Context) {
        if (!isNetworkAvailable()) {
            _driveError.value = "አውታረ መረብ የለም። ወደ Google Drive ለመላክ ኢንተርኔት ያስፈልጋል።"
            return
        }
        if (prefs.settings.value.googleAccountEmail.isNullOrBlank()) {
            signInWithGoogleForDrive(activityContext)
            return
        }
        authorizeGoogleDrive(activityContext, PendingDriveAction.SYNC_NOW)
    }

    fun refreshDriveBackups(activityContext: Context) {
        if (!isNetworkAvailable()) return
        if (prefs.settings.value.googleAccountEmail.isNullOrBlank() || !prefs.settings.value.driveApiAuthorized) {
            return
        }
        authorizeGoogleDrive(activityContext, PendingDriveAction.LIST_BACKUPS)
    }

    fun restoreFromGoogleDriveFile(activityContext: Context, fileId: String) {
        if (!isNetworkAvailable()) {
            _driveError.value = "አውታረ መረብ የለም።"
            return
        }
        viewModelScope.launch {
            _isDriveBusy.value = true
            _driveError.value = null
            when (val auth = GoogleDriveAuthManager.authorizeGoogleDrive(activityContext)) {
                is DriveAuthOutcome.Authorized -> {
                    try {
                        val bytes = driveApiClient.downloadBackupBytes(auth.accessToken, fileId)
                        val count = withContext(Dispatchers.IO) { importEncryptedBackupBytes(bytes) }
                        _statusBanner.value = "$count መዝገቦች ከGoogle Drive ተመልሰዋል።"
                    } catch (e: Exception) {
                        _driveError.value = "ከGoogle Drive መመለስ አልተቻለም: ${e.localizedMessage ?: ""}"
                    } finally {
                        _isDriveBusy.value = false
                    }
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    _isDriveBusy.value = false
                    _pendingDriveResolution.value = auth.pendingIntent.intentSender
                }
                is DriveAuthOutcome.Error -> {
                    _isDriveBusy.value = false
                    _driveError.value = auth.message
                }
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun signOutGoogle(activityContext: Context) {
        viewModelScope.launch {
            _isDriveBusy.value = true
            _driveError.value = null
            withContext(NonCancellable) {
                runCatching {
                    activeLiveSession?.stopAudioConversation()
                    activeLiveSession?.close()
                }
                activeLiveSession = null
                wavRecorder.cancelAndDiscard()
                GoogleDriveAuthManager.signOut(activityContext)
                prefs.clearGoogleAccountSession()
            }
            _driveBackups.value = emptyList()
            _isDriveBusy.value = false
            _statusBanner.value = "ከGoogle መለያ ወጥተዋል።"
        }
    }

    private suspend fun handleAuthorizedToken(
        accessToken: String,
        action: PendingDriveAction,
    ) {
        try {
            if (action == PendingDriveAction.SYNC_NOW) {
                val allCases = withContext(Dispatchers.IO) { repository.getAllNonSeedCases() }
                if (allCases.isEmpty()) {
                    _statusBanner.value = "የሚመሳሰል መዝገብ የለም።"
                } else {
                    val now = System.currentTimeMillis()
                    val fileName = "redat_dagna_cloud_$now.rdenc"
                    val encrypted = withContext(Dispatchers.Default) {
                        val json = DriveSyncWorker.serializeCasesDelta(allCases)
                        CryptoUtil.encryptAesGcm(
                            json.toByteArray(Charsets.UTF_8),
                            prefs.getOrCreateBackupPassphrase(),
                        )
                    }
                    driveApiClient.uploadEncryptedBackup(accessToken, fileName, encrypted)
                    withContext(Dispatchers.IO) {
                        allCases.forEach {
                            repository.markCaseSynced(it)
                            firestoreRepo.syncCase(currentUserId, it)
                        }
                    }
                    prefs.updateLastSyncTimestamp(now)
                    _statusBanner.value = "${allCases.size} መዝገቦች ወደ Google Drive እና Firestore ተመሳስለዋል።"
                }
            }
            _driveBackups.value = driveApiClient.listEncryptedBackups(accessToken)
        } catch (e: Exception) {
            _driveError.value = e.localizedMessage ?: "ከGoogle Drive ጋር መገናኘት አልተቻለም።"
        } finally {
            _isDriveBusy.value = false
        }
    }

    fun exportEncryptedBackupToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val all = repository.getAllNonSeedCases()
                    val json = DriveSyncWorker.serializeCasesDelta(all)
                    val encrypted = CryptoUtil.encryptAesGcm(
                        json.toByteArray(Charsets.UTF_8),
                        prefs.getOrCreateBackupPassphrase()
                    )
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(encrypted)
                    }
                }
                _statusBanner.value = "የተመሰጠረ ምትኬ (.rdenc) በተሳካ ሁኔታ ወጥቷል።"
            } catch (e: Exception) {
                _statusBanner.value = "ምትኬ ማውጣት አልተቻለም: ${e.localizedMessage ?: ""}"
            }
        }
    }

    fun restoreEncryptedBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val restoredCount = withContext(Dispatchers.IO) {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: error("ፋይሉን መክፈት አልተቻለም")
                    importEncryptedBackupBytes(bytes)
                }
                _statusBanner.value = "$restoredCount መዝገቦች ከተመሰጠረ ምትኬ ተመልሰዋል።"
            } catch (e: Exception) {
                _statusBanner.value = "ምትኬን መመለስ አልተቻለም (ቁልፉ ላይስማማ ይችላል): ${e.localizedMessage ?: ""}"
            }
        }
    }

    private suspend fun importEncryptedBackupBytes(bytes: ByteArray): Int {
        val jsonBytes = CryptoUtil.decryptAesGcm(bytes, prefs.getOrCreateBackupPassphrase())
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
        val arr = root.getJSONArray("cases")
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val entity = CaseEntity(
                caseNumber = obj.optString("caseNumber"),
                caseType = runCatching { CaseType.valueOf(obj.optString("caseType")) }
                    .getOrDefault(CaseType.DIRECT_CIVIL),
                ethiopianDate = obj.optString("ethiopianDate"),
                gregorianDate = obj.optString("gregorianDate"),
                judges = obj.optString("judges", "ኑህ መሀመድ"),
                plaintiffName = obj.optString("plaintiffName"),
                plaintiffCounsel = obj.optString("plaintiffCounsel"),
                defendantName = obj.optString("defendantName"),
                defendantCounsel = obj.optString("defendantCounsel"),
                issuesText = obj.optString("issuesText"),
                analysisText = obj.optString("analysisText"),
                decisionText = obj.optString("decisionText"),
                closingText = obj.optString("closingText"),
                judgeNotesText = obj.optString("judgeNotesText"),
                status = runCatching { CaseStatus.valueOf(obj.optString("status")) }
                    .getOrDefault(CaseStatus.FINALIZED),
                qualityRating = runCatching { QualityRating.valueOf(obj.optString("qualityRating")) }
                    .getOrDefault(QualityRating.NONE),
                currentVersion = obj.optInt("currentVersion", 1),
                contentSha256 = obj.optString("contentSha256"),
                syncStatus = SyncStatus.SYNCED,
            )
            val existing = if (entity.contentSha256.isNotBlank()) {
                repository.findByHash(entity.contentSha256)
            } else null
            if (existing == null) {
                repository.saveOrUpdateCase(entity)
                count++
            }
        }
        return count
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    private fun isNetworkAvailable(): Boolean = NetworkUtils.isNetworkAvailable(appContext)

    private fun createFreshCase(): CaseEntity {
        val todayEt = EthiopianCalendarUtil.todayEthiopian()
        val etStr = todayEt.formatNumeric()
        val gregStr = EthiopianCalendarUtil.ethiopianToGregorianString(etStr)
        return CaseEntity(
            caseType = CaseType.DIRECT_CIVIL,
            ethiopianDate = etStr,
            gregorianDate = gregStr,
            judges = "ኑህ መሀመድ",
        )
    }

    @OptIn(PublicPreviewAPI::class)
    override fun onCleared() {
        super.onCleared()
        liveConnectJob?.cancel()
        wavRecorder.cancelAndDiscard()
        runCatching {
            activeLiveSession?.stopAudioConversation()
        }
        activeLiveSession = null
    }

    private companion object {
        const val TAG = "JudicialViewModel"
    }
}
