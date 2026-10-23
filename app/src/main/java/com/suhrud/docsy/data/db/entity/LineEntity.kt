package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lines",
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "page", "lineNo"])
    ]
)
data class LineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "page")
    val page: Int = 1,
    @ColumnInfo(name = "lineNo")
    val lineNo: Int = 0,
    @ColumnInfo(name = "yTop")
    val yTop: Float = 0f,
    @ColumnInfo(name = "xLeft")
    val xLeft: Float = 0f,
    @ColumnInfo(name = "text")
    val text: String
)
