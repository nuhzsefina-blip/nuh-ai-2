package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.security.SecurePrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class JudicialTypeConverters {
    @TypeConverter fun fromCaseType(v: CaseType): String = v.name
    @TypeConverter fun toCaseType(v: String): CaseType = CaseType.valueOf(v)

    @TypeConverter fun fromCaseStatus(v: CaseStatus): String = v.name
    @TypeConverter fun toCaseStatus(v: String): CaseStatus = CaseStatus.valueOf(v)

    @TypeConverter fun fromQualityRating(v: QualityRating): String = v.name
    @TypeConverter fun toQualityRating(v: String): QualityRating = QualityRating.valueOf(v)

    @TypeConverter fun fromBadReason(v: BadReasonCategory): String = v.name
    @TypeConverter fun toBadReason(v: String): BadReasonCategory = BadReasonCategory.valueOf(v)

    @TypeConverter fun fromSyncStatus(v: SyncStatus): String = v.name
    @TypeConverter fun toSyncStatus(v: String): SyncStatus = SyncStatus.valueOf(v)

    @TypeConverter fun fromDraftSection(v: DraftSection): String = v.name
    @TypeConverter fun toDraftSection(v: String): DraftSection = DraftSection.valueOf(v)

    @TypeConverter fun fromChatRole(v: ChatRole): String = v.name
    @TypeConverter fun toChatRole(v: String): ChatRole = ChatRole.valueOf(v)
}

@Database(
    entities = [
        CaseEntity::class,
        CaseFtsEntity::class,
        OcrCacheEntity::class,
        CaseVersionEntity::class,
        ChatMessageEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(JudicialTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, prefs: SecurePrefsManager): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, prefs).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, prefs: SecurePrefsManager): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = prefs.getOrCreateSqlCipherKey()
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "redat_dagna_encrypted.db"
            )
                .openHelperFactory(factory)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedAnonymizedTemplates(getInstance(context, prefs).caseDao())
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            ensureLatestSeedTemplates(getInstance(context, prefs).caseDao())
                        }
                    }
                })
                .build()
        }

        private suspend fun ensureLatestSeedTemplates(dao: CaseDao) {
            val existing = dao.getAllSeedTemplates()
            val needsRefresh = existing.size != 3 ||
                existing.any { it.issuesText.isNotBlank() || it.decisionText.isNotBlank() }
            if (needsRefresh) {
                dao.deleteSeedTemplates()
                seedAnonymizedTemplates(dao)
            }
        }

        private suspend fun seedAnonymizedTemplates(dao: CaseDao) {
            val seeds = listOf(
                CaseEntity(
                    caseNumber = "10001",
                    caseType = CaseType.APPEAL,
                    ethiopianDate = "17/09/2018",
                    judges = "አብራሂም ረሺድ\nኑህ መሀመድ\nቲማጅ አብዱልቃድር",
                    plaintiffName = "ይግባኝ ባይ 1",
                    plaintiffCounsel = "ጠበቃ 1",
                    defendantName = "መልስ ሰጭ 1",
                    issuesText = "",
                    analysisText = """የመ/ቁ:- 10001
ቀን:- 17/09/2018 ዓ.ም

ዳኞች:-
አብራሂም ረሺድ
ኑህ መሀመድ
ቲማጅ አብዱልቃድር

ይግባኝ ባይ፦ ይግባኝ ባይ 1
ጠበቃ፡- ጠበቃ 1
መልስ ሰጭ:- መልስ ሰጭ 1

መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።

ፍ ር ድ

ለዚህ ውሳኔ ምክንያት የሆነው ይግባኝ ባይ በቀን 01/07/2018 ዓ.ም ባቀረቡት የይግባኝ አቤቱታ አማካኝነት ሲሆን ይዘቱም በአጭሩ፣ የፍርድ ባለመብቶች የተወሰነላቸውን መብት በሽያጭ ውል ስለተላለፈልኝ በአፈፃፀም መዝገቡ ውስጥ ጣልቃ ገብቼ ልከራከር የሚል ሲሆን የስር ፍ/ቤት ውድቅ ማድረጉ መሰረታዊ የህግ ስህተት ነው የሚል ነው።

መልስ ሰጪ የይግባኝ አቤቱታው ደርሷቸው መልስ የሰጡ ሲሆን ይዘቱም በአጭሩ፣ ውሉ የፍርድ ቤት እግድ እያለና ስልጣን ባለው አካል ፊት ሳይደረግ የተፈፀመ በመሆኑ ቅሬታው ውድቅ እንዲደረግ የሚል ነው።

የግራ ቀኙ የይግባኝ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን ክርክርና መዝገብ ከመረመረ በኋላ የሚከተለውን ጭብጥ ይዟል፦
የስር ፍ/ቤት ይግባኝ ባይ በነበረ የአፈፃፀም መዝገብ ላይ ጣልቃ ገብ ለመሆን ያቀረቡትን አቤቱታ ውድቅ ማድረጉ አግባብ ነው ወይስ አይደለም?

ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል። በፍ/ብ/ሥ/ሥ/ሕ/ቁ 381 (1) እና (2) መሰረት መብት በህግ አግባብ የተላለፈለት ሰው እንደፍርድ ባለመብት ሆኖ መቅረብ ይችላል። ከዚህ አንፃር መልስ ሰጪ ውሉ የማይንቀሳቀስ ንብረት ሽያጭ ነው በሚል ያነሱትን መከራከሪያ አልተቀበልነውም። በመሆኑም የስር ፍ/ቤት አቤቱታውን ውድቅ ማድረጉ መሰረታዊ የህግ ስህተት ነው በማለት የሚከተለውን ወስነናል።

ው ሳ ኔ

1. በፍ/ሥ/ሥ ሕግ አንቀፅ 348(2) መሰረት የስር ፍ/ቤት በመ/ቁ 10000 በቀን 10/07/2018 ዓ/ም የሰጠው ብይን ተሽሯል።
2. የስር ፍርድ ቤት ይግባኝ ባይ በአፈፃፀም መዝገብ ላይ ጣልቃ ገብ ለመሆን ያቀረቡትን አቤቱታ ተቀብሎ መዝገቡን ያስቀጥል በማለት መዝገቡን መልሰናል።

ግራ ቀኙ በዚህ ፍ/ቤት ላወጡት ወጪ የየራሳቸውን ይቻሉ ብለናል።
በውሳኔው ቅር የተሰኘ ይግባኝ መብት ነው።
በዚህ ችሎት የተሰጠ እግድ ካለ ተነስቷል።
መዝገቡ ተዘግቷል መዝገብ ቤት ይመለስ።
የማይነበብ የሶስቱም ዳኞች ፊርማ አለበት።""",
                    decisionText = "",
                    closingText = "",
                    status = CaseStatus.FINALIZED,
                    qualityRating = QualityRating.EXCELLENT,
                    syncStatus = SyncStatus.SYNCED,
                    isSeedTemplate = true,
                ),
                CaseEntity(
                    caseNumber = "10002",
                    caseType = CaseType.DIRECT_CIVIL,
                    ethiopianDate = "06/11/2018",
                    judges = "ኑህ መሀመድ",
                    plaintiffName = "1ኛ. ከሳሽ 1\n2ኛ. ከሳሽ 2",
                    plaintiffCounsel = "ጠበቃ 1",
                    defendantName = "1ኛ. ተከሳሽ 1\n2ኛ. ተከሳሽ 2\n3ኛ. ተከሳሽ 3",
                    issuesText = "",
                    analysisText = """የመ/ቁ:- 10002
ቀን:- 06/11/2018 ዓ.ም

ዳኛ፦ ኑህ መሀመድ

ከሳሽ/ከሳሾች፤ 1ኛ. ከሳሽ 1፣ 2ኛ. ከሳሽ 2
ጠበቃ፣ ጠበቃ 1
ተከሳሽ/ተከሳሾች፤ 1ኛ. ተከሳሽ 1፣ 2ኛ. ተከሳሽ 2፣ 3ኛ. ተከሳሽ 3

መዝገቡ የተቀጠረው ለውሳኔ ሲሆን ተመርምሮ የሚከተለው ተወስኗል።

ፍ ር ድ

ለዚህ ውሳኔ ምክንያት የሆነው ከሳሾች በቀን 01/12/2017 ዓ/ም ባቀረቡት ክስ አማካኝነት ሲሆን ይዘቱም በአጭሩ፣ በደረሰ የትራፊክ አደጋ ምክንያት ለደረሰ ጉዳት የቀለብ፣ የቀብርና ልዩ ልዩ ወጪ ካሳ ተከሳሾች በአንድነትና በተናጠል እንዲከፍሉ የሚል ነው።

ተከሳሾች ክሱ እንዲደርሳቸው ተደርጎ መልስ የሰጡ ሲሆን፣ 1ኛ እና 2ኛ ተከሳሽ ድርጊቱ በህግ ማስከበር ላይ እያለ በቴክኒክ ችግር የደረሰ እንጂ በቸልተኝነት አይደለም ሲሉ፣ 3ኛ ተከሳሽ አደጋው በደረሰበት ወቅት ንብረቷ በባለቤትነት ቁጥጥሯ ስር አልነበረም ብለዋል።

የግራ ቀኙ መከራከሪያዎች እና ማስረጃዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን ክርክርና ማስረጃ ከመረመረ በኋላ የሚከተሉትን ጭብጦች ይዟል፦
1. ለአደጋው መከሰት ተጠያቂው አካል ማን ነው? 3ኛ ተከሳሽስ በባለቤትነቷ ብቻ ልትጠየቅ ትችላለች ወይስ አትችልም?
2. ከሳሾች ቀደም ሲል ከተከፈላቸው ክፍያ ውጪ ተጨማሪ የቀለብ እና የልዩ ልዩ ወጪ የመጠየቅ መብት አላቸው ወይ?

ይህንንም ጭብጥ በቀረቡት የፅሑፍ ክርክሮችና የሰነድ ማስረጃዎች ላይ ብቻ ተመስርተን እንደሚከተለው መርምረናል። የፍ/ብ ሕግ አንቀፅ 2081 የተሽከርካሪ ባለቤትን ኃላፊነት ቢደነግግም የፌዴራል ሰበር ሰሚ ችሎት በመዝገብ ቁጥር 37691 በሰጠው አስገዳጅ የህግ ትርጓሜ መሰረት ንብረቱ በህግ አስከባሪ ቁጥጥር ስር ሲውል ባለቤቱ ተጠያቂ አይሆንም። ወደ ሁለተኛው ጭብጥ ስንመለስ ደግሞ የፍ/ብ ሕግ አንቀፅ 2095 የቀለብ ጥያቄ በማስረጃ ባለመረጋገጡ ውድቅ ሆኗል፤ ነገር ግን ቀጥተኛ የቀብር ወጪዎች በፍ/ብ/ሕ/ቁ 2110 እና 2102 መሰረት በርትዕ ሊወሰኑ ይገባል በማለት የሚከተለውን ወስነናል።

ው ሳ ኔ

1. በፍ/ብ/ሕ/ቁ 2110 እና 2102 መሠረት በርትዕ የተወሰነ ቀጥተኛ የቀብር ወጪ 300,000 ብር (ሶስት መቶ ሺህ ብር) 1ኛ እና 2ኛ ተከሳሾች ለከሳሾች ይክፈሉ በማለት ወስነናል።
2. በ3ኛ ተከሳሽ ላይ የቀረበው ክስ ውድቅ ተደርጓል።
3. የቀለብ ጥያቄው ውድቅ ተደርጓል።

ከሳሽ በዚህ ፍርድ ቤት ላወጡት ወጪ የዳኝነት በታሪፉ ልክ፣ የጠበቃ አበል በውላቸው መሰረት 1ኛ እና 2ኛ ተከሳሾች ይክፈሉ ብለናል።
በውሳኔው ቅር የተሰኘ ይግባኝ መብት ነው።
መዝገቡ ተዘግቷል፤ ወደ መዝገብ ቤት ይመለስ።
የማይነበብ የዳኛው ፊርማ አለበት።""",
                    decisionText = "",
                    closingText = "",
                    status = CaseStatus.FINALIZED,
                    qualityRating = QualityRating.EXCELLENT,
                    syncStatus = SyncStatus.SYNCED,
                    isSeedTemplate = true,
                ),
                CaseEntity(
                    caseNumber = "10003",
                    caseType = CaseType.DECREE,
                    ethiopianDate = "06/11/2018",
                    judges = "ኑህ መሀመድ",
                    plaintiffName = "ከሳሽ 1",
                    plaintiffCounsel = "ጠበቃ 1 (ቀረቡ)",
                    defendantName = "1ኛ. ተከሳሽ 1 (ቀረቡ)\n2ኛ. ተከሳሽ 2",
                    defendantCounsel = "ጠበቃ 2 (ቀረቡ)",
                    issuesText = "",
                    analysisText = """የመ/ቁ:- 10003
ቀን:- 06/11/2018 ዓ.ም

ዳኛ፦ ኑህ መሀመድ

ከሳሽ፤ ከሳሽ 1
ጠበቃ፣ ጠበቃ 1 (ቀረቡ)
ተከሳሽ፤ 1ኛ. ተከሳሽ 1 (ቀረቡ)፣ 2ኛ. ተከሳሽ 2
ጠበቃ፣ ጠበቃ 2 (ቀረቡ)

መዝገቡ የተቀጠረው ለብይን ሲሆን ተመርምሮ የሚከተለው ተበይኗል።

ብ ይ ን

ለዚህ ብይን ምክንያት የሆነው ከሳሽ በቀን 19/08/2018 ዓ.ም ባቀረቡት ክስ ሲሆን ይዘቱም በአጭሩ፣ የውል ክርክርና በአስተዳደር አካል የተያዘ ንብረት እንዲመለስ የሚል ነው።

ተከሳሾች ክሱ እንዲደርሳቸው ተደርጎ የመጀመሪያ ደረጃ መቃወሚያ አቅርበዋል። 1ኛ ተከሳሽ አስተዳደራዊ የቅሬታ ሰሚ አካል ስላለ ፍርድ ቤቱ የዳኝነት ስልጣን የለውም ሲል፣ 2ኛ ተከሳሽ የዳኝነት ክፍያ ጉድለት እና የክሶች አለመጣጣም አለ ብላለች።

የግራ ቀኙ የመጀመሪያ ደረጃ መቃወሚያ መከራከሪያዎች አጠር ባለ መልኩ ከላይ የሰፈረው ሲሆን፣ ፍርድ ቤቱም ግራ ቀኙ ያቀረቡትን መቃወሚያ ከመረመረ በኋላ የሚከተሉትን ጭብጦች ይዟል፦
1. ይህ ፍርድ ቤት የቀረበውን ክስ አይቶ የመወሰን የዳኝነት ሥልጣን አለው ወይስ የለውም?
2. 2ኛ ተከሳሽ ያነሳችው የክሶች አለመጣጣም (Misjoinder) መቃወሚያ አግባብነት አለው ወይስ የለውም?

ይህንንም ጭብጥ ከህጉ ጋር በማገናዘብ እንደሚከተለው መርምረናል። የክሶች አለመጣጣም ክሱን እንዲሻሻል ከማድረግ ውጪ መዝገቡን አያዘጋውም። ወደ ስልጣን ጭብጥ ስንመለስ ግን በአዋጅ ቁጥር 859/2006 አንቀጽ 154 እና 155 እንዲሁም በአዋጅ ቁጥር 1183/2012 አንቀጽ 49 መሰረት ጉዳዩን የማየት ቁሳዊ ስልጣን የዚህ ፍርድ ቤት ባለመሆኑ በፍትሐ-ብሔር ሥነ-ሥርዓት ሕግ ቁጥር 244(2)(ሀ) መሰረት ክሱ ውድቅ በማድረግ ተበይኗል።

ት ዕ ዛ ዝ

1. በከሳሽ ክስ በዚህ ፍርድ ቤት ታይቶ ለመወሰን የዳኝነት ሥልጣን የሌለን በመሆኑ፣ በፍ/ሥ/ሥ ሕግ አንቀፅ 244(2)(ሀ) መሠረት ክሱ ውድቅ ተደርጎ ተበይኗል።
2. ከሳሽ መብታቸውን ሥልጣን ባለው አስተዳደራዊ አካል ሂደቱን አሟጠው ወይም ክሱን ነጥለው በ2ኛ ተከሳሽ ላይ ብቻ ለይተው በክስ የመጠየቅ መብታቸው የተጠበቀ ነው።

ግራ ቀኙ በዚህ ፍ/ቤት ላወጡት ወጪ የየራሳቸውን ይቻሉ ብለናል።
በብይኑ ቅር የተሰኘ ይግባኝ መብት ነው።
መዝገቡ ተዘግቷል መዝገብ ቤት ይመለስ።
የማይነበብ የዳኛው ፊርማ አለበት።""",
                    decisionText = "",
                    closingText = "",
                    status = CaseStatus.FINALIZED,
                    qualityRating = QualityRating.EXCELLENT,
                    syncStatus = SyncStatus.SYNCED,
                    isSeedTemplate = true,
                ),
            )
            seeds.forEach { dao.insertCase(it) }
        }
    }
}
