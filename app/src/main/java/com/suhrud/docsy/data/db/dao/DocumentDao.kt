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

    @Query("SELECT * FROM documents WHERE absolutePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE contentKey = :contentKey LIMIT 1")
    suspend fun getDocumentByContentKey(contentKey: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE status = :status ORDER BY lastModified DESC")
    suspend fun getDocumentsByStatus(status: DocumentStatus): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY lastModified DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents")
    fun getDocumentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE status = :status")
    fun getCountByStatus(status: DocumentStatus): Flow<Int>

    @Query("UPDATE documents SET status = :status, failureReason = :failureReason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DocumentStatus, failureReason: String? = null)

    @Query("DELETE FROM documents WHERE absolutePath = :path")
    suspend fun deleteDocumentByPath(path: String)

    @Query("DELETE FROM documents WHERE absolutePath NOT IN (:existingPaths)")
    suspend fun deleteMissingPaths(existingPaths: List<String>)

    @Query("SELECT * FROM documents ORDER BY sizeBytes DESC LIMIT 1")
    suspend fun getLargestDocument(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE sizeBytes > 0 ORDER BY sizeBytes ASC LIMIT 1")
    suspend fun getSmallestDocument(): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY lastModified DESC LIMIT 1")
    suspend fun getNewestDocument(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE lastModified > 0 ORDER BY lastModified ASC LIMIT 1")
    suspend fun getOldestDocument(): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsSnapshot(): List<DocumentEntity>

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()
}
