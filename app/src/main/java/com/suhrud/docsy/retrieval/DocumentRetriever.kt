package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.dao.FactDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.understanding.NameMatcher
import com.suhrud.docsy.understanding.ProvenanceTracker

data class RetrievalCandidate(
    val fact: FactEntity?,
    val document: DocumentEntity,
    val score: Float,
    val matchedField: String? = null,
    val isPrimarySource: Boolean = true
)

/**
 * Hybrid Document and Fact Retriever.
 * Satisfies Phase 9.8 and 9.9 in AUDIT.md.
 *
 * Implements strict lexical token matching (Rule 9.9):
 * Content words (e.g. "CAT", "12th", "exam") must strictly exist in document content or title.
 */
class DocumentRetriever(
    private val documentDao: DocumentDao,
    private val factDao: FactDao
) {

    private val STOPWORDS = setOf(
        "what", "is", "my", "the", "a", "an", "of", "in", "for", "tell", "me",
        "show", "please", "can", "you", "find", "get", "kitna", "kya", "hai", "mera"
    )

    /**
     * Extracts salient content tokens from query (stripping conversational stopwords).
     */
    fun extractContentTokens(query: String): List<String> {
        return query.lowercase()
            .split(Regex("""[^a-zA-Z0-9]+"""))
            .filter { it.length > 1 && !STOPWORDS.contains(it) }
    }

    /**
     * Retrieves facts matching target category and field, respecting owner filtering.
     */
    suspend fun retrieveFact(
        query: String,
        targetField: String? = null,
        targetCategory: String? = null,
        targetOwner: String? = null
    ): List<RetrievalCandidate> {
        val contentTokens = extractContentTokens(query)
        val allDocs = try {
            documentDao.getAllDocumentsSnapshot()
        } catch (e: Exception) {
            android.util.Log.e("DocumentRetriever", "Error fetching documents snapshot", e)
            emptyList()
        }
        val docMap = allDocs.associateBy { it.id }

        val candidateFacts = if (targetField != null) {
            factDao.getFactsByFieldName(targetField.lowercase())
        } else {
            factDao.searchFacts(query)
        }

        val results = mutableListOf<RetrievalCandidate>()

        for (fact in candidateFacts) {
            val doc = docMap[fact.documentId] ?: continue

            // 1. Strict lexical content token check (Rule 9.9)
            // If query specifies "CAT" or "12th", it must match either document name, fact value, or category
            // File names carry no meaning: match on content-derived fields only.
            val combinedText = "${doc.displayTitle.orEmpty()} ${doc.docType.orEmpty()} ${fact.fieldName} ${fact.fieldValue} ${fact.factType}".lowercase()
            val matchesLexicalTokens = contentTokens.isEmpty() || contentTokens.any { token ->
                val digits = Regex("""^(\d+)(?:st|nd|rd|th)$""").find(token)?.groupValues?.get(1)
                combinedText.contains(token) || (digits != null && combinedText.contains(digits))
            }

            if (!matchesLexicalTokens) continue

            // 2. Owner filter
            if (!targetOwner.isNullOrBlank()) {
                val ownerMatch = fact.ownerName != null && NameMatcher.isNameMatch(targetOwner, fact.ownerName)
                if (!ownerMatch) continue
            }

            // 3. Category match
            var score = fact.confidence
            if (targetCategory != null && fact.factType.equals(targetCategory, ignoreCase = true)) {
                score += 0.2f
            }
            if (fact.isPrimarySource) {
                score += 0.1f
            }

            results.add(
                RetrievalCandidate(
                    fact = fact,
                    document = doc,
                    score = score,
                    matchedField = fact.fieldName,
                    isPrimarySource = fact.isPrimarySource
                )
            )
        }

        return results.sortedWith(
            compareByDescending<RetrievalCandidate> { it.isPrimarySource }
                .thenByDescending { it.score }
        )
    }

    /**
     * General lexical search over document files, respecting section scoping if specified.
     */
    suspend fun searchDocumentsLexical(query: String): List<DocumentEntity> {
        val tokens = extractContentTokens(query)
        val targetSection = com.suhrud.docsy.scanner.FileSection.detectQuerySection(query)
        val allDocs = try {
            documentDao.getAllDocumentsSnapshot()
        } catch (e: Exception) {
            android.util.Log.e("DocumentRetriever", "Error fetching documents snapshot", e)
            emptyList()
        }

        val sectionFilteredDocs = if (targetSection != null) {
            allDocs.filter { doc ->
                val detected = com.suhrud.docsy.scanner.ContentSectionDetector.detectSection(java.io.File(doc.path))
                doc.section.equals(targetSection.id, ignoreCase = true) || detected == targetSection
            }
        } else {
            allDocs
        }

        if (tokens.isEmpty()) return sectionFilteredDocs.take(10)

        // File names are never searched. List queries return extractedText as NULL, so use the
        // content-derived title, type and (capped) summary until full-text search is wired in.
        fun contentOf(doc: DocumentEntity): String =
            "${doc.displayTitle.orEmpty()} ${doc.docType.orEmpty()} ${doc.summary.orEmpty()}".lowercase()
        return sectionFilteredDocs.filter { doc ->
            val text = contentOf(doc)
            tokens.any { text.contains(it) }
        }.sortedByDescending { doc ->
            val text = contentOf(doc)
            tokens.count { text.contains(it) }
        }
    }
}
