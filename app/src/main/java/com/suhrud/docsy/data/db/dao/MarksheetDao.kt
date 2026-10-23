package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.MarksheetEntity
import com.suhrud.docsy.data.db.entity.MarksheetSubjectEntity

@Dao
interface MarksheetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarksheet(marksheet: MarksheetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarksheetSubjects(subjects: List<MarksheetSubjectEntity>)

    @Query("SELECT * FROM marksheets WHERE document_id = :documentId LIMIT 1")
    suspend fun getMarksheetForDocument(documentId: Long): MarksheetEntity?

    @Query("SELECT * FROM marksheet_subjects WHERE marksheet_id = :marksheetId ORDER BY row_no ASC")
    suspend fun getSubjectsForMarksheet(marksheetId: Long): List<MarksheetSubjectEntity>

    @Query("DELETE FROM marksheets WHERE document_id = :documentId")
    suspend fun deleteMarksheetForDocument(documentId: Long)

    @Query("DELETE FROM marksheet_subjects WHERE marksheet_id = :marksheetId")
    suspend fun deleteSubjectsForMarksheet(marksheetId: Long)

    @Query("SELECT COUNT(*) FROM marksheets")
    suspend fun getMarksheetsCount(): Int
}
