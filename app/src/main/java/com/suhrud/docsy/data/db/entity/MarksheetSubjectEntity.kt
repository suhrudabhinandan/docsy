package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "marksheet_subjects",
    indices = [
        Index(value = ["marksheet_id"])
    ]
)
data class MarksheetSubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "marksheet_id")
    val marksheetId: Long,

    @ColumnInfo(name = "row_no")
    val rowNo: Int,

    @ColumnInfo(name = "subject_code")
    val subjectCode: String? = null,

    @ColumnInfo(name = "subject_name")
    val subjectName: String,

    @ColumnInfo(name = "subject_kind")
    val subjectKind: String = "MAIN", // 'MAIN' | 'ADDITIONAL' | 'ELECTIVE' | 'VOCATIONAL'

    @ColumnInfo(name = "theory_marks")
    val theoryMarks: Double? = null,

    @ColumnInfo(name = "internal_marks")
    val internalMarks: Double? = null,

    @ColumnInfo(name = "total_marks")
    val totalMarks: Double? = null,

    @ColumnInfo(name = "max_marks")
    val maxMarks: Double? = 100.0,

    @ColumnInfo(name = "marks_flag")
    val marksFlag: String? = null, // 'AB' | 'ER' | 'NC'

    @ColumnInfo(name = "total_in_words")
    val totalInWords: String? = null,

    @ColumnInfo(name = "grade")
    val grade: String? = null,

    @ColumnInfo(name = "grade_point")
    val gradePoint: Double? = null,

    @ColumnInfo(name = "credits")
    val credits: Double? = null,

    @ColumnInfo(name = "subject_result")
    val subjectResult: String? = null, // 'PASS' | 'FAIL' | 'COMP'

    @ColumnInfo(name = "in_best5")
    val inBest5: Boolean = false,

    @ColumnInfo(name = "source")
    val source: String = "TABLE", // 'TABLE' | 'TEXT' | 'VLM' | 'CODE_DICT'

    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,

    @ColumnInfo(name = "needs_review")
    val needsReview: Boolean = false
)
