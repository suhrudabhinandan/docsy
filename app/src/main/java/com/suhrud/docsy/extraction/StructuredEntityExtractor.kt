package com.suhrud.docsy.extraction

import com.suhrud.docsy.classification.GeneralCategory
import com.suhrud.docsy.classification.HierarchicalClassificationResult
import com.suhrud.docsy.classification.HierarchicalDocumentClassifier
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.api.IStructuredEntityExtractor
import com.suhrud.docsy.extractor.models.ExtractedCell
import com.suhrud.docsy.extractor.models.ExtractedTable
import com.suhrud.docsy.extractor.models.ProvenanceInfo

data class ExtractedStructuredEntity(
    val entityType: String,
    val rawValue: String,
    val normalizedValue: String,
    val confidence: Float,
    val isAmbiguous: Boolean = false,
    val provenance: ProvenanceInfo? = null,
    val supportingLabel: String? = null,
    val alternativeTypes: List<String> = emptyList()
)

data class DocumentStructuredExtraction(
    val sourcePath: String,
    val classification: HierarchicalClassificationResult,
    val entities: List<ExtractedStructuredEntity> = emptyList(),
    val tables: List<ExtractedTable> = emptyList(),
    val qualityScore: Float = 1.0f
)

object StructuredEntityExtractor : IStructuredEntityExtractor {

    override fun extractEntities(docType: String, text: String): Map<String, Any> {
        val disambiguated = FieldDisambiguator.disambiguateText(text)
        return disambiguated.associate { it.fieldType to it.normalizedValue }
    }

    fun extractStructuredDocument(
        extractionResult: ExtractionResult,
        filePath: String = ""
    ): DocumentStructuredExtraction {
        val classification = HierarchicalDocumentClassifier.classify(
            text = extractionResult.fullText,
            fileName = filePath.substringAfterLast('/'),
            filePath = filePath
        )

        val disambiguatedFields = FieldDisambiguator.disambiguateText(
            text = extractionResult.fullText,
            sourcePath = filePath,
            documentType = classification.specificType
        )

        val entityList = mutableListOf<ExtractedStructuredEntity>()

        for (field in disambiguatedFields) {
            entityList.add(
                ExtractedStructuredEntity(
                    entityType = field.fieldType,
                    rawValue = field.rawValue,
                    normalizedValue = field.normalizedValue,
                    confidence = field.confidence,
                    isAmbiguous = field.isAmbiguous,
                    provenance = field.provenance,
                    supportingLabel = field.supportingLabel,
                    alternativeTypes = field.alternativeTypes
                )
            )
        }

        // Table-Aware Extraction: Convert table cell values to structured entities with headers
        val extractedTables = mutableListOf<ExtractedTable>()
        for (page in extractionResult.pages) {
            // Check if page contains structured blocks/tables
        }

        return DocumentStructuredExtraction(
            sourcePath = filePath,
            classification = classification,
            entities = entityList,
            tables = extractedTables,
            qualityScore = if (entityList.isNotEmpty()) entityList.map { it.confidence }.average().toFloat() else 0.5f
        )
    }
}
