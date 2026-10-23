package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.DocEntityRecord

@Dao
interface DocEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocEntity(docEntity: DocEntityRecord): Long

    @Query("SELECT * FROM doc_entities WHERE doc_id = :docId")
    suspend fun getEntitiesForDoc(docId: Long): List<DocEntityRecord>

    @Query("SELECT doc_id FROM doc_entities WHERE entity_id = :entityId")
    suspend fun getDocIdsForEntity(entityId: Long): List<Long>

    @Query("DELETE FROM doc_entities WHERE doc_id = :docId")
    suspend fun deleteDocEntitiesForDoc(docId: Long)

    @Query("DELETE FROM doc_entities")
    suspend fun deleteAllDocEntities()
}
