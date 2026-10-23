package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.DocJsonEntity

@Dao
interface DocJsonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocJson(docJson: DocJsonEntity)

    @Query("SELECT * FROM doc_json WHERE documentId = :documentId LIMIT 1")
    suspend fun getDocJson(documentId: Long): DocJsonEntity?

    @Query("DELETE FROM doc_json WHERE documentId = :documentId")
    suspend fun deleteDocJson(documentId: Long)

    @Query("SELECT COUNT(*) FROM doc_json")
    suspend fun getDocJsonCount(): Long
}
