package com.suhrud.docsy.tools

import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StorageCategoryCount(
    val category: String,
    val count: Int,
    val totalSizeBytes: Long
)

data class StorageStatsSnapshot(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val categories: List<StorageCategoryCount>,
    val largestFile: DocumentEntity?,
    val smallestFile: DocumentEntity?,
    val newestFile: DocumentEntity?,
    val oldestFile: DocumentEntity?
)

/**
 * Storage and media statistics tool querying the Knowledge Store.
 * Satisfies Phase 8.8 in AUDIT.md.
 */
object StorageStatsTool {

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")
    private val DOC_EXTS = setOf("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "csv")
    private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz")

    suspend fun getSnapshot(documentDao: DocumentDao): StorageStatsSnapshot {
        val allDocs = documentDao.getAllDocumentsSnapshot()
        val totalFiles = allDocs.size
        val totalSize = allDocs.sumOf { it.sizeBytes }

        var imagesCount = 0; var imagesSize = 0L
        var videosCount = 0; var videosSize = 0L
        var audioCount = 0; var audioSize = 0L
        var docsCount = 0; var docsSize = 0L
        var archivesCount = 0; var archivesSize = 0L
        var otherCount = 0; var otherSize = 0L

        for (doc in allDocs) {
            val ext = doc.fileExtension.lowercase()
            when {
                IMAGE_EXTS.contains(ext) -> { imagesCount++; imagesSize += doc.sizeBytes }
                VIDEO_EXTS.contains(ext) -> { videosCount++; videosSize += doc.sizeBytes }
                AUDIO_EXTS.contains(ext) -> { audioCount++; audioSize += doc.sizeBytes }
                DOC_EXTS.contains(ext) -> { docsCount++; docsSize += doc.sizeBytes }
                ARCHIVE_EXTS.contains(ext) -> { archivesCount++; archivesSize += doc.sizeBytes }
                else -> { otherCount++; otherSize += doc.sizeBytes }
            }
        }

        val categories = listOf(
            StorageCategoryCount("Documents", docsCount, docsSize),
            StorageCategoryCount("Images", imagesCount, imagesSize),
            StorageCategoryCount("Videos", videosCount, videosSize),
            StorageCategoryCount("Audio", audioCount, audioSize),
            StorageCategoryCount("Archives", archivesCount, archivesSize),
            StorageCategoryCount("Other", otherCount, otherSize)
        )

        val largest = documentDao.getLargestDocument()
        val smallest = documentDao.getSmallestDocument()
        val newest = documentDao.getNewestDocument()
        val oldest = documentDao.getOldestDocument()

        return StorageStatsSnapshot(
            totalFiles = totalFiles,
            totalSizeBytes = totalSize,
            categories = categories,
            largestFile = largest,
            smallestFile = smallest,
            newestFile = newest,
            oldestFile = oldest
        )
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
            bytes >= 1024L -> "%.0f KB".format(bytes.toDouble() / 1024.0)
            else -> "$bytes B"
        }
    }

    fun formatDate(epochMillis: Long): String {
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
    }

    fun formatCategoryCountAnswer(snapshot: StorageStatsSnapshot, categoryQuery: String): String {
        val lower = categoryQuery.lowercase()
        val cat = when {
            lower.contains("video") -> snapshot.categories.firstOrNull { it.category == "Videos" }
            lower.contains("photo") || lower.contains("image") || lower.contains("picture") ->
                snapshot.categories.firstOrNull { it.category == "Images" }
            lower.contains("audio") || lower.contains("music") || lower.contains("song") ->
                snapshot.categories.firstOrNull { it.category == "Audio" }
            lower.contains("doc") || lower.contains("pdf") ->
                snapshot.categories.firstOrNull { it.category == "Documents" }
            else -> null
        }

        return if (cat != null) {
            "You have ${cat.count} ${cat.category.lowercase()} on this device (${formatFileSize(cat.totalSizeBytes)})."
        } else {
            "Found ${snapshot.totalFiles} indexed files total (${formatFileSize(snapshot.totalSizeBytes)})."
        }
    }

    fun formatSuperlativeAnswer(snapshot: StorageStatsSnapshot, query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("largest") || lower.contains("biggest") -> {
                snapshot.largestFile?.let {
                    "The largest file is \"${it.fileName}\" (${formatFileSize(it.sizeBytes)})."
                } ?: "No files found to determine largest."
            }
            lower.contains("smallest") -> {
                snapshot.smallestFile?.let {
                    "The smallest file is \"${it.fileName}\" (${formatFileSize(it.sizeBytes)})."
                } ?: "No files found to determine smallest."
            }
            lower.contains("newest") || lower.contains("latest") || lower.contains("recent") -> {
                snapshot.newestFile?.let {
                    "The newest file is \"${it.fileName}\", modified on ${formatDate(it.lastModified)}."
                } ?: "No files found to determine newest."
            }
            lower.contains("oldest") -> {
                snapshot.oldestFile?.let {
                    "The oldest file is \"${it.fileName}\", modified on ${formatDate(it.lastModified)}."
                } ?: "No files found to determine oldest."
            }
            else -> formatCategoryCountAnswer(snapshot, query)
        }
    }
}
