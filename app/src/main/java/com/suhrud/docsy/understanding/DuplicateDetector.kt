package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.DocumentEntity

enum class DuplicateStatus {
    EXACT_DUPLICATE,
    NEAR_DUPLICATE,
    DISTINCT
}

data class DuplicateCheckResult(
    val docA: DocumentEntity,
    val docB: DocumentEntity,
    val status: DuplicateStatus,
    val confidence: Float,
    val explanation: String
)

object DuplicateDetector {

    fun checkDuplicate(docA: DocumentEntity, docB: DocumentEntity): DuplicateCheckResult {
        if (docA.id == docB.id) {
            return DuplicateCheckResult(
                docA = docA,
                docB = docB,
                status = DuplicateStatus.DISTINCT,
                confidence = 1.0f,
                explanation = "Same document instance"
            )
        }

        // 1. Exact Content Hash Duplicate
        if (!docA.contentHash.isNullOrBlank() && docA.contentHash == docB.contentHash) {
            return DuplicateCheckResult(
                docA = docA,
                docB = docB,
                status = DuplicateStatus.EXACT_DUPLICATE,
                confidence = 1.0f,
                explanation = "Exact content hash match (${docA.contentHash})"
            )
        }

        // 2. Safeguard against merging different semester marksheets or different invoices
        val textA = docA.extractedText.orEmpty().lowercase()
        val textB = docB.extractedText.orEmpty().lowercase()

        val isDifferentSemesters = (textA.contains("semester 1") && textB.contains("semester 2")) ||
                (textA.contains("sem 1") && textB.contains("sem 2")) ||
                (textA.contains("10th") && textB.contains("12th"))

        if (isDifferentSemesters) {
            return DuplicateCheckResult(
                docA = docA,
                docB = docB,
                status = DuplicateStatus.DISTINCT,
                confidence = 1.0f,
                explanation = "Distinct documents representing different academic semesters/classes"
            )
        }

        // 3. Near Duplicate Check
        if (docA.docType == docB.docType && docA.docType != null && docA.size == docB.size) {
            return DuplicateCheckResult(
                docA = docA,
                docB = docB,
                status = DuplicateStatus.NEAR_DUPLICATE,
                confidence = 0.85f,
                explanation = "Near duplicate with identical file size (${docA.size} bytes) and docType (${docA.docType})"
            )
        }

        return DuplicateCheckResult(
            docA = docA,
            docB = docB,
            status = DuplicateStatus.DISTINCT,
            confidence = 1.0f,
            explanation = "Distinct documents"
        )
    }
}
