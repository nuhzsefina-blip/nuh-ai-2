package com.example.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.FontRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.example.R
import java.io.InputStream
import com.example.data.local.CaseEntity
import com.example.data.local.CaseType
import com.example.data.repository.CaseRepository
import com.example.security.SecurePrefsManager
import com.example.util.CryptoUtil
import com.example.util.JudicialDocumentFormatter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GeneratedPdfResult(
    val file: File,
    val fileSha256: String,
    val contentSha256: String,
    val pageCount: Int,
)

data class PdfVerificationResult(
    val isAuthentic: Boolean,
    val statusTitle: String,
    val details: String,
    val matchedCaseNumber: String? = null,
    val fileSha256: String,
)

class JudicialPdfGenerator(
    private val context: Context,
    private val prefs: SecurePrefsManager,
    private val repository: CaseRepository,
) {
    private var pdfBoxInitialized = false

    private fun ensureInit() {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxInitialized = true
        }
    }

    suspend fun generateProtectedPdf(
        caseEntity: CaseEntity,
        includeDraftWatermark: Boolean,
    ): GeneratedPdfResult = withContext(Dispatchers.IO) {
        ensureInit()
        val contentHash = repository.computeCanonicalContentHash(caseEntity)
        val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val suffix = if (includeDraftWatermark) "DRAFT" else "FINAL"
        val safeCaseNo = caseEntity.caseNumber.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "case" }
        val outFile = File(outDir, "RD_${safeCaseNo}_v${caseEntity.currentVersion}_$suffix.pdf")

        val doc = PDDocument()
        try {
            val serifFont = openFontResource(R.font.noto_serif_ethiopic).use {
                PDType0Font.load(doc, it)
            }
            val sansFont = openFontResource(R.font.noto_sans_ethiopic).use {
                PDType0Font.load(doc, it)
            }

            // Build verification QR code bitmap
            val qrPayload = "REDAT_DAGNA|CASE:${caseEntity.caseNumber}|DATE:${caseEntity.ethiopianDate}|VER:v${caseEntity.currentVersion}|HASH:$contentHash"
            val qrBitmap = createQrBitmap(qrPayload, 220)

            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val margin = 52f
            val contentWidth = pageWidth - (margin * 2)
            val footerReserve = 62f

            val blocks = buildJudicialBlocks(caseEntity)
            val pages = mutableListOf<PDPage>()
            var currentPage = PDPage(PDRectangle.A4).also { doc.addPage(it); pages.add(it) }
            var cs = PDPageContentStream(doc, currentPage)
            var y = pageHeight - margin

            fun newPage() {
                cs.close()
                currentPage = PDPage(PDRectangle.A4).also { doc.addPage(it); pages.add(it) }
                cs = PDPageContentStream(doc, currentPage)
                y = pageHeight - margin
            }

            fun drawLine(text: String, font: PDFont, fontSize: Float, centered: Boolean = false) {
                val safe = sanitizeForFont(text, font)
                val textWidth = runCatching { font.getStringWidth(safe) / 1000f * fontSize }.getOrDefault(0f)
                val x = if (centered) ((pageWidth - textWidth) / 2f).coerceAtLeast(margin) else margin
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(20f / 255f, 28f / 255f, 42f / 255f)
                cs.newLineAtOffset(x, y)
                cs.showText(safe)
                cs.endText()
            }

            for (block in blocks) {
                val font = if (block.bold) sansFont else serifFont
                val lines = wrapText(block.text, font, block.fontSize, contentWidth)
                val lineHeight = block.fontSize * 1.48f

                if (y - lineHeight < margin + footerReserve) {
                    newPage()
                }
                if (block.spaceBefore > 0f && y < pageHeight - margin - 10f) {
                    y -= block.spaceBefore
                }
                for (line in lines) {
                    if (y - lineHeight < margin + footerReserve) {
                        newPage()
                    }
                    drawLine(line, font, block.fontSize, block.centered)
                    y -= lineHeight
                }
            }

            // Signature lines + Verification QR block on final page
            val requiredEndBlock = 155f
            if (y - requiredEndBlock < margin + footerReserve) {
                newPage()
            } else {
                y -= 20f
            }

            val sigLabel = if (caseEntity.caseType == CaseType.APPEAL) {
                "የዳኞች ፊርማ፦  __________________   __________________   __________________"
            } else {
                "የዳኛው ፊርማ፦  ______________________________"
            }
            drawLine(sigLabel, sansFont, 10.5f)
            y -= 26f

            // QR code + SHA-256 verification stamp
            val qrPdImage = LosslessFactory.createFromImage(doc, qrBitmap)
            val qrSize = 72f
            cs.drawImage(qrPdImage, margin, y - qrSize, qrSize, qrSize)

            val stampX = margin + qrSize + 12f
            val stampLines = listOf(
                "የሐረሪ ብሔራዊ ክልል ከፍተኛ ፍርድ ቤት - ማረጋገጫ",
                "የሰነድ ሁኔታ: ${if (includeDraftWatermark) "ረቂቅ (DRAFT)" else "የተጠናቀቀ (FINAL)"} | ስሪት: v${caseEntity.currentVersion}",
                "SHA-256: ${contentHash.take(32)}",
                contentHash.drop(32),
            )
            var stampY = y - 14f
            for (sLine in stampLines) {
                val safe = sanitizeForFont(sLine, sansFont)
                cs.beginText()
                cs.setFont(sansFont, 8.2f)
                cs.setNonStrokingColor(65f / 255f, 75f / 255f, 92f / 255f)
                cs.newLineAtOffset(stampX, stampY)
                cs.showText(safe)
                cs.endText()
                stampY -= 13f
            }
            cs.close()
            qrBitmap.recycle()

            // Add watermark (if draft) and page footers ("የመ/ቁ:- ... | ገጽ X ከ Y") to every page
            val totalPages = pages.size
            pages.forEachIndexed { idx, page ->
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { appendCs ->
                    if (includeDraftWatermark) {
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.14f }
                        appendCs.setGraphicsStateParameters(gs)
                        appendCs.beginText()
                        appendCs.setFont(sansFont, 48f)
                        appendCs.setNonStrokingColor(185f / 255f, 28f / 255f, 28f / 255f)
                        appendCs.setTextMatrix(
                            com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                                Math.toRadians(38.0),
                                pageWidth * 0.25f,
                                pageHeight * 0.38f
                            )
                        )
                        appendCs.showText(sanitizeForFont("ረቂቅ / DRAFT", sansFont))
                        appendCs.endText()

                        val resetGs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1.0f }
                        appendCs.setGraphicsStateParameters(resetGs)
                    }

                    // Footer separator line
                    appendCs.setStrokingColor(190f / 255f, 185f / 255f, 172f / 255f)
                    appendCs.setLineWidth(0.6f)
                    appendCs.moveTo(margin, margin - 8f)
                    appendCs.lineTo(pageWidth - margin, margin - 8f)
                    appendCs.stroke()

                    val footerLeft = sanitizeForFont(
                        "የመ/ቁ:- ${caseEntity.caseNumber.ifBlank { "-" }}  |  ሃሽ: ${contentHash.take(12)}...",
                        sansFont
                    )
                    val footerRight = sanitizeForFont("ገጽ ${idx + 1} ከ $totalPages", sansFont)

                    appendCs.beginText()
                    appendCs.setFont(sansFont, 8.5f)
                    appendCs.setNonStrokingColor(90f / 255f, 98f / 255f, 112f / 255f)
                    appendCs.newLineAtOffset(margin, margin - 22f)
                    appendCs.showText(footerLeft)
                    appendCs.endText()

                    val rightWidth = runCatching { sansFont.getStringWidth(footerRight) / 1000f * 8.5f }.getOrDefault(60f)
                    appendCs.beginText()
                    appendCs.setFont(sansFont, 8.5f)
                    appendCs.setNonStrokingColor(90f / 255f, 98f / 255f, 112f / 255f)
                    appendCs.newLineAtOffset(pageWidth - margin - rightWidth, margin - 22f)
                    appendCs.showText(footerRight)
                    appendCs.endText()
                }
            }

            // Set Document Info metadata for verification
            doc.documentInformation.title = "የመ/ቁ ${caseEntity.caseNumber} - ረዳት ዳኛ"
            doc.documentInformation.subject = "CONTENT_HASH:$contentHash"
            doc.documentInformation.creator = "Redat Dagna - Harari Regional High Court"

            // Lock editing, copying, page extraction while allowing printing (empty user password)
            val ap = AccessPermission().apply {
                setCanModify(false)
                setCanExtractContent(false)
                setCanExtractForAccessibility(true)
                setCanAssembleDocument(false)
                setCanModifyAnnotations(false)
                setCanFillInForm(false)
                setCanPrint(true)
                setCanPrintFaithful(true)
            }
            val ownerPassword = prefs.getOrCreatePdfOwnerPassword()
            val policy = StandardProtectionPolicy(ownerPassword, "", ap).apply {
                encryptionKeyLength = 128
                permissions = ap
            }
            doc.protect(policy)
            doc.save(outFile)
        } finally {
            doc.close()
        }

        val fileSha256 = CryptoUtil.sha256Hex(outFile)
        GeneratedPdfResult(
            file = outFile,
            fileSha256 = fileSha256,
            contentSha256 = contentHash,
            pageCount = 1.coerceAtLeast(
                runCatching {
                    PDDocument.load(outFile).use { it.numberOfPages }
                }.getOrDefault(1)
            ),
        )
    }

    suspend fun verifyPdf(uri: Uri): PdfVerificationResult = withContext(Dispatchers.IO) {
        ensureInit()
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext PdfVerificationResult(
                isAuthentic = false,
                statusTitle = "ፋይሉን መክፈት አልተቻለም",
                details = "የተመረጠው PDF ፋይል ሊነበብ አልቻለም።",
                fileSha256 = "",
            )
        val fileHash = CryptoUtil.sha256Hex(rawBytes)
        val exactMatch = repository.findByHash(fileHash)
        if (exactMatch != null) {
            return@withContext PdfVerificationResult(
                isAuthentic = true,
                statusTitle = "ያልተነካካ ትክክለኛ ሰነድ (100% ተረጋግጧል)",
                details = "ይህ PDF ከመዝገብ ቤቱ ኦሪጅናል ፋይል ጋር በSHA-256 ሃሽ ሙሉ በሙሉ ይዛመዳል። ምንም ለውጥ አልተደረገበትም።",
                matchedCaseNumber = exactMatch.caseNumber,
                fileSha256 = fileHash,
            )
        }

        // Inspect embedded subject metadata or text hash if re-saved/re-compressed
        val extractedSubjectHash = runCatching {
            PDDocument.load(rawBytes).use { doc ->
                val subject = doc.documentInformation?.subject.orEmpty()
                if (subject.startsWith("CONTENT_HASH:")) {
                    subject.removePrefix("CONTENT_HASH:").trim()
                } else {
                    val txt = PDFTextStripper().getText(doc)
                    Regex("SHA-256:\\s*([0-9a-f]{32})").find(txt)?.groupValues?.getOrNull(1).orEmpty()
                }
            }
        }.getOrDefault("")

        val contentMatch = if (extractedSubjectHash.length == 64) {
            repository.findByHash(extractedSubjectHash)
        } else null

        if (contentMatch != null) {
            PdfVerificationResult(
                isAuthentic = false,
                statusTitle = "ማስጠንቀቂያ: ፋይሉ ተቀይሯል ወይም እንደገና ተቀምጧል",
                details = "የመዝገብ ቁጥር ${contentMatch.caseNumber} መለያ ቢገኝም የፋይሉ የSHA-256 ሃሽ ከመጀመሪያው ፋይል ጋር አይገጥምም። ሰነዱ ተነክቶ ወይም ተለውጦ ሊሆን ይችላል።",
                matchedCaseNumber = contentMatch.caseNumber,
                fileSha256 = fileHash,
            )
        } else {
            PdfVerificationResult(
                isAuthentic = false,
                statusTitle = "ያልተመዘገበ ወይም የተለወጠ ሰነድ",
                details = "የዚህ PDF SHA-256 ሃሽ በዚህ መተግበሪያ Archives ውስጥ አልተገኘም።",
                fileSha256 = fileHash,
            )
        }
    }

    private data class PdfBlock(
        val text: String,
        val fontSize: Float = 11.2f,
        val bold: Boolean = false,
        val centered: Boolean = false,
        val spaceBefore: Float = 0f,
    )

    private fun buildJudicialBlocks(c: CaseEntity): List<PdfBlock> {
        val list = mutableListOf<PdfBlock>()
        val unifiedDoc = JudicialDocumentFormatter.composeUnifiedDocument(c)

        if (!unifiedDoc.contains("የሐረሪ ብሔራዊ ክልል")) {
            list.add(PdfBlock("የሐረሪ ብሔራዊ ክልል ከፍተኛ ፍርድ ቤት", fontSize = 13.5f, bold = true, centered = true))
        }

        if (unifiedDoc.isBlank()) {
            return list
        }

        // Render line-by-line / paragraph-by-paragraph preserving exact template headings & header lines
        val lines = unifiedDoc.replace("\r\n", "\n").split("\n")
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            when {
                JudicialDocumentFormatter.isUnifiedHeadingLine(trimmed) -> {
                    list.add(PdfBlock(trimmed, fontSize = 13f, bold = true, centered = true, spaceBefore = 12f))
                }
                JudicialDocumentFormatter.isUnifiedHeaderLabelLine(trimmed) -> {
                    val extraSpace = if (trimmed.startsWith("የመ/ቁ") || trimmed.startsWith("ዳኞች") || trimmed.startsWith("ዳኛ") ||
                        trimmed.startsWith("ይግባኝ ባይ") || trimmed.startsWith("ከሳሽ")
                    ) 6f else 0f
                    list.add(PdfBlock(trimmed, fontSize = 11f, bold = trimmed.startsWith("የመ/ቁ") || trimmed.startsWith("ቀን") || trimmed.startsWith("ዳኛ"), spaceBefore = extraSpace))
                }
                trimmed.startsWith("መዝገቡ የተቀጠረው") -> {
                    list.add(PdfBlock(trimmed, fontSize = 11f, spaceBefore = 10f))
                }
                else -> {
                    list.add(PdfBlock(trimmed, fontSize = 11f, spaceBefore = 5f))
                }
            }
        }
        return list
    }

    private fun wrapText(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.replace("\r\n", "\n").split("\n")
        for (para in paragraphs) {
            if (para.isBlank()) {
                result.add("")
                continue
            }
            val words = para.split(Regex("\\s+"))
            var currentLine = StringBuilder()
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                val safeCandidate = sanitizeForFont(candidate, font)
                val width = runCatching { font.getStringWidth(safeCandidate) / 1000f * fontSize }.getOrDefault(0f)
                if (width <= maxWidth) {
                    currentLine = StringBuilder(candidate)
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    } else {
                        result.add(word)
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }

    private fun sanitizeForFont(text: String, font: PDFont): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch == '\t') {
                sb.append("    ")
                continue
            }
            if (ch.code < 0x20) continue
            val ok = runCatching {
                font.encode(ch.toString())
                true
            }.getOrDefault(false)
            sb.append(if (ok) ch else ' ')
        }
        return sb.toString()
    }

    @SuppressLint("ResourceType")
    private fun openFontResource(@FontRes fontResId: Int): InputStream =
        context.resources.openRawResource(fontResId)

    private fun createQrBitmap(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = createBitmap(sizePx, sizePx)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp[x, y] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        return bmp
    }
}
