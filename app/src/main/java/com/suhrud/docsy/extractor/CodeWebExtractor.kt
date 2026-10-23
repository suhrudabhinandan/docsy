package com.suhrud.docsy.extractor

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONObject

object CodeWebExtractor {

    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L

    fun isSkippedPathOrName(file: File): Boolean {
        val path = file.absolutePath.lowercase(Locale.ROOT)
        val name = file.name.lowercase(Locale.ROOT)

        if (path.contains("/node_modules/") || path.contains("/.git/") ||
            path.contains("/build/") || path.contains("/.cache/") ||
            path.contains("/dist/") || path.contains("/target/") ||
            path.contains("/.gradle/")
        ) {
            return true
        }

        if (name.endsWith(".min.js") || name.endsWith(".min.css") ||
            name.endsWith(".bundle.js") || name.endsWith(".map") ||
            name.startsWith("jquery.") || name.contains("minified")
        ) {
            return true
        }

        return false
    }

    fun isMinifiedContent(text: String): Boolean {
        val lines = text.lines().take(20)
        if (lines.isEmpty()) return false
        val avgLineLength = lines.map { it.length }.average()
        return avgLineLength > 500
    }

    fun extract(file: File): ExtractionResult {
        val contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return ExtractionResult(contentKey, file.extension, 0, "", emptyList(), emptyList(), contentKey, 0L, failureReason = "Empty or missing file")
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            return ExtractionResult(contentKey, file.extension, 0, "", emptyList(), emptyList(), contentKey, 0L, failureReason = "File size exceeds 2MB limit")
        }

        if (isSkippedPathOrName(file)) {
            return ExtractionResult(contentKey, file.extension, 0, "", emptyList(), emptyList(), contentKey, 0L, failureReason = "Skipped generated or minified file")
        }

        val rawBytes = try { file.readBytes() } catch (e: Exception) {
            return ExtractionResult(contentKey, file.extension, 0, "", emptyList(), emptyList(), contentKey, 0L, failureReason = "Read error: ${e.message}")
        }
        val encoding = detectEncoding(rawBytes)
        val rawText = String(rawBytes, encoding)

        if (isMinifiedContent(rawText)) {
            return ExtractionResult(contentKey, file.extension, 0, "", emptyList(), emptyList(), contentKey, 0L, failureReason = "Minified text skipped")
        }

        val ext = file.extension.lowercase(Locale.ROOT)
        val extractedText = when {
            ext == "html" || ext == "htm" || ext == "mhtml" || rawText.lowercase().contains("<!doctype html") || rawText.lowercase().contains("<html") -> {
                extractHtmlText(rawText)
            }
            ext == "ipynb" || rawText.contains("\"cells\":") && rawText.contains("\"cell_type\":") -> {
                extractIpynbNotebook(rawText)
            }
            else -> {
                extractSourceCodeText(rawText, ext)
            }
        }

        return ExtractionResult(
            contentKey = contentKey,
            extension = file.extension,
            pageCount = 1,
            fullText = extractedText,
            contentHash = contentKey,
            durationMs = 0L,
            isFallbackOcrUsed = false,
            failureReason = null
        )
    }

    private fun detectEncoding(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }
        return StandardCharsets.UTF_8
    }

    fun extractHtmlText(htmlContent: String): String {
        val titleMatch = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(htmlContent)
        val title = if (titleMatch.find()) titleMatch.group(1)?.trim() else null

        var clean = htmlContent.replace(Regex("(?s)<script.*?>.*?</script>"), " ")
        clean = clean.replace(Regex("(?s)<style.*?>.*?</style>"), " ")
        clean = clean.replace(Regex("(?s)<!--.*?-->"), " ")

        clean = clean.replace(Regex("<br\\s*/?>"), "\n")
        clean = clean.replace(Regex("</p>"), "\n")
        clean = clean.replace(Regex("</div>"), "\n")
        clean = clean.replace(Regex("</li>"), "\n")
        clean = clean.replace(Regex("</h[1-6]>"), "\n")
        clean = clean.replace(Regex("<[^>]+>"), " ")

        clean = clean.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")

        val sb = StringBuilder()
        if (!title.isNullOrBlank()) {
            sb.append("Title: ").append(title).append("\n\n")
        }

        clean.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            sb.append(line).append("\n")
        }

        return sb.toString().trim()
    }

    fun extractIpynbNotebook(jsonContent: String): String {
        val sb = StringBuilder()
        try {
            val json = JSONObject(jsonContent)
            val cells = json.optJSONArray("cells")
            if (cells != null) {
                for (i in 0 until cells.length()) {
                    val cell = cells.optJSONObject(i) ?: continue
                    val cellType = cell.optString("cell_type", "code")
                    val sourceObj = cell.opt("source")
                    val sourceText = when (sourceObj) {
                        is org.json.JSONArray -> {
                            val list = mutableListOf<String>()
                            for (j in 0 until sourceObj.length()) list.add(sourceObj.getString(j))
                            list.joinToString("")
                        }
                        else -> sourceObj?.toString() ?: ""
                    }

                    if (sourceText.isNotBlank()) {
                        sb.append("[").append(cellType.uppercase(Locale.ROOT)).append(" CELL ").append(i + 1).append("]\n")
                        sb.append(sourceText.trim()).append("\n\n")
                    }
                }
            }
        } catch (_: Exception) {
            sb.append(jsonContent)
        }
        return sb.toString().trim()
    }

    fun extractSourceCodeText(sourceText: String, extension: String): String {
        val sb = StringBuilder()
        sb.append("Source File (.").append(extension).append(")\n\n")

        val lines = sourceText.lines()
        val functions = mutableListOf<String>()
        val classes = mutableListOf<String>()

        val classPattern = Pattern.compile("""\b(class|interface|object|struct)\s+([A-Za-z0-9_]+)""")
        val funcPattern = Pattern.compile("""\b(fun|function|def|void|int|double|String|var|val|public|private)\s+([A-Za-z0-9_]+)\s*\(""")

        for (line in lines) {
            val classMatcher = classPattern.matcher(line)
            if (classMatcher.find()) {
                classes.add(classMatcher.group(2))
            }
            val funcMatcher = funcPattern.matcher(line)
            if (funcMatcher.find()) {
                functions.add(funcMatcher.group(2))
            }
        }

        if (classes.isNotEmpty()) {
            sb.append("Classes: ").append(classes.distinct().joinToString(", ")).append("\n")
        }
        if (functions.isNotEmpty()) {
            sb.append("Functions: ").append(functions.distinct().take(15).joinToString(", ")).append("\n")
        }
        sb.append("\n")

        lines.map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            sb.append(line).append("\n")
        }

        return sb.toString().trim()
    }
}
