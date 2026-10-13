package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocalOcrEngine {

    // Latin recognizer (bundled on-device)
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Devanagari recognizer (bundled on-device for Hindi / Marathi text)
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    /**
     * Runs multi-script OCR with block and line layout preservation.
     */
    suspend fun recognizeText(bitmap: Bitmap, isBilingual: Boolean = true): ExtractedPage {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Pass 1: Latin text recognition
        val latinTextResult: Text? = try {
            awaitTask(latinRecognizer.process(inputImage))
        } catch (_: Exception) {
            null
        }

        if (latinTextResult == null) {
            return ExtractedPage(pageNumber = 1, text = "")
        }

        val extractedBlocks = mutableListOf<ExtractedBlock>()

        for (block in latinTextResult.textBlocks) {
            val lines = block.lines.map { it.text }
            extractedBlocks.add(
                ExtractedBlock(
                    text = block.text,
                    boundingBox = block.boundingBox,
                    confidence = 1.0f,
                    lines = lines
                )
            )
        }

        var fullText = latinTextResult.text

        // Pass 2: If bilingual script enabled and text is sparse or Hindi characters expected,
        // merge Devanagari recognition
        if (isBilingual && (fullText.length < 50 || containsIndicIndicators(fullText))) {
            try {
                val devanagariResult: Text = awaitTask(devanagariRecognizer.process(inputImage))
                if (devanagariResult.text.isNotBlank() && devanagariResult.text != fullText) {
                    fullText = fullText + "\n" + devanagariResult.text
                    for (block in devanagariResult.textBlocks) {
                        extractedBlocks.add(
                            ExtractedBlock(
                                text = block.text,
                                boundingBox = block.boundingBox,
                                confidence = 1.0f,
                                lines = block.lines.map { it.text }
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Keep Latin result
            }
        }

        val normalized = TextNormalizer.normalize(fullText)
        return ExtractedPage(
            pageNumber = 1,
            text = normalized,
            blocks = extractedBlocks,
            confidence = 1.0f
        )
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            cont.resume(result)
        }.addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
    }

    private fun containsIndicIndicators(text: String): Boolean {
        // Range for Devanagari Unicode: \u0900-\u097F
        return text.any { it.code in 0x0900..0x097F }
    }

    fun close() {
        try {
            latinRecognizer.close()
            devanagariRecognizer.close()
        } catch (_: Exception) {}
    }
}

