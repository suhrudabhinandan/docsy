package com.suhrud.docsy.scanner

import java.io.File
import java.util.Locale

object FileFilter {

    val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt",
        "txt", "csv", "rtf", "odt", "ods", "odp"
    )

    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif"
    )

    val ALL_SUPPORTED_EXTENSIONS = DOCUMENT_EXTENSIONS + IMAGE_EXTENSIONS

    fun isSupportedFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val ext = getExtension(file.name)
        return ext in ALL_SUPPORTED_EXTENSIONS
    }

    fun isSupportedExtension(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in ALL_SUPPORTED_EXTENSIONS
    }

    fun isDocument(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in DOCUMENT_EXTENSIONS
    }

    fun isImage(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
    }

    fun getExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex == -1 || dotIndex == fileName.length - 1) return ""
        return fileName.substring(dotIndex + 1).lowercase(Locale.ROOT)
    }

    fun sanitizeDisplayName(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        // Replace invalid or control characters with space, trim whitespace
        return baseName.replace(Regex("[\\x00-\\x1F\\x7F]"), " ").trim()
    }

    fun getMimeType(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "application/octet-stream"
        }
    }

    fun computeContentKey(path: String, length: Long, lastModified: Long): String {
        return "$path:$length:$lastModified"
    }

    /**
     * Traversal skip filter.
     * Skips:
     * - Cache dirs: cache, .cache, code_cache
     * - Temp dirs: tmp, temp
     * - Android/data, Android/obb
     * - Docsy package dir
     */
    fun shouldSkipDirectory(dir: File, docsyPackageDir: String?): Boolean {
        val name = dir.name.lowercase(Locale.ROOT)
        if (name == "cache" || name == ".cache" || name == "code_cache") return true
        if (name == "tmp" || name == "temp") return true

        val path = dir.absolutePath
        if (path.contains("/Android/data", ignoreCase = true) ||
            path.contains("/Android/obb", ignoreCase = true)
        ) {
            return true
        }

        if (docsyPackageDir != null && path.startsWith(docsyPackageDir)) {
            return true
        }

        return false
    }
}
