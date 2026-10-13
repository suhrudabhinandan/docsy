package com.suhrud.docsy.extractor

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object OfficeXmlExtractor {

    fun extract(file: File): ExtractionResult {
        val startTime = System.currentTimeMillis()
        val extension = file.extension.lowercase()
        val contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"

        if (!file.exists() || !file.canRead()) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 0,
                fullText = "",
                contentHash = "",
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "File not readable"
            )
        }

        try {
            val bytes = file.readBytes()
            val hash = PlainTextExtractor.sha256(bytes)

            ZipFile(file).use { zip ->
                val fullText = when (extension) {
                    "docx" -> extractDocx(zip)
                    "xlsx" -> extractXlsx(zip)
                    "pptx" -> extractPptx(zip)
                    else -> ""
                }

                val normalized = TextNormalizer.normalize(fullText)
                val page = ExtractedPage(
                    pageNumber = 1,
                    text = normalized,
                    confidence = 1.0f
                )

                return ExtractionResult(
                    contentKey = contentKey,
                    extension = extension,
                    pageCount = 1,
                    fullText = normalized,
                    pages = listOf(page),
                    contentHash = hash,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = extension,
                pageCount = 0,
                fullText = "",
                contentHash = "",
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "Zip extraction error: ${e.message}"
            )
        }
    }

    private fun extractDocx(zip: ZipFile): String {
        val entry = zip.getEntry("word/document.xml") ?: return ""
        return parseXmlText(zip.getInputStream(entry), targetTag = "w:t", paragraphTag = "w:p")
    }

    private fun extractXlsx(zip: ZipFile): String {
        // First parse shared strings table
        val sharedStrings = mutableListOf<String>()
        val sstEntry = zip.getEntry("xl/sharedStrings.xml")
        if (sstEntry != null) {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(zip.getInputStream(sstEntry), "UTF-8")
            var eventType = parser.eventType
            val sb = StringBuilder()
            var inT = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) sb.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            sharedStrings.add(sb.toString())
                            sb.clear()
                        } else if (parser.name == "t") {
                            inT = false
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        // Now parse sheets
        val sheetBuilder = StringBuilder()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                sheetBuilder.append(parseSheet(zip.getInputStream(entry), sharedStrings)).append("\n")
            }
        }
        return sheetBuilder.toString()
    }

    private fun parseSheet(inputStream: InputStream, sharedStrings: List<String>): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType
        val result = StringBuilder()
        var isSharedString = false
        var currentVal = ""
        var inV = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "c") {
                        val typeAttr = parser.getAttributeValue(null, "t")
                        isSharedString = (typeAttr == "s")
                    } else if (parser.name == "v") {
                        inV = true
                        currentVal = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV) currentVal += parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "v") {
                        inV = false
                        if (isSharedString) {
                            val idx = currentVal.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) {
                                result.append(sharedStrings[idx]).append(" ")
                            }
                        } else {
                            result.append(currentVal).append(" ")
                        }
                    } else if (parser.name == "row") {
                        result.append("\n")
                    }
                }
            }
            eventType = parser.next()
        }
        return result.toString()
    }

    private fun extractPptx(zip: ZipFile): String {
        val result = StringBuilder()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                result.append(parseXmlText(zip.getInputStream(entry), targetTag = "a:t", paragraphTag = "a:p"))
                result.append("\n\n")
            }
        }
        return result.toString()
    }

    private fun parseXmlText(inputStream: InputStream, targetTag: String, paragraphTag: String): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType
        val sb = StringBuilder()
        var inTarget = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == targetTag) {
                        inTarget = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTarget) {
                        sb.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == targetTag) {
                        inTarget = false
                    } else if (parser.name == paragraphTag) {
                        sb.append("\n")
                    }
                }
            }
            eventType = parser.next()
        }
        return sb.toString()
    }
}
