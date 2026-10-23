package com.suhrud.docsy.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.suhrud.docsy.scanner.ContentSectionDetector
import com.suhrud.docsy.scanner.FileSection
import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.LayoutMarksheetExtractor
import com.suhrud.docsy.understanding.MarksheetParser
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Decides when normal reading was not enough, and then lets the vision model re-read the file.
 * The vision model can misread digits, so its result replaces the normal one only if it is clearly better,
 * and anything it read is flagged for double-checking.
 */
object VlmRescue {

    private fun alnum(s: String) = s.count { it.isLetterOrDigit() }

    private fun isMarksheet(text: String, file: File): Boolean =
        DocumentClassifier.classify(text, file.name).category.contains("marksheet", ignoreCase = true)

    /** Subjects the normal pipeline can read: layout tables first, then the older text parser. */
    private fun subjectCount(result: ExtractionResult): Int {
        val fromLayout = LayoutMarksheetExtractor.extract(result.layoutPages, result.fullText).subjects.size
        val fromText = try { MarksheetParser.parse(result.fullText).subjects.size } catch (_: Throwable) { 0 }
        return maxOf(fromLayout, fromText)
    }

    suspend fun maybeRescue(context: Context, file: File, result: ExtractionResult?): ExtractionResult? {
        if (result == null || result.isEncrypted) return result
        val section = ContentSectionDetector.detectSection(file)
        if (section != FileSection.IMAGES && section != FileSection.PDF) return result
        if (!VlmEngine.isReady(context)) return result

        val text = result.fullText
        val blank = alnum(text) < 20
        val weakMarksheet = !blank && isMarksheet(text, file) && subjectCount(result) == 0
        val blankPhoto = blank && section == FileSection.IMAGES && file.length() > 50_000

        if (!weakMarksheet && !blankPhoto) return result

        val reread = withTimeoutOrNull(300_000L) { reread(context, file, section) } ?: return result

        val better = if (blankPhoto) alnum(reread.fullText) > alnum(text) + 15 else subjectCount(reread) > 0
        return if (better) reread else result
    }

    private suspend fun reread(context: Context, file: File, section: FileSection): ExtractionResult? {
        val start = System.currentTimeMillis()
        val bitmaps = try { renderPages(file, section) } catch (_: Throwable) { emptyList() }
        if (bitmaps.isEmpty()) return null

        val layoutPages = ArrayList<LayoutPageAnalysisResult>()
        val pages = ArrayList<ExtractedPage>()
        val full = StringBuilder()
        try {
            for ((i, bmp) in bitmaps.withIndex()) {
                val text = VlmPageReader.readPage(context, bmp) ?: continue
                val layout = LayoutModelAnalyzer.analyzeUnits(VlmPageReader.toUnits(text), i + 1)
                layoutPages.add(layout)
                pages.add(ExtractedPage(pageNumber = i + 1, text = layout.fullText, confidence = 0.8f))
                if (i > 0) full.append("[[PAGE ").append(i + 1).append("]]\n")
                full.append(layout.fullText).append("\n\n")
            }
        } finally {
            bitmaps.forEach { try { it.recycle() } catch (_: Throwable) {} }
        }
        if (pages.isEmpty()) return null

        return ExtractionResult(
            contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}",
            extension = file.extension.lowercase(),
            pageCount = pages.size,
            fullText = TextNormalizer.normalize(full.toString()),
            pages = pages,
            layoutPages = layoutPages,
            contentHash = "",
            durationMs = System.currentTimeMillis() - start,
            isFallbackOcrUsed = false,
            usedVlm = true
        )
    }

    private fun renderPages(file: File, section: FileSection): List<Bitmap> {
        if (section == FileSection.IMAGES) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            return listOfNotNull(bmp)
        }
        val out = ArrayList<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until minOf(renderer.pageCount, 3)) { // the first pages hold the result; keeps cost bounded
                val page = renderer.openPage(i)
                try {
                    val scale = 2f
                    val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    out.add(bmp)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return out
    }
}
