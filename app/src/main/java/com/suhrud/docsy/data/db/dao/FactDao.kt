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

    @Query("SELECT * FROM doc_facts WHERE documentId = :documentId")
    suspend fun getFactsForDocument(documentId: Long): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE key = :factType")
    suspend fun getFactsByType(factType: String): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE key = :fieldName")
    suspend fun getFactsByFieldName(fieldName: String): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE documentId = :documentId AND key = :key")
    suspend fun getFactsByDocumentAndKey(documentId: Long, key: String): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE key = :fieldName AND value = :fieldValue")
    suspend fun getFactsByFieldValue(fieldName: String, fieldValue: String): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE ownerName = :ownerName")
    suspend fun getFactsByOwner(ownerName: String): List<FactEntity>

    @Query("SELECT DISTINCT ownerName FROM doc_facts WHERE ownerName IS NOT NULL AND ownerName != ''")
    suspend fun getDistinctOwners(): List<String>

    @Query("SELECT * FROM doc_facts WHERE value LIKE '%' || :query || '%' OR key LIKE '%' || :query || '%'")
    suspend fun searchFacts(query: String): List<FactEntity>

    @Query("SELECT * FROM doc_facts WHERE value = :value OR normalizedValue = :value")
    suspend fun searchFactsByExactValue(value: String): List<FactEntity>

    @Query("DELETE FROM doc_facts WHERE documentId = :documentId")
    suspend fun deleteFactsForDocument(documentId: Long)

    @Query("DELETE FROM doc_facts")
    suspend fun deleteAllFacts()

    @Query("SELECT COUNT(*) FROM doc_facts")
    fun getFactCount(): Flow<Int>
}
