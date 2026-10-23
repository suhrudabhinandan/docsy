package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class ImageExtractor(
    private val ocrEngine: LocalOcrEngine
) {

    private companion object {
        const val MAX_LONG_SIDE = 2560
    }

    private fun applyExifRotation(file: File, source: Bitmap): Bitmap {
        return try {
            val orientation = android.media.ExifInterface(file.absolutePath)
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return source
            }
            val m = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
            if (rotated !== source) source.recycle()
            rotated
        } catch (_: Throwable) {
            source
        }
    }

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

        val hash = try { PlainTextExtractor.sha256File(file) } catch (_: Throwable) { "" }

        // Decode at a sensible size. The JPEG/PNG codec can downsample while decoding, which is far faster
        // and lighter than decoding a 12-megapixel photo in full. 2560px on the long side keeps small print readable.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / sample > MAX_LONG_SIDE) sample *= 2

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 1,
                fullText = "",
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "Image decode failed"
            )
        // Camera photos store their rotation in EXIF. Without applying it, a sideways page reads as garbage.
        val bitmap = applyExifRotation(file, decoded)

        try {
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

            // Run Layout OCR with preprocessing (deskew, denoise, upscale small text; no dimension limit)
            val layoutPage = ocrEngine.recognizeLayout(bitmap, pageNumber = 1)
            val extractedPage = ExtractedPage(
                pageNumber = 1,
                text = layoutPage.fullText,
                confidence = 1.0f
            )

            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 1,
                fullText = layoutPage.fullText,
                pages = listOf(extractedPage),
                layoutPages = listOf(layoutPage),
                contentHash = hash,
                durationMs = System.currentTimeMillis() - startTime,
                isFallbackOcrUsed = true
            )
        } finally {
            bitmap.recycle()
        }
    }
}
