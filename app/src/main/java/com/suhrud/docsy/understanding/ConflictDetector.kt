package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.FactEntity

data class ConflictingFieldRecord(
    val sharedIdentifierKey: String,
    val sharedIdentifierValue: String,
    val conflictingKey: String,
    val docNameA: String,
    val valueA: String,
    val docNameB: String,
    val valueB: String,
    val neutralDescription: String
)

object ConflictDetector {

    fun detectConflicts(
        sharedKey: String,
        sharedValue: String,
        docNameA: String,
        factsA: List<FactEntity>,
        docNameB: String,
        factsB: List<FactEntity>
    ): List<ConflictingFieldRecord> {
        val conflicts = mutableListOf<ConflictingFieldRecord>()

        val keysToCompare = listOf("STUDENT_NAME", "PARENT_NAME", "BIRTH_DATE", "AMOUNT", "INSTITUTION_NAME")

        for (targetKey in keysToCompare) {
            val factA = factsA.firstOrNull { it.key.equals(targetKey, ignoreCase = true) }
            val factB = factsB.firstOrNull { it.key.equals(targetKey, ignoreCase = true) }

            if (factA != null && factB != null) {
                val valA = factA.value.trim()
                val valB = factB.value.trim()

                if (valA.isNotBlank() && valB.isNotBlank() && !valA.equals(valB, ignoreCase = true)) {
                    val normA = (factA.normalizedValue ?: valA).uppercase()
                    val normB = (factB.normalizedValue ?: valB).uppercase()

                    if (normA != normB) {
                        conflicts.add(
                            ConflictingFieldRecord(
                                sharedIdentifierKey = sharedKey,
                                sharedIdentifierValue = sharedValue,
                                conflictingKey = targetKey,
                                docNameA = docNameA,
                                valueA = valA,
                                docNameB = docNameB,
                                valueB = valB,
                                neutralDescription = "Observed discrepancy for $targetKey across documents sharing $sharedKey=$sharedValue: '$valA' in $docNameA vs '$valB' in $docNameB"
                            )
                        )
                    }
                }
            }
        }

        return conflicts
    }
}
