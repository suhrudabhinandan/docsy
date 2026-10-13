package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.suhrud.docsy.data.db.entity.FactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: FactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(facts: List<FactEntity>): List<Long>

    @Update
    suspend fun updateFact(fact: FactEntity)

    @Query("SELECT * FROM document_facts WHERE documentId = :documentId")
    suspend fun getFactsForDocument(documentId: Long): List<FactEntity>

    @Query("SELECT * FROM document_facts WHERE factType = :factType")
    suspend fun getFactsByType(factType: String): List<FactEntity>

    @Query("SELECT * FROM document_facts WHERE fieldName = :fieldName")
    suspend fun getFactsByFieldName(fieldName: String): List<FactEntity>

    @Query("SELECT * FROM document_facts WHERE fieldName = :fieldName AND fieldValue = :fieldValue")
    suspend fun getFactsByFieldValue(fieldName: String, fieldValue: String): List<FactEntity>

    @Query("SELECT * FROM document_facts WHERE ownerName = :ownerName")
    suspend fun getFactsByOwner(ownerName: String): List<FactEntity>

    @Query("SELECT DISTINCT ownerName FROM document_facts WHERE ownerName IS NOT NULL AND ownerName != ''")
    suspend fun getDistinctOwners(): List<String>

    @Query("SELECT * FROM document_facts WHERE fieldValue LIKE '%' || :query || '%' OR fieldName LIKE '%' || :query || '%'")
    suspend fun searchFacts(query: String): List<FactEntity>

    @Query("DELETE FROM document_facts WHERE documentId = :documentId")
    suspend fun deleteFactsForDocument(documentId: Long)

    @Query("DELETE FROM document_facts")
    suspend fun deleteAllFacts()

    @Query("SELECT COUNT(*) FROM document_facts")
    fun getFactCount(): Flow<Int>
}
