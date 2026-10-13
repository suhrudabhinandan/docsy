package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class ImageExtractor(
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
                failureReason = "Image not accessible"
            )
        }

        val bytes = file.readBytes()
        val hash = PlainTextExtractor.sha256(bytes)

        // Read image bounds first to prevent OutOfMemory on 40MP photos
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var sampleSize = 1
        val maxDim = 2048
        while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 1,
                fullText = "",
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "Image decode failed"
            )

        try {
            // Quick text candidate check
            if (!ImagePreprocessor.hasTextCandidates(bitmap)) {
                return ExtractionResult(
                    contentKey = contentKey,
                    extension = extension,
                    pageCount = 1,
                    fullText = "",
                    contentHash = hash,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // Run OCR with image enhancement
            val enhancedBitmap = ImagePreprocessor.enhanceForOcr(bitmap)
            val extractedPage = ocrEngine.recognizeText(enhancedBitmap)

            if (enhancedBitmap != bitmap) {
                enhancedBitmap.recycle()
            }

            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 1,
                fullText = extractedPage.text,
                pages = listOf(extractedPage),
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                isFallbackOcrUsed = true
            )
        } finally {
            bitmap.recycle()
        }
    }
}
