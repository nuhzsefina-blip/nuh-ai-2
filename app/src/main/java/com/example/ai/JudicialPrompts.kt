package com.example.ai

import com.example.data.local.CaseEntity
import com.example.data.local.CaseType
import com.example.data.local.DraftSection
import com.example.util.JudicialDocumentFormatter
import com.example.util.PastJudgmentParser
import com.example.util.ScoredReferenceCase

object JudicialPrompts {
    const val CONVERSATIONAL_CHAT_INSTRUCTION = """
አንተ "ረዳት ዳኛ" ነህ — ለሐረሪ ክልል ከፍተኛ ፍርድ ቤት ዳኛ ኑህ መሀመድ የምታግዝ ጨዋ፣ ትሁት እና የኢትዮጵያን ህግ ጠንቅቆ የሚያውቅ የህግ ረዳት ነህ።
- ዳኛው ሰላምታ ("hi", "ሰላም", ወዘተ) ሲልክ በአክብሮት እና በአጭሩ በአማርኛ ሰላምታ መልስ፣ እንዲሁም እንዴት መርዳት እንደምትችል በአጭሩ አቅርብ።
- አጠቃላይ የህግ ጥያቄዎች ወይም ተመሳሳይ የቀድሞ መዝገብ ማጣቀሻ ሲጠየቁ (ለምሳሌ የፍ/ብ/ሕግ፣ የፍ/ሥ/ሥ ሕግ፣ የሰበር ውሳኔ፣ የአሰራር ጥያቄ) በተጨባጭ እና በአክብሮት በአማርኛ መልስ።
- ዳኛው ገና ረቂቅ ካላዘጋጀ፣ በ"የውሳኔ ረቂቅ ማዘጋጃ" ትር ላይ የከሳሽና የተከሳሽ ፎቶዎችን/ሰነዶችን አስገብቶ "ውሳኔ/ብይን ረቂቅ እና ጭብጥ አዘጋጅ" መጫን እንደሚችል በአጭሩ አስታውስ።
- መልስህ ግልጽ፣ አጭር እና በተራ አማርኛ ይሁን (JSON አትላክ)።
"""

    const val SYSTEM_INSTRUCTION = """
አንተ "ረዳት ዳኛ" ነህ። ለሐረሪ ክልል ከፍተኛ ፍርድ ቤት ዳኛ ኑህ መሀመድ ረቂቅ ፍርድ፣ ውሳኔ እና ብይን በአማርኛ የምታዘጋጅ ልምድ ያለህ የኢትዮጵያ ህግ ባለሙያ ረዳት ነህ። የምትሰራው ለዳኛው ረቂቅ ነው፤ የመጨረሻ ውሳኔ የዳኛው ነው።

## ሀ. የህግ ማዕቀፍ
በኢትዮጵያ ፌዴራላዊ እና በሐረሪ ክልል ህጎች መሰረት ተንትን፡
- የኢፌዴሪ ሕገ መንግሥት እና የሐረሪ ክልል ሕገ መንግሥት
- የፍትሐ ብሔር ሕግ (1952)፣ የንግድ ሕግ (1952)
- የፍትሐ ብሔር ሥነ ሥርዓት ሕግ (1958)
- የወንጀል ሕግ (2004)፣ የወንጀል ሥነ ሥርዓት ሕግ (1954)፣ የማስረጃ ሕግ
- የቤተሰብ ሕግ፣ የሥራ ሕግ፣ የይዞታ/መሬት ህጎች እና ልዩ አዋጆች (ጉምሩክ፣ ታክስ፣ ወዘተ)
- የሐረሪ ክልል የፍርድ ቤት እና የስልጣን ክፍፍል አዋጆች
- የፌዴራል ጠቅላይ ፍርድ ቤት ሰበር ሰሚ ችሎት አስገዳጅ ውሳኔዎች (አዋጅ 1234/2013 መሰረት)

## ለ. ከሃሰተኛ መረጃ መጠበቂያ
1. አንቀጽ ቁጥር፣ አዋጅ ቁጥር ወይም የሰበር መዝገብ ቁጥር ስትጠቅስ ቁጥሩን እና ይዘቱን በእርግጠኝነት የምታውቀው ከሆነ ብቻ ጥቀስ።
2. እርግጠኛ ካልሆንክ ቁጥሩን አትፍጠር። በምትኩ [ለማረጋገጥ: አንቀጽ?] ወይም [ለማረጋገጥ: ሰበር ውሳኔ?] ብለህ ምልክት አድርግ።
3. ከመዝገቡ ውጭ ምንም እውነታ አትፍጠር፤ ቀኖች፣ ገንዘብ መጠኖች፣ ስሞች እና የማስረጃ ዝርዝሮች ከተነበበው ጽሁፍ ብቻ ይወሰዱ። ያልተነበበ ወይም ግልጽ ያልሆነ ከሆነ [የማይነበብ] ብለህ ምልክት አድርግ።
4. የተጠራጠርካቸውን የህግ አንቀጾች ወይም የማስረጃ ክፍተቶች ካሉ ብቻ ከዋናው የፍርድ ቤት ሰነድ ውጪ በመጨረሻ ላይ በ[SECTION:JUDGE_NOTES] ስር ጻፍ።

## ሐ. የዳኛ ኑህ መሀመድ ብቸኛ የውሳኔ/ብይን አፃፃፍ ቅርጽ እና የጭብጥ አቀማመጥ (ጥብቅ መመሪያ)
የውሳኔው/የብይኑ መዋቅር በቀረቡት 3 ናሙናዎች መልክ ብቻ በዚህ የማይለወጥ ቅደም ተከተል መጻፍ አለበት፦
1. **የመዝገብ ራስጌ**: `የመ/ቁ:-`, `ቀን:-`, `ዳኞች:-`/`ዳኛ፦`, `ይግባኝ ባይ`/`ከሳሽ`, `መልስ ሰጭ`/`ተከሳሽ`, ከዚያም `መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።` (ወይም `ለብይን ... ተበይኗል።`)
2. **ዋናው ርዕስ**: `ፍ ር ድ` (ለብይን ከሆነ `ብ ይ ን`)
3. **በ`ፍ ር ድ` / `ብ ይ ን` ውስጥ የሚከተሉት 4 ክፍሎች በዚሁ ቅደም ተከተል ይጻፋሉ**:
   - **(ሀ) የክርክር ማጠቃለያ**: `ለዚህ ውሳኔ/ብይን ምክንያት የሆነው ... ይዘቱም በአጭሩ፣ ...` (የከሳሽ/ይግባኝ ባይ ክስና የተከሳሽ/መልስ ሰጪ መልስ)።
   - **(ለ) ጭብጥ — ወዲያውኑ ከክርክር ማጠቃለያ ቀጥሎ (ከማስረጃ ምዘናና ከህግ ትንታኔ በፊት!)**:
     `የግራ ቀኙ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን ክርክርና ማስረጃ ከመረመረ በኋላ የሚከተለውን ጭብጥ (ወይም የሚከተሉትን ጭብጦች) ይዟል፦`
     ከዚያ ጭብጡን በጥያቄ መልክ (`1. ... ነው ወይስ አይደለም?`) **እዚሁ** አስቀምጥ።
   - **(ሐ) የማስረጃ ምዘና እና የሕግ ትንታኔ**:
     `ይህንንም ጭብጥ ከህጉና ከማስረጃው ጋር በማገናዘብ እንደሚከተለው መርምረናል። ...` ብለህ እያንዳንዱን ጭብጥ በቅደም ተከተል መዝን፣ የህግ አንቀጾችንና የሰበር ውሳኔዎችን ተግብር።
   - **(መ) የፍርዱ መደምደሚያ**: `... በማለት የሚከተለውን ወስነናል።` (ለብይን `... ተበይኗል።`)።
4. **የትዕዛዝ/ውሳኔ ክፍል**: `ው ሳ ኔ` (ለብይን ከሆነ `ት ዕ ዛ ዝ`) — የተወሰኑ ነጥቦች (`1. ... 2. ...`)፣ ወጪና ኪሳራ፣ የይግባኝ መብት፣ እግድ ካለ መነሳት፣ `መዝገቡ ተዘግቷል መዝገብ ቤት ይመለስ።` እና የዳኛ/ዳኞች ፊርማ መስመር።

## መ. ጥብቅ ክልከላ (ZERO TOLERANCE)
- **ጭብጥን በሰነዱ መጨረሻ ላይ፣ ከትንታኔ በኋላ፣ ከ`ው ሳ ኔ`/`ት ዕ ዛ ዝ` በኋላ፣ ወይም በ`[SECTION:JUDGE_NOTES]` ውስጥ በፍጹም አታስቀምጥ!**
- ጭብጥ ሁልጊዜ ከግራ ቀኙ ክርክር ማጠቃለያ **ወዲያውኑ ቀጥሎ** እና ከማስረጃ/ህግ ትንታኔ **በፊት** በ`ፍ ር ድ` (ወይም `ብ ይ ን`) ውስጥ መቀመጥ አለበት።
- እንደ "የክርክሩ አመጣጥ"፣ "የጭብጥ አወጣጥ"፣ "የሕግ ትንታኔ"፣ `[SECTION:ISSUES]`፣ `[SECTION:ANALYSIS]`፣ `[SECTION:DECISION]` ያሉ የተናጠል ርዕሶችን ወይም መለያዎችን በፍጹም አትጠቀም። ብቸኛዎቹ አበይት ርዕሶች `ፍ ር ድ` + `ው ሳ ኔ` (ወይም `ብ ይ ን` + `ት ዕ ዛ ዝ`) ብቻ ናቸው።
"""

    fun buildOcrPrompt(sideLabel: String): String = """
በዚህ የፍርድ ቤት ሰነድ ፎቶ ውስጥ ያለውን የአማርኛ (እና ካለ የእንግሊዝኛ/ቁጥር) ጽሁፍ በሙሉ ቃል በቃል እና በትክክል አንብበህ ጻፍ።
የሰነዱ ወገን: $sideLabel
መመሪያ:
1. ያለውን ጽሁፍ በሙሉ ከመጀመሪያ እስከ መጨረሻ ያለምንም ማጠቃለል ገልብጥ።
2. ቀኖች፣ የመዝገብ ቁጥሮች፣ የገንዘብ መጠኖች፣ የአንቀጽ ቁጥሮች እና ስሞች በትክክል ይጻፉ።
3. የማይነበብ ወይም የደበዘዘ ክፍል ካለ [የማይነበብ] ብለህ ምልክት አድርግ፤ ከራስህ አትገምት።
""".trimIndent()

    fun buildBatchOcrPrompt(sideLabel: String, pageLabels: List<String>): String = """
ከላይ የቀረቡትን ${pageLabels.size} ተከታታይ የፍርድ ቤት ሰነድ ፎቶዎች (ገጽ 1 እስከ ገጽ ${pageLabels.size}) እያንዳንዱን ቃል በቃል አንብበህ ገልብጥ።
የሰነዱ ወገን: $sideLabel
የገጾች ቅደም ተከተል: ${pageLabels.mapIndexed { idx, lbl -> "ገጽ ${idx + 1}: $lbl" }.joinToString(" | ")}

ጥብቅ የቅርጸት መመሪያ:
- ለእያንዳንዱ ገጽ ከጽሁፉ በፊት በትክክል === PAGE <ቁጥር> === (ለምሳሌ === PAGE 1 ===, === PAGE 2 ===) በሚል መለያ መስመር ጀምር።
- ጽሁፉን ሳታሳጥር እና ሳትዘል በሙሉ ገልብጥ፤ ቀኖች፣ የመዝገብ ቁጥሮች፣ ስሞችና ገንዘብ መጠኖች በትክክል ይጻፉ።
- የማይነበብ ቃል ካለ [የማይነበብ] በል።
""".trimIndent()

    fun buildDraftingPrompt(
        caseEntity: CaseEntity,
        plaintiffText: String,
        defendantText: String,
        seedTemplate: CaseEntity?,
        fewShotExamples: List<CaseEntity>,
        badFeedbackReminders: List<CaseEntity>,
        similarReferences: List<ScoredReferenceCase> = emptyList(),
        pinnedKeys: Set<String> = emptySet(),
        subjectTags: Map<String, String> = emptyMap(),
    ): String {
        val typeGuide = when (caseEntity.caseType) {
            CaseType.APPEAL -> """
አይነት 1 – የይግባኝ ውሳኔ (የናሙና 1 ትክክለኛ ቅርጽ):
1. ራስጌ:
   የመ/ቁ:- <ቁጥር>
   ቀን:- <ቀን> ዓ.ም
   ዳኞች:- (3ቱን ዳኞች በየመስመሩ)
   ይግባኝ ባይ፦ <ስም> (ካለ ጠበቃ፡- <ስም>)
   መልስ ሰጭ:- <ስም>
2. መግቢያ: "መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።"
3. ርዕስ: "ፍ ር ድ"
   በዚህ ውስጥ በቅደም ተከተል:
   - የይግባኝ ባይ ቅሬታ ("ለዚህ ውሳኔ ምክንያት የሆነው ይግባኝ ባይ ... ይዘቱም በአጭሩ፣ ...")
   - የመልስ ሰጪ መልስ ("መልስ ሰጪ የይግባኝ አቤቱታው ደርሷቸው ... ይዘቱም በአጭሩ፣ ...")
   - ወዲያውኑ ቀጥሎ ጭብጥ ("የግራ ቀኙ የይግባኝ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን ክርክርና መዝገብ ከመረመረ በኋላ የሚከተለውን ጭብጥ ይዟል፦ ...")
   - ከጭብጡ ቀጥሎ ትንታኔ ("ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል። ...") እና "... በማለት የሚከተለውን ወስነናል።"
4. ርዕስ: "ው ሳ ኔ"
   - በፍ/ሥ/ሥ ሕግ አንቀፅ 348(2) መሰረት መሻር ወይም 348(1) መሰረት ማፅናት፣ የወጪ ውሳኔ፣ የይግባኝ መብት፣ እግድ መነሳት፣ "መዝገቡ ተዘግቷል መዝገብ ቤት ይመለስ።" እና የሶስቱም ዳኞች ፊርማ መስመር።
""".trimIndent()
            CaseType.DIRECT_CIVIL -> """
አይነት 2 – የቀጥታ ፍርድ (የናሙና 2 ትክክለኛ ቅርጽ):
1. ራስጌ:
   የመ/ቁ:- <ቁጥር>
   ቀን:- <ቀን> ዓ.ም
   ዳኛ፦ <ስም>
   ከሳሽ/ከሳሾች፤ <ስም> (ካለ ጠበቃ፣ <ስም>)
   ተከሳሽ/ተከሳሾች፤ <ስም>
2. መግቢያ: "መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።"
3. ርዕስ: "ፍ ር ድ"
   በዚህ ውስጥ በቅደም ተከተል:
   - የከሳሽ ክስ ("ለዚህ ውሳኔ ምክንያት የሆነው ከሳሽ ... ባቀረቡት ክስ አማካኝነት ሲሆን ይዘቱም በአጭሩ፣ ...") እና የማስረጃ ዝርዝር
   - የተከሳሽ መልስ ("ተከሳሽ/ተከሳሾች ክሱ እንዲደርሳቸው ተደርጎ መልስ የሰጡ ሲሆን ...")
   - ወዲያውኑ ቀጥሎ ጭብጥ ("የግራ ቀኙ መከራከሪያዎች እና ማስረጃዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን ክርክርና ማስረጃ ከመረመረ በኋላ የሚከተለውን ጭብጥ/ጭብጦች ይዟል፦ 1. ... ነው ወይስ አይደለም?")
   - ከጭብጡ ቀጥሎ የማስረጃ ምዘናና የህግ ትንታኔ ("ይህንንም ጭብጥ ... እንደሚከተለው መርምረናል።")። ምስክር ካልቀረበ "ፍርድ ቤቱ በቀረቡት የፅሑፍ ክርክሮች እና በሰነድ ማስረጃዎች ላይ ብቻ ተመስርቶ" የሚለውን ተጠቀም። የገንዘብ መጠን በቁጥርና በፊደል ጻፍ። "... በማለት የሚከተለውን ወስነናል።"
4. ርዕስ: "ው ሳ ኔ"
   - የተወሰኑት ነጥቦች፣ ወጪና የጠበቃ አበል፣ የይግባኝ መብት፣ "መዝገቡ ተዘግቷል፤ ወደ መዝገብ ቤት ይመለስ።" እና የዳኛው ፊርማ መስመር።
""".trimIndent()
            CaseType.DECREE -> """
አይነት 3 – ብይን / የመጀመሪያ ደረጃ መቃወሚያ (የናሙና 3 ትክክለኛ ቅርጽ):
1. ራስጌ:
   የመ/ቁ:- <ቁጥር>
   ቀን:- <ቀን> ዓ.ም
   ዳኛ፦ <ስም>
   ከሳሽ፤ <ስም> (ቀረቡ / ጠበቃ)
   ተከሳሽ፤ <ስም> (ቀረቡ / ጠበቃ ወይም ነገረፈጅ)
2. መግቢያ: "መዝገቡ የተቀጠረው ለብይን ሲሆን ተመርምሮ የሚከተለው ተበይኗል።"
3. ርዕስ: "ብ ይ ን"
   በዚህ ውስጥ በቅደም ተከተል:
   - የከሳሽ ክስ ማጠቃለያ ("ለዚህ ብይን ምክንያት የሆነው ከሳሽ ...")
   - የተከሳሽ የመጀመሪያ ደረጃ መቃወሚያ
   - ወዲያውኑ ቀጥሎ ጭብጥ ("የግራ ቀኙ የመጀመሪያ ደረጃ መቃወሚያ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን መቃወሚያ ከመረመረ በኋላ የሚከተለውን ጭብጥ/ጭብጦች ይዟል፦ 1. ...")
   - ከጭብጡ ቀጥሎ ትንታኔ ("ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል። ...") እና "... ተበይኗል።"
4. ርዕስ: "ት ዕ ዛ ዝ"
   - ክስ ውድቅ ሲደረግ ህጋዊ መሰረቱ (ለምሳሌ በፍ/ሥ/ሥ ሕግ አንቀፅ 244(2)(ሀ)) እና የከሳሽ ቀሪ መብት፣ የወጪ ውሳኔ፣ የይግባኝ መብት፣ "መዝገቡ ተዘግቷል መዝገብ ቤት ይመለስ።" እና የዳኛው ፊርማ መስመር።
""".trimIndent()
        }

        val sb = StringBuilder()
        sb.appendLine("### የውሳኔ/ብይን አይነት እና አንድ ወጥ ቅርጽ መመሪያ")
        sb.appendLine(typeGuide)
        sb.appendLine()

        if (seedTemplate != null) {
            val unifiedTemplate = JudicialDocumentFormatter.composeUnifiedDocument(seedTemplate)
            sb.appendLine("### የዳኛ ኑህ መሀመድ መደበኛ ናሙና (ቅደም ተከተሉን፣ ጭብጥ ከክርክር ቀጥሎ ከትንታኔ በፊት የሚቀመጥበትን ቦታ እና ቋንቋውን በጥብቅ ተከተል)")
            sb.appendLine(unifiedTemplate.take(2600))
            sb.appendLine()
        }

        if (fewShotExamples.isNotEmpty()) {
            sb.appendLine("### ከArchive የተመረጡ የዳኛው የአፃፃፍ ስልት ምሳሌዎች (Style Learning — ስሞችንና እውነታዎችን አትቅዳ፣ የአፃፃፍ ስልቱንና መዋቅሩን ብቻ ተማር)")
            fewShotExamples.take(2).forEachIndexed { idx, ex ->
                val isPinned = PastJudgmentParser.matchesAnyKey(pinnedKeys, ex)
                val tag = PastJudgmentParser.resolveMapValue(subjectTags, ex)
                val originTag = buildString {
                    if (isPinned) append("ዋና የዳኛው ተመራጭ ናሙና • ")
                    if (!tag.isNullOrBlank()) append("ዘርፍ: $tag • ")
                    append(
                        if (PastJudgmentParser.isUploadedReferenceCase(ex)) "የተጫነ የዳኛው ቀድሞ ውሳኔ"
                        else "ደረጃ: ${ex.qualityRating.amharicLabel}"
                    )
                }
                sb.appendLine("ምሳሌ ${idx + 1} (መ/ቁ ${ex.caseNumber.ifBlank { "-" }} | $originTag):")
                sb.appendLine(JudicialDocumentFormatter.composeUnifiedDocument(ex).take(1600))
                sb.appendLine()
            }
        }

        if (similarReferences.isNotEmpty()) {
            sb.appendLine("### ተመሳሳይ የቀድሞ ውሳኔዎች ከArchive (Precedent & Legal Reference — ተዛማጅ የህግ አተገባበርን ለማጣቀስ ብቻ ተጠቀም፤ የቀድሞዎቹን ሰዎች ስም ወይም ቁጥር ወደ አዲሱ መዝገብ አታስገባ)")
            similarReferences.take(2).forEachIndexed { idx, scored ->
                val ref = scored.caseEntity
                val kwLabel = if (scored.matchedKeywords.isNotEmpty()) {
                    " | ተዛማጅ ቃላት: ${scored.matchedKeywords.joinToString(", ")}"
                } else ""
                sb.appendLine("ማጣቀሻ ${idx + 1}: የመ/ቁ ${ref.caseNumber.ifBlank { "-" }} (${ref.caseType.amharicLabel}$kwLabel)")
                sb.appendLine(JudicialDocumentFormatter.composeUnifiedDocument(ref).take(1400))
                sb.appendLine()
            }
        }

        if (badFeedbackReminders.isNotEmpty()) {
            sb.appendLine("### አታድርግ (ከቀደሙት የዳኛው ግብረ መልሶች)")
            badFeedbackReminders.forEach { bad ->
                sb.appendLine("- [${bad.badReasonCategory.amharicLabel}] ${bad.badReasonNote.ifBlank { "ይህን ስህተት አትድገም" }}")
            }
            sb.appendLine()
        }

        sb.appendLine("### የአዲሱ መዝገብ መረጃ (ከሰነዶቹም የተገኘውን ጨምረህ አሟላ)")
        sb.appendLine("የመ/ቁ:- ${caseEntity.caseNumber.ifBlank { "[ከሰነዱ ውሰድ]" }}")
        sb.appendLine("ቀን:- ${caseEntity.ethiopianDate} ዓ.ም")
        sb.appendLine("ዳኛ/ዳኞች: ${caseEntity.judges}")
        sb.appendLine("ከሳሽ/ይግባኝ ባይ: ${caseEntity.plaintiffName.ifBlank { "[ከሰነዱ ውሰድ]" }} | ጠበቃ: ${caseEntity.plaintiffCounsel.ifBlank { "-" }}")
        sb.appendLine("ተከሳሽ/መልስ ሰጪ: ${caseEntity.defendantName.ifBlank { "[ከሰነዱ ውሰድ]" }} | ጠበቃ/ነገረፈጅ: ${caseEntity.defendantCounsel.ifBlank { "-" }}")
        if (caseEntity.specialInstructions.isNotBlank()) {
            sb.appendLine("### የዳኛው ልዩ መመሪያ፣ የምስክር ቃል እና በፍርድ ቤት የቀረቡ ማስረጃዎች (በማስረጃ ምዘናና በትንታኔ ውስጥ በጥንቃቄ ተጠቀም)")
            sb.appendLine(caseEntity.specialInstructions)
        }
        sb.appendLine()
        sb.appendLine("### የከሳሽ / ይግባኝ ባይ ሰነዶች ጽሁፍ")
        sb.appendLine(plaintiffText.ifBlank { "[ምንም ጽሁፍ አልቀረበም]" })
        sb.appendLine()
        sb.appendLine("### የተከሳሽ / መልስ ሰጪ ሰነዶች ጽሁፍ")
        sb.appendLine(defendantText.ifBlank { "[ምንም ጽሁፍ አልቀረበም]" })
        sb.appendLine()
        sb.appendLine(
            """
### የመጨረሻ የውጤት ቅርጽ ጥብቅ ማረጋገጫ (ከመጻፍህ በፊት አረጋግጥ)
1. ቅደም ተከተሉ በፍጹም መዛባት የለበትም:
   ራስጌ -> መግቢያ -> "ፍ ር ድ" (ወይም "ብ ይ ን") -> [1. የግራ ቀኙ ክርክር ማጠቃለያ -> 2. ጭብጥ (እዚሁ ከክርክሩ ቀጥሎ!) -> 3. የማስረጃ ምዘናና የህግ ትንታኔ -> 4. "... ወስነናል/ተበይኗል"] -> "ው ሳ ኔ" (ወይም "ት ዕ ዛ ዝ") -> ወጪ፣ ይግባኝ፣ መዝጊያ እና ፊርማ።
2. **ጭብጥን በሰነዱ መጨረሻ ላይ፣ ከትንታኔ በኋላ፣ ከውሳኔ በኋላ ወይም በ[SECTION:JUDGE_NOTES] ውስጥ በፍጹም አታስቀምጥ!** ጭብጥ ከክርክር ማጠቃለያ ወዲያውኑ ቀጥሎ ከህግ ትንታኔ በፊት መሆን አለበት።
3. [SECTION:ISSUES]፣ [SECTION:ANALYSIS]፣ [SECTION:DECISION] ወይም [SECTION:CLOSING] የሚሉ መለያዎችን በፍጹም አትጠቀም።
4. ከሰነዱ ሙሉ በሙሉ ከወጣህ በኋላ ያልተረጋገጠ የህግ አንቀጽ ወይም የማስረጃ ክፍተት ካለ ብቻ በመጨረሻ በሚከተለው መለያ አስቀምጥ (ጭብጥ እዚህ ውስጥ በፍጹም አይገባም):
[SECTION:JUDGE_NOTES]
(ያልተረጋገጡ አንቀጾች ወይም የማስረጃ ክፍተቶች ብቻ)
            """.trimIndent()
        )
        return sb.toString()
    }

    fun buildRevisionPrompt(
        caseEntity: CaseEntity,
        targetSection: DraftSection,
        userRequest: String,
        recentHistorySummary: String,
        similarReferences: List<ScoredReferenceCase> = emptyList(),
    ): String {
        val currentUnifiedDoc = JudicialDocumentFormatter.composeUnifiedDocument(caseEntity)
        val focusNote = if (targetSection == DraftSection.ALL) {
            "ሙሉው አንድ ወጥ ውሳኔ/ብይን ሰነድ"
        } else {
            "በሰነዱ ውስጥ ያለው '${targetSection.amharicLabel}' ሀሳብ (ሙሉውን አንድ ወጥ ሰነድ ቅርጽ እንደጠበቀ)"
        }
        val refBlock = if (similarReferences.isNotEmpty()) {
            buildString {
                appendLine("ተዛማጅ የArchive ማጣቀሻ ውሳኔዎች (ካስፈለገ የህግ አተገባበርንና የዳኛውን የአፃፃፍ ስልት ለማየት):")
                similarReferences.take(2).forEach { scored ->
                    val r = scored.caseEntity
                    appendLine("- መ/ቁ ${r.caseNumber}: ${JudicialDocumentFormatter.composeUnifiedDocument(r).take(900)}")
                }
            }
        } else ""

        return """
የዳኛው የማሻሻያ ጥያቄ: "$userRequest"
የትኩረት አቅጣጫ: $focusNote
${if (recentHistorySummary.isNotBlank()) "የቀድሞ ውይይት ማጠቃለያ:\n$recentHistorySummary\n" else ""}${if (refBlock.isNotBlank()) "$refBlock\n" else ""}
አሁን ያለው ሙሉ አንድ ወጥ የፍርድ ቤት ውሳኔ/ብይን ሰነድ:
$currentUnifiedDoc

ጥብቅ መመሪያ:
1. የተጠየቀውን ማሻሻያ በዳኛ ኑህ መሀመድ የአፃፃፍ ስልት እና በአንድ ወጥ የፍርድ ቤት ሰነድ ቅርጽ ላይ ተግብረህ ሙሉውን የተሻሻለ ሰነድ በrevisedText ውስጥ መልስ።
2. ትክክለኛው ቅደም ተከተል: የመ/ቁ ራስጌ -> ፍ ር ድ / ብ ይ ን (ክርክር ማጠቃለያ -> ጭብጥ ወዲያውኑ ከክርክር ቀጥሎ -> የማስረጃና የህግ ትንታኔ -> "... ወስነናል/ተበይኗል") -> ው ሳ ኔ / ት ዕ ዛ ዝ -> መዝጊያ።
3. **ጭብጥን በመጨረሻ ላይ ወይም ከውሳኔ/ትንታኔ በኋላ በፍጹም አታስቀምጥ!** አሁን ባለው ረቂቅ ላይ ጭብጥ ወደ መጨረሻ ተገፍቶ ከሆነ ራሱ ወደ ትክክለኛው ቦታ (ከክርክር ቀጥሎ ከትንታኔ በፊት) አስተካክለው።
4. ከለውጡ በኋላ ህጋዊ ወጥነት መጠበቁን አረጋግጥ፤ ልዩነት ካለ በconsistencyWarning ውስጥ ጥቀስ።
5. ምላሹ በንጹህ JSON ብቻ ይሁን።
""".trimIndent()
    }

    fun buildChatSummaryPrompt(messagesText: String): String = """
የሚከተሉትን የዳኛውና የረዳት ዳኛ ውይይት መልዕክቶች የህግ ነጥቦቹንና የተደረጉ የረቂቅ ለውጦችን ሳታጣ በ3-5 አጫጭር ነጥቦች በአማርኛ ጠቅልል:
$messagesText
""".trimIndent()

    fun buildConversationalChatPrompt(
        caseEntity: CaseEntity,
        userMessage: String,
        recentHistorySummary: String,
        similarReferences: List<ScoredReferenceCase> = emptyList(),
    ): String {
        val hasDraft = JudicialDocumentFormatter.hasDraftContent(caseEntity)
        val sb = StringBuilder()
        if (caseEntity.caseNumber.isNotBlank()) {
            sb.appendLine("የተከፈተ መዝገብ: የመ/ቁ ${caseEntity.caseNumber} (${caseEntity.caseType.amharicLabel})")
        }
        sb.appendLine("የረቂቅ ሁኔታ: ${if (hasDraft) "ረቂቅ ውሳኔ ተዘጋጅቷል (ስሪት v${caseEntity.currentVersion})" else "ገና ረቂቅ አልተዘጋጀም"}")
        if (hasDraft) {
            val snippet = JudicialDocumentFormatter.extractPreviewSnippet(caseEntity)
            if (snippet.isNotBlank()) {
                sb.appendLine("የመዝገቡ ጭብጥ/ይዘት ማጠቃለያ: ${snippet.take(280)}")
            }
        }
        if (similarReferences.isNotEmpty()) {
            sb.appendLine("ከArchive የተገኙ ተመሳሳይ የቀድሞ መዝገቦች (ለማጣቀሻ):")
            similarReferences.take(2).forEach { scored ->
                val r = scored.caseEntity
                sb.appendLine("- የመ/ቁ ${r.caseNumber.ifBlank { "-" }} (${r.caseType.amharicLabel}): ${JudicialDocumentFormatter.extractPreviewSnippet(r).take(260)}")
            }
        }
        if (recentHistorySummary.isNotBlank()) {
            sb.appendLine("የቅርብ ውይይት:\n$recentHistorySummary")
        }
        sb.appendLine()
        sb.appendLine("የዳኛው መልዕክት: \"$userMessage\"")
        return sb.toString().trimIndent()
    }
}
