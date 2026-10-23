package com.suhrud.docsy.classification

import com.suhrud.docsy.declarative.DocumentTypeDefinition
import com.suhrud.docsy.declarative.DocumentTypeRegistry

data class ClassificationResult(
    val typeId: String,
    val confidence: Float,
    val top1Score: Float,
    val top2Score: Float,
    val margin: Float,
    val isGenericFallback: Boolean = false
)

object GenericDocumentClassifier {

    private const val MARGIN_THRESHOLD = 0.15f
    private const val MIN_SCORE_THRESHOLD = 1.0f

    fun classify(textContent: String): ClassificationResult {
        if (textContent.isBlank()) {
            return ClassificationResult("GENERIC", 0.0f, 0.0f, 0.0f, 0.0f, isGenericFallback = true)
        }

        val cleanText = textContent.lowercase()
        val allTypes = DocumentTypeRegistry.getAllTypes()

        val typeScores = mutableListOf<Pair<DocumentTypeDefinition, Float>>()

        for (typeDef in allTypes) {
            var score = 0.0f

            // Positive signals
            for (sig in typeDef.positiveSignals) {
                if (cleanText.contains(sig.pattern.lowercase())) {
                    val multiplier = if (sig.isStructural) 2.0f else 1.0f
                    score += sig.weight * multiplier
                }
            }

            // Negative signals
            for (neg in typeDef.negativeSignals) {
                if (cleanText.contains(neg.pattern.lowercase())) {
                    score -= neg.weight
                }
            }

            typeScores.add(typeDef to score.coerceAtLeast(0.0f))
        }

        val sorted = typeScores.sortedByDescending { it.second }
        val top1 = sorted.getOrNull(0)
        val top2 = sorted.getOrNull(1)

        val top1Score = top1?.second ?: 0.0f
        val top2Score = top2?.second ?: 0.0f
        val margin = top1Score - top2Score

        if (top1 == null || top1Score < MIN_SCORE_THRESHOLD || margin < MARGIN_THRESHOLD) {
            return ClassificationResult("GENERIC", 0.5f, top1Score, top2Score, margin, isGenericFallback = true)
        }

        val confidence = (top1Score / (top1Score + top2Score + 1.0f)).coerceIn(0.5f, 0.99f)
        return ClassificationResult(
            typeId = top1.first.typeId,
            confidence = confidence,
            top1Score = top1Score,
            top2Score = top2Score,
            margin = margin,
            isGenericFallback = false
        )
    }
}
