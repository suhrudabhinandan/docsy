package com.suhrud.docsy.extractor

import com.suhrud.docsy.extractor.models.EmbeddedMediaItem
import com.suhrud.docsy.extractor.models.ExtractedCell
import com.suhrud.docsy.extractor.models.ExtractedParagraph
import com.suhrud.docsy.extractor.models.ExtractedRow
import com.suhrud.docsy.extractor.models.ExtractedTable
import com.suhrud.docsy.extractor.models.ExtractedWorksheet
import com.suhrud.docsy.extractor.models.ExtractionMethod
import com.suhrud.docsy.extractor.models.ProvenanceInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

object OfficeXmlExtractor {

    data class DocxAnalysisResult(
        val fullText: String,
        val paragraphs: List<ExtractedParagraph> = emptyList(),
        val tables: List<ExtractedTable> = emptyList(),
        val headers: List<String> = emptyList(),
        val footers: List<String> = emptyList(),
        val mediaItems: List<EmbeddedMediaItem> = emptyList()
    )

    data class XlsxAnalysisResult(
        val fullText: String,
        val worksheets: List<ExtractedWorksheet> = emptyList()
    )

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
                        sb.append(parser.text ?: "")
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

    fun extractDocxDetailed(filePath: String, zip: ZipFile): DocxAnalysisResult {
        val paragraphs = mutableListOf<ExtractedParagraph>()
        val tables = mutableListOf<ExtractedTable>()
        val headers = mutableListOf<String>()
        val footers = mutableListOf<String>()
        val mediaItems = mutableListOf<EmbeddedMediaItem>()
        val textBuilder = StringBuilder()

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("word/header") && entry.name.endsWith(".xml")) {
                val headerText = parseXmlText(zip.getInputStream(entry), "w:t", "w:p")
                if (headerText.isNotBlank()) headers.add(headerText.trim())
            } else if (entry.name.startsWith("word/footer") && entry.name.endsWith(".xml")) {
                val footerText = parseXmlText(zip.getInputStream(entry), "w:t", "w:p")
                if (footerText.isNotBlank()) footers.add(footerText.trim())
            } else if (entry.name.startsWith("word/media/")) {
                val mediaName = entry.name.substringAfterLast('/')
                val ext = mediaName.substringAfterLast('.', "").lowercase()
                val mime = when (ext) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    else -> "application/octet-stream"
                }
                mediaItems.add(
                    EmbeddedMediaItem(
                        mediaName = mediaName,
                        mimeType = mime,
                        sizeBytes = entry.size,
                        provenance = ProvenanceInfo(filePath, extractionMethod = ExtractionMethod.DOCX_PARSER)
                    )
                )
            }
        }

        if (headers.isNotEmpty()) {
            textBuilder.append("=== Headers ===\n").append(headers.joinToString("\n")).append("\n\n")
        }

        val docEntry = zip.getEntry("word/document.xml")
        if (docEntry != null) {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(zip.getInputStream(docEntry), "UTF-8")

            var eventType = parser.eventType
            val currentP = StringBuilder()
            var isHeading = false
            var headingLevel = 0
            var inTable = false
            var currentTableRows = mutableListOf<ExtractedRow>()
            var currentTableRowCells = mutableListOf<ExtractedCell>()
            val currentCellText = StringBuilder()
            var inCell = false
            var rowIndex = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "w:p" -> {
                                currentP.clear()
                                isHeading = false
                                headingLevel = 0
                            }
                            "w:pStyle" -> {
                                val valAttr = parser.getAttributeValue(null, "w:val") ?: ""
                                if (valAttr.startsWith("Heading", ignoreCase = true) || valAttr.startsWith("Title", ignoreCase = true)) {
                                    isHeading = true
                                    headingLevel = valAttr.filter { it.isDigit() }.toIntOrNull() ?: 1
                                }
                            }
                            "w:tbl" -> {
                                inTable = true
                                currentTableRows = mutableListOf()
                                rowIndex = 0
                            }
                            "w:tr" -> {
                                currentTableRowCells = mutableListOf()
                            }
                            "w:tc" -> {
                                inCell = true
                                currentCellText.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text ?: ""
                        if (inCell) {
                            currentCellText.append(txt)
                        } else {
                            currentP.append(txt)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "w:tc" -> {
                                inCell = false
                                val cellVal = currentCellText.toString().trim()
                                currentTableRowCells.add(
                                    ExtractedCell(
                                        coordinate = "R${rowIndex + 1}C${currentTableRowCells.size + 1}",
                                        rawValue = cellVal,
                                        provenance = ProvenanceInfo(filePath, extractionMethod = ExtractionMethod.DOCX_PARSER)
                                    )
                                )
                            }
                            "w:tr" -> {
                                if (currentTableRowCells.isNotEmpty()) {
                                    currentTableRows.add(
                                        ExtractedRow(
                                            rowIndex = rowIndex,
                                            cells = currentTableRowCells.toList(),
                                            isHeader = rowIndex == 0
                                        )
                                    )
                                    rowIndex++
                                }
                            }
                            "w:tbl" -> {
                                inTable = false
                                if (currentTableRows.isNotEmpty()) {
                                    val table = ExtractedTable(
                                        rows = currentTableRows.toList(),
                                        provenance = ProvenanceInfo(filePath, extractionMethod = ExtractionMethod.DOCX_PARSER)
                                    )
                                    tables.add(table)
                                    textBuilder.append("\n[Table: ${table.rows.size} rows]\n")
                                    table.rows.forEach { r ->
                                        textBuilder.append(r.cells.joinToString(" | ") { it.rawValue }).append("\n")
                                    }
                                    textBuilder.append("\n")
                                }
                            }
                            "w:p" -> {
                                if (!inTable) {
                                    val pText = currentP.toString().trim()
                                    if (pText.isNotBlank()) {
                                        val p = ExtractedParagraph(
                                            text = pText,
                                            isHeading = isHeading,
                                            headingLevel = headingLevel,
                                            provenance = ProvenanceInfo(filePath, extractionMethod = ExtractionMethod.DOCX_PARSER)
                                        )
                                        paragraphs.add(p)
                                        if (isHeading) textBuilder.append("# ".repeat(headingLevel.coerceAtLeast(1)))
                                        textBuilder.append(pText).append("\n")
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        if (footers.isNotEmpty()) {
            textBuilder.append("\n=== Footers ===\n").append(footers.joinToString("\n")).append("\n")
        }

        return DocxAnalysisResult(
            fullText = textBuilder.toString(),
            paragraphs = paragraphs,
            tables = tables,
            headers = headers,
            footers = footers,
            mediaItems = mediaItems
        )
    }

    private fun parseSheetDetailed(
        filePath: String,
        inputStream: InputStream,
        sheetName: String,
        sharedStrings: List<String>
    ): ExtractedWorksheet {
        val cells = mutableListOf<ExtractedCell>()
        val mergedRefs = mutableSetOf<String>()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType

        var currentCellRef = ""
        var currentCellType = "n"
        var currentVal = ""
        var currentFormula: String? = null
        var inV = false
        var inF = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: "n"
                            currentVal = ""
                            currentFormula = null
                        }
                        "v" -> inV = true
                        "f" -> inF = true
                        "mergeCell" -> {
                            val ref = parser.getAttributeValue(null, "ref") ?: ""
                            if (ref.isNotBlank()) mergedRefs.add(ref)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV) currentVal += parser.text ?: ""
                    if (inF) currentFormula = (currentFormula ?: "") + (parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inV = false
                        "f" -> inF = false
                        "c" -> {
                            val resolvedVal = when (currentCellType) {
                                "s" -> {
                                    val idx = currentVal.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else currentVal
                                }
                                "b" -> if (currentVal == "1") "TRUE" else "FALSE"
                                else -> currentVal
                            }

                            val isMerged = mergedRefs.any { it.contains(currentCellRef) }
                            val dataTypeStr = when {
                                currentFormula != null -> "FORMULA"
                                currentCellType == "s" -> "STRING"
                                currentCellType == "b" -> "BOOLEAN"
                                resolvedVal.toDoubleOrNull() != null -> "NUMBER"
                                else -> "STRING"
                            }

                            if (resolvedVal.isNotBlank() || currentFormula != null) {
                                cells.add(
                                    ExtractedCell(
                                        coordinate = currentCellRef,
                                        rawValue = resolvedVal,
                                        formula = currentFormula,
                                        dataType = dataTypeStr,
                                        isMerged = isMerged,
                                        provenance = ProvenanceInfo(
                                            sourcePath = filePath,
                                            worksheetName = sheetName,
                                            cellCoordinate = currentCellRef,
                                            extractionMethod = ExtractionMethod.XLSX_PARSER
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val updatedCells = cells.map { cell ->
            cell.copy(isMerged = mergedRefs.any { ref -> ref.contains(cell.coordinate) })
        }

        val rowsMap = updatedCells.groupBy { it.coordinate.filter { char -> char.isDigit() }.toIntOrNull() ?: 1 }
        val extractedRows = rowsMap.map { (rowIdx, rowCells) ->
            ExtractedRow(rowIndex = rowIdx, cells = rowCells, isHeader = rowIdx == 1)
        }.sortedBy { it.rowIndex }

        val table = ExtractedTable(
            worksheetName = sheetName,
            headers = extractedRows.firstOrNull { it.isHeader }?.cells?.map { it.rawValue } ?: emptyList(),
            rows = extractedRows,
            provenance = ProvenanceInfo(sourcePath = filePath, worksheetName = sheetName, extractionMethod = ExtractionMethod.XLSX_PARSER)
        )

        return ExtractedWorksheet(
            sheetName = sheetName,
            tables = listOf(table),
            cells = updatedCells,
            rowCount = extractedRows.size,
            columnCount = updatedCells.maxOfOrNull { it.coordinate.filter { char -> char.isLetter() }.length } ?: 0
        )
    }

    fun extractXlsxDetailed(filePath: String, zip: ZipFile): XlsxAnalysisResult {
        val worksheets = mutableListOf<ExtractedWorksheet>()
        val fullTextBuilder = StringBuilder()

        val sheetNames = mutableListOf<String>()
        val workbookEntry = zip.getEntry("xl/workbook.xml")
        if (workbookEntry != null) {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(zip.getInputStream(workbookEntry), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                    sheetNames.add(name)
                }
                eventType = parser.next()
            }
        }

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
                    XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                    XmlPullParser.TEXT -> if (inT) sb.append(parser.text ?: "")
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

        val entries = zip.entries()
        var sheetIndex = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                val sheetName = sheetNames.getOrNull(sheetIndex) ?: "Sheet${sheetIndex + 1}"
                val sheetResult = parseSheetDetailed(filePath, zip.getInputStream(entry), sheetName, sharedStrings)
                worksheets.add(sheetResult)

                fullTextBuilder.append("=== Worksheet: ").append(sheetName).append(" ===\n")
                sheetResult.cells.forEach { cell ->
                    val valStr = if (cell.formula != null) "[${cell.coordinate}=${cell.formula}] ${cell.rawValue}" else "[${cell.coordinate}] ${cell.rawValue}"
                    fullTextBuilder.append(valStr).append(" ")
                }
                fullTextBuilder.append("\n\n")
                sheetIndex++
            }
        }

        return XlsxAnalysisResult(
            fullText = fullTextBuilder.toString(),
            worksheets = worksheets
        )
    }

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
                val (fullText, quality) = when (extension) {
                    "docx" -> {
                        val docxResult = extractDocxDetailed(file.absolutePath, zip)
                        val metrics = ExtractionQualityAssessment.evaluate(
                            text = docxResult.fullText,
                            pageCount = 1,
                            extractionMethod = ExtractionMethod.DOCX_PARSER,
                            isOcrRequired = false
                        )
                        Pair(docxResult.fullText, metrics)
                    }
                    "xlsx" -> {
                        val xlsxResult = extractXlsxDetailed(file.absolutePath, zip)
                        val metrics = ExtractionQualityAssessment.evaluate(
                            text = xlsxResult.fullText,
                            pageCount = xlsxResult.worksheets.size.coerceAtLeast(1),
                            extractionMethod = ExtractionMethod.XLSX_PARSER,
                            isOcrRequired = false
                        )
                        Pair(xlsxResult.fullText, metrics)
                    }
                    "pptx" -> {
                        val pptxText = extractPptx(zip)
                        val metrics = ExtractionQualityAssessment.evaluate(
                            text = pptxText,
                            pageCount = 1,
                            extractionMethod = ExtractionMethod.STRUCTURED_XML,
                            isOcrRequired = false
                        )
                        Pair(pptxText, metrics)
                    }
                    else -> Pair("", ExtractionQualityAssessment.evaluate("", 0, ExtractionMethod.UNKNOWN))
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
                    pageCount = quality.pageCount,
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
                failureReason = "Office XML extraction error: ${e.message}"
            )
        }
    }
}
