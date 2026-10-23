package com.suhrud.docsy.understanding

import com.suhrud.docsy.extractor.models.ProvenanceInfo

enum class MatchOutcome {
    EXACT_MATCH,
    HIGH_CONFIDENCE_PROBABLE,
    POSSIBLE_MATCH_REVIEW,
    EXPLICITLY_CONFLICTING,
    UNRESOLVED
}

data class EntityMatchResult(
    val entityA: String,
    val entityB: String,
    val outcome: MatchOutcome,
    val score: Float,
    val evidenceText: String,
    val isIdentifierMatch: Boolean = false
)

object EntityResolver {

    private val ORG_ALIASES = mapOf(
        "GEC" to listOf("GANDHI ENGINEERING COLLEGE", "GANDHI ENGINEERING COLLEGE BHUBANESWAR"),
        "CBSE" to listOf("CENTRAL BOARD OF SECONDARY EDUCATION"),
        "ICSE" to listOf("INDIAN CERTIFICATE OF SECONDARY EDUCATION"),
        "IIT" to listOf("INDIAN INSTITUTE OF TECHNOLOGY")
    )

    fun resolveEntities(
        entityTypeA: String,
        nameA: String,
        entityTypeB: String,
        nameB: String,
        corroboratingIdA: String? = null,
        corroboratingIdB: String? = null
    ): EntityMatchResult {
        // Rule 1: Never merge different entity types
        if (!entityTypeA.equals(entityTypeB, ignoreCase = true)) {
            return EntityMatchResult(
                entityA = nameA,
                entityB = nameB,
                outcome = MatchOutcome.UNRESOLVED,
                score = 0.0f,
                evidenceText = "Different entity types ($entityTypeA vs $entityTypeB) cannot be merged"
            )
        }

        val normA = nameA.trim().uppercase()
        val normB = nameB.trim().uppercase()

        // Rule 2: Explicit conflicting identifiers check
        if (!corroboratingIdA.isNullOrBlank() && !corroboratingIdB.isNullOrBlank()) {
            val cleanIdA = corroboratingIdA.replace(" ", "").uppercase()
            val cleanIdB = corroboratingIdB.replace(" ", "").uppercase()

            if (cleanIdA == cleanIdB) {
                if (normA == normB) {
                    return EntityMatchResult(
                        entityA = nameA,
                        entityB = nameB,
                        outcome = MatchOutcome.EXACT_MATCH,
                        score = 1.0f,
                        evidenceText = "Exact match on identifier ($cleanIdA) and name ($normA)",
                        isIdentifierMatch = true
                    )
                } else {
                    return EntityMatchResult(
                        entityA = nameA,
                        entityB = nameB,
                        outcome = MatchOutcome.EXPLICITLY_CONFLICTING,
                        score = 0.0f,
                        evidenceText = "Same identifier ($cleanIdA) associated with different names ($normA vs $normB)",
                        isIdentifierMatch = true
                    )
                }
            } else if (normA == normB) {
                // Same common name but different identifiers -> NEVER merge
                return EntityMatchResult(
                    entityA = nameA,
                    entityB = nameB,
                    outcome = MatchOutcome.EXPLICITLY_CONFLICTING,
                    score = 0.0f,
                    evidenceText = "Same name ($normA) associated with different identifiers ($cleanIdA vs $cleanIdB)"
                )
            }
        }

        // Rule 3: Exact normalized name match
        if (normA == normB && normA.isNotBlank()) {
            return EntityMatchResult(
                entityA = nameA,
                entityB = nameB,
                outcome = MatchOutcome.EXACT_MATCH,
                score = 1.0f,
                evidenceText = "Exact normalized name match ($normA)"
            )
        }

        // Rule 4: Organization alias matching
        if (isOrgAliasMatch(normA, normB)) {
            return EntityMatchResult(
                entityA = nameA,
                entityB = nameB,
                outcome = MatchOutcome.HIGH_CONFIDENCE_PROBABLE,
                score = 0.90f,
                evidenceText = "Organization name and alias match ($normA <-> $normB)"
            )
        }

        // Rule 5: Name variations (e.g. Rahul Kumar vs Rahul K. Kumar)
        if (isNameVariation(normA, normB)) {
            return EntityMatchResult(
                entityA = nameA,
                entityB = nameB,
                outcome = MatchOutcome.POSSIBLE_MATCH_REVIEW,
                score = 0.75f,
                evidenceText = "Possible name variation ($normA vs $normB)"
            )
        }

        return EntityMatchResult(
            entityA = nameA,
            entityB = nameB,
            outcome = MatchOutcome.UNRESOLVED,
            score = 0.20f,
            evidenceText = "Insufficient evidence to resolve $nameA and $nameB"
        )
    }

    private fun isOrgAliasMatch(normA: String, normB: String): Boolean {
        for ((alias, fullNames) in ORG_ALIASES) {
            if (normA == alias && fullNames.contains(normB)) return true
            if (normB == alias && fullNames.contains(normA)) return true
        }
        return false
    }

    private fun isNameVariation(normA: String, normB: String): Boolean {
        val partsA = normA.split(Regex("""\s+"""))
        val partsB = normB.split(Regex("""\s+"""))
        if (partsA.size >= 2 && partsB.size >= 2) {
            if (partsA.first() == partsB.first() && partsA.last() == partsB.last()) {
                return true
            }
        }
        return false
    }
}
