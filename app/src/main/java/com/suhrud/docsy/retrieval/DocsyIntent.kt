package com.suhrud.docsy.retrieval

enum class RouterOutcome {
    ANSWERABLE,
    NEEDS_CLARIFICATION,
    SMALL_TALK,
    GENERAL_KNOWLEDGE,
    OUT_OF_SCOPE,
    UNRECOGNISED
}

/**
 * Exhaustive intent taxonomy for DOCSY (20+ intents).
 * Satisfies Phase 9.1 and 9.5 in AUDIT.md.
 */
enum class DocsyIntent {
    // Small Talk Intents
    GREETING,
    HOW_ARE_YOU,
    WHO_ARE_YOU,
    GRATITUDE,
    FAREWELL,
    JOKE,
    GENERAL_CHITCHAT,

    // General Knowledge Intents
    GENERAL_QUESTION,

    // Profile Intents
    USER_NAME_QUERY,
    USER_PROFILE_INFO,

    // System Clock Intents
    SYSTEM_TIME,
    SYSTEM_DATE,
    DAY_OF_WEEK,

    // Device Telemetry Intents
    BATTERY_STATUS,
    NETWORK_STATUS,
    BLUETOOTH_STATUS,
    MEMORY_RAM_STATUS,
    INTERNAL_STORAGE_SPACE,
    DEVICE_HARDWARE_SPECS,
    INSTALLED_APPS_QUERY,

    // Deep Phone Software & Hardware Specs Intents
    PHONE_OVERVIEW_SUMMARY,
    PHONE_FULL_SPECS,
    PHONE_KERNEL,
    PHONE_BUILD_NUMBER,
    PHONE_ANDROID_VERSION,
    PHONE_SECURITY_PATCH,
    PHONE_CHIPSET_CPU,
    PHONE_REFRESH_RATE,
    PHONE_CAMERAS,
    PHONE_SENSORS,
    PHONE_CONNECTIVITY_INFO,
    PHONE_AUDIO_THERMAL,
    PHONE_SECURITY_STATUS,
    PHONE_SOFTWARE_SPECS,

    // Sensor / Fitness Intents
    STEP_COUNT_QUERY,

    // Telephony & Communication Intents
    CONTACT_LOOKUP,
    UNREAD_SMS_COUNT,
    SMS_SEARCH,
    MISSED_CALLS_COUNT,
    CALL_LOG_SEARCH,

    // Media & Storage Stats Intents
    MEDIA_CATEGORY_COUNT,
    STORAGE_SUPERLATIVE_LARGEST,
    STORAGE_SUPERLATIVE_SMALLEST,
    STORAGE_SUPERLATIVE_NEWEST,
    STORAGE_SUPERLATIVE_OLDEST,

    // Document Understanding & Facts Intents
    DOCUMENT_FACT_QUERY,
    DOCUMENT_ID_LOOKUP,
    DOCUMENT_LIST_QUERY,
    DOCUMENT_SEARCH_LEXICAL,

    // Calculator Intents
    CALCULATOR_EVALUATION,

    // Metacognitive Intents
    READING_STATUS_QUERY,
    CLARIFICATION_REQUIRED,
    OUT_OF_SCOPE,
    UNRECOGNISED
}

data class RoutingDecision(
    val outcome: RouterOutcome,
    val intent: DocsyIntent,
    val confidence: Float,
    val targetCategory: String? = null,
    val targetField: String? = null,
    val targetOwner: String? = null,
    val normalizedQuery: String = ""
)
