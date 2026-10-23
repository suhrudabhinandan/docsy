package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "relations",
    indices = [
        Index(value = ["a_id"]),
        Index(value = ["b_id"]),
        Index(value = ["evidence_doc_id"])
    ]
)
data class RelationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "a_id")
    val aId: Long,
    @ColumnInfo(name = "b_id")
    val bId: Long,
    @ColumnInfo(name = "relation")
    val relation: String,
    @ColumnInfo(name = "score")
    val score: Float,
    @ColumnInfo(name = "evidence_doc_id")
    val evidenceDocId: Long
)
