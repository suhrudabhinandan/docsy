package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sheet_meta",
    indices = [
        Index(value = ["doc_id", "sheet"]),
        Index(value = ["doc_id"])
    ]
)
data class SheetMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: Long,
    @ColumnInfo(name = "sheet")
    val sheet: String = "Sheet1",
    @ColumnInfo(name = "header_row")
    val headerRow: Int = 1,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "institution")
    val institution: String? = null,
    @ColumnInfo(name = "batch")
    val batch: String? = null
)
