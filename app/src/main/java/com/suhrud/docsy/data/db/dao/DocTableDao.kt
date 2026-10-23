package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.DocTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocTableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<DocTableEntity>)

    @Query("SELECT * FROM doc_tables WHERE doc_id = :docId ORDER BY page ASC, table_id ASC, row ASC, col ASC")
    suspend fun getTablesForDocument(docId: Long): List<DocTableEntity>

    @Query("SELECT * FROM doc_tables WHERE doc_id = :docId ORDER BY page ASC, table_id ASC, row ASC, col ASC")
    fun observeTablesForDocument(docId: Long): Flow<List<DocTableEntity>>

    @Query("SELECT * FROM doc_tables WHERE text LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchTableCells(query: String, limit: Int = 20): List<DocTableEntity>

    @Query("SELECT * FROM doc_tables WHERE doc_id = :docId AND table_id = :tableId AND `row` = :row ORDER BY col ASC")
    suspend fun getTableRowCells(docId: Long, tableId: Int, row: Int): List<DocTableEntity>

    @Query("SELECT * FROM doc_tables WHERE doc_id = :docId AND table_id = :tableId AND `row` = 0 ORDER BY col ASC")
    suspend fun getTableHeaderCells(docId: Long, tableId: Int): List<DocTableEntity>

    @Query("DELETE FROM doc_tables WHERE doc_id = :docId")
    suspend fun deleteTablesForDocument(docId: Long)

    @Query("SELECT COUNT(*) FROM doc_tables")
    suspend fun getTableCount(): Int
}
