package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
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
        Index(value = ["path"], unique = true),
        Index(value = ["contentHash"]),
        Index(value = ["docType"]),
        Index(value = ["state"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "ext")
    val ext: String,
    @ColumnInfo(name = "mime")
    val mime: String,
    @ColumnInfo(name = "size")
    val size: Long,
    @ColumnInfo(name = "modified")
    val modified: Long,
    @ColumnInfo(name = "contentHash")
    val contentHash: String? = null,
    @ColumnInfo(name = "docType")
    val docType: String? = null,
    @ColumnInfo(name = "docTypeScore")
    val docTypeScore: Float = 0f,
    @ColumnInfo(name = "ownerName")
    val ownerName: String? = null,
    @ColumnInfo(name = "ownerEvidence")
    val ownerEvidence: String? = null,
    @ColumnInfo(name = "state")
    val state: String = "DISCOVERED",
    @ColumnInfo(name = "extractorVersion")
    val extractorVersion: String = "1.0",
    @ColumnInfo(name = "extractedAt")
    val extractedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "lang")
    val lang: String? = null,
    @ColumnInfo(name = "section")
    val section: String = "OTHER",

    // Summary & Legacy compat fields
    @ColumnInfo(name = "summary")
    val summary: String? = null,
    @ColumnInfo(name = "extractedText")
    val extractedText: String? = null,
    @ColumnInfo(name = "extractionMethod")
    val extractionMethod: String? = null,
    @ColumnInfo(name = "failureReason")
    val failureReason: String? = null,
    @ColumnInfo(name = "pageCount")
    val pageCount: Int? = null,
    @ColumnInfo(name = "tokenCount")
    val tokenCount: Int? = null,

    // Part 1 Inspector Fields
    @ColumnInfo(name = "display_title")
    val displayTitle: String? = null,
    @ColumnInfo(name = "chars_extracted")
    val charsExtracted: Int = 0,
    @ColumnInfo(name = "owner_entity_id")
    val ownerEntityId: Long? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "runnerUpType")
    val runnerUpType: String? = null,
    @ColumnInfo(name = "runnerUpScore")
    val runnerUpScore: Float = 0f,
    @ColumnInfo(name = "pipelineStepsJson")
    val pipelineStepsJson: String? = null
) {
    val absolutePath: String get() = path
    val displayName: String get() = displayTitle.takeIf { !it.isNullOrBlank() } ?: name.substringBeforeLast(".")
    val rawFileName: String get() = name
    val extension: String get() = ext
    val mimeType: String get() = mime
    val sizeBytes: Long get() = size
    val lastModified: Long get() = modified
    val contentKey: String get() = contentHash ?: path
    val indexedAt: Long get() = extractedAt
    val status: DocumentStatus get() = try { DocumentStatus.valueOf(state) } catch (_: Exception) { DocumentStatus.DISCOVERED }
    val fileName: String get() = name
    val fileExtension: String get() = ext
    val fileSection: com.suhrud.docsy.scanner.FileSection get() = com.suhrud.docsy.scanner.FileSection.fromId(section)
    val textSummary: String get() = (summary ?: extractedText ?: "").take(2048)
}
