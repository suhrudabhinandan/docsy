package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

class PdfExtractor(
    private val ocrEngine: LocalOcrEngine
) {

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

        val hash = try { PlainTextExtractor.sha256File(file) } catch (_: Throwable) { "" }
        val maxPages = 300

        // 1. Read the PDF's own text layer first (exact and fast). Pages without one are OCR'd below.
        val native: NativePdfText.Result? = try {
            withContext(Dispatchers.Default) { NativePdfText.extract(file, maxPages) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            var pageCount = native?.pageCount ?: 0
            val ocrResults = HashMap<Int, LayoutPageAnalysisResult?>()

            val pagesNeedingOcr: List<Int>? = native?.let { n ->
                (1..minOf(n.pageCount, maxPages)).filter { pageNum ->
                    val pageLayout = n.pages[pageNum]
                    pageLayout == null || ExtractionQualityAssessment.needsOcrFallback(pageLayout.fullText)
                }
            }

            if (native == null || !pagesNeedingOcr.isNullOrEmpty()) {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                val targets = pagesNeedingOcr ?: (1..minOf(pageCount, maxPages)).toList()
                ocrResults.putAll(ocrPages(renderer, targets, pageCount))
            }

            val pages = mutableListOf<ExtractedPage>()
            val layoutPages = mutableListOf<LayoutPageAnalysisResult>()
            val fullTextBuilder = StringBuilder()
            val ocrUsed = ocrResults.values.any { it != null }

            for (pageNum in 1..minOf(pageCount, maxPages)) {
                val layoutPage = native?.pages?.get(pageNum) ?: ocrResults[pageNum] ?: continue // a bad page must not abort the file
                pages.add(ExtractedPage(pageNumber = pageNum, text = layoutPage.fullText, confidence = 1.0f))
                layoutPages.add(layoutPage)
                // Page marker lets the scanner keep the real page number for every line.
                if (pageNum > 1) fullTextBuilder.append("[[PAGE ").append(pageNum).append("]]\n")
                fullTextBuilder.append(layoutPage.fullText).append("\n\n")
            }

            val normalized = TextNormalizer.normalize(fullTextBuilder.toString())
            if (pages.isEmpty() && pageCount > 0) {
                return ExtractionResult(contentKey = contentKey, extension = extension, pageCount = pageCount, fullText = "", contentHash = hash, durationMs = System.currentTimeMillis() - startTime, failureReason = "No page could be read")
            }

            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = pageCount,
                fullText = normalized,
                pages = pages,
                layoutPages = layoutPages,
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                isFallbackOcrUsed = ocrUsed
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
        } catch (e: CancellationException) {
            throw e
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

    /**
     * OCR for the given pages (1-based). Pipelined: while one page is being read, the next is being rendered.
     * At most 2 page bitmaps exist at a time, so memory stays bounded.
     */
    private suspend fun ocrPages(renderer: PdfRenderer, targets: List<Int>, totalPages: Int): Map<Int, LayoutPageAnalysisResult?> {
        // Large scanned PDFs: lower the render scale so 60+ page files stay memory-safe.
        val scale = when {
            totalPages > 40 -> 1.5f
            totalPages > 15 -> 1.75f
            else -> 2.0f
        }
        val ocrSlots = Semaphore(2)

        return coroutineScope {
            val jobs = LinkedHashMap<Int, Deferred<LayoutPageAnalysisResult?>>()
            for (pageNum in targets) {
                yield() // stay cancellable between pages
                ocrSlots.acquire()
                var bitmap: Bitmap? = null
                try {
                    val page = renderer.openPage(pageNum - 1)
                    try {
                        var w = (page.width * scale).toInt().coerceAtLeast(1)
                        var h = (page.height * scale).toInt().coerceAtLeast(1)
                        val longest = maxOf(w, h)
                        if (longest > 2600) { // cap bitmap size
                            val f = 2600f / longest
                            w = (w * f).toInt().coerceAtLeast(1); h = (h * f).toInt().coerceAtLeast(1)
                        }
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        android.graphics.Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } finally {
                        page.close()
                    }
                } catch (ce: CancellationException) {
                    bitmap?.recycle(); ocrSlots.release()
                    throw ce
                } catch (e: Throwable) {
                    bitmap?.recycle(); ocrSlots.release()
                    jobs[pageNum] = kotlinx.coroutines.CompletableDeferred<LayoutPageAnalysisResult?>(null)
                    continue
                }

                val readyBitmap = bitmap!!
                jobs[pageNum] = async {
                    try {
                        ocrEngine.recognizeLayout(readyBitmap, pageNumber = pageNum)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Throwable) {
                        null
                    } finally {
                        readyBitmap.recycle()
                        ocrSlots.release()
                    }
                }
            }
            jobs.mapValues { it.value.await() }
        }
    }
}
