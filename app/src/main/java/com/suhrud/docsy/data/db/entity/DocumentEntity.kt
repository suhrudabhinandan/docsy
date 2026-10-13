package com.suhrud.docsy.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DocumentStatus {
    DISCOVERED,
    READING,
    INDEXED,
    FAILED,
    SKIPPED
}

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["absolutePath"], unique = true),
        Index(value = ["contentKey"]),
        Index(value = ["status"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val absolutePath: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentKey: String,
    val indexedAt: Long = System.currentTimeMillis(),
    val status: DocumentStatus = DocumentStatus.DISCOVERED,
    val failureReason: String? = null,
    val pageCount: Int? = null,
    val tokenCount: Int? = null,
    val contentHash: String? = null
) {
    val fileName: String get() = displayName
    val fileExtension: String get() = extension
}
