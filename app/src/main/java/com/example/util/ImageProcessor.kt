package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class AttachmentKind {
    IMAGE,
    PDF,
    WORD,
    TEXT,
}

data class ProcessedPageImage(
    val sha256: String,
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
    val sizeKb: Int,
)

data class DocumentAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val kind: AttachmentKind,
    val compressedSizeKb: Int = 0,
    val directExtractedText: String? = null,
    val processedPages: List<ProcessedPageImage> = emptyList(),
)

object ImageProcessor {
    private const val MAX_LONG_EDGE_PX = 1400
    private const val TARGET_MAX_BYTES = 180 * 1024

    suspend fun processUri(context: Context, uri: Uri, id: String): DocumentAttachment =
        withContext(Dispatchers.Default) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri).orEmpty().lowercase()
            val displayName = queryDisplayName(context, uri) ?: "ሰነድ"
            val lowerName = displayName.lowercase()

            when {
                mimeType.contains("pdf") || lowerName.endsWith(".pdf") -> {
                    processPdf(context, uri, id, displayName)
                }
                mimeType.contains("wordprocessingml") || lowerName.endsWith(".docx") -> {
                    val extracted = extractDocxText(context, uri)
                    DocumentAttachment(
                        id = id,
                        uriString = uri.toString(),
                        displayName = displayName,
                        kind = AttachmentKind.WORD,
                        compressedSizeKb = max(1, extracted.toByteArray().size / 1024),
                        directExtractedText = extracted,
                    )
                }
                mimeType.startsWith("text/") || lowerName.endsWith(".txt") -> {
                    val extracted = resolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                    }.orEmpty()
                    DocumentAttachment(
                        id = id,
                        uriString = uri.toString(),
                        displayName = displayName,
                        kind = AttachmentKind.TEXT,
                        compressedSizeKb = max(1, extracted.toByteArray().size / 1024),
                        directExtractedText = extracted,
                    )
                }
                else -> {
                    val page = processImageUri(context, uri)
                    DocumentAttachment(
                        id = id,
                        uriString = uri.toString(),
                        displayName = displayName,
                        kind = AttachmentKind.IMAGE,
                        compressedSizeKb = page?.sizeKb ?: 0,
                        processedPages = listOfNotNull(page),
                    )
                }
            }
        }

    private fun processImageUri(context: Context, uri: Uri): ProcessedPageImage? {
        val resolver = context.contentResolver
        val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val sha256 = CryptoUtil.sha256Hex(rawBytes)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_LONG_EDGE_PX)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return null

        val oriented = applyExifOrientation(rawBytes, decoded)
        val trimmed = trimDarkBorders(oriented)
        val resized = scaleToMaxEdge(trimmed, MAX_LONG_EDGE_PX)
        val enhanced = enhanceDocumentContrast(resized)
        val jpegBytes = compressUnderTarget(enhanced, TARGET_MAX_BYTES)

        if (oriented !== decoded) decoded.recycle()
        if (trimmed !== oriented) oriented.recycle()
        if (resized !== trimmed) trimmed.recycle()
        if (enhanced !== resized) resized.recycle()

        val finalW = enhanced.width
        val finalH = enhanced.height
        enhanced.recycle()

        return ProcessedPageImage(
            sha256 = sha256,
            jpegBytes = jpegBytes,
            width = finalW,
            height = finalH,
            sizeKb = max(1, jpegBytes.size / 1024),
        )
    }

    private fun processPdf(
        context: Context,
        uri: Uri,
        id: String,
        displayName: String,
    ): DocumentAttachment {
        // First try direct text extraction via PDFBox (zero OCR needed if text PDF)
        val extractedText = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.getText(doc).trim()
                }
            }
        }.getOrNull().orEmpty()

        if (extractedText.length >= 40) {
            return DocumentAttachment(
                id = id,
                uriString = uri.toString(),
                displayName = displayName,
                kind = AttachmentKind.PDF,
                compressedSizeKb = max(1, extractedText.toByteArray().size / 1024),
                directExtractedText = extractedText,
            )
        }

        // Scanned PDF: render pages via Android PdfRenderer and compress for Vision OCR
        val pages = mutableListOf<ProcessedPageImage>()
        val tempFile = File.createTempFile("scan_pdf_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageLimit = minOf(renderer.pageCount, 12)
                    for (index in 0 until pageLimit) {
                        renderer.openPage(index).use { page ->
                            val scale = MAX_LONG_EDGE_PX.toFloat() / max(page.width, page.height).coerceAtLeast(1)
                            val targetW = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bmp = createBitmap(targetW, targetH)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val enhanced = enhanceDocumentContrast(bmp)
                            val jpeg = compressUnderTarget(enhanced, TARGET_MAX_BYTES)
                            val pageHash = CryptoUtil.sha256Hex(jpeg)
                            pages.add(
                                ProcessedPageImage(
                                    sha256 = pageHash,
                                    jpegBytes = jpeg,
                                    width = enhanced.width,
                                    height = enhanced.height,
                                    sizeKb = max(1, jpeg.size / 1024),
                                )
                            )
                            if (enhanced !== bmp) bmp.recycle()
                            enhanced.recycle()
                        }
                    }
                }
            }
        } finally {
            tempFile.delete()
        }

        return DocumentAttachment(
            id = id,
            uriString = uri.toString(),
            displayName = displayName,
            kind = AttachmentKind.PDF,
            compressedSizeKb = pages.sumOf { it.sizeKb },
            directExtractedText = null,
            processedPages = pages,
        )
    }

    private fun extractDocxText(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setInput(zip, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "p" && sb.isNotEmpty()) sb.append('\n')
                                    if (parser.name == "t") {
                                        sb.append(parser.nextText())
                                    }
                                }
                            }
                            event = parser.next()
                        }
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return sb.toString().trim()
    }

    @SuppressLint("ExifInterface") // minSdk = 24; InputStream ExifInterface is safe on Android 7.0+
    private fun applyExifOrientation(rawBytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            android.media.ExifInterface(rawBytes.inputStream()).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Trims dark scanner/table edges by sampling rows/columns at margins.
     */
    private fun trimDarkBorders(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 100 || h < 100) return src
        val maxTrimX = (w * 0.14f).toInt()
        val maxTrimY = (h * 0.14f).toInt()

        fun isRowDark(y: Int): Boolean {
            var sum = 0
            val step = max(1, w / 24)
            var count = 0
            var x = 0
            while (x < w) {
                val p = src[x, y]
                val lum = (((p shr 16) and 0xff) * 30 + ((p shr 8) and 0xff) * 59 + (p and 0xff) * 11) / 100
                sum += lum
                count++
                x += step
            }
            return (sum / max(1, count)) < 48
        }

        fun isColDark(x: Int): Boolean {
            var sum = 0
            val step = max(1, h / 24)
            var count = 0
            var y = 0
            while (y < h) {
                val p = src[x, y]
                val lum = (((p shr 16) and 0xff) * 30 + ((p shr 8) and 0xff) * 59 + (p and 0xff) * 11) / 100
                sum += lum
                count++
                y += step
            }
            return (sum / max(1, count)) < 48
        }

        var top = 0
        while (top < maxTrimY && isRowDark(top)) top += 4
        var bottom = h - 1
        while (bottom > h - maxTrimY && isRowDark(bottom)) bottom -= 4
        var left = 0
        while (left < maxTrimX && isColDark(left)) left += 4
        var right = w - 1
        while (right > w - maxTrimX && isColDark(right)) right -= 4

        val cropW = right - left + 1
        val cropH = bottom - top + 1
        if (cropW < w * 0.65f || cropH < h * 0.65f || (top == 0 && left == 0 && right == w - 1 && bottom == h - 1)) {
            return src
        }
        return Bitmap.createBitmap(src, left, top, cropW, cropH)
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        val newW = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return src.scale(newW, newH)
    }

    private fun enhanceDocumentContrast(src: Bitmap): Bitmap {
        val out = createBitmap(src.width, src.height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val contrast = 1.18f
        val brightness = -14f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun compressUnderTarget(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var quality = 80
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > targetBytes && quality > 45) {
            quality -= 10
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return out.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxEdge) {
            val half = longest / 2
            while (half / inSampleSize >= maxEdge) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
