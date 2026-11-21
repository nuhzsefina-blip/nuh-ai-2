package com.example.util

import com.example.data.local.CaseType

data class JudicialPreset(
    val id: String,
    val titleAmharic: String,
    val subtitleAmharic: String,
    val caseType: CaseType,
    val subjectTag: String,
    val aiInstruction: String,
    val starterTemplate: String,
)

object JudicialPresets {
    val allPresets: List<JudicialPreset> = listOf(
        JudicialPreset(
            id = "preliminary_objection",
            titleAmharic = "የመጀመሪያ ደረጃ መቃወሚያ ብይን",
            subtitleAmharic = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 244 (ሥልጣን፣ ይርጋ፣ የክስ ምክንያት)",
            caseType = CaseType.DECREE,
            subjectTag = "አስተዳደር",
            aiInstruction = "በተከሳሽ የቀረበውን የመጀመሪያ ደረጃ መቃወሚያ ብቻ (በፍ/ብ/ሥ/ሥ/ሕ/ቁ 244 መሰረት የዳኝነት ሥልጣን፣ የክርክር መቆራረጥ/Res Judicata፣ ይርጋ ወይም የክሶች አለመጣጣም) ላይ አተኩረህ ብይን አዘጋጅ።",
            starterTemplate = """የመ/ቁ:- ____
ቀን:- ____ ዓ.ም

ዳኛ፦ ኑህ መሀመድ

ከሳሽ፤ ____
ተከሳሽ፤ ____

መዝገቡ የተቀጠረው ለብይን ሲሆን ተመርምሮ የሚከተለው ተበይኗል።

ብ ይ ን

ለዚህ ብይን ምክንያት የሆነው ከሳሽ ባቀረቡት ክስ ላይ ተከሳሽ በፍ/ብ/ሥ/ሥ/ሕ/ቁ 244 መሰረት ያቀረቡት የመጀመሪያ ደረጃ መቃወሚያ ነው።

የግራ ቀኙ የመጀመሪያ ደረጃ መቃወሚያ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን መቃወሚያ ከመረመረ በኋላ የሚከተለውን ጭብጥ ይዟል፦
1. ተከሳሽ ያነሳው የመጀመሪያ ደረጃ መቃወሚያ በህግ አግባብ ተቀባይነት አለው ወይስ የለውም?

ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል።

ት ዕ ዛ ዝ

1. የቀረበው የመጀመሪያ ደረጃ መቃወሚያ ____ ተብሎ ተበይኗል።
2. መዝገቡ ____።""",
        ),
        JudicialPreset(
            id = "injunction_decree",
            titleAmharic = "የእግድ / ማገጃ ብይን",
            subtitleAmharic = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 154–156 (ጊዜያዊ እግድ)",
            caseType = CaseType.DECREE,
            subjectTag = "ንብረት",
            aiInstruction = "የቀረበውን የጊዜያዊ እግድ / የንብረት ማገጃ አቤቱታ በፍ/ብ/ሥ/ሥ/ሕ/ቁ 154–156 መሰረት (ሊተካ የማይችል ጉዳት እና ቀዳሚ መብት/Prima Facie መኖሩን) መርምረህ ብይን አዘጋጅ።",
            starterTemplate = """የመ/ቁ:- ____
ቀን:- ____ ዓ.ም

ዳኛ፦ ኑህ መሀመድ

አመልካች / ከሳሽ፤ ____
ተጠሪ / ተከሳሽ፤ ____

መዝገቡ የተቀጠረው ለብይን ሲሆን ተመርምሮ የሚከተለው ተበይኗል።

ብ ይ ን

ለዚህ ብይን ምክንያት የሆነው አመልካች በፍ/ብ/ሥ/ሥ/ሕ/ቁ 154 መሰረት ጊዜያዊ የእግድ ትዕዛዝ እንዲሰጥ ባቀረቡት አቤቱታ አማካኝነት ነው።

የግራ ቀኙ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም የቀረበውን አቤቱታና ማስረጃ ከመረመረ በኋላ የሚከተለውን ጭብጥ ይዟል፦
1. አመልካች የጠየቁት ጊዜያዊ የእግድ ትዕዛዝ በፍ/ብ/ሥ/ሥ/ሕ/ቁ 154 እና 155 መሰረት ሊሰጥ ይገባል ወይስ አይገባም?

ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል።

ት ዕ ዛ ዝ

1. በተጠቀሰው ንብረት ላይ ዋናው ክርክር እስኪወሰን ድረስ ____።
2. ትዕዛዙ ለሚመለከተው አካል ይጻፍ።""",
        ),
        JudicialPreset(
            id = "witness_adjournment",
            titleAmharic = "የምስክር እና የቀጠሮ ትዕዛዝ",
            subtitleAmharic = "ምስክር መጥሪያ፣ የማስረጃ ትዕዛዝ እና ቀጠሮ",
            caseType = CaseType.DECREE,
            subjectTag = "ውል",
            aiInstruction = "ለምስክር መስማት፣ ለተጨማሪ የሰነድ ማስረጃ ከሚመለከተው መስሪያ ቤት እንዲቀርብ ወይም መዝገቡን ለቀጣይ ቀጠሮ ለማስተላለፍ አጭርና ግልጽ የችሎት ትዕዛዝ አዘጋጅ።",
            starterTemplate = """የመ/ቁ:- ____
ቀን:- ____ ዓ.ም

ዳኛ፦ ኑህ መሀመድ

ከሳሽ፤ ____
ተከሳሽ፤ ____

መዝገቡ የተቀጠረው ለ____ ሲሆን ተመርምሮ የሚከተለው ተበይኗል።

ብ ይ ን

በመዝገቡ ላይ የቀረበው የማስረጃና የምስክር ዝርዝር ተመርምሯል።

ት ዕ ዛ ዝ

1. ለከሳሽ/ለተከሳሽ ምስክሮች በፖሊስ/በፍርድ ቤት መጥሪያ ይውጣ።
2. የሚፈለገው የሰነድ ማስረጃ ከሚመለከተው አካል እንዲቀርብ ደብዳቤ ይጻፍ።
3. መዝገቡ ለምስክር መስማት ለቀን ____ ዓ.ም ተቀጥሯል።""",
        ),
        JudicialPreset(
            id = "execution_intervention",
            titleAmharic = "የአፈጻጸም / ጣልቃ ገብነት",
            subtitleAmharic = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 381 / 418 (ይግባኝ ወይም አፈጻጸም)",
            caseType = CaseType.APPEAL,
            subjectTag = "አፈጻጸም",
            aiInstruction = "በአፈጻጸም መዝገብ ላይ የቀረበ የመብት መተላለፍ ወይም የንብረት ተቃውሞ (በፍ/ብ/ሥ/ሥ/ሕ/ቁ 381 እና 418 መሰረት) ላይ አተኩረህ ረቂቅ ውሳኔ አዘጋጅ።",
            starterTemplate = "",
        ),
        JudicialPreset(
            id = "family_property",
            titleAmharic = "የቤተሰብ / ፍቺና ንብረት ክፍፍል",
            subtitleAmharic = "የጋራ ንብረት፣ ቀለብ እና የህፃናት አስተዳደግ",
            caseType = CaseType.DIRECT_CIVIL,
            subjectTag = "ቤተሰብ",
            aiInstruction = "በቤተሰብ ሕጉ መሰረት የጋራ ንብረት ክፍፍል፣ የትዳር ወቅት ዕዳ፣ የልጆች ጥቅም (Best Interest of the Child) እና የቀለብ ውሳኔን በዝርዝር መዝን።",
            starterTemplate = "",
        ),
    )
}
