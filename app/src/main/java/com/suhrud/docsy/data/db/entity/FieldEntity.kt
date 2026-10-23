package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fields",
    indices = [
        Index(value = ["documentId", "key"]),
        Index(value = ["documentId"])
    ]
)
data class FieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "label")
    val label: String = key,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "normalizedValue")
    val normalizedValue: String? = null,
    @ColumnInfo(name = "valueType")
    val valueType: String = "text",
    @ColumnInfo(name = "page")
    val page: Int = 1,
    @ColumnInfo(name = "lineNo")
    val lineNo: Int = 0,
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "sourceBlockId")
    val sourceBlockId: Long? = null,
    @ColumnInfo(name = "isDerived")
    val isDerived: Boolean = false,
    @ColumnInfo(name = "formula")
    val formula: String? = null,
    @ColumnInfo(name = "sensitive")
    val sensitive: Boolean = false,
    @ColumnInfo(name = "userEdited")
    val userEdited: Boolean = false,
    @ColumnInfo(name = "validationWarning")
    val validationWarning: String? = null,
    @ColumnInfo(name = "needsReview")
    val needsReview: Boolean = false
)
