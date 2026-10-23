package com.suhrud.docsy.planner

import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.retrieval.DocsyIntent
import java.util.Calendar

/**
 * Deterministic response composer generating fluent, warm, and grounded natural English & Hinglish.
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

        val isMarksheet = documentName.contains("marksheet", ignoreCase = true) ||
                field.contains("roll", ignoreCase = true) ||
                field.contains("mark", ignoreCase = true) ||
                field.contains("percentage", ignoreCase = true)

        val nextStep = when {
            isHinglish -> {
                if (isMarksheet) {
                    "Aap roll number, subject-wise marks, ya best 5 percentage bhi pooch sakte hain."
                } else {
                    "Aap kisi aur field ya document ke baare me bhi pooch sakte hain."
                }
            }
            isMarksheet -> "You can also ask for your roll number, subject-wise marks, or calculate your best 5 percentage."
            else -> "Let me know if you need to verify another detail or check another document."
        }

        return "$leadIn\n**$directAnswer**\n\n$nextStep"
    }

    /**
     * Composes small talk responses (Phase 12.1, 12.2) leveraging ChitchatResponseBank.
     */
    fun composeSmallTalk(
        intent: DocsyIntent,
        userName: String = "",
        targetCategory: String? = null,
        sessionId: String = "default"
    ): String {
        if (targetCategory == "REPHRASE_REQUIRED" || intent == DocsyIntent.CLARIFICATION_REQUIRED) {
            return "I'm not sure what you mean. Could you please rephrase that or specify a document name?"
        }

        val intentName = targetCategory ?: when (intent) {
            DocsyIntent.HOW_ARE_YOU -> "how_are_you"
            DocsyIntent.WHO_ARE_YOU -> "who_are_you"
            DocsyIntent.GRATITUDE -> "gratitude"
            DocsyIntent.FAREWELL -> "farewell"
            DocsyIntent.JOKE -> "joke"
            else -> "greetings"
        }

        return com.suhrud.docsy.retrieval.ChitchatResponseBank.getReply(
            intent = intentName,
            userName = userName,
            sessionId = sessionId
        )
    }

    /**
     * Checks if user query was phrased in Hinglish for dialect mirroring (Phase 12.2).
     */
    fun isHinglishQuery(query: String): Boolean {
        val lower = query.lowercase()
        return listOf("mera", "meri", "kya", "batao", "dikhao", "kitna", "kitni", "kab", "kahan", "hai", "bhai", "bataiye", "pucho", "kaise", "kon").any {
            lower.contains(it)
        }
    }
}
