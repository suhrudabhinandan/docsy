package com.suhrud.docsy.knowledge

import com.suhrud.docsy.classification.GeneralCategory
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FactEntity

data class TopicGroup(
    val topicName: String,
    val documents: List<DocumentEntity> = emptyList()
)

data class ReviewQueueItem(
    val documentId: Long,
    val documentName: String,
    val reason: String,
    val evidenceText: String? = null
)

data class KnowledgeBaseView(
    val categoryGroups: Map<GeneralCategory, List<DocumentEntity>> = emptyMap(),
    val topicGroups: List<TopicGroup> = emptyList(),
    val deadlines: List<ExtractedDateRecord> = emptyList(),
    val reviewQueue: List<ReviewQueueItem> = emptyList(),
    val totalDocuments: Int = 0
)

object UnifiedKnowledgeIndex {

    fun buildIndex(
        documents: List<DocumentEntity>,
        facts: List<FactEntity> = emptyList()
    ): KnowledgeBaseView {
        val categoryMap = mutableMapOf<GeneralCategory, MutableList<DocumentEntity>>()
        val reviewQueueList = mutableListOf<ReviewQueueItem>()
        val topicMap = mutableMapOf<String, MutableList<DocumentEntity>>()
        val deadlineList = mutableListOf<ExtractedDateRecord>()

        for (doc in documents) {
            // Category grouping
            val generalCat = try {
                val catStr = doc.docType?.substringBefore(":") ?: "OTHER"
                GeneralCategory.valueOf(catStr)
            } catch (_: Exception) {
                GeneralCategory.OTHER
            }
            categoryMap.getOrPut(generalCat) { mutableListOf() }.add(doc)

            // Topic grouping
            val topicName = when (generalCat) {
                GeneralCategory.ACADEMIC -> "Marksheets & Academics"
                GeneralCategory.FINANCIAL -> "Invoices & Financial"
                GeneralCategory.EMPLOYMENT -> "Resumes & Employment"
                GeneralCategory.IDENTITY -> "Identity Proofs"
                GeneralCategory.MEDICAL -> "Health & Medical"
                GeneralCategory.GOVERNMENT -> "Government Records"
                else -> "General Documents"
            }
            topicMap.getOrPut(topicName) { mutableListOf() }.add(doc)

            // Review queue checks
            if (doc.state == "FAILED") {
                reviewQueueList.add(
                    ReviewQueueItem(doc.id, doc.name, "Extraction failed during indexing")
                )
            } else if (doc.docType == null || doc.docType == "OTHER:UNKNOWN") {
                reviewQueueList.add(
                    ReviewQueueItem(doc.id, doc.name, "Unclassified document requires review")
                )
            }

            // Extract dates & deadlines from document extractedText
            val extractedText = doc.extractedText.orEmpty()
            if (extractedText.isNotBlank()) {
                val dates = DeadlineExtractor.extractDatesAndDeadlines(extractedText, doc.name)
                deadlineList.addAll(dates.filter { it.isDeadline })
            }
        }

        val topicGroupsList = topicMap.map { (name, docs) ->
            TopicGroup(topicName = name, documents = docs)
        }

        return KnowledgeBaseView(
            categoryGroups = categoryMap,
            topicGroups = topicGroupsList,
            deadlines = deadlineList,
            reviewQueue = reviewQueueList,
            totalDocuments = documents.size
        )
    }
}
