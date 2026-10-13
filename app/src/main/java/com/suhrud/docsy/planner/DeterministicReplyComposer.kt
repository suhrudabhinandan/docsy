package com.suhrud.docsy.planner

import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.RoutingDecision
import com.suhrud.docsy.tools.DateTimeTool
import java.util.Calendar

/**
 * Deterministic response composer generating fluent, warm, and grounded natural English.
 * Satisfies Phase 11.5, 12.1, 12.2, 12.3, and 12.4 in AUDIT.md.
 */
object DeterministicReplyComposer {

    /**
     * Generates a time-of-day greeting (Phase 12.3).
     */
    fun getTimeOfDayGreeting(userName: String = ""): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Hello"
        }
        return if (userName.isNotBlank()) "$greeting, $userName!" else "$greeting!"
    }

    /**
     * Composes an answer with lead-in, direct answer, and helpful next step (Phase 12.1).
     */
    fun composeFactAnswer(
        fact: FactEntity,
        documentName: String,
        userQuery: String
    ): String {
        val field = fact.fieldName.replace("_", " ").trim()
        val value = fact.fieldValue.trim()
        val isHinglish = isHinglishQuery(userQuery)

        val leadIn = if (isHinglish) {
            "Aapke \"$documentName\" ke mutabiq:"
        } else {
            "According to your \"$documentName\":"
        }

        val directAnswer = "Your $field is $value."
        val nextStep = "Let me know if you need to calculate percentage, share this detail, or check another document."

        return "$leadIn\n**$directAnswer**\n\n$nextStep"
    }

    /**
     * Composes small talk responses (Phase 12.1, 12.2).
     */
    fun composeSmallTalk(intent: DocsyIntent, userName: String = ""): String {
        return when (intent) {
            DocsyIntent.GREETING -> {
                "${getTimeOfDayGreeting(userName)} I'm DOCSY, your strictly offline personal device assistant. How can I help you with your files, hardware, or notes today?"
            }
            DocsyIntent.HOW_ARE_YOU -> {
                "I'm operating smoothly and completely offline! How are you doing today? Let me know if you need any document details or system checks."
            }
            DocsyIntent.WHO_ARE_YOU -> {
                "I'm DOCSY, an on-device personal intelligence assistant. I index your documents, extract verified facts (marksheets, IDs, bills, CVs), and check hardware telemetry—without ever connecting to the internet."
            }
            DocsyIntent.GRATITUDE -> {
                "You're very welcome! Always glad to help."
            }
            DocsyIntent.FAREWELL -> {
                "Goodbye! Have a great day ahead."
            }
            else -> "Hello! How can I assist you with your documents or device today?"
        }
    }

    /**
     * Checks if user query was phrased in Hinglish for dialect mirroring (Phase 12.2).
     */
    fun isHinglishQuery(query: String): Boolean {
        val lower = query.lowercase()
        return listOf("mera", "meri", "kya", "batao", "dikhao", "kitna", "kab", "kahan", "hai", "bhai").any {
            lower.contains(it)
        }
    }
}
