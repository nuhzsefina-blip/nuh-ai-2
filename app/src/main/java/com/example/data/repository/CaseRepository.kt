package com.example.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.data.local.CaseDao
import com.example.data.local.CaseEntity
import com.example.data.local.CaseIdentityRow
import com.example.data.local.CaseType
import com.example.data.local.CaseVersionEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.OcrCacheEntity
import com.example.data.local.QualityRating
import com.example.data.local.SyncStatus
import com.example.util.CryptoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CaseRepository(private val dao: CaseDao) {
    fun observeCase(caseId: Long): Flow<CaseEntity?> = dao.observeCaseById(caseId)

    suspend fun getCase(caseId: Long): CaseEntity? = dao.getCaseById(caseId)

    fun observeAllWorkCases(): Flow<List<CaseEntity>> = dao.observeAllWorkCases()

    fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()

    fun observePendingSyncCount(): Flow<Int> = dao.observePendingSyncCount()

    fun observeVersions(caseId: Long): Flow<List<CaseVersionEntity>> = dao.observeVersions(caseId)

    fun observeChatMessages(caseId: Long): Flow<List<ChatMessageEntity>> = dao.observeChatMessages(caseId)

    fun getArchivedPager(
        searchQuery: String,
        ratingFilter: QualityRating?,
        typeFilter: CaseType?,
    ): Flow<PagingData<CaseEntity>> {
        val ratingStr = ratingFilter?.takeIf { it != QualityRating.NONE }?.name
        val typeStr = typeFilter?.name
        val trimmed = searchQuery.trim()
        return Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 8, enablePlaceholders = false),
            pagingSourceFactory = {
                if (trimmed.isBlank()) {
                    dao.pagingArchivedCases(ratingStr, typeStr)
                } else {
                    val sanitized = trimmed
                        .replace("\"", "")
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { "$it*" }
                    dao.pagingSearchArchivedCases(sanitized, ratingStr, typeStr)
                }
            }
        ).flow
    }

    suspend fun saveOrUpdateCase(caseEntity: CaseEntity, createVersionSummary: String? = null): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val computedHash = computeCanonicalContentHash(caseEntity)
            val updated = caseEntity.copy(
                contentSha256 = computedHash,
                syncStatus = SyncStatus.PENDING,
                updatedAt = now,
            )
            val id = if (updated.id == 0L) {
                dao.insertCase(updated.copy(createdAt = now))
            } else {
                dao.updateCase(updated)
                updated.id
            }

            if (createVersionSummary != null) {
                dao.insertVersion(
                    CaseVersionEntity(
                        caseId = id,
                        versionNumber = updated.currentVersion,
                        issuesText = updated.issuesText,
                        analysisText = updated.analysisText,
                        decisionText = updated.decisionText,
                        closingText = updated.closingText,
                        judgeNotesText = updated.judgeNotesText,
                        changeSummary = createVersionSummary,
                        createdAt = now,
                    )
                )
            }
            id
        }

    suspend fun deleteCase(caseId: Long) = dao.deleteCase(caseId)

    suspend fun getCachedOcr(sha256: String): OcrCacheEntity? = dao.getCachedOcr(sha256)

    suspend fun saveOcrCache(sha256: String, text: String, byteSize: Int, elapsedMs: Long) {
        dao.insertOcrCache(
            OcrCacheEntity(
                imageSha256 = sha256,
                extractedText = text,
                byteSize = byteSize,
                processingMs = elapsedMs,
            )
        )
    }

    data class StyleAndReferenceContext(
        val styleExamples: List<CaseEntity>,
        val similarReferences: List<com.example.util.ScoredReferenceCase>,
    )

    suspend fun getSeedTemplate(caseType: CaseType): CaseEntity? = dao.getSeedTemplate(caseType)

    suspend fun getBestFewShotExamples(caseType: CaseType): List<CaseEntity> =
        dao.getBestFewShotExamples(caseType)

    suspend fun selectStyleAndSimilarReferences(
        caseType: CaseType,
        queryText: String,
        pinnedKeys: Set<String> = emptySet(),
        subjectTags: Map<String, String> = emptyMap(),
        targetSubjectTag: String = "",
    ): StyleAndReferenceContext = withContext(Dispatchers.Default) {
        val candidates = withContext(Dispatchers.IO) { dao.getStyleAndReferenceCandidates() }
        val styleExamples = candidates
            .sortedWith(
                compareBy<CaseEntity>(
                    { if (com.example.util.PastJudgmentParser.matchesAnyKey(pinnedKeys, it)) 0 else 1 },
                    { if (it.caseType == caseType) 0 else 1 },
                    { if (com.example.util.PastJudgmentParser.isUploadedReferenceCase(it)) 0 else 1 },
                    {
                        when (it.qualityRating) {
                            QualityRating.EXCELLENT -> 0
                            QualityRating.GOOD -> 1
                            else -> 2
                        }
                    },
                    { -it.updatedAt }
                )
            )
            .take(2)

        val styleIds = styleExamples.map { it.id }.toSet()
        val similar = com.example.util.PastJudgmentParser.rankSimilarCases(
            candidates = candidates,
            queryText = queryText,
            targetCaseType = caseType,
            excludeIds = styleIds,
            pinnedKeys = pinnedKeys,
            subjectTags = subjectTags,
            targetSubjectTag = targetSubjectTag,
        ).take(2)

        StyleAndReferenceContext(
            styleExamples = styleExamples,
            similarReferences = similar,
        )
    }

    suspend fun getBadFeedbackReminders(caseType: CaseType): List<CaseEntity> =
        dao.getBadFeedbackReminders(caseType)

    suspend fun addChatMessage(message: ChatMessageEntity): Long = dao.insertChatMessage(message)

    suspend fun updateChatMessage(message: ChatMessageEntity) = dao.updateChatMessage(message)

    suspend fun getChatMessages(caseId: Long): List<ChatMessageEntity> = dao.getChatMessages(caseId)

    suspend fun replaceOldChatMessagesWithSummary(idsToDelete: List<Long>, summary: ChatMessageEntity) {
        if (idsToDelete.isNotEmpty()) {
            dao.deleteChatMessagesByIds(idsToDelete)
            dao.insertChatMessage(summary)
        }
    }

    suspend fun findByHash(hash: String): CaseEntity? = dao.findByHash(hash)

    suspend fun getUnsyncedCases(): List<CaseEntity> = dao.getUnsyncedCases()

    suspend fun markCaseSynced(caseEntity: CaseEntity) {
        dao.updateCase(caseEntity.copy(syncStatus = SyncStatus.SYNCED))
    }

    suspend fun getQueuedOfflineCases(): List<CaseEntity> = dao.getQueuedOfflineCases()

    suspend fun getAllNonSeedCases(): List<CaseEntity> = dao.getAllNonSeedCases()

    suspend fun getNonSeedCaseIdentities(): List<CaseIdentityRow> = dao.getNonSeedCaseIdentities()

    fun computeCanonicalContentHash(c: CaseEntity): String {
        val canonical = buildString {
            append(c.caseNumber.trim()).append('|')
            append(c.caseType.name).append('|')
            append(c.ethiopianDate.trim()).append('|')
            append(c.judges.trim()).append('|')
            append(c.plaintiffName.trim()).append('|')
            append(c.defendantName.trim()).append('|')
            append(c.issuesText.trim()).append('|')
            append(c.analysisText.trim()).append('|')
            append(c.decisionText.trim()).append('|')
            append(c.closingText.trim())
        }
        return CryptoUtil.sha256Hex(canonical)
    }
}
