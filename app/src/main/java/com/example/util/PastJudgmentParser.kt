package com.example.util

import com.example.data.local.CaseEntity
import com.example.data.local.CaseType

data class ParsedPastJudgmentMetadata(
    val caseNumber: String,
    val caseType: CaseType,
    val ethiopianDate: String,
    val gregorianDate: String,
    val judges: String,
    val plaintiffName: String,
    val defendantName: String,
    val plaintiffCounsel: String = "",
    val defendantCounsel: String = "",
)

data class ScoredReferenceCase(
    val caseEntity: CaseEntity,
    val score: Double,
    val matchedKeywords: List<String>,
)

object PastJudgmentParser {

    val SUBJECT_TAGS: List<String> = listOf(
        "ውል",
        "ቤተሰብ",
        "አፈጻጸም",
        "ውርስ",
        "ንብረት",
        "ወንጀል",
        "አስተዳደር",
        "ሌላ",
    )

    private val STOPWORDS = setOf(
        "ነው", "ናቸው", "ነበር", "ነበሩ", "ሲሆን", "ስለሆነ", "ስለሆነም", "በመሆኑ", "በመሆኑም",
        "መሰረት", "መሠረት", "አግባብ", "አይደለም", "ወይስ", "ላይ", "ውስጥ", "ጋር", "እና", "ወይም",
        "እንዲሁም", "ነገር", "ግን", "ብቻ", "በቀን", "ዓ.ም", "ዓ/ም", "ፍርድ", "ቤት", "ፍ/ቤት",
        "ከሳሽ", "ተከሳሽ", "ይግባኝ", "መልስ", "ሰጪ", "ሰጭ", "ባይ", "ዳኛ", "ዳኞች", "የመ/ቁ",
        "መዝገብ", "መዝገቡ", "ውሳኔ", "ብይን", "ትዕዛዝ", "ክስ", "አቤቱታ", "ቀን", "አቶ", "ወ/ሮ",
        "ወ/ሪት", "ብለዋል", "ብሏል", "ሲል", "ሲሉ", "ይህን", "ይህንን", "የሚል", "ተብሎ", "ስለ",
        "በዚህ", "ከዚህ", "ወደ", "እንደ", "እስከ", "ብሎ", "አለ", "የለም", "the", "and", "for",
        "with", "that", "this", "from", "are", "was", "not", "case", "court"
    )

    fun extractFromOcr(plaintiffText: String, defendantText: String): ParsedPastJudgmentMetadata {
        val cleaned = listOf(plaintiffText, defendantText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .replace(Regex("""^\[[^\]]+\]\R?""", RegexOption.MULTILINE), "")
            .replace(Regex("""^===\s*PAGE\s+\d+\s*===\R?""", RegexOption.MULTILINE), "")
            .replace("\n---\n", "\n")
            .trim()
        return parseMetadata(rawText = cleaned, fallbackFileName = "")
    }

    fun parseMetadata(rawText: String, fallbackFileName: String): ParsedPastJudgmentMetadata {
        val text = rawText.trim()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val caseType = detectCaseType(text)
        val caseNumber = extractCaseNumber(text, fallbackFileName)
        val etDate = extractEthiopianDate(text)
            ?: EthiopianCalendarUtil.todayEthiopian().formatNumeric()
        val gregDate = EthiopianCalendarUtil.ethiopianToGregorianString(etDate)

        val judges = extractJudges(lines, caseType)
        val (plaintiff, plaintiffCounsel) = extractPartyAndCounsel(
            lines = lines,
            prefixes = listOf("ይግባኝ ባይ", "አመልካች", "ከሳሽ/ከሳሾች", "ከሳሾች", "ከሳሽ"),
            stopPrefixes = listOf("መልስ ሰጭ", "መልስ ሰጪ", "ተጠሪ", "ተከሳሽ/ተከሳሾች", "ተከሳሾች", "ተከሳሽ"),
        )
        val (defendant, defendantCounsel) = extractPartyAndCounsel(
            lines = lines,
            prefixes = listOf("መልስ ሰጭ", "መልስ ሰጪ", "ተጠሪ", "ተከሳሽ/ተከሳሾች", "ተከሳሾች", "ተከሳሽ"),
            stopPrefixes = listOf("መዝገቡ", "ፍ ር ድ", "ብ ይ ን", "ለዚህ"),
        )

        return ParsedPastJudgmentMetadata(
            caseNumber = caseNumber,
            caseType = caseType,
            ethiopianDate = etDate,
            gregorianDate = gregDate,
            judges = judges,
            plaintiffName = plaintiff,
            defendantName = defendant,
            plaintiffCounsel = plaintiffCounsel,
            defendantCounsel = defendantCounsel,
        )
    }

    fun detectCaseType(text: String): CaseType {
        val normalized = text.replace(Regex("""\s+"""), " ")
        val compact = text.replace(Regex("""[ \t]+"""), "")
        val hasDecreeHeading = compact.contains("ብይን") ||
            normalized.contains("ለብይን") ||
            normalized.contains("ተበይኗል") ||
            compact.contains("ትዕዛዝ")
        val hasAppealMarkers = normalized.contains("ይግባኝ ባይ") ||
            normalized.contains("መልስ ሰጭ") ||
            normalized.contains("መልስ ሰጪ") ||
            normalized.contains("የስር ፍርድ ቤት") ||
            normalized.contains("የስር ፍ/ቤት")

        return when {
            hasAppealMarkers && !hasDecreeHeading -> CaseType.APPEAL
            hasDecreeHeading && !normalized.contains("ለውሳኔ ሲሆን") && !compact.contains("ፍርድ") -> CaseType.DECREE
            hasAppealMarkers -> CaseType.APPEAL
            else -> CaseType.DIRECT_CIVIL
        }
    }

    private fun extractCaseNumber(text: String, fallbackFileName: String): String {
        val regex = Regex("""(?:የመ/ቁ|መ/ቁ|መ\.ቁ|የመዝገብ\s*ቁጥር|መዝገብ\s*ቁ\.?)[:\-፦\s]+([0-9A-Za-z][0-9A-Za-z/\-]*)""")
        val found = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!found.isNullOrBlank()) {
            return found.take(70)
        }
        val baseName = fallbackFileName
            .substringBeforeLast('.')
            .trim()
        if (baseName.isNotBlank()) return baseName.take(70)
        return ""
    }

    private fun extractEthiopianDate(text: String): String? {
        val dateRegex = Regex("""ቀን[:\-፦\s]*(\d{1,2}[/\-]\d{1,2}[/\-]\d{4})""")
        val m = dateRegex.find(text) ?: Regex("""\b(\d{1,2}[/\-]\d{1,2}[/\-]20\d{2})\b""").find(text)
        return m?.groupValues?.getOrNull(1)?.replace("-", "/")?.trim()
    }

    private fun extractJudges(lines: List<String>, caseType: CaseType): String {
        lines.forEachIndexed { idx, line ->
            if (line.startsWith("ዳኞች") || line.startsWith("ዳኛ")) {
                val remainder = line
                    .removePrefix("ዳኞች")
                    .removePrefix("ዳኛ")
                    .trimStart(':', '-', '፦', '፤', '፣', ' ')
                    .trim()
                if (remainder.isNotBlank()) {
                    return remainder.take(280)
                }
                val collected = mutableListOf<String>()
                for (nextIdx in (idx + 1)..minOf(lines.lastIndex, idx + 4)) {
                    val nextLine = lines[nextIdx]
                    if (nextLine.startsWith("ይግባኝ") ||
                        nextLine.startsWith("ከሳሽ") ||
                        nextLine.startsWith("መልስ") ||
                        nextLine.startsWith("ተከሳሽ") ||
                        nextLine.startsWith("መዝገቡ")
                    ) {
                        break
                    }
                    collected.add(nextLine)
                }
                if (collected.isNotEmpty()) {
                    return collected.joinToString("\n").take(280)
                }
            }
        }
        return if (caseType == CaseType.APPEAL) {
            "አብራሂም ረሺድ\nኑህ መሀመድ\nቲማጅ አብዱልቃድር"
        } else {
            "ኑህ መሀመድ"
        }
    }

    private fun extractPartyAndCounsel(
        lines: List<String>,
        prefixes: List<String>,
        stopPrefixes: List<String>,
    ): Pair<String, String> {
        val scanLimit = minOf(lines.size, 45)
        for (i in 0 until scanLimit) {
            val line = lines[i]
            for (prefix in prefixes) {
                if (line.startsWith(prefix)) {
                    val remainder = line.removePrefix(prefix)
                        .trimStart(':', '-', '፦', '፤', '፣', ' ')
                        .trim()
                    if (remainder.isNotBlank()) {
                        var counsel = ""
                        for (j in (i + 1)..minOf(lines.lastIndex, i + 2)) {
                            val nextLine = lines[j]
                            if (stopPrefixes.any { nextLine.startsWith(it) }) break
                            if (nextLine.startsWith("ጠበቃ") || nextLine.startsWith("ነገረፈጅ")) {
                                counsel = nextLine
                                    .removePrefix("ጠበቃ / ነገረፈጅ")
                                    .removePrefix("ጠበቃ")
                                    .removePrefix("ነገረፈጅ")
                                    .trimStart(':', '-', '፦', '፤', '፣', '/', ' ')
                                    .trim()
                                    .take(200)
                                break
                            }
                        }
                        return remainder.take(240) to counsel
                    }
                }
            }
        }
        return "" to ""
    }

    fun tokenizeKeywords(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        return text
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}/\-]+"""), " ")
            .split(Regex("""\s+"""))
            .asSequence()
            .map { it.trim('/', '-', ' ') }
            .filter { it.length >= 3 && it !in STOPWORDS }
            .take(180)
            .toSet()
    }

    fun rankSimilarCases(
        candidates: List<CaseEntity>,
        queryText: String,
        targetCaseType: CaseType,
        excludeIds: Set<Long> = emptySet(),
        pinnedKeys: Set<String> = emptySet(),
        subjectTags: Map<String, String> = emptyMap(),
        targetSubjectTag: String = "",
    ): List<ScoredReferenceCase> {
        val queryTokens = tokenizeKeywords(queryText)
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .asSequence()
            .filter { it.id !in excludeIds }
            .mapNotNull { candidate ->
                val docText = buildString {
                    append(candidate.caseNumber).append(' ')
                    append(candidate.issuesText).append(' ')
                    append(candidate.analysisText).append(' ')
                    append(candidate.decisionText)
                }
                if (docText.isBlank()) return@mapNotNull null

                val candTokens = tokenizeKeywords(docText)
                val matched = if (queryTokens.isNotEmpty()) {
                    queryTokens.intersect(candTokens).toList()
                } else {
                    emptyList()
                }

                // Require at least 2 meaningful shared keywords if a query exists
                if (queryTokens.isNotEmpty() && matched.size < 2) {
                    return@mapNotNull null
                }

                val overlapRatio = if (queryTokens.isNotEmpty()) {
                    matched.size.toDouble() / queryTokens.size.coerceAtMost(40).toDouble()
                } else {
                    0.0
                }
                val typeBonus = if (candidate.caseType == targetCaseType) 0.25 else 0.0
                val uploadedBonus = if (isUploadedReferenceCase(candidate)) 0.20 else 0.0
                val pinnedBonus = if (matchesAnyKey(pinnedKeys, candidate)) 0.65 else 0.0
                val candTag = resolveMapValue(subjectTags, candidate)
                val subjectBonus = if (targetSubjectTag.isNotBlank() && candTag == targetSubjectTag) 0.90 else 0.0
                val score = matched.size.toDouble() + overlapRatio * 5.0 + typeBonus + uploadedBonus + pinnedBonus + subjectBonus

                ScoredReferenceCase(
                    caseEntity = candidate,
                    score = score,
                    matchedKeywords = matched.take(8),
                )
            }
            .sortedByDescending { it.score }
            .toList()
    }

    fun caseKeys(caseEntity: CaseEntity): List<String> = buildList {
        if (caseEntity.id > 0L) add("id:${caseEntity.id}")
        if (caseEntity.contentSha256.isNotBlank()) add("sha:${caseEntity.contentSha256}")
        if (caseEntity.caseNumber.isNotBlank()) add("num:${caseEntity.caseType.name}:${caseEntity.caseNumber.trim()}")
    }

    fun primaryCaseKey(caseEntity: CaseEntity): String =
        caseKeys(caseEntity).firstOrNull().orEmpty()

    fun matchesAnyKey(keys: Set<String>, caseEntity: CaseEntity): Boolean =
        caseKeys(caseEntity).any { it in keys }

    fun resolveMapValue(map: Map<String, String>, caseEntity: CaseEntity): String? =
        caseKeys(caseEntity).firstNotNullOfOrNull { map[it] }

    fun isUploadedReferenceCase(caseEntity: CaseEntity): Boolean =
        caseEntity.specialInstructions.startsWith(UPLOADED_REF_PREFIX) ||
            caseEntity.judgeNotesText.contains(UPLOADED_REF_NOTE_MARKER)

    const val UPLOADED_REF_PREFIX = "[UPLOADED_REFERENCE]"
    const val UPLOADED_REF_NOTE_MARKER = "[የተጫነ የቀድሞ ውሳኔ"
}
