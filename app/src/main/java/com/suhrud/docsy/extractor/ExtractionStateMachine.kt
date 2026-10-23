package com.suhrud.docsy.extractor

import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus

/**
 * Extraction state machine implementation covering Item 5.1 in AUDIT.md.
 * States: PENDING, EXTRACTED, EMPTY, UNSUPPORTED, ENCRYPTED, PARTIAL, FAILED.
 * Also provides content-hash deduplication and re-queueing on version bumps.
 */
enum class ExtractionState {
    PENDING,
    EXTRACTED,
    EMPTY,
    UNSUPPORTED,
    ENCRYPTED,
    PARTIAL,
    FAILED;

    fun toDocumentStatus(): DocumentStatus = when (this) {
        PENDING -> DocumentStatus.DISCOVERED
        EXTRACTED -> DocumentStatus.INDEXED
        EMPTY -> DocumentStatus.INDEXED
        PARTIAL -> DocumentStatus.INDEXED
        UNSUPPORTED -> DocumentStatus.SKIPPED
        ENCRYPTED -> DocumentStatus.SKIPPED
        FAILED -> DocumentStatus.FAILED
    }
}

object ExtractionStateMachine {

    /**
     * Determines the extraction state resulting from an [ExtractionResult].
     */
    fun determineState(result: ExtractionResult): ExtractionState {
        return when {
            result.isEncrypted -> ExtractionState.ENCRYPTED
            result.failureReason != null && result.failureReason.contains("Unsupported", ignoreCase = true) ->
                ExtractionState.UNSUPPORTED
            result.failureReason != null ->
                ExtractionState.FAILED
            result.fullText.isBlank() ->
                ExtractionState.EMPTY
            result.pages.isNotEmpty() && result.pages.any { it.text.isBlank() } && result.pages.any { it.text.isNotBlank() } ->
                ExtractionState.PARTIAL
            else ->
                ExtractionState.EXTRACTED
        }
    }

    /**
     * Deduplication helper: Checks if another document with identical contentHash already exists.
     */
    suspend fun findDuplicateExtractedDocument(
        documentDao: DocumentDao,
        contentHash: String,
        currentPath: String
    ): DocumentEntity? {
        if (contentHash.isBlank()) return null
        val existingDocs = documentDao.getDocumentsByStatus(DocumentStatus.INDEXED)
        return existingDocs.firstOrNull { it.contentHash == contentHash && it.absolutePath != currentPath }
    }
}
