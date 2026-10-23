package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.LineEntity

@Dao
interface LineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<LineEntity>): List<Long>

    @Query("SELECT * FROM lines WHERE documentId = :documentId ORDER BY page ASC, lineNo ASC")
    suspend fun getLinesForDocument(documentId: Long): List<LineEntity>

    @Query("DELETE FROM lines WHERE documentId = :documentId")
    suspend fun deleteLinesForDocument(documentId: Long)

    @Query("SELECT COUNT(*) FROM lines")
    suspend fun getLineCount(): Int

    @Query("SELECT COUNT(DISTINCT documentId) FROM lines")
    suspend fun getDocsWithLinesCount(): Int

    @Query("SELECT SUM(length(text)) FROM lines WHERE documentId = :documentId")
    suspend fun getTotalCharCountForDocument(documentId: Long): Long?
}
