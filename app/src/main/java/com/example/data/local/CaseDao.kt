package com.example.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases WHERE id = :id LIMIT 1")
    fun observeCaseById(id: Long): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE id = :id LIMIT 1")
    suspend fun getCaseById(id: Long): CaseEntity?

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 
        ORDER BY updatedAt DESC
        """
    )
    fun observeAllWorkCases(): Flow<List<CaseEntity>>

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 AND status = :status
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun getLatestByStatus(status: CaseStatus): CaseEntity?

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 
          AND status = 'FINALIZED'
          AND (:ratingFilter IS NULL OR qualityRating = :ratingFilter)
          AND (:typeFilter IS NULL OR caseType = :typeFilter)
        ORDER BY updatedAt DESC
        """
    )
    fun pagingArchivedCases(
        ratingFilter: String?,
        typeFilter: String?,
    ): PagingSource<Int, CaseEntity>

    @Query(
        """
        SELECT cases.* FROM cases 
        JOIN cases_fts ON cases.id = cases_fts.rowid
        WHERE cases.isSeedTemplate = 0 
          AND cases.status = 'FINALIZED'
          AND cases_fts MATCH :ftsQuery
          AND (:ratingFilter IS NULL OR cases.qualityRating = :ratingFilter)
          AND (:typeFilter IS NULL OR cases.caseType = :typeFilter)
        ORDER BY cases.updatedAt DESC
        """
    )
    fun pagingSearchArchivedCases(
        ftsQuery: String,
        ratingFilter: String?,
        typeFilter: String?,
    ): PagingSource<Int, CaseEntity>

    @Query("SELECT COUNT(*) FROM cases WHERE isSeedTemplate = 0 AND status = 'FINALIZED'")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cases WHERE isSeedTemplate = 0 AND syncStatus != 'SYNCED'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM cases WHERE isSeedTemplate = 0 AND syncStatus != 'SYNCED'")
    suspend fun getUnsyncedCases(): List<CaseEntity>

    @Query("SELECT * FROM cases WHERE isSeedTemplate = 0 AND status = 'QUEUED_OFFLINE'")
    suspend fun getQueuedOfflineCases(): List<CaseEntity>

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 1 AND caseType = :caseType 
        LIMIT 1
        """
    )
    suspend fun getSeedTemplate(caseType: CaseType): CaseEntity?

    @Query("SELECT * FROM cases WHERE isSeedTemplate = 1")
    suspend fun getAllSeedTemplates(): List<CaseEntity>

    @Query("DELETE FROM cases WHERE isSeedTemplate = 1")
    suspend fun deleteSeedTemplates()

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 
          AND caseType = :caseType 
          AND qualityRating IN ('EXCELLENT', 'GOOD')
          AND (LENGTH(analysisText) > 60 OR LENGTH(decisionText) > 20)
        ORDER BY CASE WHEN qualityRating = 'EXCELLENT' THEN 0 ELSE 1 END, updatedAt DESC
        LIMIT 4
        """
    )
    suspend fun getBestFewShotExamples(caseType: CaseType): List<CaseEntity>

    @Query(
        """
        SELECT * FROM cases
        WHERE isSeedTemplate = 0
          AND (qualityRating IN ('EXCELLENT', 'GOOD') OR status = 'FINALIZED')
          AND (LENGTH(analysisText) > 60 OR LENGTH(decisionText) > 20)
        ORDER BY CASE WHEN qualityRating = 'EXCELLENT' THEN 0 WHEN qualityRating = 'GOOD' THEN 1 ELSE 2 END, updatedAt DESC
        LIMIT 40
        """
    )
    suspend fun getStyleAndReferenceCandidates(): List<CaseEntity>

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 
          AND caseType = :caseType 
          AND qualityRating = 'BAD'
          AND (badReasonCategory != 'NONE' OR LENGTH(badReasonNote) > 0)
        ORDER BY updatedAt DESC
        LIMIT 3
        """
    )
    suspend fun getBadFeedbackReminders(caseType: CaseType): List<CaseEntity>

    @Query(
        """
        SELECT * FROM cases 
        WHERE isSeedTemplate = 0 
          AND (pdfFileSha256 = :hash OR contentSha256 = :hash)
        LIMIT 1
        """
    )
    suspend fun findByHash(hash: String): CaseEntity?

    @Query("SELECT * FROM cases WHERE isSeedTemplate = 0")
    suspend fun getAllNonSeedCases(): List<CaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(caseEntity: CaseEntity): Long

    @Update
    suspend fun updateCase(caseEntity: CaseEntity)

    @Query("DELETE FROM cases WHERE id = :id AND isSeedTemplate = 0")
    suspend fun deleteCase(id: Long)

    // OCR Cache
    @Query("SELECT * FROM ocr_cache WHERE imageSha256 = :sha256 LIMIT 1")
    suspend fun getCachedOcr(sha256: String): OcrCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrCache(entry: OcrCacheEntity)

    // Version History
    @Query("SELECT * FROM case_versions WHERE caseId = :caseId ORDER BY versionNumber DESC")
    fun observeVersions(caseId: Long): Flow<List<CaseVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: CaseVersionEntity): Long

    // Chat History
    @Query("SELECT * FROM chat_messages WHERE caseId = :caseId ORDER BY createdAt ASC")
    fun observeChatMessages(caseId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE caseId = :caseId ORDER BY createdAt ASC")
    suspend fun getChatMessages(caseId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteChatMessagesByIds(ids: List<Long>)
}
