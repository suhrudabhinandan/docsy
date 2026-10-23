package com.suhrud.docsy.extractor.api

import android.graphics.Bitmap
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.LayoutPageAnalysisResult
import java.io.File

/**
 * Interface for format-aware document extractors.
 */
interface IDocumentExtractor {
    suspend fun extract(file: File): ExtractionResult
    fun supportsExtension(extension: String): Boolean
}

/**
 * Interface for OCR engines.
 */
interface IOcrEngine {
    suspend fun recognizeText(bitmap: Bitmap): String
    suspend fun recognizeLayout(bitmap: Bitmap, pageNumber: Int): LayoutPageAnalysisResult?
    fun close()
}

/**
 * Interface for document classification.
 */
interface IDocumentClassifier {
    fun classifyDocument(file: File, extractedText: String): String
}

/**
 * Interface for structured entity extraction (e.g. key-value pairs, dates, amounts).
 */
interface IStructuredEntityExtractor {
    fun extractEntities(docType: String, text: String): Map<String, Any>
}

/**
 * Interface for vector embedding generation.
 */
interface IEmbeddingEngine {
    suspend fun generateEmbedding(text: String): FloatArray?
}

/**
 * Interface for local LLM / SLM inference.
 */
interface ILocalLlmManager {
    fun isAvailable(): Boolean
    suspend fun generateResponse(prompt: String): String?
}
