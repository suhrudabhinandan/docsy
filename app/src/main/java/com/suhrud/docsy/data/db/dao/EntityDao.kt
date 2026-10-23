package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.EntityRecord

@Dao
interface EntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: EntityRecord): Long

    @Query("SELECT * FROM entities WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: Long): EntityRecord?

    @Query("SELECT * FROM entities WHERE type = :type AND canonical_name = :canonicalName LIMIT 1")
    suspend fun getEntityByTypeAndName(type: String, canonicalName: String): EntityRecord?

    @Query("SELECT * FROM entities WHERE type = :type")
    suspend fun getEntitiesByType(type: String): List<EntityRecord>

    @Query("SELECT * FROM entities")
    suspend fun getAllEntities(): List<EntityRecord>

    @Query("DELETE FROM entities")
    suspend fun deleteAllEntities()
}
