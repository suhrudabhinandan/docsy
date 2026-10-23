package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.dao.ChatContextDao
import com.suhrud.docsy.data.db.entity.ChatContextEntity
import org.json.JSONArray
import java.util.Locale

data class ResolvedChatContext(
    val isFollowUp: Boolean,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val inheritedDocIds: List<Long> = emptyList(),
    val inheritedOwner: String? = null,
    val inheritedDocType: String? = null,
    val inheritedPeriod: String? = null,
    val sourceCardInheritedContext: String? = null
)

class ChatMemoryManager(
    private val chatContextDao: ChatContextDao
) {

    /**
     * Resolves context before retrieval.
     */
    suspend fun resolveQueryContext(
        chatId: Long,
        query: String,
        parsedDocType: String? = null,
        parsedOwner: String? = null,
        parsedPeriod: String? = null,
        parsedField: String? = null
    ): ResolvedChatContext {
        val lastContext = chatContextDao.getLatestContextForChat(chatId)
            ?: return ResolvedChatContext(isFollowUp = false)

        val lowerQuery = query.lowercase(Locale.ROOT).trim()

        // 1. Topic Switch check: If query explicitly specifies a NEW document type, owner, or period
        val isNewDocType = !parsedDocType.isNullOrBlank() &&
                lastContext.activeDocType != null &&
                !parsedDocType.equals(lastContext.activeDocType, ignoreCase = true)

        val isNewOwner = !parsedOwner.isNullOrBlank() &&
                lastContext.activeOwner != null &&
                !parsedOwner.equals(lastContext.activeOwner, ignoreCase = true)

        val isNewPeriod = !parsedPeriod.isNullOrBlank() &&
                lastContext.activePeriod != null &&
                !parsedPeriod.equals(lastContext.activePeriod, ignoreCase = true)

        if (isNewDocType || isNewOwner || isNewPeriod) {
            // Topic switch: Reset context, do NOT inherit
            return ResolvedChatContext(isFollowUp = false)
        }

        // 2. Follow-up detection:
        val hasPronoun = lowerQuery.contains("it") || lowerQuery.contains("they") || lowerQuery.contains("them") ||
                lowerQuery.contains("her") || lowerQuery.contains("his") || lowerQuery.contains("their") ||
                lowerQuery.contains("this") || lowerQuery.contains("same") || lowerQuery.contains("previous")

        val lacksExplicitSubject = parsedDocType.isNullOrBlank() && parsedOwner.isNullOrBlank() && parsedPeriod.isNullOrBlank()
        val isShortQuery = lowerQuery.split("\\s+".toRegex()).size <= 6

        val isFollowUp = (hasPronoun || lacksExplicitSubject || isShortQuery) &&
                (!lastContext.activeDocType.isNullOrBlank() || !lastContext.activePeriod.isNullOrBlank() || !lastContext.activeDocIds.isNullOrBlank())

        if (!isFollowUp) {
            return ResolvedChatContext(isFollowUp = false)
        }

        // 3. Ambiguity check: If follow-up confidence is borderline or ambiguous
        val isAmbiguous = lowerQuery.length < 3 || lowerQuery.contains("or") || lowerQuery.contains("maybe")
        if (isAmbiguous) {
            val assumedDocStr = lastContext.activePeriod ?: lastContext.activeDocType ?: "document"
            return ResolvedChatContext(
                isFollowUp = true,
                needsClarification = true,
                clarificationQuestion = "Did you mean from your $assumedDocStr?"
            )
        }

        // 4. Inherit slots from previous turn
        val docIds = parseDocIds(lastContext.activeDocIds)
        val inheritedDocType = lastContext.activeDocType
        val inheritedPeriod = lastContext.activePeriod
        val inheritedOwner = lastContext.activeOwner

        val contextDescriptor = buildString {
            append("from your ")
            if (!inheritedPeriod.isNullOrBlank()) {
                append("$inheritedPeriod ")
            }
            if (!inheritedDocType.isNullOrBlank()) {
                append(inheritedDocType)
            } else {
                append("document")
            }
        }.trim()

        return ResolvedChatContext(
            isFollowUp = true,
            inheritedDocIds = docIds,
            inheritedOwner = inheritedOwner,
            inheritedDocType = inheritedDocType,
            inheritedPeriod = inheritedPeriod,
            sourceCardInheritedContext = contextDescriptor
        )
    }

    /**
     * Stores turn context after each answered turn.
     */
    suspend fun recordTurnContext(
        chatId: Long,
        docIds: List<Long>,
        owner: String?,
        docType: String?,
        period: String?,
        fieldsReturned: List<String>,
        intent: String?,
        entitiesJson: String? = null
    ) {
        val previousContexts = chatContextDao.getContextsForChat(chatId)
        val nextTurn = (previousContexts.maxOfOrNull { it.turn } ?: 0) + 1

        val docIdsStr = JSONArray(docIds).toString()
        val fieldsStr = JSONArray(fieldsReturned).toString()

        val entity = ChatContextEntity(
            chatId = chatId,
            turn = nextTurn,
            entitiesJson = entitiesJson,
            activeDocIds = docIdsStr,
            activeOwner = owner,
            activeDocType = docType,
            activePeriod = period,
            lastField = fieldsStr,
            lastIntent = intent,
            ts = System.currentTimeMillis()
        )

        chatContextDao.insertContext(entity)
        chatContextDao.trimContextHistory(chatId = chatId, maxTurns = 10)
    }

    suspend fun clearChatMemory(chatId: Long) {
        chatContextDao.deleteContextForChat(chatId)
    }

    private fun parseDocIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<Long>()
            for (i in 0 until array.length()) {
                list.add(array.getLong(i))
            }
            list
        } catch (_: Exception) {
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
    }

    suspend fun addTurnToHistory(
        chatId: Long,
        userQuery: String,
        decision: RoutingDecision?,
        replyText: String
    ) {
        val owner = decision?.targetOwner
        val docType = decision?.targetCategory
        val field = decision?.targetField
        val fields = if (field != null) listOf(field) else emptyList()

        recordTurnContext(
            chatId = chatId,
            docIds = emptyList(),
            owner = owner,
            docType = docType,
            period = null,
            fieldsReturned = fields,
            intent = decision?.intent?.name
        )
    }

    companion object {
        suspend fun restoreContextForChat(chatId: Long) {
            // Context is persisted in chat_context table and read by ChatMemoryManager instance on query
        }
    }
}
