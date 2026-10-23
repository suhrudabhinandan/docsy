package com.suhrud.docsy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doc_json")
data class DocJsonEntity(
    @PrimaryKey
    val documentId: Long,
    val json: String
)
