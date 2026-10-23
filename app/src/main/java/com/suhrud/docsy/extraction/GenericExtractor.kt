package com.suhrud.docsy.extraction

import com.suhrud.docsy.classification.GenericDocumentClassifier
import com.suhrud.docsy.declarative.DocumentTypeRegistry
import com.suhrud.docsy.declarative.FieldSchema
import com.suhrud.docsy.model.CommonDocumentModel.PageDoc
import com.suhrud.docsy.understanding.DocumentClassifier

data class ExtractedFact(
    val factKey: String,
    val rawValue: String,
    val normalizedValue: String,
    val confidence: Float,
    val needsConfirm: Boolean,
    val pageNumber: Int,
    val evidenceText: String,
    val method: String
)

data class ExtractionOutput(
    val docType: String,
    val owner: String?,
    val ownerEvidence: String?,
    val facts: List<ExtractedFact>,
    val useCases: List<String> = emptyList(),
    val qualityScore: Float,
    val needsConfirmAny: Boolean
)

object GenericExtractor {

    fun extractDocument(
        pageDocs: List<PageDoc>,
        rawFullText: String
    ): ExtractionOutput {
        // 1. Classification
        val classResult = GenericDocumentClassifier.classify(rawFullText)
        val docType = classResult.typeId

        // 2. Pair extraction across pages
        val allPairs = pageDocs.flatMap { KeyValuePairingEngine.extractPairs(it) }

        val extractedFacts = mutableListOf<ExtractedFact>()
        var ownerCandidate: String? = null
        var ownerEvidenceCandidate: String? = null

        val typeDef = DocumentTypeRegistry.getType(docType)
        val useCases = typeDef?.useCases?.ifEmpty { DocumentClassifier.getUseCasesForCategory(docType) }
            ?: DocumentClassifier.getUseCasesForCategory(docType)

        if (typeDef != null) {
            // Extract declared fields
            for (fieldSchema in typeDef.fields) {
                val matchingPair = allPairs.firstOrNull { pair ->
                    fieldSchema.labelIds.any { label ->
                        pair.keyLabel.equals(label, ignoreCase = true) || pair.keyLabel.contains(label, ignoreCase = true)
                    }
                }

                if (matchingPair != null) {
                    val rawVal = matchingPair.valueText
                    val repairedVal = repairCharacterConfusion(rawVal, fieldSchema.dataType)
                    val (valid, needsConfirm) = validateField(repairedVal, fieldSchema)

                    extractedFacts.add(
                        ExtractedFact(
                            factKey = fieldSchema.key,
                            rawValue = rawVal,
                            normalizedValue = repairedVal,
                            confidence = if (valid) matchingPair.confidence else matchingPair.confidence * 0.5f,
                            needsConfirm = needsConfirm || !valid,
                            pageNumber = matchingPair.pageNumber,
                            evidenceText = "${matchingPair.keyLabel}: ${matchingPair.valueText}",
                            method = "KEY_VALUE"
                        )
                    )
                }
            }

            // Extract owner according to owner rules (excluding role labels like father/guardian)
            val ownerPair = allPairs.firstOrNull { pair ->
                !pair.isRoleOrRelationship &&
                        typeDef.ownerRule.candidateFields.any { cand ->
                            pair.keyLabel.contains(cand, ignoreCase = true)
                        }
            }
            if (ownerPair != null) {
                ownerCandidate = ownerPair.valueText
                ownerEvidenceCandidate = "${ownerPair.keyLabel}: ${ownerPair.valueText}"
            }
        } else {
            // Generic Fallback: store all key-value pairs as generic facts
            for (pair in allPairs) {
                extractedFacts.add(
                    ExtractedFact(
                        factKey = pair.keyLabel,
                        rawValue = pair.valueText,
                        normalizedValue = pair.valueText,
                        confidence = pair.confidence,
                        needsConfirm = false,
                        pageNumber = pair.pageNumber,
                        evidenceText = "${pair.keyLabel}: ${pair.valueText}",
                        method = "GENERIC_PAIR"
                    )
                )
            }
        }

        val qualityScore = if (extractedFacts.isNotEmpty()) {
            extractedFacts.map { it.confidence }.average().toFloat()
        } else 0.5f

        val needsConfirmAny = extractedFacts.any { it.needsConfirm }

        return ExtractionOutput(
            docType = docType,
            owner = ownerCandidate,
            ownerEvidence = ownerEvidenceCandidate,
            facts = extractedFacts,
            useCases = useCases,
            qualityScore = qualityScore,
            needsConfirmAny = needsConfirmAny
        )
    }

    private fun repairCharacterConfusion(raw: String, dataType: String): String {
        return when (dataType) {
            "NUMBER", "PERCENTAGE" -> {
                raw.replace("O", "0")
                    .replace("o", "0")
                    .replace("I", "1")
                    .replace("l", "1")
                    .trim()
            }
            else -> raw.trim()
        }
    }

    private fun validateField(valStr: String, schema: FieldSchema): Pair<Boolean, Boolean> {
        if (valStr.isBlank()) return Pair(!schema.required, schema.required)

        var valid = true
        var needsConfirm = false

        for (vId in schema.validatorIds) {
            when (vId) {
                "percentage_range" -> {
                    val cleaned = valStr.replace("%", "").trim()
                    val num = cleaned.toFloatOrNull()
                    if (num == null || num < 0f || num > 100f) {
                        valid = false
                        needsConfirm = true
                    }
                }
                "number_non_negative" -> {
                    val num = valStr.toDoubleOrNull()
                    if (num == null || num < 0.0) {
                        valid = false
                        needsConfirm = true
                    }
                }
                "date_format" -> {
                    if (!valStr.contains("/") && !valStr.contains("-") && !valStr.contains(" ")) {
                        needsConfirm = true
                    }
                }
            }
        }
        return Pair(valid, needsConfirm)
    }
}
