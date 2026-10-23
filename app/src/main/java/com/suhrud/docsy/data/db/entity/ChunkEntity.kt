package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunks",
    indices = [
        Index(value = ["documentId"])
    ]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "page")
    val page: Int,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "kind")
    val kind: String = "PARAGRAPH", // HEADER | KEYVALUE | TABLE_ROW | PARAGRAPH | LINE
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "contentHash")
    val contentHash: String = ""
) {
    val type: String get() = kind
}
