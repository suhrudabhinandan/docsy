package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.FactEntity

data class FactDisagreement(
    val fieldName: String,
    val ownerName: String,
    val valueA: String,
    val documentIdA: Long,
    val valueB: String,
    val documentIdB: Long,
    val description: String,
    val severity: DisagreementSeverity
)

enum class DisagreementSeverity {
    HIGH,    // e.g. Different Date of Birth across official IDs
    MEDIUM,  // e.g. Name spelling variation
    LOW      // e.g. Formatting or punctuation differences
}

/**
 * Checks for fact disagreements across documents belonging to the same owner.
 * Satisfies Phase 7.5 in AUDIT.md.
 */
object FactConsistencyChecker {

    /**
     * Inspects a collection of facts for an owner and identifies conflicts.
     */
    fun findDisagreements(facts: List<FactEntity>): List<FactDisagreement> {
        val disagreements = mutableListOf<FactDisagreement>()
        val factsByField = facts.groupBy { it.fieldName.lowercase().trim() }

        for ((field, fieldFacts) in factsByField) {
            if (fieldFacts.size < 2) continue

            // Compare each pair
            for (i in 0 until fieldFacts.size) {
                for (j in i + 1 until fieldFacts.size) {
                    val factA = fieldFacts[i]
                    val factB = fieldFacts[j]

                    if (factA.documentId == factB.documentId) continue

                    val valA = normalizeFieldValue(field, factA.fieldValue)
                    val valB = normalizeFieldValue(field, factB.fieldValue)

                    if (valA != valB) {
                        val severity = when (field) {
                            "date_of_birth", "dob" -> DisagreementSeverity.HIGH
                            "father_name", "id_number" -> DisagreementSeverity.HIGH
                            "owner_name", "candidate_name" -> {
                                if (NameMatcher.isNameMatch(valA, valB)) DisagreementSeverity.LOW
                                else DisagreementSeverity.MEDIUM
                            }
                            else -> DisagreementSeverity.MEDIUM
                        }

                        disagreements.add(
                            FactDisagreement(
                                fieldName = field,
                                ownerName = factA.ownerName ?: factB.ownerName ?: "Unknown",
                                valueA = factA.fieldValue,
                                documentIdA = factA.documentId,
                                valueB = factB.fieldValue,
                                documentIdB = factB.documentId,
                                description = "Conflicting $field: '${factA.fieldValue}' vs '${factB.fieldValue}'",
                                severity = severity
                            )
                        )
                    }
                }
            }
        }

        return disagreements
    }

    private fun normalizeFieldValue(field: String, value: String): String {
        return when (field) {
            "date_of_birth", "dob" -> {
                // Normalize dates 15/08/1998 vs 15-08-1998
                value.replace("-", "/").replace(".", "/").trim()
            }
            "owner_name" -> NameMatcher.normalizeName(value)
            else -> value.trim().lowercase()
        }
    }
}
