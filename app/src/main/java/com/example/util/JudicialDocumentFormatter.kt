package com.example.util

import com.example.data.local.CaseEntity
import com.example.data.local.CaseType

object JudicialDocumentFormatter {

    fun hasDraftContent(c: CaseEntity): Boolean =
        c.analysisText.isNotBlank() || c.issuesText.isNotBlank() || c.decisionText.isNotBlank()

    /**
     * Returns the full decision/decree as a single unified document matching Judge Nuh's
     * three templates. Handles both newly generated unified drafts (stored in analysisText)
     * and legacy/seed split records seamlessly.
     */
    fun composeUnifiedDocument(c: CaseEntity): String {
        val isAlreadyUnified = c.issuesText.isBlank() &&
            c.decisionText.isBlank() &&
            c.closingText.isBlank()
        if (isAlreadyUnified) {
            return c.analysisText.trim()
        }

        // Also check if analysisText already contains a full header + operative heading
        val normalizedBody = c.analysisText.trim()
        if (normalizedBody.startsWith("የመ/ቁ") ||
            (normalizedBody.contains("ፍ ር ድ") && normalizedBody.contains("ው ሳ ኔ")) ||
            (normalizedBody.contains("ብ ይ ን") && normalizedBody.contains("ት ዕ ዛ ዝ"))
        ) {
            return normalizedBody
        }

        // Legacy/seed split record -> weave into one continuous template document
        val sb = StringBuilder()
        sb.appendLine("የመ/ቁ:- ${c.caseNumber.ifBlank { "____" }}")
        val dateSuffix = if (c.ethiopianDate.contains("ዓ")) "" else " ዓ.ም"
        sb.appendLine("ቀን:- ${c.ethiopianDate.ifBlank { "____" }}$dateSuffix")
        sb.appendLine()

        if (c.caseType == CaseType.APPEAL) {
            sb.appendLine("ዳኞች:-")
            c.judges.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { j ->
                sb.appendLine(j)
            }
            sb.appendLine()
            sb.appendLine("ይግባኝ ባይ፦ ${c.plaintiffName.ifBlank { "____" }}")
            if (c.plaintiffCounsel.isNotBlank()) {
                sb.appendLine("ጠበቃ፡- ${c.plaintiffCounsel}")
            }
            sb.appendLine("መልስ ሰጭ:- ${c.defendantName.ifBlank { "____" }}")
            if (c.defendantCounsel.isNotBlank()) {
                sb.appendLine("ጠበቃ፡- ${c.defendantCounsel}")
            }
        } else {
            val judgeNames = c.judges.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (judgeNames.size > 1) {
                sb.appendLine("ዳኞች:-")
                judgeNames.forEach { sb.appendLine(it) }
            } else {
                sb.appendLine("ዳኛ፦ ${judgeNames.firstOrNull() ?: "ኑህ መሀመድ"}")
            }
            sb.appendLine()
            sb.appendLine("ከሳሽ/ከሳሾች፤ ${c.plaintiffName.ifBlank { "____" }}")
            if (c.plaintiffCounsel.isNotBlank()) {
                sb.appendLine("ጠበቃ፣ ${c.plaintiffCounsel}")
            }
            sb.appendLine("ተከሳሽ/ተከሳሾች፤ ${c.defendantName.ifBlank { "____" }}")
            if (c.defendantCounsel.isNotBlank()) {
                sb.appendLine("ጠበቃ / ነገረፈጅ፤ ${c.defendantCounsel}")
            }
        }

        sb.appendLine()
        val opening = if (c.caseType == CaseType.DECREE) {
            "መዝገቡ የተቀጠረው ለብይን ሲሆን ተመርምሮ የሚከተለው ተበይኗል።"
        } else {
            "መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።"
        }
        sb.appendLine(opening)
        sb.appendLine()

        val bodyHeading = if (c.caseType == CaseType.DECREE) "ብ ይ ን" else "ፍ ር ድ"
        sb.appendLine(bodyHeading)
        sb.appendLine()

        val trimmedIssues = c.issuesText.trim()
        val hasIssuesOutsideBody = trimmedIssues.isNotBlank() &&
            !normalizedBody.contains(trimmedIssues.take(24))

        if (normalizedBody.isNotBlank() && hasIssuesOutsideBody) {
            val transitionIdx = listOf("የግራ ቀኙ", "ፍርድ ቤቱም የያዝነውን", "ፍርድ ቤቱ ጭብጦቹን")
                .map { normalizedBody.indexOf(it) }
                .filter { it > 0 }
                .minOrNull()
            if (transitionIdx != null) {
                val pleadingsPart = normalizedBody.substring(0, transitionIdx).trimEnd()
                val analysisPart = normalizedBody.substring(transitionIdx).trimStart()
                sb.appendLine(pleadingsPart)
                sb.appendLine()
                sb.appendLine("ፍርድ ቤቱም የያዘው ጭብጥ፦")
                sb.appendLine(trimmedIssues)
                sb.appendLine()
                sb.appendLine(analysisPart)
            } else {
                sb.appendLine(normalizedBody)
                sb.appendLine()
                sb.appendLine("ፍርድ ቤቱም የያዘው ጭብጥ፦")
                sb.appendLine(trimmedIssues)
            }
        } else {
            if (normalizedBody.isNotBlank()) {
                sb.appendLine(normalizedBody)
            }
            if (hasIssuesOutsideBody) {
                sb.appendLine("ፍርድ ቤቱም የያዘው ጭብጥ፦")
                sb.appendLine(trimmedIssues)
            }
        }

        val decHeading = if (c.caseType == CaseType.DECREE) "ት ዕ ዛ ዝ" else "ው ሳ ኔ"
        if (c.decisionText.isNotBlank() || c.closingText.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(decHeading)
            sb.appendLine()
            if (c.decisionText.isNotBlank()) {
                sb.appendLine(c.decisionText.trim())
            }
            if (c.closingText.isNotBlank()) {
                sb.appendLine()
                sb.appendLine(c.closingText.trim())
            }
        }
        return sb.toString().trim()
    }

    fun extractPreviewSnippet(c: CaseEntity): String {
        val doc = composeUnifiedDocument(c)
        if (doc.isBlank()) return c.issuesText.trim()
        var firstFallback: String? = null
        var secondFallback: String? = null
        var scanned = 0
        for (rawLine in doc.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (firstFallback == null) firstFallback = line
            else if (secondFallback == null) secondFallback = line

            if (line.startsWith("ለዚህ ውሳኔ") ||
                line.startsWith("ለዚህ ብይን") ||
                (!isUnifiedHeadingLine(line) && !isUnifiedHeaderLabelLine(line) &&
                    !line.startsWith("መዝገቡ የተቀጠረው") && line.length > 25)
            ) {
                return line
            }
            scanned++
            if (scanned >= 40) break
        }
        return c.issuesText.ifBlank {
            listOfNotNull(firstFallback, secondFallback).joinToString(" • ")
        }
    }

    fun isUnifiedHeadingLine(line: String): Boolean {
        val compact = line.replace(Regex("\\s+"), "")
        return compact == "ፍርድ" || compact == "ብይን" || compact == "ውሳኔ" || compact == "ትዕዛዝ"
    }

    fun isUnifiedHeaderLabelLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("የመ/ቁ") ||
            trimmed.startsWith("ቀን:") ||
            trimmed.startsWith("ቀን፦") ||
            trimmed.startsWith("ዳኞች") ||
            trimmed.startsWith("ዳኛ") ||
            trimmed.startsWith("ይግባኝ ባይ") ||
            trimmed.startsWith("መልስ ሰጭ") ||
            trimmed.startsWith("መልስ ሰጪ") ||
            trimmed.startsWith("ከሳሽ") ||
            trimmed.startsWith("ተከሳሽ") ||
            trimmed.startsWith("ጠበቃ")
    }
}
