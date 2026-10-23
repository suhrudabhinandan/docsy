package com.suhrud.docsy.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "doc_layout",
    primaryKeys = ["documentId", "page"]
)
data class DocLayoutEntity(
    val documentId: Long,
    val page: Int,
    val blob: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocLayoutEntity
        if (documentId != other.documentId) return false
        if (page != other.page) return false
        if (!blob.contentEquals(other.blob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = documentId.hashCode()
        result = 31 * result + page
        result = 31 * result + blob.contentHashCode()
        return result
    }
}
