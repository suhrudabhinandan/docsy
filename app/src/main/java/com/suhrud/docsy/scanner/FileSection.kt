package com.suhrud.docsy.scanner

enum class FileSection(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val extensions: Set<String>,
    val queryKeywords: Set<String>
) {
    PDF(
        id = "pdf",
        displayName = "PDF Documents",
        shortLabel = "PDF",
        extensions = setOf("pdf"),
        queryKeywords = setOf("pdf", "pdfs", "pdf document", "pdf file", "pdf files")
    ),
    WORD(
        id = "word",
        displayName = "Word & Documents",
        shortLabel = "Word",
        extensions = setOf("doc", "docx", "dotx", "odt", "rtf"),
        queryKeywords = setOf("word", "doc", "docx", "document", "documents", "rtf", "odt")
    ),
    EXCEL(
        id = "excel",
        displayName = "Spreadsheets & Data",
        shortLabel = "Excel",
        extensions = setOf("xls", "xlsx", "xlsm", "csv", "ods"),
        queryKeywords = setOf("excel", "spreadsheet", "spreadsheets", "csv", "sheet", "sheets", "xlsx", "xls", "table")
    ),
    IMAGES(
        id = "images",
        displayName = "Photos & Images",
        shortLabel = "Images",
        extensions = setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp", "dng"),
        queryKeywords = setOf("image", "images", "photo", "photos", "picture", "pictures", "png", "jpg", "jpeg")
    ),
    VIDEOS(
        id = "videos",
        displayName = "Video Files",
        shortLabel = "Videos",
        extensions = setOf("mp4", "mkv", "3gp", "webm", "mov"),
        queryKeywords = setOf("video", "videos", "clip", "clips", "mp4", "mov", "mkv", "movie", "movies")
    ),
    AUDIOS(
        id = "audios",
        displayName = "Audio & Music",
        shortLabel = "Audio",
        extensions = setOf("mp3", "m4a", "wav", "ogg", "flac", "amr", "opus"),
        queryKeywords = setOf("audio", "audios", "music", "song", "songs", "recording", "recordings", "mp3", "wav")
    ),
    ARCHIVES(
        id = "archives",
        displayName = "Compressed Archives",
        shortLabel = "Archives",
        extensions = setOf("zip", "rar", "7z", "tar", "gz"),
        queryKeywords = setOf("archive", "archives", "zip", "zipped", "rar", "tar", "gz", "compressed")
    ),
    APPS(
        id = "apps",
        displayName = "Apps & Installers",
        shortLabel = "Apps",
        extensions = setOf("apk", "aab", "xapk"),
        queryKeywords = setOf("app", "apps", "apk", "apks", "installer", "installers", "package", "aab")
    ),
    CODE_WEB(
        id = "code_web",
        displayName = "Code & Web",
        shortLabel = "Code",
        extensions = setOf("html", "htm", "css", "js", "ts", "json", "xml", "kt", "java", "py", "c", "cpp", "sh", "php", "sql", "yaml", "ipynb"),
        queryKeywords = setOf("code", "source", "web", "html", "htm", "css", "javascript", "js", "typescript", "ts", "json", "xml", "kotlin", "kt", "java", "python", "py", "c", "cpp", "sql", "script", "notebook", "ipynb")
    ),
    SMS(
        id = "sms",
        displayName = "SMS Messages",
        shortLabel = "SMS",
        extensions = setOf("sms"),
        queryKeywords = setOf("sms", "message", "messages", "text message", "txt msg")
    ),
    OTHER(
        id = "other",
        displayName = "Other Files",
        shortLabel = "Other",
        extensions = emptySet(),
        queryKeywords = setOf("other", "unknown")
    );

    companion object {
        val SECTIONS_9 = listOf(PDF, WORD, EXCEL, IMAGES, VIDEOS, AUDIOS, ARCHIVES, APPS, CODE_WEB)
        val SECTIONS_10 = listOf(PDF, WORD, EXCEL, IMAGES, VIDEOS, AUDIOS, ARCHIVES, APPS, CODE_WEB, SMS)
        val ALL_SECTIONS = entries
        val ALL_SUPPORTED_EXTENSIONS = SECTIONS_10.flatMap { it.extensions }.toSet()

        fun fromId(id: String?): FileSection {
            if (id.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: OTHER
        }

        fun fromExtension(ext: String): FileSection {
            val cleanExt = ext.lowercase().removePrefix(".")
            return SECTIONS_10.firstOrNull { cleanExt in it.extensions } ?: OTHER
        }

        fun detectQuerySection(query: String): FileSection? {
            val q = query.lowercase()
            for (section in SECTIONS_10) {
                for (kw in section.queryKeywords) {
                    if (q.contains("in my $kw") || q.contains("in $kw") || q.contains("from $kw") || q.contains("$kw files") || q.contains("$kw file")) {
                        return section
                    }
                }
            }
            return null
        }
    }
}
