package com.suhrud.docsy.extractor

import android.graphics.Rect

/**
 * Normalized extraction result for any supported document or file.
 */
data class ExtractedPage(
    val pageNumber: Int,
    val text: String,
    val blocks: List<ExtractedBlock> = emptyList(),
    val confidence: Float = 1.0f
)

data class ExtractedBlock(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float = 1.0f,
    val lines: List<String> = emptyList()
)

data class ExtractionResult(
    val contentKey: String,
    val extension: String,
    val pageCount: Int,
    val fullText: String,
    val pages: List<ExtractedPage> = emptyList(),
    val contentHash: String,
    val durationMs: Long,
    val isEncrypted: Boolean = false,
    val isFallbackOcrUsed: Boolean = false,
    val failureReason: String? = null
)
