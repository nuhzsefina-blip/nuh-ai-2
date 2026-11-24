package com.example

import com.example.ai.GeminiJudicialService
import com.example.security.PiiMasker
import com.example.util.CryptoUtil
import com.example.util.DiffType
import com.example.util.EthiopianCalendarUtil
import com.example.util.TextDiffUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedatDagnaUnitTest {
    @Test
    fun ethiopianCalendar_conversionRoundTrip_isAccurate() {
        // 17/09/2018 E.C. (Genbot 17, 2018) -> 25 May 2026
        val (gy, gm, gd) = EthiopianCalendarUtil.ethiopianToGregorian(2018, 9, 17)
        assertEquals(2026, gy)
        assertEquals(5, gm)
        assertEquals(25, gd)

        val back = EthiopianCalendarUtil.gregorianToEthiopian(gy, gm, gd)
        assertEquals(2018, back.year)
        assertEquals(9, back.month)
        assertEquals(17, back.day)
    }

    @Test
    fun piiMasker_masksAndRestoresEthiopianPhoneIdsAndNames() {
        val raw = "ከሳሽ አቶ መሐመድ ሃጂ ስልክ 0911223344 መታወቂያ ID: HR-998812 ሂሳብ 1000234567890"
        val masked = PiiMasker.mask(raw, knownPartyNames = listOf("መሐመድ ሃጂ"))

        assertTrue(!masked.maskedText.contains("0911223344"))
        assertTrue(!masked.maskedText.contains("መሐመድ ሃጂ"))
        assertTrue(!masked.maskedText.contains("1000234567890"))

        val restored = PiiMasker.unmask(masked.maskedText, masked.replacements)
        assertEquals(raw, restored)
    }

    @Test
    fun sectionParser_splitsFiveJudicialSectionsCleanly() {
        val raw = """
            [SECTION:ISSUES]
            የስር ፍ/ቤት ውሳኔ አግባብ ነው ወይስ አይደለም?
            [SECTION:ANALYSIS]
            በፍ/ብ/ሥ/ሥ/ሕ/ቁ 381 መሰረት ተመርምሯል።
            [SECTION:DECISION]
            ብይኑ ተሽሯል።
            [SECTION:CLOSING]
            መዝገቡ ተዘግቷል።
            [SECTION:JUDGE_NOTES]
            [ለማረጋገጥ: አንቀጽ?]
        """.trimIndent()

        val parsed = GeminiJudicialService.parseSections(raw)
        assertTrue(parsed.issues.contains("አግባብ ነው ወይስ አይደለም?"))
        assertTrue(parsed.analysis.contains("381"))
        assertTrue(parsed.decision.contains("ተሽሯል"))
        assertTrue(parsed.closing.contains("ተዘግቷል"))
        assertTrue(parsed.judgeNotes.contains("ለማረጋገጥ"))
    }

    @Test
    fun textDiffUtil_detectsAddedAndRemovedLines() {
        val orig = "መስመር አንድ\nመስመር ሁለት"
        val rev = "መስመር አንድ\nመስመር ሁለት ተሻሽሏል\nመስመር ሶስት"
        val diff = TextDiffUtil.computeLineDiff(orig, rev)

        assertTrue(diff.any { it.type == DiffType.UNCHANGED && it.text == "መስመር አንድ" })
        assertTrue(diff.any { it.type == DiffType.REMOVED && it.text == "መስመር ሁለት" })
        assertTrue(diff.any { it.type == DiffType.ADDED && it.text == "መስመር ሶስት" })
    }

    @Test
    fun aesGcmEncryption_roundTripAndSha256() {
        val key = ByteArray(32) { it.toByte() }
        val payload = "የሐረሪ ክልል ከፍተኛ ፍርድ ቤት መ/ቁ 31513".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoUtil.encryptAesGcm(payload, key)
        val decrypted = CryptoUtil.decryptAesGcm(encrypted, key)

        assertEquals(String(payload, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
        assertNotEquals(CryptoUtil.sha256Hex("a"), CryptoUtil.sha256Hex("b"))
    }

    @Test
    fun chatIntentClassifier_distinguishesGreetingsQuestionsAndRevisions() {
        assertTrue(GeminiJudicialService.isConversationalMessage("hi", hasExistingDraft = false))
        assertTrue(GeminiJudicialService.isConversationalMessage("hi", hasExistingDraft = true))
        assertTrue(GeminiJudicialService.isConversationalMessage("ሰላም", hasExistingDraft = true))
        assertTrue(
            GeminiJudicialService.isConversationalMessage(
                "የፍ/ሥ/ሥ ሕግ አንቀጽ 244 ምን ይላል?",
                hasExistingDraft = true
            )
        )
        assertTrue(
            !GeminiJudicialService.isConversationalMessage(
                "ሁለተኛውን ጭብጥ አጠናክር",
                hasExistingDraft = true
            )
        )
        assertTrue(
            GeminiJudicialService.isConversationalMessage(
                "ሁለተኛውን ጭብጥ አጠናክር",
                hasExistingDraft = false
            )
        )
    }

    @Test
    fun batchOcrSplitter_parsesValidMarkersAndRejectsIncomplete() {
        val good = """
            === PAGE 1 ===
            የከሳሽ ክስ ገጽ አንድ።
            === PAGE 2 ===
            የከሳሽ ክስ ገጽ ሁለት።
            === PAGE 3 ===
            የከሳሽ ክስ ገጽ ሶስት።
        """.trimIndent()
        val split = GeminiJudicialService.splitBatchedOcrResponse(good, 3)
        assertEquals(3, split?.size)
        assertEquals("የከሳሽ ክስ ገጽ አንድ።", split?.get(0))
        assertEquals("የከሳሽ ክስ ገጽ ሁለት።", split?.get(1))
        assertEquals("የከሳሽ ክስ ገጽ ሶስት።", split?.get(2))

        val missingPage2 = """
            === PAGE 1 ===
            ጽሁፍ ብቻ።
        """.trimIndent()
        assertEquals(null, GeminiJudicialService.splitBatchedOcrResponse(missingPage2, 2))
    }

    @Test
    fun quotaAndFallbackHelpers_mapModelsAndDetect429() {
        assertEquals("gemini-3.5-flash-lite", GeminiJudicialService.fallbackModelFor("gemini-3.8-flash"))
        assertEquals("gemini-3.5-flash-lite", GeminiJudicialService.fallbackModelFor("gemini-4-argon"))
        assertEquals(null, GeminiJudicialService.fallbackModelFor("gemini-3.5-flash-lite"))

        assertTrue(
            GeminiJudicialService.isQuotaExceededError(
                RuntimeException("Quota exceeded for metric ... limit: 20, Please retry in 12h13m39s")
            )
        )
        assertTrue(GeminiJudicialService.isQuotaExceededError(IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED")))
    }

    @Test
    fun unifiedParser_preservesFullTemplateAndExtractsJudgeNotes() {
        val raw = """
            የመ/ቁ:- 31513
            ቀን:- 17/09/2018 ዓ.ም

            ዳኞች:-
            አብራሂም ረሺድ
            ኑህ መሀመድ
            ቲማጅ አብዱልቃድር

            መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።

            ፍ ር ድ

            ግራ ቀኙ ያቀረቡት ክርክር ተመርምሯል።

            ው ሳ ኔ

            ይግባኙ ውድቅ ተደርጓል።

            [SECTION:JUDGE_NOTES]
            [ለማረጋገጥ: አንቀጽ?]
        """.trimIndent()

        val parsed = GeminiJudicialService.parseSections(raw)
        assertEquals("", parsed.issues)
        assertEquals("", parsed.decision)
        assertEquals("", parsed.closing)
        assertTrue(parsed.analysis.contains("የመ/ቁ:- 31513"))
        assertTrue(parsed.analysis.contains("ፍ ር ድ"))
        assertTrue(parsed.analysis.contains("ው ሳ ኔ"))
        assertTrue(!parsed.analysis.contains("[SECTION:JUDGE_NOTES]"))
        assertTrue(parsed.judgeNotes.contains("ለማረጋገጥ"))
    }

    @Test
    fun judicialDocumentFormatter_handlesUnifiedAndLegacyRecords() {
        val unifiedCase = com.example.data.local.CaseEntity(
            caseNumber = "44100",
            caseType = com.example.data.local.CaseType.DECREE,
            analysisText = "የመ/ቁ:- 44100\n\nብ ይ ን\n\nተመርምሯል።\n\nት ዕ ዛ ዝ\n\nመዝገቡ ተቀጥሯል።",
        )
        assertTrue(com.example.util.JudicialDocumentFormatter.hasDraftContent(unifiedCase))
        val doc = com.example.util.JudicialDocumentFormatter.composeUnifiedDocument(unifiedCase)
        assertTrue(doc.startsWith("የመ/ቁ:- 44100"))
        assertTrue(doc.contains("ብ ይ ን"))
        assertTrue(doc.contains("ት ዕ ዛ ዝ"))

        val legacyCase = com.example.data.local.CaseEntity(
            caseNumber = "29001",
            caseType = com.example.data.local.CaseType.DIRECT_CIVIL,
            ethiopianDate = "10/08/2018",
            judges = "ኑህ መሀመድ",
            plaintiffName = "አቶ አበበ",
            defendantName = "አቶ ከበደ",
            issuesText = "ውሉ ህጋዊ ነው ወይስ አይደለም?",
            analysisText = "ለዚህ ውሳኔ መነሻ የሆነው የከሳሽ ክስ ነው።",
            decisionText = "ክሱ ተቀባይነት አግኝቷል።",
            closingText = "መዝገቡ ተዘግቷል።",
        )
        val stitched = com.example.util.JudicialDocumentFormatter.composeUnifiedDocument(legacyCase)
        assertTrue(stitched.contains("የመ/ቁ:- 29001"))
        assertTrue(stitched.contains("ፍ ር ድ"))
        assertTrue(stitched.contains("ው ሳ ኔ"))
        assertTrue(stitched.contains("ክሱ ተቀባይነት አግኝቷል።"))
        assertTrue(com.example.util.JudicialDocumentFormatter.isUnifiedHeadingLine("ፍ ር ድ"))
        assertTrue(com.example.util.JudicialDocumentFormatter.isUnifiedHeadingLine("ት ዕ ዛ ዝ"))
        val snippet = com.example.util.JudicialDocumentFormatter.extractPreviewSnippet(legacyCase)
        assertTrue(snippet.startsWith("ለዚህ ውሳኔ"))
    }

    @Test
    fun audioWavRecorder_encodesValid16kHzMonoWavHeader() {
        val pcm = ByteArray(320) { (it % 127).toByte() }
        val wav = com.example.util.AudioWavRecorder.encodePcmToWavBytes(pcm, 16000, 1, 16)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        // Sample rate 16000 little-endian at offset 24..27: 0x80, 0x3E, 0x00, 0x00
        val sampleRate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(16000, sampleRate)
    }

    @Test
    fun firestoreModels_createMapOmitsDocumentIdAndFiltersNulls() {
        val profile = com.example.data.remote.FirestoreUserProfile(
            id = "uid_123",
            userId = "uid_123",
            displayName = "ዳኛ ኑህ",
            email = null,
            photoUrl = null,
        )
        val profileMap = profile.toCreateMap()
        assertTrue(!profileMap.containsKey("id"))
        assertTrue(!profileMap.containsKey("email"))
        assertTrue(!profileMap.containsKey("photoUrl"))
        assertEquals("uid_123", profileMap["userId"])

        val note = com.example.data.remote.FirestoreResearchNote(
            id = "note_abc",
            userId = "uid_123",
            noteType = "INVALID_VALUE",
            title = "ሙከራ",
            content = "ይዘት",
        )
        assertEquals(com.example.data.remote.ResearchNoteType.SEARCH, note.resolvedType())
        val noteMap = note.toCreateMap()
        assertTrue(!noteMap.containsKey("id"))
        assertEquals("SEARCH", noteMap["noteType"])
        assertEquals("case_num_APPEAL_31513", com.example.data.remote.FirestoreSyncRepository.buildStableCaseDocId(
            com.example.data.local.CaseEntity(caseNumber = "31513", caseType = com.example.data.local.CaseType.APPEAL)
        ))
    }

    @Test
    fun pastJudgmentParser_extractsMetadataAndRanksSimilarCases() {
        val appealDoc = """
            የመ/ቁ:- 31513
            ቀን:- 17/09/2018 ዓ.ም

            ዳኞች:-
            አብራሂም ረሺድ
            ኑህ መሀመድ
            ቲማጅ አብዱልቃድር

            ይግባኝ ባይ፦ አቶ እድሜዓለም ተሰራ
            መልስ ሰጭ:- ተስፋዬና ጓደኞቹ

            ፍ ር ድ

            ለዚህ ውሳኔ ምክንያት የሆነው በአፈጻጸም መዝገብ ላይ የጣልቃ ገብነት አቤቱታ ነው።
            ው ሳ ኔ
            ተሽሯል።
        """.trimIndent()

        val parsedAppeal = com.example.util.PastJudgmentParser.parseMetadata(appealDoc, "appeal_31513.pdf")
        assertEquals("31513", parsedAppeal.caseNumber)
        assertEquals(com.example.data.local.CaseType.APPEAL, parsedAppeal.caseType)
        assertEquals("17/09/2018", parsedAppeal.ethiopianDate)
        assertTrue(parsedAppeal.judges.contains("ኑህ መሀመድ"))
        assertTrue(parsedAppeal.plaintiffName.contains("እድሜዓለም"))

        val decreeDoc = """
            የመ/ቁ:- 44210
            ቀን:- 06/11/2018

            ብ ይ ን

            የዳኝነት ሥልጣን መቃወሚያ ተመርምሮ ተበይኗል።
            ት ዕ ዛ ዝ
            ክሱ ውድቅ ሆኗል።
        """.trimIndent()
        val parsedDecree = com.example.util.PastJudgmentParser.parseMetadata(decreeDoc, "decree.docx")
        assertEquals(com.example.data.local.CaseType.DECREE, parsedDecree.caseType)

        val executionRef = com.example.data.local.CaseEntity(
            id = 11L,
            caseNumber = "31513",
            caseType = com.example.data.local.CaseType.APPEAL,
            analysisText = "በአፈጻጸም መዝገብ ላይ ጣልቃ ገብነትና የመብት ማስተላለፍ ውል ክርክር በፍ/ብ/ሥ/ሥ/ሕ/ቁ 381 መሰረት ተመርምሯል።",
            specialInstructions = "${com.example.util.PastJudgmentParser.UPLOADED_REF_PREFIX} appeal.pdf",
        )
        val trafficRef = com.example.data.local.CaseEntity(
            id = 12L,
            caseNumber = "20999",
            caseType = com.example.data.local.CaseType.DIRECT_CIVIL,
            analysisText = "በትራፊክ አደጋ ምክንያት የደረሰ የቀብር እና የቀለብ ካሳ ክርክር።",
        )
        val ranked = com.example.util.PastJudgmentParser.rankSimilarCases(
            candidates = listOf(trafficRef, executionRef),
            queryText = "በአፈጻጸም መዝገብ ላይ ጣልቃ ገብነት የመብት ማስተላለፍ ውል ክርክር 381",
            targetCaseType = com.example.data.local.CaseType.APPEAL,
        )
        assertTrue(ranked.isNotEmpty())
        assertEquals(11L, ranked.first().caseEntity.id)
    }

    @Test
    fun judicialPromptsAndFormatter_enforceInlineIssuesAndProhibitTrailingIssues() {
        assertTrue(com.example.ai.JudicialPrompts.SYSTEM_INSTRUCTION.contains("ጭብጥን በሰነዱ መጨረሻ ላይ"))
        val ref = com.example.util.ScoredReferenceCase(
            caseEntity = com.example.data.local.CaseEntity(
                id = 5L,
                caseNumber = "31513",
                caseType = com.example.data.local.CaseType.APPEAL,
                analysisText = "የይግባኝ ናሙና ውሳኔ።",
            ),
            score = 4.5,
            matchedKeywords = listOf("አፈጻጸም", "ጣልቃ"),
        )
        val prompt = com.example.ai.JudicialPrompts.buildDraftingPrompt(
            caseEntity = com.example.data.local.CaseEntity(caseNumber = "50010", caseType = com.example.data.local.CaseType.APPEAL),
            plaintiffText = "የይግባኝ ባይ ቅሬታ",
            defendantText = "የመልስ ሰጪ መልስ",
            seedTemplate = null,
            fewShotExamples = emptyList(),
            badFeedbackReminders = emptyList(),
            similarReferences = listOf(ref),
        )
        assertTrue(prompt.contains("ጭብጥን በሰነዱ መጨረሻ ላይ"))
        assertTrue(prompt.contains("ማጣቀሻ 1: የመ/ቁ 31513"))

        // Verify formatter places split issuesText BEFORE "የግራ ቀኙ" transition, never at the end
        val splitWithTransition = com.example.data.local.CaseEntity(
            caseNumber = "50011",
            caseType = com.example.data.local.CaseType.APPEAL,
            issuesText = "የስር ፍ/ቤት ብይን አግባብ ነው ወይ?",
            analysisText = "ለዚህ ውሳኔ ምክንያት የሆነው የከሳሽ ክስ ነው።\n\nየግራ ቀኙ መከራከሪያ ከላይ የተገለጸው ሲሆን በማለት ወስነናል።",
            decisionText = "ብይኑ ጸንቷል።",
            closingText = "መዝገቡ ተዘግቷል።",
        )
        val formatted = com.example.util.JudicialDocumentFormatter.composeUnifiedDocument(splitWithTransition)
        val issueIdx = formatted.indexOf("የስር ፍ/ቤት ብይን አግባብ ነው ወይ?")
        val conclusionIdx = formatted.indexOf("በማለት ወስነናል።")
        assertTrue(issueIdx in 0 until conclusionIdx)
    }

    @Test
    fun docxExporter_buildsValidOoxmlZipWithAmharicAndEscaping() {
        val sampleCase = com.example.data.local.CaseEntity(
            caseNumber = "31513",
            caseType = com.example.data.local.CaseType.APPEAL,
            ethiopianDate = "17/09/2018",
            judges = "ኑህ መሀመድ",
            analysisText = "የመ/ቁ:- 31513\n\nፍ ር ድ\n\nA & B <ክርክር> ተመርምሯል።\n\nው ሳ ኔ\n\nይግባኙ ጸንቷል።",
        )
        val bytes = com.example.pdf.DocxExporter.writeDocxBytes(sampleCase, isDraft = true)
        assertTrue(bytes.isNotEmpty())

        val entryMap = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryMap[entry.name] = String(zis.readBytes(), Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        assertTrue(entryMap.containsKey("[Content_Types].xml"))
        assertTrue(entryMap.containsKey("word/document.xml"))
        assertTrue(entryMap.containsKey("word/styles.xml"))
        val docXml = entryMap.getValue("word/document.xml")
        assertTrue(docXml.contains("ፍ ር ድ"))
        assertTrue(docXml.contains("ው ሳ ኔ"))
        assertTrue(docXml.contains("A &amp; B &lt;ክርክር&gt;"))
        assertTrue(docXml.contains("DRAFT"))
    }

    @Test
    fun ocrMetadataExtractor_handlesPageMarkersAndCounsels() {
        val plaintiffOcr = """
            [ገጽ 1 - doc.jpg]
            የመ/ቁ:- 48912
            ቀን:- 12/10/2018 ዓ.ም
            ከሳሽ:- ወ/ሮ ፋጡማ አህመድ
            ጠበቃ:- አቶ ዳዊት ተስፋዬ
            ተከሳሽ:- አቶ ሀሰን ሙሳ
            ጠበቃ / ነገረፈጅ:- አቶ ካሊድ ዑመር
        """.trimIndent()
        val extracted = com.example.util.PastJudgmentParser.extractFromOcr(plaintiffOcr, "")
        assertEquals("48912", extracted.caseNumber)
        assertEquals("12/10/2018", extracted.ethiopianDate)
        assertEquals("ወ/ሮ ፋጡማ አህመድ", extracted.plaintiffName)
        assertEquals("አቶ ዳዊት ተስፋዬ", extracted.plaintiffCounsel)
        assertEquals("አቶ ሀሰን ሙሳ", extracted.defendantName)
        assertEquals("አቶ ካሊድ ዑመር", extracted.defendantCounsel)
    }

    @Test
    fun pinnedAndSubjectTags_boostSimilarCaseRanking() {
        val c1 = com.example.data.local.CaseEntity(
            id = 101L,
            caseNumber = "1001",
            caseType = com.example.data.local.CaseType.DIRECT_CIVIL,
            analysisText = "የውል ክርክር እና ንብረት ባለቤትነት መብት ማስከበሪያ ውሳኔ።",
        )
        val c2 = com.example.data.local.CaseEntity(
            id = 102L,
            caseNumber = "1002",
            caseType = com.example.data.local.CaseType.DIRECT_CIVIL,
            analysisText = "የውል ክርክር እና ንብረት ባለቤትነት መብት ማስከበሪያ ውሳኔ።",
        )
        val ranked = com.example.util.PastJudgmentParser.rankSimilarCases(
            candidates = listOf(c1, c2),
            queryText = "የውል ክርክር ንብረት ባለቤትነት ውሳኔ",
            targetCaseType = com.example.data.local.CaseType.DIRECT_CIVIL,
            pinnedKeys = setOf("id:102"),
            subjectTags = mapOf("id:102" to "ውል"),
            targetSubjectTag = "ውል",
        )
        assertEquals(2, ranked.size)
        assertEquals(102L, ranked.first().caseEntity.id)
    }

    @Test
    fun adjournmentHelperAndPresets_workAsExpected() {
        val (nextEt, nextGreg) = EthiopianCalendarUtil.addDaysToEthiopian("17/09/2018", 15)
        assertEquals("02/10/2018", nextEt)
        assertTrue(nextGreg.isNotBlank())
        assertTrue(com.example.util.JudicialPresets.allPresets.size >= 5)
    }

    @Test
    fun chatIntentClassifier_handlesAmharicAddRemoveReviseInflections() {
        val revisionPrompts = listOf(
            "በውሳኔው ላይ አዲስ ነጥብ ጨምርበት",
            "ይህንን ክፍል ቀንሰው",
            "ተደጋጋሚውን ዓረፍተ ነገር አስወግድ",
            "የፍርድ ክፍሉን አሻሽለው",
            "አዲስ የህግ አንቀጽ አክል",
        )
        revisionPrompts.forEach { prompt ->
            assertTrue(
                "Expected revision intent for: $prompt",
                !GeminiJudicialService.isConversationalMessage(prompt, hasExistingDraft = true)
            )
        }
    }

    @Test
    fun networkUtils_classifiesTransientCellularFailuresAccurately() {
        assertTrue(com.example.util.NetworkUtils.isTransientNetworkFailure(java.net.SocketTimeoutException("timeout")))
        assertTrue(com.example.util.NetworkUtils.isTransientNetworkFailure(javax.net.ssl.SSLException("Read error")))
        assertTrue(com.example.util.NetworkUtils.isTransientNetworkFailure(java.io.EOFException("unexpected end of stream")))
        assertTrue(com.example.util.NetworkUtils.isTransientNetworkFailure(RuntimeException(java.net.UnknownHostException("host"))))
        assertTrue(!com.example.util.NetworkUtils.isTransientNetworkFailure(kotlinx.coroutines.CancellationException("cancelled")))
        assertTrue(!com.example.util.NetworkUtils.isTransientNetworkFailure(IllegalArgumentException("bad input")))
    }

    @Test
    fun draftingPrompt_includesJudgeWitnessAndEvidenceInstructions() {
        val prompt = com.example.ai.JudicialPrompts.buildDraftingPrompt(
            caseEntity = com.example.data.local.CaseEntity(
                caseNumber = "51002",
                caseType = com.example.data.local.CaseType.DIRECT_CIVIL,
                specialInstructions = "የምስክር ቃል: አቶ ከበደ ውሉ መፈረሙን መስክረዋል።",
            ),
            plaintiffText = "የከሳሽ ክስ",
            defendantText = "የተከሳሽ መልስ",
            seedTemplate = null,
            fewShotExamples = emptyList(),
            badFeedbackReminders = emptyList(),
        )
        assertTrue(prompt.contains("የምስክር ቃል"))
        assertTrue(prompt.contains("አቶ ከበደ"))
    }

    @Test
    fun notificationHelper_computesAdjournmentReminderDelaysAccurately() {
        val tz = java.util.TimeZone.getTimeZone("Africa/Addis_Ababa")
        // Hearing date: 17/09/2018 E.C. -> 25 May 2026
        val twoDaysBefore = java.util.Calendar.getInstance(tz).apply {
            clear()
            set(2026, java.util.Calendar.MAY, 23, 10, 0, 0)
        }.timeInMillis
        val delayTwoDaysBefore = com.example.util.NotificationHelper.computeAdjournmentReminderDelayMs(
            ethiopianDateStr = "17/09/2018",
            nowMs = twoDaysBefore,
            timeZone = tz,
        )
        assertTrue(delayTwoDaysBefore != null && delayTwoDaysBefore > 0L)
        assertTrue(
            com.example.util.NotificationHelper.isDayBeforeReminderTarget(
                ethiopianDateStr = "17/09/2018",
                nowMs = twoDaysBefore,
                timeZone = tz,
            )
        )

        // Morning of hearing at 06:00 AM -> targets 07:30 AM (90 minutes = 5,400,000 ms)
        val sameMorning = java.util.Calendar.getInstance(tz).apply {
            clear()
            set(2026, java.util.Calendar.MAY, 25, 6, 0, 0)
        }.timeInMillis
        val delaySameMorning = com.example.util.NotificationHelper.computeAdjournmentReminderDelayMs(
            ethiopianDateStr = "17/09/2018",
            nowMs = sameMorning,
            timeZone = tz,
        )
        assertEquals(90L * 60L * 1000L, delaySameMorning)
        assertTrue(
            !com.example.util.NotificationHelper.isDayBeforeReminderTarget(
                ethiopianDateStr = "17/09/2018",
                nowMs = sameMorning,
                timeZone = tz,
            )
        )

        // Past hearing date returns null
        val afterHearing = java.util.Calendar.getInstance(tz).apply {
            clear()
            set(2026, java.util.Calendar.MAY, 26, 9, 0, 0)
        }.timeInMillis
        assertEquals(
            null,
            com.example.util.NotificationHelper.computeAdjournmentReminderDelayMs(
                ethiopianDateStr = "17/09/2018",
                nowMs = afterHearing,
                timeZone = tz,
            )
        )

        // Invalid date string returns null
        assertEquals(null, com.example.util.NotificationHelper.computeAdjournmentReminderDelayMs("invalid-date"))
    }
}
