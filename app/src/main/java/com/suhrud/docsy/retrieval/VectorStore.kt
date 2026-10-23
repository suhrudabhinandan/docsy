package com.suhrud.docsy.retrieval

import com.suhrud.docsy.extractor.models.ProvenanceInfo

data class VectorEntry(
    val chunkId: Long,
    val documentId: Long,
    val vector: FloatArray,
    val dimension: Int,
    val modelId: String,
    val modelVersion: String,
    val text: String,
    val kind: String = "PARAGRAPH",
    val page: Int = 1,
    val provenance: ProvenanceInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorEntry) return false
        return chunkId == other.chunkId && documentId == other.documentId && modelId == other.modelId
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + modelId.hashCode()
        return result
    }
}

data class VectorSearchResult(
    val entry: VectorEntry,
    val similarityScore: Float
)

object VectorStore {

    private val store = mutableMapOf<Long, VectorEntry>()

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size

    fun put(entry: VectorEntry): Boolean {
        if (entry.vector.isEmpty() || entry.vector.size != entry.dimension) return false
        for (v in entry.vector) {
            if (v.isNaN() || v.isInfinite()) return false
        }
        store[entry.chunkId] = entry
        return true
    }

    fun removeByDocumentId(documentId: Long) {
        store.entries.removeIf { it.value.documentId == documentId }
    }

    fun search(
        queryVector: FloatArray,
        topK: Int = 10,
        modelId: String? = null,
        minSimilarity: Float = 0.10f
    ): List<VectorSearchResult> {
        if (queryVector.isEmpty() || store.isEmpty()) return emptyList()

        val results = mutableListOf<VectorSearchResult>()
        for (entry in store.values) {
            if (modelId != null && entry.modelId != modelId) continue
            if (entry.vector.size != queryVector.size) continue

            val sim = LocalEmbeddingManager.cosineSimilarity(queryVector, entry.vector)
            if (sim >= minSimilarity) {
                results.add(VectorSearchResult(entry, sim))
            }
        }

        return results.sortedByDescending { it.similarityScore }.take(topK)
    }
}
