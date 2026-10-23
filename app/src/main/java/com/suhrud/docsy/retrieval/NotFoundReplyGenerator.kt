package com.suhrud.docsy.retrieval

import com.suhrud.docsy.tools.TopicDomain

/**
 * Grounded contextual not-found template generator naming missing subject and cause.
 * Satisfies Phase 9.11 in AUDIT.md.
 */
object NotFoundReplyGenerator {

    fun generateNotFound(
        subject: String,
        targetField: String? = null,
        owner: String? = null,
        totalIndexedFiles: Int = 0
    ): DocsyReply {
        val cleanSubject = subject.trim()

        val text = when {
            totalIndexedFiles == 0 -> {
                "I couldn't find that. No documents have been indexed on your device yet. Please run storage reading in Settings to scan your files."
            }
            !owner.isNullOrBlank() && !targetField.isNullOrBlank() -> {
                "I looked through your indexed documents for \"$cleanSubject\" ($targetField), but couldn't find any record belonging to \"$owner\"."
            }
            !targetField.isNullOrBlank() -> {
                "I found matching documents for \"$cleanSubject\", but the field \"$targetField\" was not found in the extracted text."
            }
            else -> {
                "I searched your $totalIndexedFiles indexed files, but couldn't find any document or record matching \"$cleanSubject\"."
            }
        }

        return DocsyReply(
            primaryText = text,
            domain = TopicDomain.DOCUMENT_FACT,
            replyType = ReplyType.NOT_FOUND
        )
    }

    fun generateOutOfScope(query: String): DocsyReply {
        return DocsyReply(
            primaryText = "I operate strictly offline as your personal device assistant. I don't have access to live internet services like web search, weather forecasts, or stock tickers.",
            domain = TopicDomain.GENERAL,
            replyType = ReplyType.TEXT_ONLY
        )
    }
}
