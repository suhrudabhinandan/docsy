package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SharedOcrClients {
    private const val TAG = "SharedOcrClients"
    private var latinClient: TextRecognizer? = null
    private var devanagariClient: TextRecognizer? = null
    private var initAttempted = false
    private var isAvailable = false

    @Synchronized
    fun getLatinRecognizer(): TextRecognizer? {
        ensureInit()
        return latinClient
    }

    @Synchronized
    fun getDevanagariRecognizer(): TextRecognizer? {
        ensureInit()
        return devanagariClient
    }

    @Synchronized
    fun isOcrAvailable(): Boolean {
        ensureInit()
        return isAvailable
    }

    @Synchronized
    private fun ensureInit() {
        if (initAttempted) return
        initAttempted = true
        try {
            latinClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                devanagariClient = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            } catch (t: Throwable) {
                Log.w(TAG, "Devanagari OCR recognizer unavailable", t)
            }
            isAvailable = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ML Kit text recognition", t)
            isAvailable = false
        }
    }
}

class LocalOcrEngine {

    private class Pass(val result: LayoutPageAnalysisResult, val chars: Int, val avgConfidence: Float) {
        val score: Float get() = chars * avgConfidence
        val isGood: Boolean get() = chars >= 25 && avgConfidence >= 0.85f
    }

    private fun emptyResult(pageNumber: Int) = LayoutPageAnalysisResult(
        pageNumber = pageNumber,
        fullText = "",
        blocks = emptyList(),
        tables = emptyList(),
        keyValues = emptyList(),
        lines = emptyList()
    )

    /**
     * Two-pass OCR: accuracy never drops, speed rises on clean pages.
     *  Pass 1 (fast): read the image as it is. Most clean scans and screenshots are fully read here.
     *  Pass 2 (careful): only when pass 1 looks weak (little text or low recognizer confidence), upscale small
     *  text, deskew and boost contrast, then read again. The better of the two results is kept.
     */
    suspend fun recognizeLayout(bitmap: Bitmap, pageNumber: Int = 1, isBilingual: Boolean = true): LayoutPageAnalysisResult {
        val fast = runPass(bitmap, pageNumber, isBilingual, preprocess = false)
            ?: return emptyResult(pageNumber)
        if (fast.isGood) return fast.result

        val careful = runPass(bitmap, pageNumber, isBilingual, preprocess = true)
        return if (careful != null && careful.score > fast.score) careful.result else fast.result
    }

    private suspend fun runPass(bitmap: Bitmap, pageNumber: Int, isBilingual: Boolean, preprocess: Boolean): Pass? {
        val latinRecognizer = SharedOcrClients.getLatinRecognizer() ?: return null

        val prepared = if (preprocess) ImagePreprocessor.preprocess(bitmap) else bitmap
        try {
            val inputImage = InputImage.fromBitmap(prepared, 0)

            val latinTextResult: Text = try {
                awaitTask(latinRecognizer.process(inputImage))
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                return null
            }

            val rawUnits = mutableListOf<RawTextUnit>()
            var confSum = 0f
            var confCount = 0
            var chars = 0
            for (textBlock in latinTextResult.textBlocks) {
                for (line in textBlock.lines) {
                    val box = line.boundingBox ?: Rect(0, 0, 100, 20)
                    // Stored confidence stays 1.0 as before; the real value only judges whether a second pass is needed.
                    rawUnits.add(RawTextUnit(text = line.text, rect = box, confidence = 1.0f))
                    val n = line.text.count { it.isLetterOrDigit() }
                    chars += n
                    confSum += line.confidence * n
                    confCount += n
                }
            }

            val devanagariRecognizer = SharedOcrClients.getDevanagariRecognizer()
            if (isBilingual && devanagariRecognizer != null && (latinTextResult.text.length < 50 || containsIndicIndicators(latinTextResult.text))) {
                try {
                    val devanagariResult: Text = awaitTask(devanagariRecognizer.process(inputImage))
                    if (devanagariResult.text.isNotBlank() && devanagariResult.text != latinTextResult.text) {
                        for (textBlock in devanagariResult.textBlocks) {
                            for (line in textBlock.lines) {
                                val box = line.boundingBox ?: Rect(0, 0, 100, 20)
                                if (rawUnits.none { it.text == line.text }) {
                                    rawUnits.add(RawTextUnit(text = line.text, rect = box, confidence = 1.0f))
                                    val n = line.text.count { it.isLetterOrDigit() }
                                    chars += n
                                    confSum += line.confidence * n
                                    confCount += n
                                }
                            }
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {}
            }

            val avg = if (confCount > 0) confSum / confCount else 0f
            return Pass(LayoutModelAnalyzer.analyzeUnits(rawUnits, pageNumber), chars, avg)
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    suspend fun recognizeText(bitmap: Bitmap, isBilingual: Boolean = true): ExtractedPage {
        val layout = recognizeLayout(bitmap, pageNumber = 1, isBilingual = isBilingual)
        val extractedBlocks = layout.blocks.map { b ->
            ExtractedBlock(
                text = b.text,
                boundingBox = Rect(b.x.toInt(), b.y.toInt(), (b.x + b.w).toInt(), (b.y + b.h).toInt()),
                confidence = b.confidence,
                lines = listOf(b.text)
            )
        }
        val normalized = TextNormalizer.normalize(layout.fullText)
        return ExtractedPage(
            pageNumber = 1,
            text = normalized,
            blocks = extractedBlocks,
            confidence = 1.0f
        )
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }.addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
    }

    private fun containsIndicIndicators(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    fun close() {}
}
