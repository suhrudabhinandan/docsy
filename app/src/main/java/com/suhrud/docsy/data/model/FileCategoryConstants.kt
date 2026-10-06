package com.suhrud.docsy.data.model

import java.util.Locale

object FileCategoryConstants {
    val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "flv")
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "wma")
    val PDF_EXTENSIONS = setOf("pdf")
    val WORD_EXTENSIONS = setOf("doc", "docx")
    val EXCEL_EXTENSIONS = setOf("xls", "xlsx", "csv")
    val PPT_EXTENSIONS = setOf("ppt", "pptx")
    val TEXT_EXTENSIONS = setOf("txt", "log", "md", "json", "xml")

    val ALL_DOCUMENT_EXTENSIONS = PDF_EXTENSIONS + WORD_EXTENSIONS + EXCEL_EXTENSIONS + PPT_EXTENSIONS + TEXT_EXTENSIONS

    fun isPhoto(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in PHOTO_EXTENSIONS || mimeType.startsWith("image/")
    }

    fun isVideo(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS || mimeType.startsWith("video/")
    }

    fun isAudio(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in AUDIO_EXTENSIONS || mimeType.startsWith("audio/")
    }

    fun isPdf(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in PDF_EXTENSIONS || mimeType.contains("pdf")
    }

    fun isWord(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in WORD_EXTENSIONS || mimeType.contains("word")
    }

    fun isExcel(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in EXCEL_EXTENSIONS || mimeType.contains("excel") || mimeType.contains("spreadsheet") || mimeType == "text/csv"
    }

    fun isPowerPoint(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in PPT_EXTENSIONS || mimeType.contains("presentation") || mimeType.contains("powerpoint")
    }

    fun isText(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in TEXT_EXTENSIONS || (mimeType.startsWith("text/") && !isExcel(extension, mimeType))
    }

    fun isDocument(extension: String, mimeType: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in ALL_DOCUMENT_EXTENSIONS ||
                isPdf(extension, mimeType) ||
                isWord(extension, mimeType) ||
                isExcel(extension, mimeType) ||
                isPowerPoint(extension, mimeType) ||
                isText(extension, mimeType)
    }

    fun isHiddenOrThumbnailPath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.contains("/.thumbnails") ||
                lower.contains("/.cache") ||
                lower.contains("/.trashed") ||
                lower.contains("/.pending") ||
                lower.contains("/.nomedia") ||
                lower.contains("/.git/") ||
                lower.substringAfterLast('/').startsWith(".")
    }
}
