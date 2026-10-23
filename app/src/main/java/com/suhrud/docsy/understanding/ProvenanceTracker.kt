package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.FactEntity

data class FactProvenance(
    val fact: FactEntity,
    val sourceDocumentName: String,
    val isPrimarySource: Boolean,
    val secondaryCitations: List<String> = emptyList()
)

/**
 * Distinguishes primary certificate sources from secondary citations (e.g. CV citing 12th marks).
 * Satisfies Phase 7.9 in AUDIT.md.
 */
object ProvenanceTracker {

    /**
     * Resolves the highest confidence fact, prioritizing primary sources over secondary citations.
     */
    fun resolvePrimaryFact(
        facts: List<FactEntity>,
        documentNames: Map<Long, String> = emptyMap()
    ): FactProvenance? {
        if (facts.isEmpty()) return null

        // Sort: primary source first, then highest confidence, then most recent
        val sorted = facts.sortedWith(
            compareByDescending<FactEntity> { it.isPrimarySource }
                .thenByDescending { it.confidence }
                .thenByDescending { it.extractedAt }
        )

        val best = sorted.first()
        val secondarySources = sorted.drop(1).map {
            documentNames[it.documentId] ?: "Doc #${it.documentId}"
        }

        return FactProvenance(
            fact = best,
            sourceDocumentName = documentNames[best.documentId] ?: "Doc #${best.documentId}",
            isPrimarySource = best.isPrimarySource,
            secondaryCitations = secondarySources
        )
    }
}
