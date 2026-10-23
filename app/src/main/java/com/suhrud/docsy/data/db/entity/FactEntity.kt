package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doc_facts",
    indices = [
        Index(value = ["documentId", "key"]),
        Index(value = ["documentId"])
    ]
)
data class FactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "normalizedValue")
    val normalizedValue: String? = null,
    @ColumnInfo(name = "evidence")
    val evidence: String? = null,
    @ColumnInfo(name = "page")
    val page: Int = 1,
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "needsConfirm")
    val needsConfirm: Boolean = false,

    // Legacy fields
    @ColumnInfo(name = "isPrimarySource")
    val isPrimarySource: Boolean = true,
    @ColumnInfo(name = "ownerName")
    val ownerName: String? = null,
    @ColumnInfo(name = "extractedAt")
    val extractedAt: Long = System.currentTimeMillis()
) {
    val factType: String get() = key
    val fieldName: String get() = key
    val fieldValue: String get() = value
    val pageNumber: Int get() = page
}
