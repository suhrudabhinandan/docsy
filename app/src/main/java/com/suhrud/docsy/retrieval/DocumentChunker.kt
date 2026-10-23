package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.entity.ChunkEntity
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.PlainTextExtractor
import com.suhrud.docsy.extractor.models.ExtractedTable
import com.suhrud.docsy.extractor.models.ExtractedWorksheet

object DocumentChunker {

    fun chunkDocument(
        documentId: Long,
        extractionResult: ExtractionResult,
        worksheets: List<ExtractedWorksheet> = emptyList(),
        tables: List<ExtractedTable> = emptyList()
    ): List<ChunkEntity> {
        val chunks = mutableListOf<ChunkEntity>()
        var position = 0

        // 1. Chunk by worksheets / spreadsheet tables
        if (worksheets.isNotEmpty()) {
            for (sheet in worksheets) {
                val sheetName = sheet.sheetName
                for (table in sheet.tables) {
                    val headersStr = table.headers.joinToString(", ")
                    for (row in table.rows) {
                        val rowStr = row.cells.joinToString(" | ") { "${it.coordinate}:${it.rawValue}" }
                        if (rowStr.isNotBlank()) {
                            val contextText = "[Worksheet: $sheetName | Headers: $headersStr] $rowStr"
                            val hash = PlainTextExtractor.sha256(contextText.toByteArray())
                            chunks.add(
                                ChunkEntity(
                                    documentId = documentId,
                                    page = 1,
                                    position = position++,
                                    kind = "WORKSHEET_ROW_GROUP",
                                    text = contextText,
                                    contentHash = hash
                                )
                            )
                        }
                    }
                }
            }
            if (chunks.isNotEmpty()) return chunks
        }

        // 2. Chunk by DOCX tables
        if (tables.isNotEmpty()) {
            for (table in tables) {
                val headersStr = table.headers.joinToString(", ")
                for (row in table.rows) {
                    val rowStr = row.cells.joinToString(" | ") { it.rawValue }
                    if (rowStr.isNotBlank()) {
                        val contextText = if (headersStr.isNotBlank()) "[Headers: $headersStr] $rowStr" else rowStr
                        val hash = PlainTextExtractor.sha256(contextText.toByteArray())
                        chunks.add(
                            ChunkEntity(
                                documentId = documentId,
                                page = 1,
                                position = position++,
                                kind = "TABLE_ROW",
                                text = contextText,
                                contentHash = hash
                            )
                        )
                    }
                }
            }
        }

        // 3. Chunk by PDF pages or text paragraphs
        if (extractionResult.pages.isNotEmpty()) {
            for (page in extractionResult.pages) {
                val pageNo = page.pageNumber
                val paragraphs = page.text.split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }

                for (p in paragraphs) {
                    val trimmed = p.trim()
                    if (trimmed.length < 10) continue

                    val kind = if (trimmed.startsWith("#")) "HEADER" else "PARAGRAPH"
                    val hash = PlainTextExtractor.sha256(trimmed.toByteArray())

                    chunks.add(
                        ChunkEntity(
                            documentId = documentId,
                            page = pageNo,
                            position = position++,
                            kind = kind,
                            text = trimmed,
                            contentHash = hash
                        )
                    )
                }
            }
        } else if (extractionResult.fullText.isNotBlank()) {
            val paragraphs = extractionResult.fullText.split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }
            for (p in paragraphs) {
                val trimmed = p.trim()
                if (trimmed.length < 10) continue
                val hash = PlainTextExtractor.sha256(trimmed.toByteArray())
                chunks.add(
                    ChunkEntity(
                        documentId = documentId,
                        page = 1,
                        position = position++,
                        kind = "PARAGRAPH",
                        text = trimmed,
                        contentHash = hash
                    )
                )
            }
        }

        return chunks
    }
}
