package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "marksheets",
    indices = [
        Index(value = ["document_id"], unique = true),
        Index(value = ["level", "exam_year"]),
        Index(value = ["roll_no"])
    ]
)
data class MarksheetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "document_id")
    val documentId: Long,

    @ColumnInfo(name = "level")
    val level: String, // '10TH' | '12TH' | 'SEMESTER' | 'OTHER'

    @ColumnInfo(name = "board")
    val board: String? = null,

    @ColumnInfo(name = "exam_name")
    val examName: String? = null,

    @ColumnInfo(name = "exam_year")
    val examYear: Int? = null,

    @ColumnInfo(name = "exam_session")
    val examSession: String? = null, // 'MAIN' | 'COMPARTMENT' | 'SUPPLEMENTARY'

    @ColumnInfo(name = "stream")
    val stream: String? = null, // 'SCIENCE' | 'COMMERCE' | 'ARTS' | 'OTHER'

    @ColumnInfo(name = "semester")
    val semester: String? = null,

    @ColumnInfo(name = "student_name")
    val studentName: String? = null,

    @ColumnInfo(name = "mother_name")
    val motherName: String? = null,

    @ColumnInfo(name = "father_name")
    val fatherName: String? = null,

    @ColumnInfo(name = "dob")
    val dob: String? = null,

    @ColumnInfo(name = "roll_no")
    val rollNo: String? = null,

    @ColumnInfo(name = "regn_no")
    val regnNo: String? = null,

    @ColumnInfo(name = "certificate_no")
    val certificateNo: String? = null,

    @ColumnInfo(name = "school_code")
    val schoolCode: String? = null,

    @ColumnInfo(name = "school_name")
    val schoolName: String? = null,

    @ColumnInfo(name = "result")
    val result: String? = null, // 'PASS' | 'FAIL' | 'COMPARTMENT' | 'ESSENTIAL_REPEAT' | 'WITHHELD'

    @ColumnInfo(name = "division")
    val division: String? = null,

    @ColumnInfo(name = "issue_date")
    val issueDate: String? = null,

    @ColumnInfo(name = "total_obtained")
    val totalObtained: Double? = null,

    @ColumnInfo(name = "total_max")
    val totalMax: Double? = null,

    @ColumnInfo(name = "percentage_printed")
    val percentagePrinted: Double? = null,

    @ColumnInfo(name = "percentage_all")
    val percentageAll: Double? = null,

    @ColumnInfo(name = "percentage_best5")
    val percentageBest5: Double? = null,

    @ColumnInfo(name = "sgpa")
    val sgpa: Double? = null,

    @ColumnInfo(name = "cgpa")
    val cgpa: Double? = null,

    @ColumnInfo(name = "total_credits")
    val totalCredits: Double? = null,

    @ColumnInfo(name = "read_by")
    val readBy: String = "NATIVE", // 'NATIVE' | 'OCR' | 'VLM'

    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,

    @ColumnInfo(name = "needs_review")
    val needsReview: Boolean = false,

    @ColumnInfo(name = "validation_flags")
    val validationFlags: String? = null,

    @ColumnInfo(name = "extra_json")
    val extraJson: String? = null,

    @ColumnInfo(name = "extracted_at")
    val extractedAt: Long = System.currentTimeMillis()
)
