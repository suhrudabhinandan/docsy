package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.FactEntity
import java.math.BigDecimal

enum class ComparisonOutcome {
    EXACT_AGREEMENT,
    EQUIVALENT_NORMALIZED,
    EXPECTED_FORMATTING_DIFFERENCE,
    MISSING_VALUES,
    POTENTIAL_MISMATCH,
    CONFIRMED_INCONSISTENCY,
    INCOMPARABLE_VALUES
}

data class FieldComparisonResult(
    val fieldKey: String,
    val docNameA: String,
    val valueA: String,
    val docNameB: String,
    val valueB: String,
    val outcome: ComparisonOutcome,
    val explanation: String
)

object CrossDocumentComparator {

    fun compareFacts(
        fieldKey: String,
        factA: FactEntity,
        docNameA: String,
        factB: FactEntity,
        docNameB: String
    ): FieldComparisonResult {
        val valA = factA.value.trim()
        val valB = factB.value.trim()

        if (valA.isBlank() || valB.isBlank()) {
            return FieldComparisonResult(
                fieldKey = fieldKey,
                docNameA = docNameA,
                valueA = valA,
                docNameB = docNameB,
                valueB = valB,
                outcome = ComparisonOutcome.MISSING_VALUES,
                explanation = "One or both document values are blank ($docNameA='$valA', $docNameB='$valB')"
            )
        }

        // 1. Exact string agreement
        if (valA == valB) {
            return FieldComparisonResult(
                fieldKey = fieldKey,
                docNameA = docNameA,
                valueA = valA,
                docNameB = docNameB,
                valueB = valB,
                outcome = ComparisonOutcome.EXACT_AGREEMENT,
                explanation = "Exact match across $docNameA and $docNameB"
            )
        }

        // 2. Equivalent normalized agreement
        val normA = (factA.normalizedValue ?: valA).trim().uppercase()
        val normB = (factB.normalizedValue ?: valB).trim().uppercase()

        if (normA == normB) {
            return FieldComparisonResult(
                fieldKey = fieldKey,
                docNameA = docNameA,
                valueA = valA,
                docNameB = docNameB,
                valueB = valB,
                outcome = ComparisonOutcome.EQUIVALENT_NORMALIZED,
                explanation = "Equivalent normalized values ($normA) across $docNameA and $docNameB"
            )
        }

        // 3. Formatting difference check (e.g. date formatting "12/05/2024" vs "2024-05-12")
        val cleanA = normA.replace(Regex("""[^A-Z0-9]"""), "")
        val cleanB = normB.replace(Regex("""[^A-Z0-9]"""), "")
        if (cleanA == cleanB && cleanA.isNotBlank()) {
            return FieldComparisonResult(
                fieldKey = fieldKey,
                docNameA = docNameA,
                valueA = valA,
                docNameB = docNameB,
                valueB = valB,
                outcome = ComparisonOutcome.EXPECTED_FORMATTING_DIFFERENCE,
                explanation = "Formatting difference ($valA vs $valB) for same underlying value"
            )
        }

        // 4. Inconsistency
        return FieldComparisonResult(
            fieldKey = fieldKey,
            docNameA = docNameA,
            valueA = valA,
            docNameB = docNameB,
            valueB = valB,
            outcome = ComparisonOutcome.CONFIRMED_INCONSISTENCY,
            explanation = "Confirmed inconsistency: $fieldKey is '$valA' in $docNameA but '$valB' in $docNameB"
        )
    }
}
