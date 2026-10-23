package com.suhrud.docsy.extractor.models

import android.graphics.Rect

enum class ExtractionMethod {
    NATIVE_PDF,
    OCR_FALLBACK,
    DOCX_PARSER,
    XLSX_PARSER,
    STRUCTURED_XML,
    PLAIN_TEXT,
    IMAGE_OCR,
    UNKNOWN
}

data class ProvenanceInfo(
    val sourcePath: String,
    val pageNumber: Int = 1,
    val worksheetName: String? = null,
    val cellCoordinate: String? = null,
    val boundingBox: Rect? = null,
    val startCharOffset: Int = 0,
    val endCharOffset: Int = 0,
    val extractionMethod: ExtractionMethod = ExtractionMethod.NATIVE_PDF
)

data class QualityMetrics(
    val extractionMethod: ExtractionMethod,
    val characterCount: Int,
    val pageCount: Int,
    val isSuccess: Boolean,
    val isOcrRequired: Boolean,
    val isPartial: Boolean,
    val qualityScore: Float, // 0.0f to 1.0f
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class ExtractedParagraph(
    val text: String,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val provenance: ProvenanceInfo? = null
)

data class ExtractedCell(
    val coordinate: String, // e.g. "A1"
    val rawValue: String,
    val formula: String? = null,
    val dataType: String = "STRING", // STRING, NUMBER, BOOLEAN, DATE, FORMULA
    val isHeader: Boolean = false,
    val isMerged: Boolean = false,
    val provenance: ProvenanceInfo? = null
)

data class ExtractedRow(
    val rowIndex: Int,
    val cells: List<ExtractedCell> = emptyList(),
    val isHeader: Boolean = false
)

data class ExtractedTable(
    val tableName: String? = null,
    val worksheetName: String? = null,
    val headers: List<String> = emptyList(),
    val rows: List<ExtractedRow> = emptyList(),
    val provenance: ProvenanceInfo? = null
)

data class ExtractedWorksheet(
    val sheetName: String,
    val tables: List<ExtractedTable> = emptyList(),
    val cells: List<ExtractedCell> = emptyList(),
    val rowCount: Int = 0,
    val columnCount: Int = 0
)

data class EmbeddedMediaItem(
    val mediaName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val provenance: ProvenanceInfo? = null
)
