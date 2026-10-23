package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.FieldEntity

@Dao
interface FieldDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<FieldEntity>): List<Long>

    @Query("SELECT * FROM fields WHERE documentId = :documentId")
    suspend fun getFieldsForDocument(documentId: Long): List<FieldEntity>

    @Query("SELECT * FROM fields WHERE documentId = :documentId AND key = :key")
    suspend fun getFieldsByDocumentAndKey(documentId: Long, key: String): List<FieldEntity>

    @Query("SELECT * FROM fields WHERE key = :key")
    suspend fun getFieldsByKey(key: String): List<FieldEntity>

    @Query("DELETE FROM fields WHERE documentId = :documentId")
    suspend fun deleteFieldsForDocument(documentId: Long)

    @Query("SELECT COUNT(*) FROM fields")
    suspend fun getFieldCount(): Int

    @Query("SELECT COUNT(DISTINCT documentId) FROM fields")
    suspend fun getDocsWithFieldsCount(): Int

    @Query("UPDATE fields SET value = :newValue, userEdited = 1 WHERE id = :fieldId")
    suspend fun updateFieldValueByUser(fieldId: Long, newValue: String)
}
