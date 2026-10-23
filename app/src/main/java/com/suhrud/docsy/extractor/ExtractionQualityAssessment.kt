package com.suhrud.docsy.extractor

import com.suhrud.docsy.extractor.models.ExtractionMethod
import com.suhrud.docsy.extractor.models.QualityMetrics

/**
 * Assesses the quality and completeness of extracted content.
 * Provides testable, configurable quality evaluation and OCR fallback decisions.
 */
object ExtractionQualityAssessment {

    data class Config(
        val minMeaningfulCharsPerPage: Int = 25,
        val maxBrokenCharRatio: Float = 0.05f,
        val minPrintableRatio: Float = 0.85f
    )

    fun evaluate(
        text: String,
        pageCount: Int = 1,
        extractionMethod: ExtractionMethod,
        isOcrRequired: Boolean = false,
        config: Config = Config()
    ): QualityMetrics {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val totalChars = text.length
        if (totalChars == 0) {
            errors.add("No text extracted")
            return QualityMetrics(
                extractionMethod = extractionMethod,
                characterCount = 0,
                pageCount = pageCount,
                isSuccess = false,
                isOcrRequired = isOcrRequired,
                isPartial = true,
                qualityScore = 0.0f,
                errors = errors,
                warnings = warnings
            )
        }

        val meaningfulChars = text.count { it.isLetterOrDigit() }
        val brokenChars = text.count { it == '\uFFFD' || it in '\uE000'..'\uF8FF' || (it.isISOControl() && !it.isWhitespace()) }
        val brokenRatio = if (totalChars > 0) brokenChars.toFloat() / totalChars else 0.0f

        val avgCharsPerPage = if (pageCount > 0) meaningfulChars / pageCount else meaningfulChars
        val isLowDensity = avgCharsPerPage < config.minMeaningfulCharsPerPage

        if (brokenRatio > config.maxBrokenCharRatio) {
            warnings.add("High proportion of broken or unprintable characters (${(brokenRatio * 100).toInt()}%)")
        }
        if (isLowDensity) {
            warnings.add("Low meaningful text density ($avgCharsPerPage chars/page)")
        }

        val densityScore = (avgCharsPerPage.toFloat() / 200f).coerceIn(0.1f, 1.0f)
        val cleanlinessScore = (1.0f - (brokenRatio * 5f)).coerceIn(0.0f, 1.0f)
        val qualityScore = (densityScore * 0.5f + cleanlinessScore * 0.5f).coerceIn(0.0f, 1.0f)

        val isPartial = isLowDensity || brokenRatio > config.maxBrokenCharRatio

        return QualityMetrics(
            extractionMethod = extractionMethod,
            characterCount = totalChars,
            pageCount = pageCount,
            isSuccess = totalChars > 0,
            isOcrRequired = isOcrRequired || isLowDensity || brokenRatio > config.maxBrokenCharRatio,
            isPartial = isPartial,
            qualityScore = qualityScore,
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Determines whether native PDF page text needs OCR fallback based on quality criteria.
     */
    fun needsOcrFallback(pageText: String, config: Config = Config()): Boolean {
        if (pageText.isBlank()) return true
        val meaningful = pageText.count { it.isLetterOrDigit() }
        if (meaningful < config.minMeaningfulCharsPerPage) return true
        val broken = pageText.count { it == '\uFFFD' || it in '\uE000'..'\uF8FF' || (it.isISOControl() && !it.isWhitespace()) }
        val brokenRatio = broken.toFloat() / pageText.length.coerceAtLeast(1)
        return brokenRatio > config.maxBrokenCharRatio
    }
}
