package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<DocumentEntity>): List<Long>

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE path = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE contentHash = :contentHash OR path = :contentHash LIMIT 1")
    suspend fun getDocumentByContentKey(contentHash: String): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE state = :status ORDER BY modified DESC")
    suspend fun getDocumentsByStatus(status: DocumentStatus): List<DocumentEntity>

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents ORDER BY modified DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents")
    fun getDocumentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE state = :status")
    fun getCountByStatus(status: DocumentStatus): Flow<Int>

    @Query("UPDATE documents SET state = :status, failureReason = :failureReason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DocumentStatus, failureReason: String? = null)

    @Query("DELETE FROM documents WHERE path = :path")
    suspend fun deleteDocumentByPath(path: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM documents WHERE path NOT IN (:existingPaths)")
    suspend fun deleteMissingPaths(existingPaths: List<String>)

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents ORDER BY size DESC LIMIT 1")
    suspend fun getLargestDocument(): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE size > 0 ORDER BY size ASC LIMIT 1")
    suspend fun getSmallestDocument(): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents ORDER BY modified DESC LIMIT 1")
    suspend fun getNewestDocument(): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE modified > 0 ORDER BY modified ASC LIMIT 1")
    suspend fun getOldestDocument(): DocumentEntity?

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents ORDER BY modified DESC")
    suspend fun getAllDocumentsSnapshot(): List<DocumentEntity>

    @Query("SELECT id, path, name, ext, mime, size, modified, contentHash, docType, docTypeScore, ownerName, ownerEvidence, state, extractorVersion, extractedAt, lang, section, substr(summary, 1, 1024) as summary, NULL as extractedText, extractionMethod, failureReason, pageCount, tokenCount, display_title, chars_extracted, owner_entity_id, updated_at, runnerUpType, runnerUpScore, pipelineStepsJson FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT extractedText FROM documents WHERE id = :id LIMIT 1")
    suspend fun getExtractedTextById(id: Long): String?

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()
}
