package com.example.pdf

import android.content.Context
import com.example.data.local.CaseEntity
import com.example.data.local.CaseType
import com.example.util.JudicialDocumentFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GeneratedDocxResult(
    val file: File,
    val paragraphCount: Int,
)

object DocxExporter {

    suspend fun exportDocx(
        context: Context,
        caseEntity: CaseEntity,
        isDraft: Boolean = false,
    ): GeneratedDocxResult = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "docx").apply { mkdirs() }
        val suffix = if (isDraft) "DRAFT" else "FINAL"
        val safeCaseNo = caseEntity.caseNumber
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .ifBlank { "case" }
        val outFile = File(outDir, "RD_${safeCaseNo}_v${caseEntity.currentVersion}_$suffix.docx")

        val bytes = writeDocxBytes(caseEntity, isDraft)
        outFile.writeBytes(bytes)

        val lineCount = JudicialDocumentFormatter.composeUnifiedDocument(caseEntity)
            .lines()
            .count { it.isNotBlank() }

        GeneratedDocxResult(
            file = outFile,
            paragraphCount = lineCount,
        )
    }

    fun writeDocxBytes(caseEntity: CaseEntity, isDraft: Boolean = false): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            addZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            addZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            addZipEntry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
            addZipEntry(zip, "word/styles.xml", STYLES_XML)
            addZipEntry(zip, "word/document.xml", buildDocumentXml(caseEntity, isDraft))
        }
        return baos.toByteArray()
    }

    fun buildDocumentXml(caseEntity: CaseEntity, isDraft: Boolean = false): String {
        val unifiedDoc = JudicialDocumentFormatter.composeUnifiedDocument(caseEntity)
        val bodyParagraphs = StringBuilder()

        // Court Title Header
        if (!unifiedDoc.contains("የሐረሪ ብሔራዊ ክልል")) {
            bodyParagraphs.appendLine(
                buildParagraphXml(
                    text = "የሐረሪ ብሔራዊ ክልል ከፍተኛ ፍርድ ቤት",
                    bold = true,
                    centered = true,
                    fontSizeHalfPoints = 28,
                    spacingAfterTwips = 160,
                )
            )
        }

        if (isDraft) {
            bodyParagraphs.appendLine(
                buildParagraphXml(
                    text = "[ረቂቅ ሰነድ / DRAFT — ለዳኛ ክለሳ ብቻ]",
                    bold = true,
                    centered = true,
                    fontSizeHalfPoints = 20,
                    colorHex = "B91C1C",
                    spacingAfterTwips = 140,
                )
            )
        }

        val lines = unifiedDoc.replace("\r\n", "\n").split("\n")
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            when {
                JudicialDocumentFormatter.isUnifiedHeadingLine(trimmed) -> {
                    bodyParagraphs.appendLine(
                        buildParagraphXml(
                            text = trimmed,
                            bold = true,
                            centered = true,
                            fontSizeHalfPoints = 26,
                            spacingBeforeTwips = 200,
                            spacingAfterTwips = 140,
                        )
                    )
                }
                JudicialDocumentFormatter.isUnifiedHeaderLabelLine(trimmed) -> {
                    val rightAlign = trimmed.startsWith("የመ/ቁ") || trimmed.startsWith("ቀን:") || trimmed.startsWith("ቀን፦")
                    bodyParagraphs.appendLine(
                        buildParagraphXml(
                            text = trimmed,
                            bold = rightAlign || trimmed.startsWith("ዳኛ"),
                            rightAligned = rightAlign,
                            fontSizeHalfPoints = 24,
                            spacingAfterTwips = 80,
                        )
                    )
                }
                trimmed.startsWith("መዝገቡ የተቀጠረው") -> {
                    bodyParagraphs.appendLine(
                        buildParagraphXml(
                            text = trimmed,
                            bold = false,
                            fontSizeHalfPoints = 24,
                            spacingBeforeTwips = 140,
                            spacingAfterTwips = 120,
                        )
                    )
                }
                else -> {
                    bodyParagraphs.appendLine(
                        buildParagraphXml(
                            text = trimmed,
                            bold = false,
                            justified = true,
                            fontSizeHalfPoints = 24,
                            spacingAfterTwips = 120,
                        )
                    )
                }
            }
        }

        // Signature block if not already present at the end of unified text
        if (!unifiedDoc.contains("ፊርማ")) {
            val sigLine = if (caseEntity.caseType == CaseType.APPEAL) {
                "የዳኞች ፊርማ፦  __________________    __________________    __________________"
            } else {
                "የዳኛው ፊርማ፦  ______________________________"
            }
            bodyParagraphs.appendLine(
                buildParagraphXml(
                    text = sigLine,
                    bold = true,
                    fontSizeHalfPoints = 22,
                    spacingBeforeTwips = 260,
                    spacingAfterTwips = 120,
                )
            )
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
$bodyParagraphs
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun buildParagraphXml(
        text: String,
        bold: Boolean = false,
        centered: Boolean = false,
        rightAligned: Boolean = false,
        justified: Boolean = false,
        fontSizeHalfPoints: Int = 24,
        colorHex: String? = null,
        spacingBeforeTwips: Int = 0,
        spacingAfterTwips: Int = 100,
    ): String {
        val jcVal = when {
            centered -> "center"
            rightAligned -> "right"
            justified -> "both"
            else -> "left"
        }
        val boldXml = if (bold) "<w:b/><w:bCs/>" else ""
        val colorXml = if (colorHex != null) "<w:color w:val=\"$colorHex\"/>" else ""
        val escaped = xmlEscape(text)

        return """    <w:p>
      <w:pPr>
        <w:spacing w:before="$spacingBeforeTwips" w:after="$spacingAfterTwips" w:line="320" w:lineRule="auto"/>
        <w:jc w:val="$jcVal"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Noto Sans Ethiopic" w:hAnsi="Noto Sans Ethiopic" w:cs="Noto Sans Ethiopic"/>
          $boldXml
          $colorXml
          <w:sz w:val="$fontSizeHalfPoints"/>
          <w:szCs w:val="$fontSizeHalfPoints"/>
        </w:rPr>
        <w:t xml:space="preserve">$escaped</w:t>
      </w:r>
    </w:p>"""
    }

    private fun xmlEscape(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> {
                    if (ch.code >= 0x20 || ch == '\t') append(ch)
                }
            }
        }
    }

    private fun addZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Noto Sans Ethiopic" w:hAnsi="Noto Sans Ethiopic" w:cs="Noto Sans Ethiopic"/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
</w:styles>"""
}
