package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doc_tables",
    indices = [
        Index(value = ["doc_id"]),
        Index(value = ["doc_id", "page", "table_id"])
    ]
)
data class DocTableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: Long,
    @ColumnInfo(name = "page")
    val page: Int = 1,
    @ColumnInfo(name = "table_id")
    val tableId: Int = 0,
    @ColumnInfo(name = "row")
    val row: Int = 0,
    @ColumnInfo(name = "col")
    val col: Int = 0,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "conf")
    val conf: Float = 1.0f
)
