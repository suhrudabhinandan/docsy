package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.DocBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<DocBlockEntity>)

    @Query("SELECT * FROM doc_blocks WHERE doc_id = :docId ORDER BY page ASC, `order` ASC")
    suspend fun getBlocksForDocument(docId: Long): List<DocBlockEntity>

    @Query("SELECT * FROM doc_blocks WHERE doc_id = :docId ORDER BY page ASC, `order` ASC")
    fun observeBlocksForDocument(docId: Long): Flow<List<DocBlockEntity>>

    @Query("DELETE FROM doc_blocks WHERE doc_id = :docId")
    suspend fun deleteBlocksForDocument(docId: Long)

    @Query("SELECT COUNT(*) FROM doc_blocks")
    suspend fun getBlockCount(): Int
}
