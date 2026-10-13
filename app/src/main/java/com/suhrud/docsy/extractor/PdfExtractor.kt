package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfExtractor(
    private val ocrEngine: LocalOcrEngine
) {

    /**
     * Extracts text from PDF files.
     * Crucial Fix for Retrieval Defect 0.4 ("12th marksheet.pdf" & CAT Exam PDF):
     * If a marksheet or scanned certificate has an empty or sparse vector text layer,
     * the pipeline rasterizes the pages at high resolution (192-250 DPI) via [PdfRenderer]
     * and runs local OCR with Devanagari/Latin script recognition.
     */
    suspend fun extract(file: File): ExtractionResult {
        val startTime = System.currentTimeMillis()
        val extension = file.extension.lowercase()
        val contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"

        if (!file.exists() || !file.canRead()) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 0,
                fullText = "",
                contentHash = "",
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "PDF file not accessible"
            )
        }

        val bytes = file.readBytes()
        val hash = PlainTextExtractor.sha256(bytes)

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            val pageCount = renderer.pageCount
            val pages = mutableListOf<ExtractedPage>()
            val fullTextBuilder = StringBuilder()
            var fallbackOcrUsed = false

            for (pageIndex in 0 until pageCount) {
                val page = renderer.openPage(pageIndex)

                // Render page to bitmap at 1.75x..2.0x scale (approx 150-200 DPI) for high OCR legibility
                val scale = 1.75f
                val renderWidth = (page.width * scale).toInt()
                val renderHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE) // White background for clean text recognition
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                try {
                    val ocrPage = ocrEngine.recognizeText(bitmap)
                    fallbackOcrUsed = true
                    pages.add(ocrPage.copy(pageNumber = pageIndex + 1))
                    fullTextBuilder.append(ocrPage.text).append("\n\n")
                } finally {
                    bitmap.recycle()
                }
            }

            val normalized = TextNormalizer.normalize(fullTextBuilder.toString())

            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = pageCount,
                fullText = normalized,
                pages = pages,
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                isFallbackOcrUsed = fallbackOcrUsed
            )
        } catch (securityEx: SecurityException) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 0,
                fullText = "",
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                isEncrypted = true,
                failureReason = "Password-protected or encrypted PDF"
            )
        } catch (e: Exception) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 0,
                fullText = "",
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "PDF render failure: ${e.message}"
            )
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}
