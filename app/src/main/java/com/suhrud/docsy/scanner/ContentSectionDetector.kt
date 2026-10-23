package com.suhrud.docsy.scanner

import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

object ContentSectionDetector {

    private const val HEADER_SIZE = 8192

    fun detectSection(file: File): FileSection {
        if (file.extension.equals("sms", ignoreCase = true) ||
            file.name.contains("sms", ignoreCase = true) ||
            file.path.contains("sms", ignoreCase = true)
        ) {
            return FileSection.SMS
        }

        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return FileSection.fromExtension(file.extension)
        }

        val headerBytes = try {
            file.inputStream().use { stream ->
                val buffer = ByteArray(HEADER_SIZE)
                val read = stream.read(buffer)
                if (read > 0) buffer.copyOf(read) else ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }

        if (headerBytes.isEmpty()) {
            return FileSection.fromExtension(file.extension)
        }

        // 1. Check Magic Bytes
        val magicSection = detectFromMagicBytes(headerBytes, file)
        if (magicSection != null) {
            return magicSection
        }

        // 2. Check Text / Code / Data Content
        if (isTextContent(headerBytes)) {
            val textSection = detectFromTextContent(file, headerBytes)
            if (textSection != null) {
                return textSection
            }
        }

        // 3. Extension hint fallback
        return FileSection.fromExtension(file.extension)
    }

    fun detectFromMagicBytes(header: ByteArray, file: File): FileSection? {
        val len = header.size

        // PDF: %PDF-
        if (len >= 5 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte() && header[4] == 0x2D.toByte()
        ) {
            return FileSection.PDF
        }

        // PNG: 0x89 PNG
        if (len >= 8 && header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()
        ) {
            return FileSection.IMAGES
        }

        // JPEG: 0xFF 0xD8 0xFF
        if (len >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            return FileSection.IMAGES
        }

        // GIF: GIF87a / GIF89a
        if (len >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
            return FileSection.IMAGES
        }

        // BMP: BM
        if (len >= 2 && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) {
            return FileSection.IMAGES
        }

        // TIFF / DNG: II*. / MM.*
        if (len >= 4 && ((header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte()) ||
                    (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() && header[3] == 0x2A.toByte()))
        ) {
            return FileSection.IMAGES
        }

        // RTF: {\rtf1
        val headerString = String(header.take(500).toByteArray(), StandardCharsets.ISO_8859_1)
        if (headerString.startsWith("{\\rtf1")) {
            return FileSection.WORD
        }

        // OLE2 Compound Document: 0xD0 0xCF 0x11 0xE0 0xA1 0xB1 0x1A 0xE1 (.doc or .xls)
        if (len >= 8 && header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
        ) {
            val contentStr = String(header, StandardCharsets.ISO_8859_1).lowercase()
            return when {
                contentStr.contains("workbook") || contentStr.contains("book") -> FileSection.EXCEL
                contentStr.contains("worddocument") -> FileSection.WORD
                else -> if (file.extension.equals("xls", true)) FileSection.EXCEL else FileSection.WORD
            }
        }

        // ZIP Archives & OpenXML formats (PK\x03\x04 or PK\x05\x06)
        if (len >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte())
        ) {
            return inspectZipContent(file)
        }

        // 7z (\x37\x7A\xBC\xAF\x27\x1C)
        if (len >= 6 && header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte()
        ) {
            return FileSection.ARCHIVES
        }

        // RAR (Rar!)
        if (len >= 4 && header[0] == 'R'.code.toByte() && header[1] == 'a'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == '!'.code.toByte()
        ) {
            return FileSection.ARCHIVES
        }

        // GZ (\x1F\x8B)
        if (len >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
            return FileSection.ARCHIVES
        }

        // FLAC: fLaC
        if (len >= 4 && header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
        ) {
            return FileSection.AUDIOS
        }

        // WAV / AVI: RIFF....WAVE / RIFF....AVI
        if (len >= 12 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
        ) {
            val riffType = String(header.copyOfRange(8, 12), StandardCharsets.US_ASCII)
            if (riffType == "WAVE") return FileSection.AUDIOS
            if (riffType == "AVI") return FileSection.VIDEOS
        }

        // OGG / Opus: OggS
        if (len >= 4 && header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()
        ) {
            return FileSection.AUDIOS
        }

        // MP3 ID3 header
        if (len >= 3 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            return FileSection.AUDIOS
        }

        // AMR: #!AMR
        if (len >= 5 && String(header.copyOfRange(0, 5), StandardCharsets.US_ASCII) == "#!AMR") {
            return FileSection.AUDIOS
        }

        // ISO BMFF (MP4, MOV, 3GP, M4A, HEIC) - ftyp box at offset 4
        if (len >= 12 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        ) {
            val ftypMajor = String(header.copyOfRange(8, 12), StandardCharsets.US_ASCII).lowercase()
            return when {
                ftypMajor.contains("heic") || ftypMajor.contains("heim") || ftypMajor.contains("mif1") -> FileSection.IMAGES
                ftypMajor.contains("m4a") || ftypMajor.contains("mp42") && file.extension.equals("m4a", true) -> FileSection.AUDIOS
                else -> FileSection.VIDEOS
            }
        }

        // Matroska / WebM EBML: 0x1A 0x45 0xDF 0xA3
        if (len >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return FileSection.VIDEOS
        }

        return null
    }

    private fun inspectZipContent(file: File): FileSection {
        var hasAndroidManifest = false
        var hasWordDoc = false
        var hasExcelDoc = false
        var hasPdfEntry = false
        var hasImageEntry = false

        try {
            file.inputStream().use { fileStream ->
                ZipInputStream(fileStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    var entriesChecked = 0
                    while (entry != null && entriesChecked < 30) {
                        val name = entry.name.lowercase(Locale.ROOT)
                        if (name == "androidmanifest.xml" || name == "resources.arsc" || name.startsWith("base/manifest/")) {
                            hasAndroidManifest = true
                            break
                        }
                        if (name.startsWith("word/document.xml") || name == "[content_types].xml" && name.contains("wordprocessingml")) {
                            hasWordDoc = true
                        }
                        if (name.startsWith("xl/workbook.xml") || name == "[content_types].xml" && name.contains("spreadsheetml")) {
                            hasExcelDoc = true
                        }
                        if (name.endsWith(".pdf")) hasPdfEntry = true
                        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) hasImageEntry = true

                        entriesChecked++
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (_: Exception) {}

        return when {
            hasAndroidManifest || file.extension.equals("apk", true) || file.extension.equals("aab", true) || file.extension.equals("xapk", true) -> FileSection.APPS
            hasWordDoc || file.extension.equals("docx", true) || file.extension.equals("dotx", true) -> FileSection.WORD
            hasExcelDoc || file.extension.equals("xlsx", true) || file.extension.equals("xlsm", true) -> FileSection.EXCEL
            else -> FileSection.ARCHIVES
        }
    }

    private fun isTextContent(header: ByteArray): Boolean {
        var nulls = 0
        val checkLen = header.size.coerceAtMost(1024)
        for (i in 0 until checkLen) {
            if (header[i] == 0.toByte()) nulls++
        }
        return nulls < checkLen * 0.05
    }

    private fun detectFromTextContent(file: File, header: ByteArray): FileSection? {
        val text = try {
            String(header, StandardCharsets.UTF_8).trim()
        } catch (_: Exception) {
            return null
        }
        if (text.isBlank()) return null

        val lower = text.lowercase(Locale.ROOT)

        // 1. Saved Web Page / HTML
        if (lower.contains("<!doctype html") || lower.contains("<html") || lower.contains("<head>") ||
            lower.contains("<body") || lower.contains("<div") || lower.contains("mhtml document")
        ) {
            return FileSection.CODE_WEB
        }

        // 2. Jupyter Notebook (ipynb)
        if (lower.contains("\"cells\":") && lower.contains("\"cell_type\":")) {
            return FileSection.CODE_WEB
        }

        // 3. Tabular JSON vs Code JSON / Tabular XML
        if (lower.startsWith("[") || lower.startsWith("{")) {
            if (isTabularStructuredDataJson(text)) {
                return FileSection.EXCEL
            }
        }
        if (lower.startsWith("<?xml") || lower.startsWith("<data") || lower.startsWith("<rows") || lower.startsWith("<table")) {
            if (lower.contains("<row>") || lower.contains("<record>") || lower.contains("<entry>")) {
                return FileSection.EXCEL
            }
        }

        // 4. Source code syntax indicators
        val codePatterns = listOf(
            "package ", "import ", "class ", "interface ", "function ", "def ", "val ", "var ",
            "const ", "let ", "public ", "private ", "#include", "using namespace", "<?php",
            "select ", "insert into", "create table", "from ", "where ", "style>", "script>", "```"
        )
        if (codePatterns.any { lower.contains(it) }) {
            return FileSection.CODE_WEB
        }

        // Extension matches for text files
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext in FileSection.CODE_WEB.extensions) {
            return FileSection.CODE_WEB
        }
        if (ext in FileSection.EXCEL.extensions) {
            return FileSection.EXCEL
        }

        return null
    }

    private fun isTabularStructuredDataJson(text: String): Boolean {
        try {
            val clean = text.trim()
            if (clean.startsWith("[")) {
                val firstBrace = clean.indexOf('{')
                val closeBrace = clean.indexOf('}')
                if (firstBrace != -1 && closeBrace > firstBrace) {
                    return true
                }
            } else if (clean.startsWith("{")) {
                val lower = clean.lowercase(Locale.ROOT)
                if (lower.contains("\"rows\"") || lower.contains("\"data\"") || lower.contains("\"records\"") || lower.contains("\"items\"")) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }
}
