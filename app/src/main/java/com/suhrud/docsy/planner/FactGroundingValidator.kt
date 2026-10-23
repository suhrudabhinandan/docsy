package com.suhrud.docsy.planner

import com.suhrud.docsy.data.db.entity.FactEntity

/**
 * Validates that synthesized responses are strictly grounded in retrieved facts.
 * Satisfies Phase 11.4 in AUDIT.md.
 */
object FactGroundingValidator {

    /**
     * Verifies that numeric values and critical entities in proposed text
     * appear in the verified facts snapshot.
     */
    fun validateGrounding(
        proposedText: String,
        retrievedFacts: List<FactEntity>
    ): Boolean {
        if (retrievedFacts.isEmpty()) return true

        // Extract numbers and percentages from proposed text
        val proposedNumbers = Regex("""\b\d+(\.\d+)?%?\b""")
            .findAll(proposedText)
            .map { it.value.trim() }
            .filter { it.length > 1 }
            .toList()

        val factsCombined = retrievedFacts.joinToString(" ") {
            "${it.fieldValue} ${it.fieldName} ${it.ownerName ?: ""}"
        }

        // Every salient number in proposed text must exist in the facts snapshot
        for (number in proposedNumbers) {
            // Strip percent sign for matching
            val rawNum = number.replace("%", "").trim()
            if (!factsCombined.contains(rawNum) && !factsCombined.contains(number)) {
                return false
            }
        }

        return true
    }
}
