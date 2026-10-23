package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.ChunkEntity

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>): List<Long>

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: Long)

    @Query("SELECT contentHash FROM chunks WHERE documentId = :documentId LIMIT 1")
    suspend fun getContentHash(documentId: Long): String?

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY page ASC, position ASC")
    suspend fun getChunksByDocumentId(documentId: Long): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun getChunkCount(): Int
}
