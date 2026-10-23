package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

object ImagePreprocessor {

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
     * Complete preprocessing for layout model:
     * 1. Upscale small text / low resolution images
     * 2. Deskew (detect skew angle and rotate if needed)
     * 3. Denoise and contrast enhancement
     */
    fun preprocess(bitmap: Bitmap): Bitmap {
        var current = bitmap

        // 1. Upscale only genuinely small images. Phone screenshots and scans already have enough pixels,
        //    and doubling them cost seconds per file without helping the recognizer.
        if (maxOf(current.width, current.height) < 1500) {
            val scale = 2.0f
            val newWidth = (current.width * scale).toInt()
            val newHeight = (current.height * scale).toInt()
            val upscaled = Bitmap.createScaledBitmap(current, newWidth, newHeight, true)
            if (current != bitmap) current.recycle()
            current = upscaled
        }

        // 2. Deskew
        val skewAngle = estimateSkewAngle(current)
        if (Math.abs(skewAngle) > 0.5f) {
            val matrix = Matrix()
            matrix.postRotate(-skewAngle)
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (current != bitmap && current != rotated) current.recycle()
            current = rotated
        }

        // 3. Denoise and enhance contrast
        val enhanced = enhanceForOcr(current)
        if (current != bitmap && current != enhanced) {
            current.recycle()
        }

        return enhanced
    }

    fun estimateSkewAngle(bitmap: Bitmap): Float {
        try {
            val w = (bitmap.width / 4).coerceAtLeast(100)
            val h = (bitmap.height / 4).coerceAtLeast(100)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)

            var bestAngle = 0f
            var maxVariance = -1f

            for (angle in -10..10 step 2) {
                val rad = Math.toRadians(angle.toDouble())
                val cos = Math.cos(rad)
                val sin = Math.sin(rad)
                val proj = FloatArray(h)

                for (y in 0 until h step 2) {
                    var sum = 0f
                    for (x in 0 until w step 4) {
                        val pixel = scaled.getPixel(x, y)
                        val lum = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                        if (lum < 150) sum += 1f
                    }
                    val projY = (y * cos - (w / 2) * sin).toInt().coerceIn(0, h - 1)
                    proj[projY] += sum
                }

                var mean = 0f
                for (v in proj) mean += v
                mean /= h.toFloat()

                var variance = 0f
                for (v in proj) variance += (v - mean) * (v - mean)

                if (variance > maxVariance) {
                    maxVariance = variance
                    bestAngle = angle.toFloat()
                }
            }

            scaled.recycle()
            return bestAngle
        } catch (_: Throwable) {
            return 0f
        }
    }

    fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)

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
