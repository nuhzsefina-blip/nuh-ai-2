package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class GroundingSource(
    val title: String,
    val uri: String,
    val domain: String,
)

data class GroundedSearchResult(
    val query: String,
    val answerText: String,
    val searchEntryPointHtml: String?,
    val sources: List<GroundingSource>,
    val webSearchQueries: List<String>,
)

object FirebaseAiFeatures {
    const val MODEL_LIVE_VOICE = "gemini-3.8-live"
    const val MODEL_GROUNDING_SEARCH = "gemini-4-hydrogen"
    const val MODEL_TRANSCRIBE = "gemini-3.5-transcribe"

    suspend fun searchLegalKnowledgeWithGrounding(
        query: String,
        caseContext: String = "",
    ): GroundedSearchResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        require(trimmedQuery.isNotEmpty()) { "የምርምር ጥያቄው ባዶ መሆን የለበትም።" }

        val systemText = """
            You are an Ethiopian legal research assistant for Judge Nuh Mohammed at the Harari Regional High Court.
            Answer in clear, formal Amharic (or English if the user asks in English), grounded with Google Search results.
            Focus on Ethiopian Federal & Regional laws, Cassation Bench decisions (ሰበር ሰሚ ችሎት), Civil Procedure Code, Civil Code, and verifiable legal principles.
            Be concise, cite specific proclamations or principles returned in search results, and never fabricate citations.
        """.trimIndent()

        val model = Firebase.ai.generativeModel(
            modelName = MODEL_GROUNDING_SEARCH,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(systemText) },
        )

        val fullPrompt = buildString {
            append(trimmedQuery)
            if (caseContext.isNotBlank()) {
                append("\n\nየተያያዘ የመዝገብ አውድ (ለማገናዘብ):\n")
                append(caseContext.take(1500))
            }
        }

        val response = withTimeout(60_000L) {
            model.generateContent(fullPrompt)
        }

        val candidate = response.candidates.firstOrNull()
        val metadata = candidate?.groundingMetadata
        val sources = metadata?.groundingChunks
            ?.mapNotNull { chunk ->
                val web = chunk.web ?: return@mapNotNull null
                val uri = web.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GroundingSource(
                    title = web.title?.takeIf { it.isNotBlank() } ?: web.domain.orEmpty().ifBlank { uri },
                    uri = uri,
                    domain = web.domain.orEmpty(),
                )
            }
            ?.distinctBy { it.uri }
            ?: emptyList()

        val answer = response.text?.trim().orEmpty()
        if (answer.isBlank()) {
            throw IllegalStateException("ከGoogle Search ምርምር ምላሽ ማግኘት አልተቻለም።")
        }

        GroundedSearchResult(
            query = trimmedQuery,
            answerText = answer,
            searchEntryPointHtml = metadata?.searchEntryPoint?.renderedContent,
            sources = sources,
            webSearchQueries = metadata?.webSearchQueries.orEmpty(),
        )
    }

    suspend fun transcribeAudioWithGemini(
        audioBytes: ByteArray,
        mimeType: String = "audio/wav",
        contextHint: String = "",
    ): String = withContext(Dispatchers.IO) {
        require(audioBytes.isNotEmpty()) { "የተቀዳ ድምጽ አልተገኘም።" }

        val model = Firebase.ai.generativeModel(
            modelName = MODEL_TRANSCRIBE,
        )

        val prompt = buildString {
            append("Transcribe the spoken audio accurately verbatim. ")
            append("The speaker is an Ethiopian judge dictating in Amharic (or English legal terms). ")
            append("Return ONLY the clean transcribed text without timestamps, labels, or extra commentary.")
            if (contextHint.isNotBlank()) {
                append(" Case context hint: ").append(contextHint.take(400))
            }
        }

        val input = content {
            inlineData(audioBytes, mimeType)
            text(prompt)
        }

        val response = withTimeout(60_000L) {
            model.generateContent(input)
        }

        val text = response.text?.trim().orEmpty()
        if (text.isBlank()) {
            throw IllegalStateException("በተቀዳው ድምጽ ውስጥ የሚነበብ ንግግር አልተገኘም።")
        }
        text
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun connectLiveJudicialSession(caseSummary: String): LiveSession {
        val systemText = buildString {
            append("You are Judge Nuh Mohammed's real-time judicial voice assistant at the Harari Regional High Court. ")
            append("Respond concisely, respectfully, and accurately in Amharic (or English if addressed in English) ")
            append("to help analyze case issues (ጭብጥ), procedural rules, and drafting edits.\n")
            if (caseSummary.isNotBlank()) {
                append("\nActive case context:\n").append(caseSummary.take(2500))
            }
        }

        val liveModel = Firebase.ai.liveModel(
            modelName = MODEL_LIVE_VOICE,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("AOEDE"))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content { text(systemText) },
        )

        return try {
            withTimeout(25_000L) {
                liveModel.connect()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }
}
