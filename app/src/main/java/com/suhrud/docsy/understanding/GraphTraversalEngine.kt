package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.RelationRecord

data class TraversalNode(
    val entityId: Long,
    val entityName: String,
    val entityType: String,
    val depth: Int
)

data class TraversalPath(
    val startEntity: TraversalNode,
    val relations: List<RelationRecord> = emptyList(),
    val connectedDocuments: List<DocumentEntity> = emptyList()
)

object GraphTraversalEngine {

    suspend fun traverseRelations(
        database: DocsyDatabase,
        startEntityId: Long,
        maxDepth: Int = 2,
        maxResults: Int = 20
    ): TraversalPath {
        val entityDao = database.entityDao()
        val docEntityDao = database.docEntityDao()
        val relationDao = database.relationDao()
        val documentDao = database.documentDao()

        val startRecord = entityDao.getEntityById(startEntityId) ?: return TraversalPath(
            startEntity = TraversalNode(startEntityId, "Unknown", "unknown", 0)
        )

        val startNode = TraversalNode(
            entityId = startRecord.id,
            entityName = startRecord.canonicalName,
            entityType = startRecord.type,
            depth = 0
        )

        val visitedEntityIds = mutableSetOf<Long>(startEntityId)
        val collectedRelations = mutableListOf<RelationRecord>()
        val connectedDocIds = mutableSetOf<Long>()

        // Queue for BFS traversal
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.add(Pair(startEntityId, 0))

        while (queue.isNotEmpty() && collectedRelations.size < maxResults) {
            val (currentId, currentDepth) = queue.removeFirst()

            if (currentDepth >= maxDepth) continue

            val docIds = docEntityDao.getDocIdsForEntity(currentId)
            connectedDocIds.addAll(docIds)

            val relations = relationDao.getRelationsForEntity(currentId)
            for (rel in relations) {
                if (collectedRelations.size >= maxResults) break
                if (!collectedRelations.contains(rel)) {
                    collectedRelations.add(rel)
                }

                val neighborId = if (rel.aId == currentId) rel.bId else rel.aId
                if (neighborId !in visitedEntityIds) {
                    visitedEntityIds.add(neighborId)
                    queue.add(Pair(neighborId, currentDepth + 1))
                }
            }
        }

        val documents = connectedDocIds.take(maxResults).mapNotNull { docId ->
            documentDao.getDocumentById(docId)
        }

        return TraversalPath(
            startEntity = startNode,
            relations = collectedRelations,
            connectedDocuments = documents
        )
    }
}
