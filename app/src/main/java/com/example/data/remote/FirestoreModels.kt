package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.BadReasonCategory
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.example.data.local.CaseType
import com.example.data.local.QualityRating
import com.example.data.local.SyncStatus
import com.example.util.JudicialDocumentFormatter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ResearchNoteType(val amharicLabel: String) {
    SEARCH("የህግ ምርምር (Google Search)"),
    TRANSCRIPT("የድምጽ ጽሁፍ (Transcription)"),
    LIVE_VOICE("የድምጽ ውይይት (Live API)"),
}

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val courtName: String = "የሐረሪ ብሔራዊ ክልል ከፍተኛ ፍርድ ቤት",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId.take(128),
        "displayName" to displayName.trim().ifBlank { "ዳኛ" }.take(120),
        "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
        "photoUrl" to photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
        "courtName" to courtName.trim().take(160),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null } as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    fun toUpdateMap(): Map<String, Any> = mapOf(
        "displayName" to displayName.trim().ifBlank { "ዳኛ" }.take(120),
        "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
        "photoUrl" to photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
        "courtName" to courtName.trim().take(160),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirestoreCloudCase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val caseNumber: String = "",
    val caseType: String = CaseType.DIRECT_CIVIL.name,
    val ethiopianDate: String = "",
    val gregorianDate: String = "",
    val judges: String = "ኑህ መሀመድ",
    val plaintiffName: String = "",
    val plaintiffCounsel: String = "",
    val defendantName: String = "",
    val defendantCounsel: String = "",
    val unifiedDocumentText: String = "",
    val judgeNotesText: String = "",
    val status: String = CaseStatus.DRAFT.name,
    val qualityRating: String = QualityRating.NONE.name,
    val badReasonCategory: String = BadReasonCategory.NONE.name,
    val badReasonNote: String = "",
    val currentVersion: Int = 1,
    val contentSha256: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId.take(128),
        "caseNumber" to caseNumber.take(80),
        "caseType" to safeEnumName<CaseType>(caseType, CaseType.DIRECT_CIVIL).name,
        "ethiopianDate" to ethiopianDate.take(40),
        "gregorianDate" to gregorianDate.take(40),
        "judges" to judges.ifBlank { "ኑህ መሀመድ" }.take(300),
        "plaintiffName" to plaintiffName.take(500),
        "plaintiffCounsel" to plaintiffCounsel.take(300),
        "defendantName" to defendantName.take(500),
        "defendantCounsel" to defendantCounsel.take(300),
        "unifiedDocumentText" to unifiedDocumentText.take(120000),
        "judgeNotesText" to judgeNotesText.take(20000),
        "status" to safeEnumName<CaseStatus>(status, CaseStatus.DRAFT).name,
        "qualityRating" to safeEnumName<QualityRating>(qualityRating, QualityRating.NONE).name,
        "badReasonCategory" to safeEnumName<BadReasonCategory>(badReasonCategory, BadReasonCategory.NONE).name,
        "badReasonNote" to badReasonNote.take(2000),
        "currentVersion" to currentVersion.coerceIn(1, 10000),
        "contentSha256" to contentSha256.take(128),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "caseNumber" to caseNumber.take(80),
        "caseType" to safeEnumName<CaseType>(caseType, CaseType.DIRECT_CIVIL).name,
        "ethiopianDate" to ethiopianDate.take(40),
        "gregorianDate" to gregorianDate.take(40),
        "judges" to judges.ifBlank { "ኑህ መሀመድ" }.take(300),
        "plaintiffName" to plaintiffName.take(500),
        "plaintiffCounsel" to plaintiffCounsel.take(300),
        "defendantName" to defendantName.take(500),
        "defendantCounsel" to defendantCounsel.take(300),
        "unifiedDocumentText" to unifiedDocumentText.take(120000),
        "judgeNotesText" to judgeNotesText.take(20000),
        "status" to safeEnumName<CaseStatus>(status, CaseStatus.DRAFT).name,
        "qualityRating" to safeEnumName<QualityRating>(qualityRating, QualityRating.NONE).name,
        "badReasonCategory" to safeEnumName<BadReasonCategory>(badReasonCategory, BadReasonCategory.NONE).name,
        "badReasonNote" to badReasonNote.take(2000),
        "currentVersion" to currentVersion.coerceIn(1, 10000),
        "contentSha256" to contentSha256.take(128),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toLocalEntity(): CaseEntity {
        val tsMs = (updatedAt ?: createdAt ?: Timestamp.now()).toDate().time
        val createdMs = (createdAt ?: updatedAt ?: Timestamp.now()).toDate().time
        return CaseEntity(
            caseNumber = caseNumber,
            caseType = safeEnumName(caseType, CaseType.DIRECT_CIVIL),
            ethiopianDate = ethiopianDate,
            gregorianDate = gregorianDate,
            judges = judges.ifBlank { "ኑህ መሀመድ" },
            plaintiffName = plaintiffName,
            plaintiffCounsel = plaintiffCounsel,
            defendantName = defendantName,
            defendantCounsel = defendantCounsel,
            analysisText = unifiedDocumentText,
            judgeNotesText = judgeNotesText,
            status = safeEnumName(status, CaseStatus.DRAFT),
            qualityRating = safeEnumName(qualityRating, QualityRating.NONE),
            badReasonCategory = safeEnumName(badReasonCategory, BadReasonCategory.NONE),
            badReasonNote = badReasonNote,
            currentVersion = currentVersion.coerceAtLeast(1),
            contentSha256 = contentSha256,
            syncStatus = SyncStatus.SYNCED,
            createdAt = createdMs,
            updatedAt = tsMs,
        )
    }

    companion object {
        fun fromLocalEntity(userId: String, caseEntity: CaseEntity): FirestoreCloudCase {
            val unified = JudicialDocumentFormatter.composeUnifiedDocument(caseEntity)
            return FirestoreCloudCase(
                userId = userId,
                caseNumber = caseEntity.caseNumber,
                caseType = caseEntity.caseType.name,
                ethiopianDate = caseEntity.ethiopianDate,
                gregorianDate = caseEntity.gregorianDate,
                judges = caseEntity.judges.ifBlank { "ኑህ መሀመድ" },
                plaintiffName = caseEntity.plaintiffName,
                plaintiffCounsel = caseEntity.plaintiffCounsel,
                defendantName = caseEntity.defendantName,
                defendantCounsel = caseEntity.defendantCounsel,
                unifiedDocumentText = unified,
                judgeNotesText = caseEntity.judgeNotesText,
                status = caseEntity.status.name,
                qualityRating = caseEntity.qualityRating.name,
                badReasonCategory = caseEntity.badReasonCategory.name,
                badReasonNote = caseEntity.badReasonNote,
                currentVersion = caseEntity.currentVersion.coerceAtLeast(1),
                contentSha256 = caseEntity.contentSha256,
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreResearchNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val noteType: String = ResearchNoteType.SEARCH.name,
    val title: String = "",
    val content: String = "",
    val sources: List<String> = emptyList(),
    val caseNumber: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedType(): ResearchNoteType = safeEnumName(noteType, ResearchNoteType.SEARCH)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId.take(128),
        "noteType" to safeEnumName(noteType, ResearchNoteType.SEARCH).name,
        "title" to title.trim().ifBlank { "የህግ ማስታወሻ" }.take(200),
        "content" to content.trim().ifBlank { "-" }.take(60000),
        "sources" to sources.map { it.trim().take(500) }.filter { it.isNotEmpty() }.take(20),
        "caseNumber" to caseNumber.trim().take(80),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

inline fun <reified T : Enum<T>> safeEnumName(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
