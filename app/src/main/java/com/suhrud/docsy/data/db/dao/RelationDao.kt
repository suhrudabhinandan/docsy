package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.RelationRecord

@Dao
interface RelationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: RelationRecord): Long

    @Query("SELECT * FROM relations WHERE a_id = :entityId OR b_id = :entityId")
    suspend fun getRelationsForEntity(entityId: Long): List<RelationRecord>

    @Query("SELECT * FROM relations WHERE evidence_doc_id = :docId")
    suspend fun getRelationsForDoc(docId: Long): List<RelationRecord>

    @Query("DELETE FROM relations WHERE evidence_doc_id = :docId")
    suspend fun deleteRelationsForDoc(docId: Long)

    @Query("DELETE FROM relations")
    suspend fun deleteAllRelations()
}
