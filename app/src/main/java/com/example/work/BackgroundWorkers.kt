package com.example.work

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ai.GeminiJudicialService
import com.example.data.local.AppDatabase
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.example.data.local.SyncStatus
import com.example.data.repository.CaseRepository
import com.example.security.GoogleDriveAuthManager
import com.example.security.SecurePrefsManager
import com.example.sync.GoogleDriveApiClient
import com.example.util.CryptoUtil
import com.example.util.NotificationHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "BackgroundWorkers"

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefsManager.getInstance(applicationContext)
        val db = AppDatabase.getInstance(applicationContext, prefs)
        val repo = CaseRepository(db.caseDao())

        val unsynced = repo.getUnsyncedCases()
        if (unsynced.isEmpty()) {
            prefs.updateLastSyncTimestamp(System.currentTimeMillis())
            return Result.success()
        }

        val payloadJson = serializeCasesDelta(unsynced)
        val key = prefs.getOrCreateBackupPassphrase()
        val encrypted = CryptoUtil.encryptAesGcm(payloadJson.toByteArray(Charsets.UTF_8), key)

        val timestamp = System.currentTimeMillis()
        val fileName = "redat_dagna_delta_$timestamp.rdenc"

        // Always persist local encrypted vault backup
        val localVaultDir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
        File(localVaultDir, fileName).writeBytes(encrypted)

        val currentSettings = prefs.settings.value
        var cloudSuccess = false

        // 1. Google Drive REST API upload if signed in & Drive scope authorized
        if (!currentSettings.googleAccountEmail.isNullOrBlank() && currentSettings.driveApiAuthorized) {
            val token = GoogleDriveAuthManager.getSilentDriveAccessToken(applicationContext)
            if (!token.isNullOrBlank()) {
                val uploaded = runCatching {
                    GoogleDriveApiClient().uploadEncryptedBackup(token, fileName, encrypted)
                    true
                }.onFailure { e ->
                    Log.w(TAG, "Google Drive API upload failed; will retry", e)
                }.getOrDefault(false)
                if (!uploaded) {
                    return Result.retry()
                }
                cloudSuccess = true
            }
        }

        // 2. Also write to SAF folder URI if connected
        val treeUriStr = currentSettings.driveTreeUri
        if (!treeUriStr.isNullOrBlank()) {
            val writeOk = runCatching {
                val treeUri = treeUriStr.toUri()
                val folder = DocumentFile.fromTreeUri(applicationContext, treeUri)
                val targetFile = folder?.createFile("application/octet-stream", fileName)
                if (targetFile != null) {
                    applicationContext.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                        out.write(encrypted)
                    }
                    true
                } else false
            }.getOrDefault(false)

            if (!writeOk && !cloudSuccess) {
                Log.w(TAG, "Drive folder write failed; retrying later.")
                return Result.retry()
            }
        }

        unsynced.forEach { repo.markCaseSynced(it) }
        prefs.updateLastSyncTimestamp(timestamp)
        NotificationHelper.showBackupCompletedNotification(applicationContext, unsynced.size)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "redat_dagna_drive_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun serializeCasesDelta(cases: List<CaseEntity>): String {
            val arr = JSONArray()
            cases.forEach { c ->
                arr.put(
                    JSONObject().apply {
                        put("caseNumber", c.caseNumber)
                        put("caseType", c.caseType.name)
                        put("ethiopianDate", c.ethiopianDate)
                        put("gregorianDate", c.gregorianDate)
                        put("judges", c.judges)
                        put("plaintiffName", c.plaintiffName)
                        put("plaintiffCounsel", c.plaintiffCounsel)
                        put("defendantName", c.defendantName)
                        put("defendantCounsel", c.defendantCounsel)
                        put("issuesText", c.issuesText)
                        put("analysisText", c.analysisText)
                        put("decisionText", c.decisionText)
                        put("closingText", c.closingText)
                        put("judgeNotesText", c.judgeNotesText)
                        put("status", c.status.name)
                        put("qualityRating", c.qualityRating.name)
                        put("currentVersion", c.currentVersion)
                        put("contentSha256", c.contentSha256)
                        put("updatedAt", c.updatedAt)
                    }
                )
            }
            return JSONObject().put("cases", arr).put("exportedAt", System.currentTimeMillis()).toString()
        }
    }
}

class OfflineDraftWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefsManager.getInstance(applicationContext)
        val db = AppDatabase.getInstance(applicationContext, prefs)
        val repo = CaseRepository(db.caseDao())
        val ai = GeminiJudicialService(prefs, repo, applicationContext)

        val queued = repo.getQueuedOfflineCases()
        if (queued.isEmpty()) return Result.success()

        for (caseEntity in queued) {
            try {
                val seed = repo.getSeedTemplate(caseEntity.caseType)
                val fewShot = repo.getBestFewShotExamples(caseEntity.caseType)
                val bad = repo.getBadFeedbackReminders(caseEntity.caseType)
                var latestParsed: com.example.ai.ParsedDraftSections? = null
                ai.streamDraft(
                    caseEntity = caseEntity,
                    plaintiffText = caseEntity.ocrPlaintiffText,
                    defendantText = caseEntity.ocrDefendantText,
                    seedTemplate = seed,
                    fewShotExamples = fewShot,
                    badReminders = bad,
                ).collect { ev ->
                    if (ev is com.example.ai.StreamEvent.Completed) {
                        latestParsed = ev.parsed
                    }
                }
                val parsed = latestParsed ?: continue
                val savedId = repo.saveOrUpdateCase(
                    caseEntity.copy(
                        issuesText = parsed.issues,
                        analysisText = parsed.analysis,
                        decisionText = parsed.decision,
                        closingText = parsed.closing,
                        judgeNotesText = parsed.judgeNotes,
                        status = CaseStatus.DRAFT,
                        syncStatus = SyncStatus.PENDING,
                    ),
                    createVersionSummary = "ከኔትወርክ ወረፋ የተሰራ የመጀመሪያ ረቂቅ (v${caseEntity.currentVersion})"
                )
                NotificationHelper.showDraftReadyNotification(
                    context = applicationContext,
                    caseId = savedId,
                    caseNumber = caseEntity.caseNumber,
                    version = caseEntity.currentVersion,
                    isFromOfflineQueue = true,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Offline queue processing failed for caseId=${caseEntity.id}", e)
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "redat_dagna_offline_queue"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<OfflineDraftWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

class AdjournmentReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefsManager.getInstance(applicationContext)
        if (!prefs.settings.value.notificationsEnabled) return Result.success()

        val caseKey = inputData.getString(KEY_CASE_KEY).orEmpty()
        val ethiopianDate = inputData.getString(KEY_ETHIOPIAN_DATE).orEmpty()
        if (ethiopianDate.isBlank()) return Result.success()

        // Ensure reminder wasn't cleared or rescheduled to a different date
        if (caseKey.isNotBlank()) {
            val savedDate = prefs.settings.value.adjournmentDates[caseKey]
            if (savedDate.isNullOrBlank() || savedDate != ethiopianDate) {
                return Result.success()
            }
        }

        val caseId = inputData.getLong(KEY_CASE_ID, -1L)
        val caseNumber = inputData.getString(KEY_CASE_NUMBER).orEmpty()
        val partiesSummary = inputData.getString(KEY_PARTIES).orEmpty()
        val isDayBefore = NotificationHelper.isDayBeforeReminderTarget(ethiopianDate)

        NotificationHelper.showAdjournmentReminder(
            context = applicationContext,
            caseId = caseId,
            caseNumber = caseNumber,
            ethiopianDate = ethiopianDate,
            partiesSummary = partiesSummary,
            isDayBefore = isDayBefore,
        )
        return Result.success()
    }

    companion object {
        private const val KEY_CASE_ID = "case_id"
        private const val KEY_CASE_KEY = "case_key"
        private const val KEY_CASE_NUMBER = "case_number"
        private const val KEY_PARTIES = "parties"
        private const val KEY_ETHIOPIAN_DATE = "ethiopian_date"

        private fun uniqueWorkName(caseKey: String): String =
            "redat_dagna_adjournment_${caseKey.hashCode()}"

        fun schedule(
            context: Context,
            caseKey: String,
            caseId: Long,
            caseNumber: String,
            partiesSummary: String,
            ethiopianDate: String,
        ): Boolean {
            val delayMs = NotificationHelper.computeAdjournmentReminderDelayMs(ethiopianDate)
            if (delayMs == null) {
                cancel(context, caseKey)
                return false
            }

            val input = Data.Builder()
                .putLong(KEY_CASE_ID, caseId)
                .putString(KEY_CASE_KEY, caseKey)
                .putString(KEY_CASE_NUMBER, caseNumber)
                .putString(KEY_PARTIES, partiesSummary)
                .putString(KEY_ETHIOPIAN_DATE, ethiopianDate)
                .build()

            val req = OneTimeWorkRequestBuilder<AdjournmentReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(uniqueWorkName(caseKey), ExistingWorkPolicy.REPLACE, req)
            return true
        }

        fun cancel(context: Context, caseKey: String) {
            if (caseKey.isBlank()) return
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(caseKey))
        }
    }
}
