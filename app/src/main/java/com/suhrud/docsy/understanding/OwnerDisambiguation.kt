package com.suhrud.docsy.understanding

data class OwnerCandidate(
    val ownerName: String,
    val documentCount: Int,
    val isPrimaryProfile: Boolean = false
)

/**
 * Owner disambiguation coordinator for resolving multi-owner documents.
 * Satisfies Phase 7.8 in AUDIT.md.
 */
object OwnerDisambiguation {

    /**
     * Finds matching candidates given a query filter and available owners.
     */
    fun findCandidates(
        query: String,
        availableOwners: List<String>,
        profileName: String = ""
    ): List<OwnerCandidate> {
        val cleanQuery = query.trim().lowercase()
        val candidates = mutableListOf<OwnerCandidate>()

        for (owner in availableOwners) {
            val isPrimary = profileName.isNotBlank() && NameMatcher.isNameMatch(owner, profileName)
            val matches = cleanQuery.isBlank() ||
                owner.lowercase().contains(cleanQuery) ||
                NameMatcher.isNameMatch(cleanQuery, owner, threshold = 0.75f)

            if (matches) {
                candidates.add(
                    OwnerCandidate(
                        ownerName = owner,
                        documentCount = 1,
                        isPrimaryProfile = isPrimary
                    )
                )
            }
        }

        return candidates.sortedWith(
            compareByDescending<OwnerCandidate> { it.isPrimaryProfile }
                .thenBy { it.ownerName }
        )
    }
}
