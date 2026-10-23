package com.suhrud.docsy.knowledge

data class UserDocumentMetadata(
    val documentId: Long,
    val isPinned: Boolean = false,
    val isBookmarked: Boolean = false,
    val userTopics: List<String> = emptyList(),
    val userNote: String? = null,
    val isInsightDismissed: Boolean = false
)

object UserKnowledgeManager {

    private val userMetadataStore = mutableMapOf<Long, UserDocumentMetadata>()

    fun getMetadata(documentId: Long): UserDocumentMetadata {
        return userMetadataStore.getOrPut(documentId) {
            UserDocumentMetadata(documentId = documentId)
        }
    }

    fun togglePin(documentId: Long): UserDocumentMetadata {
        val current = getMetadata(documentId)
        val updated = current.copy(isPinned = !current.isPinned)
        userMetadataStore[documentId] = updated
        return updated
    }

    fun toggleBookmark(documentId: Long): UserDocumentMetadata {
        val current = getMetadata(documentId)
        val updated = current.copy(isBookmarked = !current.isBookmarked)
        userMetadataStore[documentId] = updated
        return updated
    }

    fun addUserTopic(documentId: Long, topic: String): UserDocumentMetadata {
        val current = getMetadata(documentId)
        if (topic.isBlank() || current.userTopics.contains(topic.trim())) return current
        val updated = current.copy(userTopics = current.userTopics + topic.trim())
        userMetadataStore[documentId] = updated
        return updated
    }

    fun setUserNote(documentId: Long, note: String?): UserDocumentMetadata {
        val current = getMetadata(documentId)
        val updated = current.copy(userNote = note?.trim())
        userMetadataStore[documentId] = updated
        return updated
    }

    fun clear() {
        userMetadataStore.clear()
    }
}
