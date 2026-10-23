package com.suhrud.docsy.understanding

import com.suhrud.docsy.extractor.models.ProvenanceInfo

enum class LinkStrength {
    STRONG_LINK,
    SUPPORTED_LINK,
    WEAK_LINK,
    CONFLICTING_LINK,
    UNKNOWN
}

data class DocumentLink(
    val docIdA: Long,
    val docNameA: String,
    val docIdB: Long,
    val docNameB: String,
    val linkStrength: LinkStrength,
    val score: Float,
    val sharedIdentifierKey: String? = null,
    val sharedIdentifierValue: String? = null,
    val evidenceText: String,
    val provenance: ProvenanceInfo? = null
)

object DocumentRelationshipIndex {

    fun createLink(
        docIdA: Long,
        docNameA: String,
        docIdB: Long,
        docNameB: String,
        sharedKey: String,
        sharedValue: String,
        isExactIdentifier: Boolean
    ): DocumentLink {
        val strength = if (isExactIdentifier) LinkStrength.STRONG_LINK else LinkStrength.SUPPORTED_LINK
        val score = if (isExactIdentifier) 1.0f else 0.85f

        return DocumentLink(
            docIdA = docIdA,
            docNameA = docNameA,
            docIdB = docIdB,
            docNameB = docNameB,
            linkStrength = strength,
            score = score,
            sharedIdentifierKey = sharedKey,
            sharedIdentifierValue = sharedValue,
            evidenceText = "Documents $docNameA and $docNameB share $sharedKey = $sharedValue",
            provenance = ProvenanceInfo(docNameA)
        )
    }
}
