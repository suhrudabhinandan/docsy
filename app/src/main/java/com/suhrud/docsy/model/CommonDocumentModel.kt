package com.suhrud.docsy.model

import androidx.compose.ui.geometry.Rect

sealed class CommonDocumentModel {

    data class BoundingBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)
    }

    data class Word(
        val text: String,
        val box: BoundingBox? = null,
        val confidence: Float = 1.0f
    )

    data class Line(
        val words: List<Word> = emptyList(),
        val text: String = words.joinToString(" ") { it.text },
        val box: BoundingBox? = null,
        val confidence: Float = 1.0f
    )

    data class Block(
        val lines: List<Line> = emptyList(),
        val box: BoundingBox? = null,
        val confidence: Float = 1.0f
    )

    data class PageDoc(
        val pageNumber: Int,
        val blocks: List<Block> = emptyList(),
        val width: Float = 0f,
        val height: Float = 0f
    ) : CommonDocumentModel()

    enum class CellType { NUMBER, DATE, TEXT, EMPTY }

    data class Cell(
        val rawValue: String,
        val typedValue: Any? = null,
        val cellType: CellType = CellType.TEXT,
        val confidence: Float = 1.0f,
        val box: BoundingBox? = null
    )

    data class Row(
        val cells: List<Cell> = emptyList(),
        val isHeader: Boolean = false,
        val isScoring: Boolean = true
    )

    data class Table(
        val headers: List<String> = emptyList(),
        val rows: List<Row> = emptyList(),
        val tableName: String? = null
    )

    data class TableDoc(
        val tables: List<Table> = emptyList()
    ) : CommonDocumentModel()

    data class Paragraph(
        val text: String,
        val isHeading: Boolean = false,
        val headingLevel: Int = 0
    )

    data class FlowDoc(
        val paragraphs: List<Paragraph> = emptyList()
    ) : CommonDocumentModel()
}
