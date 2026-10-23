package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.RecordEntity

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<RecordEntity>): List<Long>

    @Query("SELECT * FROM records WHERE doc_id = :docId ORDER BY sheet ASC, row_no ASC, col_index ASC")
    suspend fun getRecordsForDocument(docId: Long): List<RecordEntity>

    @Query("SELECT COUNT(DISTINCT doc_id) FROM records")
    suspend fun getDocsWithRecordsCount(): Int

    @Query("DELETE FROM records WHERE doc_id = :docId")
    suspend fun deleteRecordsForDocument(docId: Long)
}
