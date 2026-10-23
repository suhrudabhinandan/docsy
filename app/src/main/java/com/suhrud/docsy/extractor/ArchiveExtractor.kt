package com.suhrud.docsy.extractor

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedArchiveChild(
    val entryName: String,
    val tempFile: File,
    val sizeBytes: Long
)

object ArchiveExtractor {

    private const val MAX_ENTRIES_INDEX = 100
    private const val MAX_TEMP_FILE_SIZE_BYTES = 10 * 1024 * 1024L

    fun extractArchiveInfoAndChildren(
        archiveFile: File,
        tempDir: File,
        onChildExtracted: (ExtractedArchiveChild) -> Unit
    ): ExtractionResult {
        val contentKey = "${archiveFile.absolutePath}:${archiveFile.length()}:${archiveFile.lastModified()}"
        if (!archiveFile.exists() || !archiveFile.isFile || archiveFile.length() == 0L) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = archiveFile.extension,
                pageCount = 0,
                fullText = "",
                contentHash = contentKey,
                durationMs = 0L,
                failureReason = "Invalid archive file"
            )
        }

        val sb = StringBuilder()
        sb.append("Archive: ").append(archiveFile.name).append("\n")
        sb.append("Size: ").append(archiveFile.length() / 1024).append(" KB\n\n")
        sb.append("Contained Files:\n")

        val entryNames = mutableListOf<String>()
        var count = 0

        try {
            archiveFile.inputStream().use { fileStream ->
                ZipInputStream(fileStream).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry
                    while (entry != null && count < MAX_ENTRIES_INDEX) {
                        if (!entry.isDirectory) {
                            val name = entry.name
                            val size = entry.size
                            entryNames.add(name)
                            sb.append("- ").append(name).append(" (").append(if (size > 0) size / 1024 else 0).append(" KB)\n")

                            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            if (ext in listOf("pdf", "docx", "xlsx", "txt", "png", "jpg", "jpeg", "csv", "json", "html")) {
                                if (entry.size <= MAX_TEMP_FILE_SIZE_BYTES) {
                                    val safeName = "arch_child_${System.currentTimeMillis()}_${name.replace('/', '_')}"
                                    val tempFile = File(tempDir, safeName)
                                    try {
                                        FileOutputStream(tempFile).use { out ->
                                            val buffer = ByteArray(8192)
                                            var read: Int
                                            var totalRead = 0L
                                            while (zipStream.read(buffer).also { read = it } != -1) {
                                                out.write(buffer, 0, read)
                                                totalRead += read
                                                if (totalRead > MAX_TEMP_FILE_SIZE_BYTES) break
                                            }
                                        }
                                        if (tempFile.exists() && tempFile.length() > 0) {
                                            onChildExtracted(ExtractedArchiveChild(name, tempFile, tempFile.length()))
                                        }
                                    } catch (_: Exception) {
                                        if (tempFile.exists()) tempFile.delete()
                                    }
                                }
                            }
                            count++
                        }
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("\n[Archive listing partial: ").append(e.message).append("]")
        }

        return ExtractionResult(
            contentKey = contentKey,
            extension = archiveFile.extension,
            pageCount = 1,
            fullText = sb.toString().trim(),
            contentHash = contentKey,
            durationMs = 0L,
            isFallbackOcrUsed = false,
            failureReason = null
        )
    }
}
