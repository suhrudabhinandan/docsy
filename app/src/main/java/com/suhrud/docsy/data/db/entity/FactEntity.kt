package com.suhrud.docsy.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_facts",
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["factType"]),
        Index(value = ["fieldName"]),
        Index(value = ["fieldValue"])
    ]
)
data class FactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val factType: String,
    val fieldName: String,
    val fieldValue: String,
    val confidence: Float = 1.0f,
    val pageNumber: Int = 1,
    val boundingBox: String? = null,
    val isPrimarySource: Boolean = true,
    val ownerName: String? = null,
    val extractedAt: Long = System.currentTimeMillis()
)
