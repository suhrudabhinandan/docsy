package com.suhrud.docsy.tools

enum class TopicDomain {
    SYSTEM_CLOCK,
    BATTERY_HARDWARE,
    NETWORK_CONNECTIVITY,
    STEP_FITNESS,
    CONTACTS_TELEPHONY,
    STORAGE_MEDIA,
    DOCUMENT_FACT,
    SMALL_TALK,
    GENERAL,
    PHONE_SPECS
}

/**
 * Strict topic isolation filter ensuring cross-topic facts never bleed into responses.
 * Satisfies Phase 8.9 in AUDIT.md and architectural Rule R8.
 */
object TopicIsolationFilter {

    private val DOCUMENT_CONTAMINATION_PATTERNS = listOf(
        Regex("""\b(marksheet|roll number|aadhaar|pan card|passport number|admit card|cgpa|percentage)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(pdf|docx|scanned document|extracted text|certificate)\b""", RegexOption.IGNORE_CASE)
    )

    private val DEVICE_CONTAMINATION_PATTERNS = listOf(
        Regex("""\b(battery is at|charging via|ram used|link speed|airplane mode|total rx)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(steps today|bluetooth is|display: \d+x\d+)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Checks if a response text contains forbidden bleed-over from foreign topic domains.
     */
    fun hasTopicBleed(text: String, expectedTopic: TopicDomain): Boolean {
        when (expectedTopic) {
            TopicDomain.SYSTEM_CLOCK,
            TopicDomain.BATTERY_HARDWARE,
            TopicDomain.NETWORK_CONNECTIVITY,
            TopicDomain.STEP_FITNESS,
            TopicDomain.STORAGE_MEDIA,
            TopicDomain.PHONE_SPECS -> {
                // Device telemetry responses must NEVER include document-specific facts
                return DOCUMENT_CONTAMINATION_PATTERNS.any { it.containsMatchIn(text) }
            }
            TopicDomain.DOCUMENT_FACT -> {
                // Document fact responses must NEVER include device telemetry
                return DEVICE_CONTAMINATION_PATTERNS.any { it.containsMatchIn(text) }
            }
            TopicDomain.CONTACTS_TELEPHONY,
            TopicDomain.SMALL_TALK,
            TopicDomain.GENERAL -> return false
        }
    }

    /**
     * Sanitizes response text to enforce strict topic isolation.
     */
    fun enforceIsolation(responseText: String, topic: TopicDomain): String {
        var clean = responseText
        when (topic) {
            TopicDomain.SYSTEM_CLOCK,
            TopicDomain.BATTERY_HARDWARE,
            TopicDomain.NETWORK_CONNECTIVITY,
            TopicDomain.STEP_FITNESS,
            TopicDomain.STORAGE_MEDIA -> {
                for (pattern in DOCUMENT_CONTAMINATION_PATTERNS) {
                    clean = clean.replace(pattern, "")
                }
            }
            TopicDomain.DOCUMENT_FACT -> {
                for (pattern in DEVICE_CONTAMINATION_PATTERNS) {
                    clean = clean.replace(pattern, "")
                }
            }
            else -> {}
        }
        return clean.trim()
    }
}
