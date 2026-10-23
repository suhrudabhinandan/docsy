package com.suhrud.docsy.extractor

import android.graphics.Rect

enum class DocumentLayoutType {
    SINGLE_COLUMN_TEXT,
    MULTI_COLUMN_TEXT,
    FORM_KEY_VALUE,
    TABLE_GRID,
    SCANNED_IMAGE_PAGE,
    HYBRID_MIXED
}

data class PageLayoutAnalysis(
    val pageNumber: Int,
    val layoutType: DocumentLayoutType,
    val columnCount: Int,
    val hasTables: Boolean,
    val hasKeyValues: Boolean,
    val lineCount: Int,
    val confidence: Float
)

object LayoutDetector {

    fun detectPageLayout(
        analysisResult: LayoutPageAnalysisResult,
        pageNumber: Int = 1
    ): PageLayoutAnalysis {
        val lines = analysisResult.lines
        val tables = analysisResult.tables
        val keyValues = analysisResult.keyValues

        if (lines.isEmpty() && analysisResult.fullText.isBlank()) {
            return PageLayoutAnalysis(
                pageNumber = pageNumber,
                layoutType = DocumentLayoutType.SCANNED_IMAGE_PAGE,
                columnCount = 1,
                hasTables = false,
                hasKeyValues = false,
                lineCount = 0,
                confidence = 0.90f
            )
        }

        val hasTables = tables.isNotEmpty()
        val hasKeyValues = keyValues.isNotEmpty()

        // Column detection based on x-coordinates of lines
        val xPositions = lines.map { it.xLeft }
        val distinctColumns = if (xPositions.isNotEmpty()) {
            val minX = xPositions.minOrNull() ?: 0f
            val maxX = xPositions.maxOrNull() ?: 0f
            val range = maxX - minX
            if (range > 300f) 2 else 1
        } else 1

        val layoutType = when {
            hasTables -> DocumentLayoutType.TABLE_GRID
            hasKeyValues -> DocumentLayoutType.FORM_KEY_VALUE
            distinctColumns > 1 -> DocumentLayoutType.MULTI_COLUMN_TEXT
            lines.isNotEmpty() -> DocumentLayoutType.SINGLE_COLUMN_TEXT
            else -> DocumentLayoutType.HYBRID_MIXED
        }

        return PageLayoutAnalysis(
            pageNumber = pageNumber,
            layoutType = layoutType,
            columnCount = distinctColumns,
            hasTables = hasTables,
            hasKeyValues = hasKeyValues,
            lineCount = lines.size,
            confidence = 0.92f
        )
    }
}
