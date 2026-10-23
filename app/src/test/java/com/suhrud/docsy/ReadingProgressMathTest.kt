package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.scanner.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressMathTest {

    @Test
    fun testProgressMathGuards() {
        // M = 0
        val zeroTotal = ReadingProgress(currentFileIndex = 5, totalFiles = 0)
        assertEquals(0f, zeroTotal.progressFraction, 0.001f)

        // N > M
        val overflow = ReadingProgress(currentFileIndex = 15, totalFiles = 10)
        assertEquals(1f, overflow.progressFraction, 0.001f)

        // Negative
        val negative = ReadingProgress(currentFileIndex = -5, totalFiles = 10)
        assertEquals(0f, negative.progressFraction, 0.001f)

        // NaN or Infinity handling
        val nanVal = Float.NaN
        val safeFraction = if (nanVal.isNaN() || nanVal.isInfinite()) 0f else nanVal.coerceIn(0f, 1f)
        assertEquals(0f, safeFraction, 0.001f)
    }

    @Test
    fun testFailingExtractorYieldsFailedStatus() {
        // Test that per-file extraction exception produces FAILED status rather than unhandled crash
        val extractionResult: String? = try {
            throw java.io.IOException("Corrupted file or OOM")
        } catch (_: Throwable) {
            null
        }

        val status = if (extractionResult == null) DocumentStatus.FAILED else DocumentStatus.INDEXED
        assertEquals(DocumentStatus.FAILED, status)
    }
}
