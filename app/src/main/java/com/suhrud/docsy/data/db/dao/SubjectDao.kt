package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.SubjectEntity

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>): List<Long>

    @Query("SELECT * FROM subjects WHERE documentId = :documentId")
    suspend fun getSubjectsForDocument(documentId: Long): List<SubjectEntity>

    @Query("DELETE FROM subjects WHERE documentId = :documentId")
    suspend fun deleteSubjectsForDocument(documentId: Long)

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun getSubjectCount(): Int

    @Query("SELECT COUNT(DISTINCT documentId) FROM subjects")
    suspend fun getDocsWithSubjectsCount(): Int
}
