package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doc_blocks",
    indices = [
        Index(value = ["doc_id"]),
        Index(value = ["doc_id", "page", "order"])
    ]
)
data class DocBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: Long,
    @ColumnInfo(name = "page")
    val page: Int = 1,
    @ColumnInfo(name = "order")
    val order: Int = 0,
    @ColumnInfo(name = "type")
    val type: String = "TEXT",
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "x")
    val x: Float = 0f,
    @ColumnInfo(name = "y")
    val y: Float = 0f,
    @ColumnInfo(name = "w")
    val w: Float = 0f,
    @ColumnInfo(name = "h")
    val h: Float = 0f,
    @ColumnInfo(name = "conf")
    val conf: Float = 1.0f
)
