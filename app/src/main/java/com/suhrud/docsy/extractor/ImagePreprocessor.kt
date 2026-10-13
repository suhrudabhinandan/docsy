package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImagePreprocessor {

    /**
     * Fast check to determine if an image has candidate text regions before running full OCR.
     * Computes localized contrast variance across downscaled image thumbnail.
     */
    fun hasTextCandidates(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false

        val stepX = (width / 80).coerceAtLeast(1)
        val stepY = (height / 80).coerceAtLeast(1)

        var totalContrastTransitions = 0
        var totalSamples = 0

        for (y in 0 until height - stepY step stepY) {
            for (x in 0 until width - stepX step stepX) {
                totalSamples++
                val pCurr = bitmap.getPixel(x, y)
                val pRight = bitmap.getPixel(x + stepX, y)
                val pDown = bitmap.getPixel(x, y + stepY)

                val lumCurr = (Color.red(pCurr) * 299 + Color.green(pCurr) * 587 + Color.blue(pCurr) * 114) / 1000
                val lumRight = (Color.red(pRight) * 299 + Color.green(pRight) * 587 + Color.blue(pRight) * 114) / 1000
                val lumDown = (Color.red(pDown) * 299 + Color.green(pDown) * 587 + Color.blue(pDown) * 114) / 1000

                if (Math.abs(lumCurr - lumRight) > 25 || Math.abs(lumCurr - lumDown) > 25) {
                    totalContrastTransitions++
                }
            }
        }

        return totalSamples > 0 && totalContrastTransitions > (totalSamples * 0.01)
    }

    /**
     * Grayscale + high contrast enhancement for faded scans and low-contrast marksheets.
     */
    fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // Grayscale conversion
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)

        // Increase contrast
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
}
