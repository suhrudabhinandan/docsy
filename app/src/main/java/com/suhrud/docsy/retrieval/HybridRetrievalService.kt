package com.suhrud.docsy.retrieval

import com.suhrud.docsy.classification.GeneralCategory
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.extraction.FieldDisambiguator

enum class MatchMethod {
    EXACT_IDENTIFIER,
    ENTITY_MATCH,
    LEXICAL_FTS5,
    SEMANTIC_EMBEDDING,
    HYBRID_FUSED
}

data class HybridSearchResultItem(
    val chunkId: Long,
    val documentId: Long,
    val page: Int,
    val position: Int,
    val text: String,
    val finalScore: Float,
    val matchMethod: MatchMethod,
    val explanation: String,
    val isExactMatch: Boolean = false,
    val matchedEntityKey: String? = null
)

data class SearchFilter(
    val generalCategory: GeneralCategory? = null,
    val specificType: String? = null,
    val documentId: Long? = null
)

class HybridRetrievalService(
    private val database: DocsyDatabase,
    private val fts5Engine: Fts5SearchEngine
) {

    suspend fun search(
        userQuery: String,
        filter: SearchFilter = SearchFilter(),
        topK: Int = 10
    ): List<HybridSearchResultItem> {
        val resultsMap = mutableMapOf<Long, HybridSearchResultItem>()

        val factDao = database.factDao()
        val documentDao = database.documentDao()

        // 1. STAGE 1: Exact Identifier & Entity Matching
        val disambiguatedFields = FieldDisambiguator.disambiguateText(userQuery)
        val exactIdentField = disambiguatedFields.firstOrNull {
            it.fieldType in listOf("ROLL_NUMBER", "REGISTRATION_NUMBER", "INVOICE_NUMBER", "PHONE_NUMBER", "EMAIL")
        }

        if (exactIdentField != null) {
            val matchingFacts: List<FactEntity> = try {
                factDao.searchFactsByExactValue(exactIdentField.normalizedValue)
            } catch (_: Exception) { emptyList() }

            if (matchingFacts.isNotEmpty()) {
                for (fact in matchingFacts) {
                    val doc = documentDao.getDocumentById(fact.documentId) ?: continue
                    if (filter.documentId != null && fact.documentId != filter.documentId) continue

                    val item = HybridSearchResultItem(
                        chunkId = fact.id,
                        documentId = fact.documentId,
                        page = fact.page,
                        position = 0,
                        text = "${fact.key}: ${fact.value} (File: ${doc.name})",
                        finalScore = 1.0f,
                        matchMethod = MatchMethod.EXACT_IDENTIFIER,
                        explanation = "Exact identifier match for ${fact.key}=${fact.value}",
                        isExactMatch = true,
                        matchedEntityKey = fact.key
                    )
                    resultsMap[fact.id] = item
                }
                if (resultsMap.isNotEmpty()) return resultsMap.values.toList()
            }
        }

        // STAGE 1.5: Table Cell & Table Row Search
        val matchingTableCells = try {
            database.docTableDao().searchTableCells(userQuery, limit = 15)
        } catch (_: Exception) { emptyList() }

        if (matchingTableCells.isNotEmpty()) {
            val groupedByRow = matchingTableCells.groupBy { Triple(it.docId, it.tableId, it.row) }
            for ((keyTriple, cells) in groupedByRow) {
                val (docId, tableId, rowIdx) = keyTriple
                if (filter.documentId != null && docId != filter.documentId) continue

                val doc = documentDao.getDocumentById(docId) ?: continue
                val rowCells = try { database.docTableDao().getTableRowCells(docId, tableId, rowIdx) } catch (_: Exception) { emptyList() }
                val headerCells = try { database.docTableDao().getTableHeaderCells(docId, tableId) } catch (_: Exception) { emptyList() }

                val headerMap = headerCells.associate { it.col to it.text }
                val formattedRow = rowCells.joinToString(", ") { c ->
                    val h = headerMap[c.col]
                    if (!h.isNullOrBlank()) "$h: ${c.text}" else c.text
                }

                if (formattedRow.isNotBlank()) {
                    val tableChunkId = 100_000L + docId * 1000 + tableId * 100 + rowIdx
                    val item = HybridSearchResultItem(
                        chunkId = tableChunkId,
                        documentId = docId,
                        page = cells.firstOrNull()?.page ?: 1,
                        position = rowIdx,
                        text = "[Table Row $rowIdx in ${doc.name}] $formattedRow",
                        finalScore = 0.95f,
                        matchMethod = MatchMethod.ENTITY_MATCH,
                        explanation = "Accurate table row match with column headers",
                        isExactMatch = false,
                        matchedEntityKey = "TABLE_ROW"
                    )
                    resultsMap[tableChunkId] = item
                }
            }
        }

        // 2. STAGE 2: Lexical Retrieval (FTS5)
        val ftsResults = try {
            fts5Engine.searchFreeText(userQuery, limit = topK * 2)
        } catch (_: Exception) { emptyList() }

        val ftsRanks = mutableMapOf<Long, Int>()
        for ((rank, fts) in ftsResults.withIndex()) {
            ftsRanks[fts.chunkId] = rank + 1
        }

        // 3. STAGE 3: Semantic Retrieval (Local Embedding Vector Search)
        val semanticResults = if (LocalEmbeddingManager.getState() == ModelAvailabilityState.READY) {
            val queryVec = LocalEmbeddingManager.embed(userQuery)
            if (queryVec.isNotEmpty()) {
                VectorStore.search(queryVec, topK = topK * 2)
            } else emptyList()
        } else emptyList()

        val semanticRanks = mutableMapOf<Long, Int>()
        for ((rank, sem) in semanticResults.withIndex()) {
            semanticRanks[sem.entry.chunkId] = rank + 1
        }

        // 4. STAGE 4: Reciprocal Rank Fusion (RRF) & Deduplication
        val allChunkIds = (ftsRanks.keys + semanticRanks.keys).distinct()
        val fusedScores = mutableListOf<Pair<Long, Float>>()

        val k = 60.0f
        for (chunkId in allChunkIds) {
            var rrfScore = 0.0f

            ftsRanks[chunkId]?.let { r ->
                rrfScore += 1.0f / (k + r)
            }

            semanticRanks[chunkId]?.let { r ->
                rrfScore += 1.2f / (k + r) // Semantic match given 1.2x weight
            }

            fusedScores.add(Pair(chunkId, rrfScore))
        }

        val sortedFused = fusedScores.sortedByDescending { it.second }.take(topK)

        for ((chunkId, rrfScore) in sortedFused) {
            val ftsItem = ftsResults.firstOrNull { it.chunkId == chunkId }
            val semItem = semanticResults.firstOrNull { it.entry.chunkId == chunkId }

            val text = ftsItem?.text ?: semItem?.entry?.text ?: ""
            val docId = ftsItem?.documentId ?: semItem?.entry?.documentId ?: 0L
            val page = ftsItem?.page ?: semItem?.entry?.page ?: 1
            val pos = ftsItem?.position ?: semItem?.entry?.page ?: 0

            val method = when {
                ftsItem != null && semItem != null -> MatchMethod.HYBRID_FUSED
                ftsItem != null -> MatchMethod.LEXICAL_FTS5
                else -> MatchMethod.SEMANTIC_EMBEDDING
            }

            val explanation = when (method) {
                MatchMethod.HYBRID_FUSED -> "Fused lexical (FTS5 rank #${ftsRanks[chunkId]}) and semantic (similarity ${(semItem?.similarityScore ?: 0f) * 100}%)"
                MatchMethod.LEXICAL_FTS5 -> "Lexical match via FTS5 BM25 (rank #${ftsRanks[chunkId]})"
                MatchMethod.SEMANTIC_EMBEDDING -> "Semantic concept match (similarity ${(semItem?.similarityScore ?: 0f) * 100}%)"
                else -> "Match"
            }

            resultsMap[chunkId] = HybridSearchResultItem(
                chunkId = chunkId,
                documentId = docId,
                page = page,
                position = pos,
                text = text,
                finalScore = rrfScore,
                matchMethod = method,
                explanation = explanation
            )
        }

        return resultsMap.values.sortedByDescending { it.finalScore }.take(topK)
    }
}
