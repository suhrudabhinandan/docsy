package com.suhrud.docsy.knowledge

import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.extractor.models.ProvenanceInfo

enum class InsightType {
    APPROACHING_DEADLINE,
    SHARED_IDENTIFIER,
    POSSIBLE_DUPLICATE,
    UNREVIEWED_DOCUMENT
}

data class ProactiveInsight(
    val id: String,
    val title: String,
    val message: String,
    val insightType: InsightType,
    val confidence: Float,
    val evidenceText: String,
    val sourceDocuments: List<String> = emptyList(),
    val provenance: ProvenanceInfo? = null,
    val isDismissed: Boolean = false
)

object ProactiveInsightsManager {

    fun generateInsights(
        knowledgeView: KnowledgeBaseView,
        documents: List<DocumentEntity>
    ): List<ProactiveInsight> {
        val insights = mutableListOf<ProactiveInsight>()

        // 1. Approaching Deadlines
        for (dl in knowledgeView.deadlines) {
            insights.add(
                ProactiveInsight(
                    id = "insight_deadline_${dl.rawText.hashCode()}",
                    title = "Time-Sensitive Date Detected",
                    message = "Document contains ${dl.semanticRole.name.replace('_', ' ')}: ${dl.rawText}",
                    insightType = InsightType.APPROACHING_DEADLINE,
                    confidence = 0.90f,
                    evidenceText = dl.evidenceText,
                    sourceDocuments = listOf(dl.provenance?.sourcePath ?: "Document")
                )
            )
        }

        // 2. Unreviewed / Failed Extractions
        for (item in knowledgeView.reviewQueue) {
            insights.add(
                ProactiveInsight(
                    id = "insight_review_${item.documentId}",
                    title = "Action Needed: ${item.documentName}",
                    message = item.reason,
                    insightType = InsightType.UNREVIEWED_DOCUMENT,
                    confidence = 1.0f,
                    evidenceText = item.evidenceText ?: item.reason,
                    sourceDocuments = listOf(item.documentName)
                )
            )
        }

        return insights
    }
}
