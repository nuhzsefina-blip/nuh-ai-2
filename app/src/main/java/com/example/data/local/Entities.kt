package com.example.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CaseType(val amharicLabel: String) {
    APPEAL("የይግባኝ ፍርድ"),
    DIRECT_CIVIL("የቀጥታ ፍርድ"),
    DECREE("ብይን"),
}

enum class CaseStatus {
    DRAFT,
    QUEUED_OFFLINE,
    FINALIZED,
}

enum class QualityRating(val amharicLabel: String) {
    NONE("ያልተመዘነ"),
    BAD("መጥፎ"),
    GOOD("ጥሩ"),
    EXCELLENT("እጅግ ጥሩ"),
}

enum class BadReasonCategory(val amharicLabel: String) {
    NONE("ምክንያት አልተጠቀሰም"),
    STYLE("የአፃፃፍ ስልት"),
    LEGAL_ERROR("የህግ ስህተት"),
    ISSUES("የጭብጥ አወጣጥ"),
    LANGUAGE("ቋንቋ እና ግልጽነት"),
}

enum class SyncStatus(val amharicLabel: String) {
    PENDING("በመጠባበቅ ላይ"),
    SYNCED("ተመሳስሏል"),
    OFFLINE_QUEUED("ኔትወርክ ይጠብቃል"),
}

enum class DraftSection(val amharicLabel: String) {
    ALL("ሁሉም ክፍሎች"),
    ISSUES("ጭብጥ"),
    ANALYSIS("ፍ ር ድ / ትንታኔ"),
    DECISION("ው ሳ ኔ / ት ዕ ዛ ዝ"),
}

enum class ChatRole {
    USER,
    ASSISTANT,
    SUMMARY,
}

@Entity(
    tableName = "cases",
    indices = [
        Index(value = ["caseNumber"]),
        Index(value = ["status", "updatedAt"]),
        Index(value = ["qualityRating"]),
        Index(value = ["pdfFileSha256"]),
        Index(value = ["contentSha256"]),
    ]
)
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val caseType: CaseType = CaseType.DIRECT_CIVIL,
    val ethiopianDate: String = "",
    val gregorianDate: String = "",
    val judges: String = "ኑህ መሀመድ",
    val plaintiffName: String = "",
    val plaintiffCounsel: String = "",
    val defendantName: String = "",
    val defendantCounsel: String = "",
    val specialInstructions: String = "",
    val attachmentUrisJson: String = "[]",
    val ocrPlaintiffText: String = "",
    val ocrDefendantText: String = "",
    val ocrTimeMs: Long = 0L,
    val issuesText: String = "",
    val analysisText: String = "",
    val decisionText: String = "",
    val closingText: String = "",
    val judgeNotesText: String = "",
    val status: CaseStatus = CaseStatus.DRAFT,
    val qualityRating: QualityRating = QualityRating.NONE,
    val badReasonCategory: BadReasonCategory = BadReasonCategory.NONE,
    val badReasonNote: String = "",
    val currentVersion: Int = 1,
    val pdfPath: String = "",
    val pdfFileSha256: String = "",
    val contentSha256: String = "",
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val isSeedTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Fts4(contentEntity = CaseEntity::class)
@Entity(tableName = "cases_fts")
data class CaseFtsEntity(
    val caseNumber: String,
    val plaintiffName: String,
    val defendantName: String,
    val issuesText: String,
    val analysisText: String,
    val decisionText: String,
    val ocrPlaintiffText: String,
    val ocrDefendantText: String,
)

@Entity(tableName = "ocr_cache")
data class OcrCacheEntity(
    @PrimaryKey val imageSha256: String,
    val extractedText: String,
    val byteSize: Int,
    val processingMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "case_versions",
    indices = [Index(value = ["caseId", "versionNumber"])]
)
data class CaseVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val versionNumber: Int,
    val issuesText: String,
    val analysisText: String,
    val decisionText: String,
    val closingText: String,
    val judgeNotesText: String,
    val changeSummary: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["caseId", "createdAt"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val role: ChatRole,
    val targetSection: DraftSection = DraftSection.ALL,
    val content: String,
    val proposedOriginalText: String? = null,
    val proposedRevisedText: String? = null,
    val proposedSection: DraftSection? = null,
    val isApplied: Boolean = false,
    val isDiscarded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
