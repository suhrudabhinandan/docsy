package com.suhrud.docsy.retrieval

import com.suhrud.docsy.planner.LocalLlmManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmartTitleGenerator {

    /**
     * Generates a smart 2-5 word chat title based on retrieval signals, device topics, chit-chat intents, or LLM summary.
     */
    fun generateTitle(
        userQuery: String,
        assistantResponseText: String,
        decision: RoutingDecision? = null,
        retrievalResponse: RetrievalResponse? = null,
        historyTurns: List<Pair<String, String>> = emptyList()
    ): String {
        // If LLM model is loaded, attempt LLM summary title
        if (LocalLlmManager.isModelLoaded.value && historyTurns.isNotEmpty()) {
            val llmSummary = LocalLlmManager.generateSummary(
                historyTurns.joinToString("\n") { "User: ${it.first}\nAssistant: ${it.second}" }
            )
            if (!llmSummary.isNullOrBlank()) {
                val candidate = extractMainNounPhrase(llmSummary)
                if (candidate.isNotBlank()) return sanitizeTitle(candidate)
            }
        }

        // Signal 1: Retrieval / Document Signals
        if (decision != null) {
            val category = decision.targetCategory
            val field = decision.targetField
            val qLower = userQuery.lowercase(Locale.ROOT)

            if (decision.intent == DocsyIntent.DOCUMENT_FACT_QUERY ||
                decision.intent == DocsyIntent.DOCUMENT_ID_LOOKUP ||
                decision.intent == DocsyIntent.DOCUMENT_LIST_QUERY ||
                !category.isNullOrBlank()
            ) {
                val is12th = qLower.contains("12th") || qLower.contains("class xii") || category?.equals("12th", ignoreCase = true) == true
                val is10th = qLower.contains("10th") || qLower.contains("class x") || category?.equals("10th", ignoreCase = true) == true
                val isPct = field?.equals("percentage", ignoreCase = true) == true ||
                        field?.equals("percent", ignoreCase = true) == true ||
                        qLower.contains("percentage") || qLower.contains("percent") || qLower.contains("marks")

                val title = when {
                    is12th && isPct -> "12th marksheet percentage"
                    is12th -> "12th marksheet"
                    is10th && isPct -> "10th marksheet percentage"
                    is10th -> "10th marksheet"
                    category?.equals("AADHAAR", ignoreCase = true) == true -> "Aadhaar card details"
                    category?.equals("PAN", ignoreCase = true) == true -> "PAN card details"
                    category?.equals("CV", ignoreCase = true) == true || category?.equals("RESUME", ignoreCase = true) == true -> "Resume details"
                    category?.equals("MARKSHEET", ignoreCase = true) == true -> if (isPct) "Marksheet percentage" else "Marksheet details"
                    category?.equals("BILL", ignoreCase = true) == true -> "Bill statement"
                    !category.isNullOrBlank() -> "${category.lowercase(Locale.ROOT).replace("_", " ").capitalizeWords()} details"
                    else -> "Document details"
                }
                return sanitizeTitle(title)
            }

            // Signal 2: Device Telemetry & Phone Specs Signals
            if (decision.outcome == RouterOutcome.ANSWERABLE) {
                val isBattery = decision.intent == DocsyIntent.BATTERY_STATUS || qLower.contains("battery")
                val isStorage = decision.intent == DocsyIntent.INTERNAL_STORAGE_SPACE || qLower.contains("storage")

                if (isBattery && isStorage) return sanitizeTitle("Battery and storage")
                if (isBattery) return sanitizeTitle("Battery status")
                if (isStorage) return sanitizeTitle("Storage space")

                val deviceTitle = when (decision.intent) {
                    DocsyIntent.MEMORY_RAM_STATUS -> "RAM memory info"
                    DocsyIntent.PHONE_CAMERAS -> "Camera specs"
                    DocsyIntent.PHONE_FULL_SPECS, DocsyIntent.PHONE_OVERVIEW_SUMMARY -> "Phone specifications"
                    DocsyIntent.PHONE_CHIPSET_CPU -> "CPU and chipset"
                    DocsyIntent.STEP_COUNT_QUERY -> "Step count stats"
                    DocsyIntent.NETWORK_STATUS, DocsyIntent.BLUETOOTH_STATUS -> "Network connectivity"
                    DocsyIntent.SYSTEM_TIME, DocsyIntent.SYSTEM_DATE -> "Date and time"
                    else -> null
                }
                if (deviceTitle != null) return sanitizeTitle(deviceTitle)
            }

            // Signal 3: Chit-Chat Signals
            if (decision.outcome == RouterOutcome.SMALL_TALK) {
                return sanitizeTitle("Small talk")
            }

            // Signal 4: General Questions
            if (decision.outcome == RouterOutcome.GENERAL_KNOWLEDGE) {
                val nounPhrase = extractMainNounPhrase(userQuery)
                if (nounPhrase.isNotBlank()) return sanitizeTitle(nounPhrase)
            }
        }

        // Signal 5: Dominant topic across history turns (for turn 3 re-titling)
        if (historyTurns.isNotEmpty()) {
            val dominant = findDominantTopic(historyTurns)
            if (!dominant.isNullOrBlank()) return sanitizeTitle(dominant)
        }

        // Fallback: "Chat · 17 Oct"
        return formatFallbackTitle()
    }

    /**
     * Re-titles after turn 3 based on dominant topic across turns.
     */
    fun generateTurn3Title(turns: List<Pair<String, String>>): String {
        val dominant = findDominantTopic(turns)
        return if (!dominant.isNullOrBlank()) sanitizeTitle(dominant) else formatFallbackTitle()
    }

    private fun findDominantTopic(turns: List<Pair<String, String>>): String? {
        val qAll = turns.joinToString(" ") { it.first.lowercase() }
        val hasBattery = qAll.contains("battery")
        val hasStorage = qAll.contains("storage") || qAll.contains("space") || qAll.contains("files")
        val hasMarksheet = qAll.contains("marksheet") || qAll.contains("12th") || qAll.contains("10th") || qAll.contains("percent")

        return when {
            hasBattery && hasStorage -> "Battery and storage"
            hasBattery -> "Battery status"
            hasStorage -> "Storage space"
            hasMarksheet -> "Marksheet details"
            else -> null
        }
    }

    private fun extractMainNounPhrase(query: String): String {
        val clean = query.lowercase().trim()
            .replace(Regex("""^(explain|what is|how to|write an essay on|tell me about|how does|why is|describe)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        val words = clean.split(" ").filter { it.isNotBlank() }
        return if (words.size in 1..4) {
            clean.capitalizeWords()
        } else if (words.size > 4) {
            words.take(3).joinToString(" ").capitalizeWords()
        } else {
            ""
        }
    }

    /**
     * Sanitizes titles:
     * 1. Strips all personal numbers (Aadhaar 12-digit, PAN, 10-digit phone, long numeric IDs).
     * 2. Never allows raw user message.
     * 3. Ensures 2-5 words.
     */
    fun sanitizeTitle(title: String): String {
        // Strip 12-digit numbers (Aadhaar)
        var clean = title.replace(Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b"""), "").trim()
        // Strip PAN numbers
        clean = clean.replace(Regex("""\b[A-Za-z]{5}\d{4}[A-Za-z]\b"""), "").trim()
        // Strip 10-digit phone numbers
        clean = clean.replace(Regex("""\b\d{10}\b"""), "").trim()
        // Strip any remaining long numbers
        clean = clean.replace(Regex("""\b\d{5,}\b"""), "").trim()

        // Clean extra spaces or punctuation
        clean = clean.replace(Regex("""\s+"""), " ").replace(Regex("""^[^\w]+|[^\w]+$"""), "").trim()

        val words = clean.split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> formatFallbackTitle()
            words.size > 5 -> words.take(4).joinToString(" ").capitalizeWords()
            else -> clean.capitalizeWords()
        }
    }

    fun formatFallbackTitle(dateMs: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(dateMs))
        return "Chat · $dateStr"
    }

    private fun String.capitalizeWords(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
    }
}
