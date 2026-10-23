package com.suhrud.docsy.retrieval

import com.suhrud.docsy.tools.TopicDomain

data class FollowUpReferent(
    val lastTopic: TopicDomain? = null,
    val lastDocumentId: Long? = null,
    val lastDocumentName: String? = null,
    val lastOwnerName: String? = null,
    val pendingDisambiguationOwners: List<String> = emptyList()
)

/**
 * Tracks conversational context and referents across chat turns.
 * Satisfies Phase 9.7 in AUDIT.md.
 */
class ConversationSessionContext {

    var currentReferent: FollowUpReferent = FollowUpReferent()
        private set

    fun updateContext(
        topic: TopicDomain? = null,
        documentId: Long? = null,
        documentName: String? = null,
        ownerName: String? = null,
        disambiguationOwners: List<String>? = null
    ) {
        currentReferent = currentReferent.copy(
            lastTopic = topic ?: currentReferent.lastTopic,
            lastDocumentId = documentId ?: currentReferent.lastDocumentId,
            lastDocumentName = documentName ?: currentReferent.lastDocumentName,
            lastOwnerName = ownerName ?: currentReferent.lastOwnerName,
            pendingDisambiguationOwners = disambiguationOwners ?: currentReferent.pendingDisambiguationOwners
        )
    }

    /**
     * Resolves follow-up query referents (e.g. "what about her roll number?").
     */
    fun resolveFollowUpOwner(query: String): String? {
        val lower = query.lowercase().trim()
        val isPronoun = lower.contains("her ") || lower.contains("his ") || lower.contains("their ") ||
            lower.contains("uska ") || lower.contains("unki ")
        return if (isPronoun) currentReferent.lastOwnerName else null
    }

    fun clear() {
        currentReferent = FollowUpReferent()
    }
}
