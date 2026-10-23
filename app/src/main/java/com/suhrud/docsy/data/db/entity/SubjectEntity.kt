package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subjects",
    indices = [
        Index(value = ["documentId"])
    ]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "rowNo")
    val rowNo: Int = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "marks")
    val marks: String? = null,
    @ColumnInfo(name = "maxMarks")
    val maxMarks: String? = null,
    @ColumnInfo(name = "grade")
    val grade: String? = null,
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f
) {
    val subject: String get() = name
}
