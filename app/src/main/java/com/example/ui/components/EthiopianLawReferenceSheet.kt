package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VerifiedEmerald

enum class LawCodeCategory(val label: String) {
    ALL("ሁሉም"),
    CIVIL_PROC("ፍ/ብ/ሥ/ሥ/ሕግ"),
    CIVIL_CODE("ፍትሐ ብሔር ሕግ"),
    FAMILY("ቤተሰብ ሕግ"),
    CRIMINAL("ወንጀል ሕግ"),
    CASSATION("ሰበር መርሆዎች"),
}

data class LawProvisionItem(
    val id: String,
    val category: LawCodeCategory,
    val citation: String,
    val titleAmharic: String,
    val summaryAmharic: String,
)

object EthiopianLawCatalog {
    val provisions: List<LawProvisionItem> = listOf(
        LawProvisionItem(
            id = "cpc_33",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 33",
            titleAmharic = "የመክሰስ መብትና ጥቅም (Standing)",
            summaryAmharic = "በክሱ ጉዳይ ላይ ህጋዊ መብት ወይም ጥቅም የሌለው ሰው ክስ ማቅረብ አይችልም።",
        ),
        LawProvisionItem(
            id = "cpc_154",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 154–156",
            titleAmharic = "ጊዜያዊ እግድ / ማገጃ ትዕዛዝ",
            summaryAmharic = "ክርክሩ እስኪወሰን ንብረቱ እንዳይጠፋ ወይም እንዳይተላለፍ ሊተካ የማይችል ጉዳትን ለመከላከል እግድ ሊሰጥ ይችላል።",
        ),
        LawProvisionItem(
            id = "cpc_231",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 231",
            titleAmharic = "የክስ አቤቱታ ውድቅ ማድረግ",
            summaryAmharic = "የክሱ አቤቱታ የክስ ምክንያት (Cause of Action) የሌለው ወይም በህግ የተከለከለ ሲሆን ክሱ አይቀበልም።",
        ),
        LawProvisionItem(
            id = "cpc_244",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 244(2)",
            titleAmharic = "የመጀመሪያ ደረጃ መቃወሚያዎች",
            summaryAmharic = "የዳኝነት ሥልጣን እጦት (ሀ)፣ በተመሳሳይ ጉዳይ ክስ መኖር/Res Judicata (ለ)፣ ችሎታ ማጣት፣ ይርጋና የክሶች አለመጣጣም።",
        ),
        LawProvisionItem(
            id = "cpc_246",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 246–248",
            titleAmharic = "ጭብጥ መያዝ (Framing Issues)",
            summaryAmharic = "በግራ ቀኙ መካከል የፍሬ ነገር ወይም የህግ ልዩነት በተፈጠረባቸው ነጥቦች ላይ ፍ/ቤቱ ግልጽ ጭብጥ ይይዛል።",
        ),
        LawProvisionItem(
            id = "cpc_348",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 348(1) እና (2)",
            titleAmharic = "የይግባኝ ውሳኔ (ማፅናት ወይም መሻር/መለወጥ)",
            summaryAmharic = "348(1) የስር ፍ/ቤት ውሳኔን ማፅናት፣ መሻር ወይም ማሻሻል፤ 348(2) በመጀመሪያ ደረጃ ነጥብ የተዘጋን መዝገብ ወደ ስር ፍ/ቤት መመለስ።",
        ),
        LawProvisionItem(
            id = "cpc_381",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 381(1)-(2)",
            titleAmharic = "በአፈጻጸም መብት የተላለፈለት ሰው",
            summaryAmharic = "የተወሰነ መብት በውል ወይም በህግ የተላለፈለት ሰው እንደፍርድ ባለመብት በአፈጻጸም መዝገብ ላይ አቤቱታ ማቅረብ ይችላል።",
        ),
        LawProvisionItem(
            id = "cpc_418",
            category = LawCodeCategory.CIVIL_PROC,
            citation = "ፍ/ብ/ሥ/ሥ/ሕ/ቁ 418",
            titleAmharic = "በተያዘ ንብረት ላይ ጣልቃ ገብ ተቃውሞ",
            summaryAmharic = "በአፈጻጸም የተያዘው ንብረት የእኔ ነው የሚል ሦስተኛ ወገን የንብረቱን ባለቤትነት በማስረዳት እንዲለቀቅ መጠየቅ ይችላል።",
        ),
        LawProvisionItem(
            id = "cc_1195",
            category = LawCodeCategory.CIVIL_CODE,
            citation = "ፍ/ብ/ሕ/ቁ 1195–1196",
            titleAmharic = "የማይንቀሳቀስ ንብረት ባለቤትነት ማረጋገጫ (ካርታ)",
            summaryAmharic = "በስልጣን ባለው አካል የተሰጠ የባለቤትነት ምስክር ወረቀት ተቃራኒው እስካልተረጋገጠ ድረስ የባለቤትነት ግምት ይፈጥራል።",
        ),
        LawProvisionItem(
            id = "cc_1678",
            category = LawCodeCategory.CIVIL_CODE,
            citation = "ፍ/ብ/ሕ/ቁ 1678 እና 1731",
            titleAmharic = "የውል መሰረታዊ መስፈርቶችና አስገዳጅነት",
            summaryAmharic = "በህግ አግባብ የተቋቋመ ውል በተዋዋዮች መካከል እንደ ህግ ሆኖ ያስገድዳል (Pacta Sunt Servanda)።",
        ),
        LawProvisionItem(
            id = "cc_1845",
            category = LawCodeCategory.CIVIL_CODE,
            citation = "ፍ/ብ/ሕ/ቁ 1845",
            titleAmharic = "የ10 ዓመት የይርጋ ጊዜ (Limitation)",
            summaryAmharic = "ልዩ የይርጋ ጊዜ ካልተደነገገ በቀር በውል ወይም በመብት ላይ የሚቀርብ ክስ በ10 ዓመት ውስጥ ካልቀረበ በይርጋ ይታገዳል።",
        ),
        LawProvisionItem(
            id = "cc_2081",
            category = LawCodeCategory.CIVIL_CODE,
            citation = "ፍ/ብ/ሕ/ቁ 2081, 2095, 2102, 2110",
            titleAmharic = "ከውል ውጪ ኃላፊነት (ተሽከርካሪ፣ ቀለብ እና በርትዕ ካሳ)",
            summaryAmharic = "2081 የተሽከርካሪ ባለቤት ኃላፊነት፤ 2095 የቀለብ ካሳ፤ 2102/2110 ትክክለኛው መጠን ሳይታወቅ በርትዕ ቀጥተኛ ወጪ መወሰን።",
        ),
        LawProvisionItem(
            id = "cc_2877",
            category = LawCodeCategory.CIVIL_CODE,
            citation = "ፍ/ብ/ሕ/ቁ 2877 እና 1723",
            titleAmharic = "የማይንቀሳቀስ ንብረት ሽያጭ ውል ቅርጽ",
            summaryAmharic = "የማይንቀሳቀስ ንብረት ሽያጭ ውል በጽሑፍ ተደርጎ በፍ/ቤት ወይም ውል ለማዋዋል ስልጣን ባለው አካል ፊት መረጋገጥ አለበት።",
        ),
        LawProvisionItem(
            id = "fam_common",
            category = LawCodeCategory.FAMILY,
            citation = "የተሻሻለው የቤተሰብ ሕግ አንቀጽ 62, 75–90",
            titleAmharic = "የባልና ሚስት የጋራ ንብረት፣ ፍቺ እና የልጆች ጥቅም",
            summaryAmharic = "በጋብቻ ወቅት በጥረት የተገኘ ንብረት የጋራ ነው፤ በፍቺ ጊዜ የልጆች አስተዳደግና ቀለብ በህፃናቱ ከፍተኛ ጥቅም (Best Interest) መሰረት ይወሰናል።",
        ),
        LawProvisionItem(
            id = "crim_bail",
            category = LawCodeCategory.CRIMINAL,
            citation = "የወ/ሥ/ሥ/ሕ/ቁ 63, 67 እና የወንጀል ሕግ አንቀጽ 87–88",
            titleAmharic = "ዋስትና (Bail) እና የቅጣት አወሳሰን",
            summaryAmharic = "ከባድ የማያስዋስስ ወንጀል ካልሆነ በቀር (63) ተከሳሽ በዋስ የመለቀቅ መብት አለው (67)፤ ቅጣት በማክበጃና ማቅለያ ይሰላል።",
        ),
        LawProvisionItem(
            id = "cass_1234",
            category = LawCodeCategory.CASSATION,
            citation = "አዋጅ ቁጥር 1234/2013 አንቀጽ 10(2) እና ሰበር መ/ቁ 37691",
            titleAmharic = "የሰበር አስገዳጅ የህግ ትርጓሜ",
            summaryAmharic = "በፌዴራል ጠቅላይ ፍ/ቤት ሰበር ሰሚ ችሎት 5 እና ከዚያ በላይ ዳኞች የተሰጠ የህግ ትርጓሜ በየደረጃው ባሉ ፍ/ቤቶች አስገዳጅ ነው። (መ/ቁ 37691: ንብረቱ በህግ አስከባሪ ስር ሲውል ባለቤቱ አይጠየቅም)።",
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EthiopianLawReferenceSheet(
    onDismiss: () -> Unit,
    onInsertCitation: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LawCodeCategory.ALL) }
    var copiedId by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filtered = remember(query, selectedCategory) {
        EthiopianLawCatalog.provisions.filter { item ->
            val catOk = selectedCategory == LawCodeCategory.ALL || item.category == selectedCategory
            val q = query.trim().lowercase()
            val queryOk = q.isBlank() ||
                item.citation.lowercase().contains(q) ||
                item.titleAmharic.lowercase().contains(q) ||
                item.summaryAmharic.lowercase().contains(q)
            catOk && queryOk
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("የኢትዮጵያ ህጎች ፈጣን ማጣቀሻ (Offline)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "ያለ ኢንተርኔት አንቀጾችን ይፈልጉ፣ ይቅዱ ወይም ወደ AI መመሪያ ይጨምሩ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "ዝጋ")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("አንቀጽ ቁጥር ወይም ርዕስ ፈልግ (ምሳሌ: 244, 381, እግድ)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "አጽዳ")
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth().testTag("law_ref_search_input"),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LawCodeCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(filtered, key = { it.id }) { item ->
                    ElevatedCard(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        item.citation,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    item.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(item.titleAmharic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(item.summaryAmharic, style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val copyText = "${item.citation} (${item.titleAmharic}): ${item.summaryAmharic}"
                                        clipboard.setText(AnnotatedString(copyText))
                                        copiedId = item.id
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        if (copiedId == item.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (copiedId == item.id) VerifiedEmerald else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (copiedId == item.id) "ተቀድቷል" else "ቅዳ")
                                }
                                OutlinedButton(
                                    onClick = { onInsertCitation("${item.citation} — ${item.titleAmharic}") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ወደ መመሪያ አስገባ")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
