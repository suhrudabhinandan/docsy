package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doc_entities",
    indices = [
        Index(value = ["doc_id"]),
        Index(value = ["entity_id"])
    ]
)
data class DocEntityRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: Long,
    @ColumnInfo(name = "entity_id")
    val entityId: Long,
    @ColumnInfo(name = "role")
    val role: String // owner, issuer, subject, account_holder, mentioned
)
