package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["doc_id", "sheet"]),
        Index(value = ["doc_id"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: Long,
    @ColumnInfo(name = "sheet")
    val sheet: String = "Sheet1",
    @ColumnInfo(name = "row_no")
    val rowNo: Int,
    @ColumnInfo(name = "col_index")
    val colIndex: Int,
    @ColumnInfo(name = "col_key")
    val colKey: String? = null,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "value_type")
    val valueType: String = "text"
)
