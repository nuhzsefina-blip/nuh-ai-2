package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.local.CaseEntity
import com.example.data.local.DraftSection
import com.example.data.repository.CaseRepository
import com.example.security.PiiMasker
import com.example.security.SecurePrefsManager
import com.example.util.DocumentAttachment
import com.example.util.NetworkUtils
import com.example.util.ProcessedPageImage
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.EOFException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

data class ParsedDraftSections(
    val issues: String,
    val analysis: String,
    val decision: String,
    val closing: String,
    val judgeNotes: String,
)

data class OcrBatchResult(
    val combinedText: String,
    val totalPages: Int,
    val cacheHits: Int,
    val elapsedMs: Long,
)

data class RevisionProposal(
    val targetSection: DraftSection,
    val revisedText: String,
    val explanation: String,
    val consistencyWarning: String,
    val usedFallbackModel: Boolean = false,
)

sealed interface StreamEvent {
    data class Stage(val label: String) : StreamEvent
    data class Token(val accumulatedRaw: String, val parsed: ParsedDraftSections) : StreamEvent
    data class Completed(val raw: String, val parsed: ParsedDraftSections) : StreamEvent
}

sealed interface ChatStreamEvent {
    data class Delta(val accumulatedText: String) : ChatStreamEvent
    data class Completed(val fullText: String, val modelUsed: String) : ChatStreamEvent
}

private class RestHttpException(
    val statusCode: Int,
    val serverMessage: String,
) : IllegalStateException("HTTP $statusCode: $serverMessage")

private const val TAG = "GeminiJudicialService"
private const val MODEL_LITE = "gemini-3.5-flash-lite"
private const val MODEL_FLASH = "gemini-3.8-flash"
private const val MODEL_PRO = "gemini-4-argon"
private const val OCR_BATCH_SIZE = 3
private const val CELLULAR_OCR_BATCH_SIZE = 2

class GeminiJudicialService(
    private val prefs: SecurePrefsManager,
    private val repository: CaseRepository,
    private val appContext: Context? = null,
) {
    private val httpClient: OkHttpClient by lazy {
        NetworkUtils.createMobileResilientOkHttpClient(
            connectTimeoutSec = 35L,
            readTimeoutSec = 120L,
            writeTimeoutSec = 90L,
            callTimeoutSec = 180L,
        )
    }

    // Shared across plaintiff & defendant OCR so concurrent uploads stay bounded to 2 on Wi-Fi, 1 on Mobile Data
    private val ocrSemaphore = Semaphore(2)
    private val cellularOcrSemaphore = Semaphore(1)

    // Lazy cache of Firebase GenerativeModel instances to reuse underlying APIController & HTTP pools
    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun getOrCreateModel(
        modelName: String,
        systemInstructionText: String? = null,
        responseSchema: Schema? = null,
        responseMimeType: String? = null,
    ): GenerativeModel {
        val cacheKey = buildString {
            append(modelName)
            append('|')
            append(systemInstructionText?.hashCode() ?: 0)
            append('|')
            append(responseMimeType.orEmpty())
            append('|')
            append(responseSchema != null)
        }
        return modelCache.getOrPut(cacheKey) {
            Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = systemInstructionText?.let { text -> content { text(text) } },
                generationConfig = if (responseMimeType != null || responseSchema != null) {
                    generationConfig {
                        if (responseMimeType != null) this.responseMimeType = responseMimeType
                        if (responseSchema != null) this.responseSchema = responseSchema
                    }
                } else {
                    null
                },
            )
        }
    }

    private data class IndexedOcrPage(
        val order: Int,
        val label: String,
        val page: ProcessedPageImage,
    )

    /**
     * Stage 1: Batched & throttled OCR across up to 3 uncached pages per request (max 2 requests in flight),
     * skipping any page already cached in Room by SHA-256 hash.
     */
    suspend fun runParallelOcr(
        attachments: List<DocumentAttachment>,
        sideLabel: String,
        onProgress: ((String) -> Unit)? = null,
    ): OcrBatchResult = coroutineScope {
        val startMs = System.currentTimeMillis()
        val cacheHits = AtomicInteger(0)

        // Direct text attachments (Word or text-based PDF) require zero OCR
        val directParts = attachments.mapNotNull { att ->
            att.directExtractedText?.takeIf { it.isNotBlank() }?.let { text ->
                "[${att.displayName}]\n$text"
            }
        }

        val allPages = attachments.flatMapIndexed { idx, att ->
            att.processedPages.mapIndexed { pageIdx, page ->
                IndexedOcrPage(
                    order = idx * 1000 + pageIdx,
                    label = "${att.displayName} (ገጽ ${pageIdx + 1})",
                    page = page,
                )
            }
        }

        if (allPages.isEmpty()) {
            return@coroutineScope OcrBatchResult(
                combinedText = directParts.joinToString("\n\n---\n\n"),
                totalPages = directParts.size,
                cacheHits = 0,
                elapsedMs = System.currentTimeMillis() - startMs,
            )
        }

        val resolvedByOrder = ConcurrentHashMap<Int, String>()
        val uncachedPages = mutableListOf<IndexedOcrPage>()

        for (item in allPages) {
            val cached = withContext(Dispatchers.IO) { repository.getCachedOcr(item.page.sha256) }
            if (cached != null) {
                cacheHits.incrementAndGet()
                resolvedByOrder[item.order] = "[${item.label}]\n${cached.extractedText}"
            } else {
                uncachedPages.add(item)
            }
        }

        val ocrModelName = prefs.settings.value.ocrModelName.ifBlank { MODEL_LITE }
        val customApiKey = prefs.getCustomApiKey()
        val totalUncached = uncachedPages.size
        val completedCounter = AtomicInteger(0)

        val isCellular = appContext?.let { NetworkUtils.isCellularConnection(it) } == true
        val effectiveBatchSize = if (isCellular) CELLULAR_OCR_BATCH_SIZE else OCR_BATCH_SIZE
        val activeSemaphore = if (isCellular) cellularOcrSemaphore else ocrSemaphore

        val batches = uncachedPages.chunked(effectiveBatchSize)
        val batchJobs = batches.mapIndexed { batchIndex, batch ->
            async(Dispatchers.IO) {
                activeSemaphore.withPermit {
                    val rangeStart = batchIndex * effectiveBatchSize + 1
                    val rangeEnd = rangeStart + batch.size - 1
                    val rangeText = if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart–$rangeEnd"
                    onProgress?.invoke("$sideLabel: ገጽ $rangeText ከ$totalUncached በመነበብ ላይ...")

                    if (batch.size == 1) {
                        val single = batch.first()
                        val text = ocrSinglePageWithFallback(
                            page = single.page,
                            sideLabel = sideLabel,
                            preferredModel = ocrModelName,
                            customApiKey = customApiKey,
                        )
                        resolvedByOrder[single.order] = "[${single.label}]\n$text"
                        val done = completedCounter.incrementAndGet()
                        onProgress?.invoke("$sideLabel: $done / $totalUncached ገጽ ተነቧል")
                    } else {
                        val batchTexts = ocrBatchWithFallback(
                            batch = batch,
                            sideLabel = sideLabel,
                            preferredModel = ocrModelName,
                            customApiKey = customApiKey,
                        )
                        batch.forEachIndexed { idx, item ->
                            val text = batchTexts[idx]
                            resolvedByOrder[item.order] = "[${item.label}]\n$text"
                            val done = completedCounter.incrementAndGet()
                            onProgress?.invoke("$sideLabel: $done / $totalUncached ገጽ ተነቧል")
                        }
                    }
                }
            }
        }
        batchJobs.awaitAll()

        val orderedPageTexts = allPages.mapNotNull { resolvedByOrder[it.order] }
        val combined = (directParts + orderedPageTexts).joinToString("\n\n---\n\n")
        val elapsed = System.currentTimeMillis() - startMs

        OcrBatchResult(
            combinedText = combined,
            totalPages = allPages.size + directParts.size,
            cacheHits = cacheHits.get(),
            elapsedMs = elapsed,
        )
    }

    private suspend fun ocrBatchWithFallback(
        batch: List<IndexedOcrPage>,
        sideLabel: String,
        preferredModel: String,
        customApiKey: String?,
    ): List<String> {
        val batchStart = System.currentTimeMillis()
        val prompt = JudicialPrompts.buildBatchOcrPrompt(
            sideLabel = sideLabel,
            pageLabels = batch.map { it.label },
        )
        val raw = runCatching {
            withTimeout(120_000L) {
                executeVisionWithQuotaFallback(
                    preferredModel = preferredModel,
                    customApiKey = customApiKey,
                    prompt = prompt,
                    pages = batch.map { it.page },
                    labels = batch.map { it.label },
                )
            }
        }.getOrElse { err ->
            if (err is CancellationException && err !is TimeoutCancellationException) throw err
            Log.w(TAG, "Batch OCR failed (${err.javaClass.simpleName}), falling back page-by-page", err)
            return batch.map { item ->
                ocrSinglePageWithFallback(
                    page = item.page,
                    sideLabel = sideLabel,
                    preferredModel = preferredModel,
                    customApiKey = customApiKey,
                )
            }
        }

        val split = splitBatchedOcrResponse(raw, batch.size)
        if (split != null && split.size == batch.size) {
            val perPageElapsed = (System.currentTimeMillis() - batchStart) / batch.size
            batch.forEachIndexed { idx, item ->
                repository.saveOcrCache(
                    sha256 = item.page.sha256,
                    text = split[idx],
                    byteSize = item.page.jpegBytes.size,
                    elapsedMs = perPageElapsed,
                )
            }
            return split
        }

        Log.w(TAG, "Batch OCR response lacked complete === PAGE N === markers; falling back per-page")
        return batch.map { item ->
            ocrSinglePageWithFallback(
                page = item.page,
                sideLabel = sideLabel,
                preferredModel = preferredModel,
                customApiKey = customApiKey,
            )
        }
    }

    private suspend fun ocrSinglePageWithFallback(
        page: ProcessedPageImage,
        sideLabel: String,
        preferredModel: String,
        customApiKey: String?,
    ): String {
        val pageStart = System.currentTimeMillis()
        val prompt = JudicialPrompts.buildOcrPrompt(sideLabel)
        val text = withTimeout(120_000L) {
            executeVisionWithQuotaFallback(
                preferredModel = preferredModel,
                customApiKey = customApiKey,
                prompt = prompt,
                pages = listOf(page),
                labels = emptyList(),
            )
        }
        val elapsed = System.currentTimeMillis() - pageStart
        repository.saveOcrCache(
            sha256 = page.sha256,
            text = text,
            byteSize = page.jpegBytes.size,
            elapsedMs = elapsed,
        )
        return text
    }

    private suspend fun executeVisionWithQuotaFallback(
        preferredModel: String,
        customApiKey: String?,
        prompt: String,
        pages: List<ProcessedPageImage>,
        labels: List<String>,
    ): String {
        return try {
            callVisionModel(preferredModel, customApiKey, prompt, pages, labels)
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val fallback = fallbackModelFor(preferredModel)
            if (fallback != null && isQuotaExceededError(e)) {
                Log.w(TAG, "Quota exceeded on $preferredModel for OCR; retrying with $fallback")
                callVisionModel(fallback, customApiKey, prompt, pages, labels)
            } else {
                throw e
            }
        }
    }

    private suspend fun callVisionModel(
        modelName: String,
        customApiKey: String?,
        prompt: String,
        pages: List<ProcessedPageImage>,
        labels: List<String>,
    ): String = NetworkUtils.withMobileDataRetry(maxAttempts = 2, initialDelayMs = 700L) {
        if (!customApiKey.isNullOrBlank()) {
            callRestVisionPages(
                apiKey = customApiKey,
                modelName = modelName,
                prompt = prompt,
                pages = pages,
                labels = labels,
            )
        } else {
            callFirebaseVisionPages(
                modelName = modelName,
                prompt = prompt,
                pages = pages,
                labels = labels,
            )
        }
    }

    /**
     * Stage 2: Streaming draft generation with Judge Nuh Mohammed's style, uploaded exemplars,
     * and similar archived precedent context.
     * Automatically falls back to gemini-3.5-flash-lite if quota is hit before streaming starts.
     */
    fun streamDraft(
        caseEntity: CaseEntity,
        plaintiffText: String,
        defendantText: String,
        seedTemplate: CaseEntity?,
        fewShotExamples: List<CaseEntity>,
        badReminders: List<CaseEntity>,
        similarReferences: List<com.example.util.ScoredReferenceCase> = emptyList(),
    ): Flow<StreamEvent> = flow {
        val settings = prefs.settings.value
        val customApiKey = prefs.getCustomApiKey()
        val draftingModel = settings.draftingModelName.ifBlank { MODEL_FLASH }

        emit(StreamEvent.Stage("ጭብጥ እየተለየ"))

        var unmaskMap: Map<String, String> = emptyMap()
        val (safePlaintiff, safeDefendant, safeCase) = if (settings.piiMaskingEnabled) {
            val knownNames = listOf(
                caseEntity.plaintiffName,
                caseEntity.defendantName,
                caseEntity.plaintiffCounsel,
                caseEntity.defendantCounsel,
            ).flatMap { it.split("\n", ",", "፤") }
                .map { it.replace(Regex("""^\d+ኛ\.\s*"""), "").trim() }
            val m1 = PiiMasker.mask(plaintiffText, knownNames)
            val m2 = PiiMasker.mask(defendantText, knownNames, m1.replacements)
            unmaskMap = m2.replacements
            Triple(
                m1.maskedText,
                m2.maskedText,
                caseEntity.copy(
                    plaintiffName = PiiMasker.mask(caseEntity.plaintiffName, knownNames, unmaskMap).maskedText,
                    defendantName = PiiMasker.mask(caseEntity.defendantName, knownNames, unmaskMap).maskedText,
                )
            )
        } else {
            Triple(plaintiffText, defendantText, caseEntity)
        }

        val prompt = JudicialPrompts.buildDraftingPrompt(
            caseEntity = safeCase,
            plaintiffText = safePlaintiff,
            defendantText = safeDefendant,
            seedTemplate = seedTemplate,
            fewShotExamples = fewShotExamples,
            badFeedbackReminders = badReminders,
            similarReferences = similarReferences,
            pinnedKeys = settings.pinnedStyleKeys,
            subjectTags = settings.subjectTags,
        )

        emit(StreamEvent.Stage("ረቂቅ እየተጻፈ"))
        val rawBuffer = StringBuilder()

        suspend fun streamWithModel(targetModel: String) {
            var lastTokenEmitMs = 0L
            withTimeout(150_000L) {
                if (!customApiKey.isNullOrBlank()) {
                    streamRestText(
                        apiKey = customApiKey,
                        modelName = targetModel,
                        systemInstruction = JudicialPrompts.SYSTEM_INSTRUCTION,
                        userPrompt = prompt,
                    ).collect { delta ->
                        rawBuffer.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastTokenEmitMs >= 90L) {
                            lastTokenEmitMs = now
                            val unmasked = PiiMasker.unmask(rawBuffer.toString(), unmaskMap)
                            emit(StreamEvent.Token(unmasked, parseSections(unmasked)))
                        }
                    }
                } else {
                    val model = getOrCreateModel(
                        modelName = targetModel,
                        systemInstructionText = JudicialPrompts.SYSTEM_INSTRUCTION,
                    )
                    model.generateContentStream(prompt).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            rawBuffer.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastTokenEmitMs >= 90L) {
                                lastTokenEmitMs = now
                                val unmasked = PiiMasker.unmask(rawBuffer.toString(), unmaskMap)
                                emit(StreamEvent.Token(unmasked, parseSections(unmasked)))
                            }
                        }
                    }
                }
            }
        }

        try {
            try {
                streamWithModel(draftingModel)
            } catch (e: Throwable) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (rawBuffer.isEmpty() && NetworkUtils.isTransientNetworkFailure(e)) {
                    Log.w(TAG, "Transient mobile/network error before first draft token; retrying once", e)
                    kotlinx.coroutines.delay(750L)
                    streamWithModel(draftingModel)
                } else {
                    throw e
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val fallback = fallbackModelFor(draftingModel)
            if (rawBuffer.isEmpty() && fallback != null && isQuotaExceededError(e)) {
                Log.w(TAG, "Quota exceeded on $draftingModel during draft; switching to $fallback")
                emit(StreamEvent.Stage("ወደ ፈጣን ሞዴል ($fallback) በመቀየር ላይ"))
                streamWithModel(fallback)
            } else {
                throw e
            }
        }

        val finalRaw = PiiMasker.unmask(rawBuffer.toString(), unmaskMap)
        if (finalRaw.isBlank()) {
            throw IllegalStateException("AI ባዶ ምላሽ መልሷል። እባክዎ ደግመው ይሞክሩ።")
        }
        emit(StreamEvent.Completed(finalRaw, parseSections(finalRaw)))
    }.flowOn(Dispatchers.IO)

    /**
     * Conversational streaming reply for greetings ("hi", "ሰላም"), legal Q&A, or general chat
     * when no specific section rewrite is requested. Always prefers gemini-3.5-flash-lite for instant speed.
     */
    fun streamConversationalReply(
        caseEntity: CaseEntity,
        userMessage: String,
        recentHistorySummary: String,
        similarReferences: List<com.example.util.ScoredReferenceCase> = emptyList(),
    ): Flow<ChatStreamEvent> = flow {
        val customApiKey = prefs.getCustomApiKey()
        val prompt = JudicialPrompts.buildConversationalChatPrompt(
            caseEntity = caseEntity,
            userMessage = userMessage,
            recentHistorySummary = recentHistorySummary,
            similarReferences = similarReferences,
        )
        val buffer = StringBuilder()

        suspend fun streamChat(modelName: String) {
            var lastDeltaEmitMs = 0L
            withTimeout(90_000L) {
                if (!customApiKey.isNullOrBlank()) {
                    streamRestText(
                        apiKey = customApiKey,
                        modelName = modelName,
                        systemInstruction = JudicialPrompts.CONVERSATIONAL_CHAT_INSTRUCTION,
                        userPrompt = prompt,
                    ).collect { delta ->
                        buffer.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastDeltaEmitMs >= 70L) {
                            lastDeltaEmitMs = now
                            emit(ChatStreamEvent.Delta(buffer.toString()))
                        }
                    }
                } else {
                    val model = getOrCreateModel(
                        modelName = modelName,
                        systemInstructionText = JudicialPrompts.CONVERSATIONAL_CHAT_INSTRUCTION,
                    )
                    model.generateContentStream(prompt).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            buffer.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastDeltaEmitMs >= 70L) {
                                lastDeltaEmitMs = now
                                emit(ChatStreamEvent.Delta(buffer.toString()))
                            }
                        }
                    }
                }
            }
        }

        var usedModel = MODEL_LITE
        try {
            try {
                streamChat(MODEL_LITE)
            } catch (e: Throwable) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (buffer.isEmpty() && NetworkUtils.isTransientNetworkFailure(e)) {
                    Log.w(TAG, "Transient mobile/network error on chat; retrying once", e)
                    kotlinx.coroutines.delay(650L)
                    streamChat(MODEL_LITE)
                } else {
                    throw e
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            if (buffer.isEmpty() && (isQuotaExceededError(e) || e is TimeoutCancellationException)) {
                Log.w(TAG, "Conversational chat on $MODEL_LITE failed, trying $MODEL_FLASH", e)
                usedModel = MODEL_FLASH
                streamChat(MODEL_FLASH)
            } else {
                throw e
            }
        }

        val finalReply = buffer.toString().trim()
        if (finalReply.isBlank()) {
            throw IllegalStateException("AI ባዶ ምላሽ መልሷል። እባክዎ ደግመው ይሞክሩ።")
        }
        emit(ChatStreamEvent.Completed(finalReply, usedModel))
    }.flowOn(Dispatchers.IO)

    /**
     * Targeted section revision via chat with structured JSON response for clean diffing.
     * Automatically falls back to gemini-3.5-flash-lite if quota is exceeded.
     */
    suspend fun reviseSection(
        caseEntity: CaseEntity,
        targetSection: DraftSection,
        userRequest: String,
        recentHistorySummary: String,
        similarReferences: List<com.example.util.ScoredReferenceCase> = emptyList(),
    ): RevisionProposal = withContext(Dispatchers.IO) {
        val settings = prefs.settings.value
        val customApiKey = prefs.getCustomApiKey()
        val preferredModel = settings.draftingModelName.ifBlank { MODEL_FLASH }

        val prompt = JudicialPrompts.buildRevisionPrompt(
            caseEntity = caseEntity,
            targetSection = targetSection,
            userRequest = userRequest,
            recentHistorySummary = recentHistorySummary,
            similarReferences = similarReferences,
        )

        suspend fun requestRevision(modelName: String): String = NetworkUtils.withMobileDataRetry(
            maxAttempts = 2,
            initialDelayMs = 650L,
        ) {
            withTimeout(90_000L) {
                if (!customApiKey.isNullOrBlank()) {
                    callRestStructuredJson(
                        apiKey = customApiKey,
                        modelName = modelName,
                        systemInstruction = JudicialPrompts.SYSTEM_INSTRUCTION,
                        userPrompt = prompt,
                    )
                } else {
                    val revisionSchema = Schema.obj(
                        properties = mapOf(
                            "revisedText" to Schema.string("The updated Amharic legal text for ONLY the requested section"),
                            "explanation" to Schema.string("Brief Amharic explanation of what was changed"),
                            "consistencyWarning" to Schema.string("Any legal consistency note across issues/analysis/decision, or empty string if consistent"),
                        ),
                    )
                    val model = getOrCreateModel(
                        modelName = modelName,
                        systemInstructionText = JudicialPrompts.SYSTEM_INSTRUCTION,
                        responseSchema = revisionSchema,
                        responseMimeType = "application/json",
                    )
                    model.generateContent(prompt).text
                        ?: throw IllegalStateException("AI ባዶ ምላሽ መልሷል።")
                }
            }
        }

        var usedFallback = false
        val jsonText = try {
            requestRevision(preferredModel)
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val fallback = fallbackModelFor(preferredModel)
            if (fallback != null && isQuotaExceededError(e)) {
                Log.w(TAG, "Quota exceeded on $preferredModel during revision; falling back to $fallback")
                usedFallback = true
                requestRevision(fallback)
            } else {
                throw e
            }
        }

        val cleaned = jsonText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = JSONObject(cleaned)
        RevisionProposal(
            targetSection = targetSection,
            revisedText = json.optString("revisedText", ""),
            explanation = json.optString("explanation", "ማሻሻያው ተዘጋጅቷል።"),
            consistencyWarning = json.optString("consistencyWarning", ""),
            usedFallbackModel = usedFallback,
        )
    }

    suspend fun summarizeChatHistory(messagesText: String): String = withContext(Dispatchers.IO) {
        val settings = prefs.settings.value
        val customApiKey = prefs.getCustomApiKey()
        val ocrModel = settings.ocrModelName.ifBlank { MODEL_LITE }
        val prompt = JudicialPrompts.buildChatSummaryPrompt(messagesText)
        NetworkUtils.withMobileDataRetry(maxAttempts = 2, initialDelayMs = 600L) {
            withTimeout(45_000L) {
                if (!customApiKey.isNullOrBlank()) {
                    callRestText(customApiKey, ocrModel, prompt)
                } else {
                    val model = getOrCreateModel(modelName = ocrModel)
                    model.generateContent(prompt).text.orEmpty()
                }
            }
        }
    }

    /**
     * Uses Firebase AI SDK's inlineData + streaming to avoid Bitmap decode/re-encode CPU & memory bloat
     * and to start receiving tokens quickly before socket read stalls.
     */
    private suspend fun callFirebaseVisionPages(
        modelName: String,
        prompt: String,
        pages: List<ProcessedPageImage>,
        labels: List<String>,
    ): String {
        val model = getOrCreateModel(modelName = modelName)
        val inputContent = content {
            pages.forEachIndexed { idx, page ->
                if (labels.size == pages.size && pages.size > 1) {
                    text("[ገጽ ${idx + 1}: ${labels[idx]}]")
                }
                inlineData(page.jpegBytes, "image/jpeg")
            }
            text(prompt)
        }
        val sb = StringBuilder()
        model.generateContentStream(inputContent).collect { chunk ->
            val delta = chunk.text.orEmpty()
            if (delta.isNotEmpty()) sb.append(delta)
        }
        val text = sb.toString().trim()
        if (text.isBlank()) {
            throw IllegalStateException("ከምስሉ ጽሁፍ ማንበብ አልተቻለም። እባክዎ የሰነዱን ጥራት ያረጋግጡ።")
        }
        return text
    }

    private fun callRestVisionPages(
        apiKey: String,
        modelName: String,
        prompt: String,
        pages: List<ProcessedPageImage>,
        labels: List<String>,
    ): String {
        val partsArray = JSONArray()
        pages.forEachIndexed { idx, page ->
            if (labels.size == pages.size && pages.size > 1) {
                partsArray.put(JSONObject().put("text", "[ገጽ ${idx + 1}: ${labels[idx]}]"))
            }
            val b64 = Base64.encodeToString(page.jpegBytes, Base64.NO_WRAP)
            partsArray.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", b64)
                )
            )
        }
        partsArray.put(JSONObject().put("text", prompt))

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", partsArray))
        )
        return executeRestGenerateContent(apiKey, modelName, payload)
    }

    private fun callRestText(apiKey: String, modelName: String, prompt: String): String {
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
        }
        return executeRestGenerateContent(apiKey, modelName, payload)
    }

    private fun callRestStructuredJson(
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        userPrompt: String,
    ): String {
        val schemaJson = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject()
                    .put("revisedText", JSONObject().put("type", "STRING"))
                    .put("explanation", JSONObject().put("type", "STRING"))
                    .put("consistencyWarning", JSONObject().put("type", "STRING"))
            )
            put("required", JSONArray().put("revisedText").put("explanation").put("consistencyWarning"))
        }
        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userPrompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schemaJson)
            )
        }
        return executeRestGenerateContent(apiKey, modelName, payload)
    }

    private fun executeRestGenerateContent(
        apiKey: String,
        modelName: String,
        payload: JSONObject,
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val respText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "REST API error status=${response.code} body=${respText.take(300)}")
                throw RestHttpException(response.code, respText)
            }
            val root = JSONObject(respText)
            return extractTextFromCandidate(root)
        }
    }

    private fun streamRestText(
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        userPrompt: String,
    ): Flow<String> = flow {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse"
        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userPrompt))
                    )
                )
            )
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                Log.e(TAG, "REST Stream error status=${response.code} body=${errBody.take(300)}")
                throw RestHttpException(response.code, errBody)
            }
            val stream = response.body?.byteStream() ?: return@use
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("data:")) {
                        val jsonStr = line.removePrefix("data:").trim()
                        if (jsonStr.isNotEmpty() && jsonStr != "[DONE]") {
                            val chunkObj = runCatching { JSONObject(jsonStr) }.getOrNull()
                            if (chunkObj != null) {
                                val text = extractTextFromCandidate(chunkObj)
                                if (text.isNotEmpty()) emit(text)
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun extractTextFromCandidate(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString()
    }

    companion object {
        private val GREETING_SET = setOf(
            "hi", "hello", "hey", "selam", "salam", "peace",
            "ሰላም", "ሰላምታ", "ታዲያስ", "እንዴት", "እንደምን", "ጤና ይስጥልኝ",
            "አመሰግናለሁ", "thanks", "thank you", "ok", "okay", "እሺ",
        )

        private val REVISION_KEYWORDS = listOf(
            "አሻሽል", "አሻሽለው", "አሻሽለህ", "ቀይር", "ቀይረው", "ለውጥ",
            "ጨምር", "ጨምርበት", "ጨምረው", "ጨምረህ", "አክል",
            "ቀንስ", "ቀንስበት", "ቀንሰው", "ቀንሰህ", "አስወግድ", "አንሳ",
            "አጥፋ", "አጥፋው", "ደምስስ", "አስተካክል", "አስተካክለው", "አርም",
            "አሳጥር", "አጠናክር", "ጻፍ", "አስገባ", "አውጣ", "ተካ", "ተካው", "ገምግም",
            "revise", "edit", "change", "add", "insert", "expand",
            "remove", "delete", "trim", "shorten", "update", "rewrite", "strengthen", "modify",
        )

        fun fallbackModelFor(modelName: String): String? = when (modelName.trim().lowercase()) {
            MODEL_FLASH, MODEL_PRO -> MODEL_LITE
            else -> null
        }

        fun isConversationalMessage(message: String, hasExistingDraft: Boolean): Boolean {
            if (!hasExistingDraft) return true
            val normalized = message.trim().lowercase()
                .replace(Regex("""[!?.,።፣፤]+$"""), "")
                .trim()
            if (normalized.isEmpty()) return true
            if (GREETING_SET.any { normalized == it || normalized.startsWith("$it ") }) {
                return true
            }
            val hasRevisionVerb = REVISION_KEYWORDS.any { normalized.contains(it) }
            if (!hasRevisionVerb && (message.contains('?') || message.contains('፧') ||
                    normalized.contains("ምን ይላል") || normalized.contains("ስንት") || normalized.contains("አብራራ"))
            ) {
                return true
            }
            return false
        }

        fun splitBatchedOcrResponse(rawText: String, expectedCount: Int): List<String>? {
            if (expectedCount <= 0) return emptyList()
            val results = mutableListOf<String>()
            for (pageNum in 1..expectedCount) {
                val markerRegex = Regex("""={2,}\s*PAGE\s*$pageNum\s*={2,}""", RegexOption.IGNORE_CASE)
                val match = markerRegex.find(rawText) ?: return null
                val contentStart = match.range.last + 1
                val nextMatch = if (pageNum < expectedCount) {
                    val nextRegex = Regex("""={2,}\s*PAGE\s*${pageNum + 1}\s*={2,}""", RegexOption.IGNORE_CASE)
                    nextRegex.find(rawText, contentStart)
                } else null
                val contentEnd = nextMatch?.range?.first ?: rawText.length
                val pageText = rawText.substring(contentStart, contentEnd).trim()
                if (pageText.isBlank()) return null
                results.add(pageText)
            }
            return results
        }

        fun isQuotaExceededError(e: Throwable): Boolean {
            var cur: Throwable? = e
            var depth = 0
            while (cur != null && depth < 8) {
                if (cur is QuotaExceededException) return true
                if (cur is RestHttpException && cur.statusCode == 429) return true
                val msg = cur.message.orEmpty()
                if (msg.contains("429") ||
                    msg.contains("quota", ignoreCase = true) ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    msg.contains("rate-limit", ignoreCase = true) ||
                    msg.contains("rate limit", ignoreCase = true)
                ) {
                    return true
                }
                cur = cur.cause
                depth++
            }
            return false
        }

        private fun hasCause(e: Throwable, predicate: (Throwable) -> Boolean): Boolean {
            var cur: Throwable? = e
            var depth = 0
            while (cur != null && depth < 8) {
                if (predicate(cur)) return true
                cur = cur.cause
                depth++
            }
            return false
        }

        private fun extractRetryHint(e: Throwable): String? {
            var cur: Throwable? = e
            var depth = 0
            val regex = Regex("""retry in\s+(\d+h\d+m|\d+m\d+s|\d+s|\d+(?:\.\d+)?s)""", RegexOption.IGNORE_CASE)
            while (cur != null && depth < 8) {
                val m = regex.find(cur.message.orEmpty())
                if (m != null) return m.groupValues[1]
                cur = cur.cause
                depth++
            }
            return null
        }

        fun friendlyError(e: Throwable): String {
            Log.e(TAG, "AI operation failed", e)

            if (e is CancellationException && e !is TimeoutCancellationException) {
                throw e
            }

            if (hasCause(e) {
                    it is TimeoutCancellationException ||
                        it is RequestTimeoutException ||
                        it is SocketTimeoutException ||
                        it.message.orEmpty().contains("timeout", ignoreCase = true)
                }
            ) {
                return "የAI ምላሽ ጊዜው አልፏል። ኢንተርኔቱ ዝግተኛ ሊሆን ይችላል፤ እባክዎ ጥቂት ፎቶዎችን በአንድ ጊዜ ይሞክሩ ወይም ደግመው ይጫኑ።"
            }

            if (isQuotaExceededError(e)) {
                val retryHint = extractRetryHint(e)
                val waitNote = if (retryHint != null) " ($retryHint በኋላ ይታደሳል)" else ""
                return "የነጻ AI ዕለታዊ የጥያቄ ገደብ ተሟጥጧል$waitNote። በላይኛው የቁልፍ (Settings) አዶ ውስጥ ሞዴሉን ወደ 'gemini-3.5-flash-lite' ይቀይሩ ወይም የራስዎን Gemini API ቁልፍ ያስገቡ።"
            }

            if (hasCause(e) {
                    it is InvalidAPIKeyException ||
                        (it is RestHttpException && (it.statusCode == 401 || it.statusCode == 403)) ||
                        it.message.orEmpty().contains("API key", ignoreCase = true)
                }
            ) {
                return "የAPI ቁልፍ ወይም ፍቃድ ትክክል አይደለም። እባክዎ በላይኛው የቁልፍ (Settings) አዶ ውስጥ ያረጋግጡ።"
            }

            if (hasCause(e) { it is ServiceDisabledException }) {
                return "የFirebase AI አገልግሎት ገና አልነቃም። እባክዎ የFirebase ቅንብርን ወይም የግል API ቁልፍን ይጠቀሙ።"
            }

            if (hasCause(e) { it is ContentBlockedException || it is PromptBlockedException }) {
                return "ጥያቄው በደህንነት መመሪያ ምክንያት ተገድቧል። እባክዎ ቃላቱን አስተካክለው ደግመው ይሞክሩ።"
            }

            if (hasCause(e) {
                    it is UnknownHostException ||
                        it is ConnectException ||
                        it is SSLException ||
                        it is SocketException ||
                        it is EOFException ||
                        it.message.orEmpty().contains("unexpected end of stream", ignoreCase = true) ||
                        it.message.orEmpty().contains("connection reset", ignoreCase = true) ||
                        it.message.orEmpty().contains("broken pipe", ignoreCase = true) ||
                        it.message.orEmpty().contains("failed to connect", ignoreCase = true)
                }
            ) {
                return "ከAI ሰርቨር ጋር መገናኘት አልተቻለም። የMobile Data ወይም የኢንተርኔት ግንኙነትዎ መቆራረጡን አረጋግጠው ደግመው ይሞክሩ።"
            }

            if (e is RestHttpException) {
                return when (e.statusCode) {
                    400 -> "የተላከው ጥያቄ ቅርጸት ትክክል አይደለም (400)።"
                    404 -> "የተመረጠው AI ሞዴል ስም አልተገኘም (404)። በSettings ውስጥ ሞዴሉን ወደ 'gemini-3.5-flash-lite' ይቀይሩ።"
                    else -> "የAI አገልግሎት ስህተት ተፈጥሯል (HTTP ${e.statusCode})።"
                }
            }

            val rawMsg = e.localizedMessage?.trim().orEmpty()
            val hasAmharic = rawMsg.any { it in '\u1200'..'\u137F' }
            return if (hasAmharic && rawMsg.length <= 240) {
                rawMsg
            } else {
                "ከAI ጋር መገናኘት አልተቻለም። እባክዎ ኢንተርኔቱን ወይም በSettings ውስጥ ያለውን ሞዴል (gemini-3.5-flash-lite) ያረጋግጡ።"
            }
        }

        fun parseSections(raw: String): ParsedDraftSections {
            val splitMarkers = listOf(
                "[SECTION:ISSUES]",
                "[SECTION:ANALYSIS]",
                "[SECTION:DECISION]",
                "[SECTION:CLOSING]",
            )
            val hasSplitMarkers = splitMarkers.any { raw.contains(it) }
            if (!hasSplitMarkers) {
                val notesTag = "[SECTION:JUDGE_NOTES]"
                val notesIdx = raw.indexOf(notesTag)
                val body = (if (notesIdx != -1) raw.substring(0, notesIdx) else raw)
                    .replace("[SECTION:JUDGMENT]", "")
                    .trim()
                val notes = if (notesIdx != -1) {
                    raw.substring(notesIdx + notesTag.length).trim()
                } else ""
                return ParsedDraftSections(
                    issues = "",
                    analysis = body,
                    decision = "",
                    closing = "",
                    judgeNotes = notes,
                )
            }

            val allMarkers = splitMarkers + "[SECTION:JUDGE_NOTES]"
            fun extractBetween(startMarker: String): String {
                val startIdx = raw.indexOf(startMarker)
                if (startIdx == -1) return ""
                val contentStart = startIdx + startMarker.length
                val nextIndices = allMarkers
                    .filter { it != startMarker }
                    .map { raw.indexOf(it, contentStart) }
                    .filter { it != -1 }
                val endIdx = nextIndices.minOrNull() ?: raw.length
                return raw.substring(contentStart, endIdx).trim()
            }

            return ParsedDraftSections(
                issues = extractBetween("[SECTION:ISSUES]"),
                analysis = extractBetween("[SECTION:ANALYSIS]"),
                decision = extractBetween("[SECTION:DECISION]"),
                closing = extractBetween("[SECTION:CLOSING]"),
                judgeNotes = extractBetween("[SECTION:JUDGE_NOTES]"),
            )
        }
    }
}
