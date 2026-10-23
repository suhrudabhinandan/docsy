package com.suhrud.docsy.scanner

import java.io.File
import java.util.Locale

object FileFilter {

    val OFFICE_EXTENSIONS = setOf("pdf", "docx", "doc", "dotx", "xlsx", "xls", "xlsm", "pptx", "ppt")
    val TEXT_STRUCTURED_EXTENSIONS = setOf("txt", "csv", "md", "html", "htm", "xml", "json", "rtf")
    val OPENDOCUMENT_EXTENSIONS = setOf("odt", "ods", "odp")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "tiff", "tif")

    // Document-only focus: media files (images, audio, video) are excluded from background scanning
    val PRIMARY_DOCUMENT_EXTENSIONS = OFFICE_EXTENSIONS + TEXT_STRUCTURED_EXTENSIONS + OPENDOCUMENT_EXTENSIONS
    val ALL_SUPPORTED_EXTENSIONS = PRIMARY_DOCUMENT_EXTENSIONS

    fun isPrimaryDocumentFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val ext = getExtension(file.name)
        return ext in PRIMARY_DOCUMENT_EXTENSIONS
    }

    fun isSupportedFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val ext = getExtension(file.name)
        return ext in ALL_SUPPORTED_EXTENSIONS
    }

    fun isSupportedExtension(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in ALL_SUPPORTED_EXTENSIONS
    }

    fun isOfficeDocument(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in OFFICE_EXTENSIONS
    }

    fun isTextOrStructuredData(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in TEXT_STRUCTURED_EXTENSIONS
    }

    fun isOpenDocument(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in OPENDOCUMENT_EXTENSIONS
    }

    fun isImageDocument(extension: String): Boolean {
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
            "md" -> "text/markdown"
            "html", "htm" -> "text/html"
            "xml" -> "text/xml"
            "json" -> "application/json"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "db", "sqlite" -> "application/x-sqlite3"
            else -> "application/octet-stream"
        }
    }

    fun computeContentKey(path: String, length: Long, lastModified: Long): String {
        return "$path:$length:$lastModified"
    }

    /**
     * Traversal skip filter:
     * - Skips cache/tmp/temp/thumbnails dirs
     * - Skips Android/data and Android/obb (blocked by OS)
     * - Skips media directories (DCIM, Pictures, Movies, Music, WhatsApp Media, Telegram Media)
     */
    fun shouldSkipDirectory(dir: File, docsyPackageDir: String?): Boolean {
        val name = dir.name.lowercase(Locale.ROOT)
        if (name == "cache" || name == ".cache" || name == "code_cache") return true
        if (name == "tmp" || name == "temp") return true
        if (name == ".thumbnails" || name == "thumbnails" || name == "thumbs.db") return true

        val path = dir.absolutePath
        if (path.contains("/Android/data", ignoreCase = true) ||
            path.contains("/Android/obb", ignoreCase = true)
        ) {
            return true
        }

        if (docsyPackageDir != null && path.startsWith(docsyPackageDir)) {
            return true
        }

        // Exclude media, photo, video, audio directories
        if (path.contains("/DCIM", ignoreCase = true) ||
            path.contains("/Pictures", ignoreCase = true) ||
            path.contains("/Movies", ignoreCase = true) ||
            path.contains("/Music", ignoreCase = true) ||
            path.contains("/Audio", ignoreCase = true) ||
            path.contains("/WhatsApp/Media", ignoreCase = true) ||
            path.contains("/Telegram/Telegram Images", ignoreCase = true) ||
            path.contains("/Telegram/Telegram Video", ignoreCase = true)
        ) {
            return true
        }

        return false
    }
}
