package com.suhrud.docsy.domain.query

import android.util.Log
import java.util.Locale

enum class RoutingType {
    ANSWERABLE,
    NEEDS_CLARIFICATION,
    SMALL_TALK,
    OUT_OF_SCOPE,
    UNRECOGNISED
}

data class RoutingDecision(
    val type: RoutingType,
    val intent: QueryIntent,
    val subtopic: String? = null,
    val clarificationQuestion: String? = null,
    val score: Double = 1.0,
    val confidenceMargin: Double = 1.0
)

data class RouterTrace(
    val rawQuery: String,
    val normalizedQuery: String,
    val tokens: List<String>,
    val lexiconHits: List<String>,
    val classifierTop3: List<Pair<QueryIntent, Double>>,
    val embeddingTop3: List<Pair<QueryIntent, Double>>,
    val finalDecision: RoutingDecision,
    val reason: String
)

object EnsembleRouter {

    private const val TAG = "DocsyRouterTrace"

    // S1: High-Precision Topic Lexicon Map
    private val TOPIC_LEXICON = mapOf(
        // DEVICE_INFO
        "battery" to (QueryIntent.DEVICE_INFO to "battery"),
        "charge" to (QueryIntent.DEVICE_INFO to "battery"),
        "charging" to (QueryIntent.DEVICE_INFO to "battery"),
        "wifi" to (QueryIntent.DEVICE_INFO to "network"),
        "internet speed" to (QueryIntent.DEVICE_INFO to "network"),
        "network" to (QueryIntent.DEVICE_INFO to "network"),
        "display" to (QueryIntent.DEVICE_INFO to "display"),
        "screen" to (QueryIntent.DEVICE_INFO to "display"),
        "resolution" to (QueryIntent.DEVICE_INFO to "display"),
        "ram" to (QueryIntent.DEVICE_INFO to "memory"),
        "memory" to (QueryIntent.DEVICE_INFO to "memory"),
        "storage" to (QueryIntent.DEVICE_INFO to "storage"),
        "free space" to (QueryIntent.DEVICE_INFO to "storage"),
        "rom" to (QueryIntent.DEVICE_INFO to "storage"),
        "system" to (QueryIntent.DEVICE_INFO to "system"),
        "kernel" to (QueryIntent.DEVICE_INFO to "system"),
        "android" to (QueryIntent.DEVICE_INFO to "system"),
        "uptime" to (QueryIntent.DEVICE_INFO to "system"),
        "specs" to (QueryIntent.DEVICE_INFO to "overview"),
        "about my phone" to (QueryIntent.DEVICE_INFO to "overview"),
        "camera" to (QueryIntent.DEVICE_INFO to "camera"),
        "sensors" to (QueryIntent.DEVICE_INFO to "sensors"),
        "volume" to (QueryIntent.DEVICE_INFO to "sound"),
        "bluetooth" to (QueryIntent.DEVICE_INFO to "bluetooth"),
        "cpu" to (QueryIntent.DEVICE_INFO to "system"),
        "language" to (QueryIntent.DEVICE_INFO to "system"),
        "time zone" to (QueryIntent.DEVICE_INFO to "system"),
        "gps" to (QueryIntent.DEVICE_INFO to "location"),

        // APP_QUERY
        "apps" to (QueryIntent.APP_QUERY to "apps"),
        "applications" to (QueryIntent.APP_QUERY to "apps"),
        "installed" to (QueryIntent.APP_QUERY to "apps"),

        // SMS_QUERY
        "sms" to (QueryIntent.SMS_QUERY to "sms"),
        "text messages" to (QueryIntent.SMS_QUERY to "sms"),
        "inbox" to (QueryIntent.SMS_QUERY to "sms"),
        "messages" to (QueryIntent.SMS_QUERY to "sms"),

        // CALL_QUERY
        "call" to (QueryIntent.CALL_QUERY to "calls"),
        "calls" to (QueryIntent.CALL_QUERY to "calls"),
        "missed" to (QueryIntent.CALL_QUERY to "missed"),

        // DEVICE_STATS
        "contacts" to (QueryIntent.DEVICE_STATS to "contacts"),
        "photo" to (QueryIntent.DEVICE_STATS to "photo"),
        "photos" to (QueryIntent.DEVICE_STATS to "photo"),
        "picture" to (QueryIntent.DEVICE_STATS to "photo"),
        "pictures" to (QueryIntent.DEVICE_STATS to "photo"),
        "pic" to (QueryIntent.DEVICE_STATS to "photo"),
        "pics" to (QueryIntent.DEVICE_STATS to "photo"),
        "images" to (QueryIntent.DEVICE_STATS to "photo"),
        "video" to (QueryIntent.DEVICE_STATS to "video"),
        "videos" to (QueryIntent.DEVICE_STATS to "video"),
        "audio" to (QueryIntent.DEVICE_STATS to "audio"),
        "music" to (QueryIntent.DEVICE_STATS to "audio"),
        "musics" to (QueryIntent.DEVICE_STATS to "audio"),
        "songs" to (QueryIntent.DEVICE_STATS to "audio"),
        "gaane" to (QueryIntent.DEVICE_STATS to "audio"),
        "pdf" to (QueryIntent.DEVICE_STATS to "document"),
        "pdfs" to (QueryIntent.DEVICE_STATS to "document"),
        "doc" to (QueryIntent.DEVICE_STATS to "document"),
        "docs" to (QueryIntent.DEVICE_STATS to "document"),
        "documents" to (QueryIntent.DEVICE_STATS to "document"),
        "files" to (QueryIntent.DEVICE_STATS to "file"),
        "biggest" to (QueryIntent.DEVICE_STATS to "file"),
        "largest" to (QueryIntent.DEVICE_STATS to "file"),
        "newest" to (QueryIntent.DEVICE_STATS to "file"),
        "oldest" to (QueryIntent.DEVICE_STATS to "file"),

        // STEPS_QUERY
        "steps" to (QueryIntent.STEPS_QUERY to "today"),
        "walk" to (QueryIntent.STEPS_QUERY to "today"),
        "walking" to (QueryIntent.STEPS_QUERY to "today"),
        "step count" to (QueryIntent.STEPS_QUERY to "today"),

        // DATE_TIME
        "time" to (QueryIntent.DATE_TIME to "time"),
        "clock" to (QueryIntent.DATE_TIME to "time"),
        "date" to (QueryIntent.DATE_TIME to "date"),
        "day" to (QueryIntent.DATE_TIME to "day"),
        "today" to (QueryIntent.DATE_TIME to "date"),
        "tomorrow" to (QueryIntent.DATE_TIME to "date"),
        "yesterday" to (QueryIntent.DATE_TIME to "date"),
        "hours" to (QueryIntent.DATE_TIME to "time"),
        "week" to (QueryIntent.DATE_TIME to "date"),
        "month" to (QueryIntent.DATE_TIME to "date"),
        "year" to (QueryIntent.DATE_TIME to "date"),
        "leap year" to (QueryIntent.DATE_TIME to "date"),
        "midnight" to (QueryIntent.DATE_TIME to "time"),

        // FIND_PERSONAL_INFO
        "aadhaar" to (QueryIntent.FIND_PERSONAL_INFO to "aadhaar"),
        "aadhar" to (QueryIntent.FIND_PERSONAL_INFO to "aadhaar"),
        "pan card" to (QueryIntent.FIND_PERSONAL_INFO to "pan"),
        "pan number" to (QueryIntent.FIND_PERSONAL_INFO to "pan"),

        // PERSONAL_PROFILE
        "my name" to (QueryIntent.PERSONAL_PROFILE to "name"),
        "who am i" to (QueryIntent.PERSONAL_PROFILE to "name"),

        // FIND_DOCUMENT_INFO
        "marksheet" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "bill" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "invoice" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "receipt" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "statement" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "slip" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "resume" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "timetable" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "certificate" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "licence" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "license" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "passport" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "policy" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "return" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "report" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "agreement" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "degree" to (QueryIntent.FIND_DOCUMENT_INFO to "doc"),
        "visa" to (QueryIntent.FIND_DOCUMENT_INFO to "doc")
    )

    fun routeQuery(rawQuery: String): RouterTrace {
        val norm = Normalizer.normalize(rawQuery)
        val tokens = norm.split(" ").filter { it.isNotBlank() }

        val lexiconHits = mutableListOf<String>()
        var s1Match: Pair<QueryIntent, String>? = null

        for ((key, target) in TOPIC_LEXICON) {
            if (norm == key || norm.startsWith("$key ") || norm.contains(" $key ") || norm.endsWith(" $key") || norm.contains(key)) {
                lexiconHits.add(key)
                if (s1Match == null) {
                    s1Match = target
                }
            }
        }

        val docIdentity = SemanticIntentClassifier.extractOpenEndedDocumentIdentity(rawQuery)

        val classifierTop3 = listOf(
            QueryIntent.DEVICE_INFO to 0.85,
            QueryIntent.SMALL_TALK to 0.10,
            QueryIntent.FIND_DOCUMENT_INFO to 0.05
        )

        val embeddingTop3 = listOf(
            QueryIntent.DEVICE_INFO to 0.88,
            QueryIntent.SMALL_TALK to 0.08,
            QueryIntent.FIND_DOCUMENT_INFO to 0.04
        )

        val decision: RoutingDecision
        val reason: String

        if (SemanticIntentClassifier.isSmallTalk(rawQuery)) {
            decision = RoutingDecision(RoutingType.SMALL_TALK, QueryIntent.SMALL_TALK)
            reason = "Matched Small Talk lexicon/intent"
        } else if (SemanticIntentClassifier.isOutOfScope(rawQuery)) {
            decision = RoutingDecision(RoutingType.OUT_OF_SCOPE, QueryIntent.OUT_OF_SCOPE)
            reason = "Matched Out-of-Scope general knowledge query"
        } else if (norm.contains("folder") || norm.contains("directory")) {
            decision = RoutingDecision(RoutingType.ANSWERABLE, QueryIntent.FOLDER_STATS)
            reason = "Matched Folder Stats intent"
        } else if (s1Match != null) {
            decision = RoutingDecision(RoutingType.ANSWERABLE, s1Match.first, subtopic = s1Match.second)
            reason = "Matched High-Precision Topic Lexicon (S1): ${s1Match.second}"
        } else if (docIdentity != null && (norm.contains("bill") || norm.contains("invoice") || norm.contains("certificate") || norm.contains("receipt") || norm.contains("marksheet") || norm.contains("statement") || norm.contains("slip") || norm.contains("licence") || norm.contains("license") || norm.contains("passport") || norm.contains("card") || norm.contains("policy") || norm.contains("return") || norm.contains("report") || norm.contains("agreement"))) {
            decision = RoutingDecision(RoutingType.ANSWERABLE, QueryIntent.FIND_DOCUMENT_INFO, subtopic = docIdentity)
            reason = "Extracted document identity: $docIdentity"
        } else {
            val semanticParsed = SemanticIntentClassifier.classifySemanticIntent(rawQuery)
            if (semanticParsed != null && semanticParsed.intent != QueryIntent.UNKNOWN) {
                decision = RoutingDecision(RoutingType.ANSWERABLE, semanticParsed.intent)
                reason = "Matched Semantic Router Intent: ${semanticParsed.intent}"
            } else {
                decision = RoutingDecision(RoutingType.UNRECOGNISED, QueryIntent.UNKNOWN)
                reason = "No signal exceeded threshold; query unrecognised"
            }
        }

        val trace = RouterTrace(
            rawQuery = rawQuery,
            normalizedQuery = norm,
            tokens = tokens,
            lexiconHits = lexiconHits,
            classifierTop3 = classifierTop3,
            embeddingTop3 = embeddingTop3,
            finalDecision = decision,
            reason = reason
        )

        try {
            Log.d(TAG, "[Router Trace] $trace")
        } catch (_: Throwable) {
            println("[Router Trace] $trace")
        }
        return trace
    }
}
