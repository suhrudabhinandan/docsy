package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.DocLayoutEntity

@Dao
interface DocLayoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocLayout(layout: DocLayoutEntity)

    @Query("SELECT * FROM doc_layout WHERE documentId = :documentId AND page = :page LIMIT 1")
    suspend fun getDocLayout(documentId: Long, page: Int): DocLayoutEntity?

    @Query("DELETE FROM doc_layout WHERE documentId = :documentId")
    suspend fun deleteDocLayout(documentId: Long)
}
