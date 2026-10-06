package com.suhrud.docsy.domain.query

import java.util.Locale

object SemanticIntentClassifier {

    data class IntentSample(
        val intent: QueryIntent,
        val phrase: String,
        val dateTimeOp: DateTimeOp? = null
    )

    private val TRAINING_PHRASES = listOf(
        // SMALL_TALK
        IntentSample(QueryIntent.SMALL_TALK, "hi"),
        IntentSample(QueryIntent.SMALL_TALK, "hello"),
        IntentSample(QueryIntent.SMALL_TALK, "hey"),
        IntentSample(QueryIntent.SMALL_TALK, "good morning"),
        IntentSample(QueryIntent.SMALL_TALK, "good afternoon"),
        IntentSample(QueryIntent.SMALL_TALK, "good evening"),
        IntentSample(QueryIntent.SMALL_TALK, "good night"),
        IntentSample(QueryIntent.SMALL_TALK, "namaste"),
        IntentSample(QueryIntent.SMALL_TALK, "shubh prabhat"),
        IntentSample(QueryIntent.SMALL_TALK, "kaise ho"),
        IntentSample(QueryIntent.SMALL_TALK, "how are you"),
        IntentSample(QueryIntent.SMALL_TALK, "who are you"),
        IntentSample(QueryIntent.SMALL_TALK, "what can you do"),
        IntentSample(QueryIntent.SMALL_TALK, "help"),
        IntentSample(QueryIntent.SMALL_TALK, "thanks"),
        IntentSample(QueryIntent.SMALL_TALK, "thank you"),
        IntentSample(QueryIntent.SMALL_TALK, "dhanyawad"),
        IntentSample(QueryIntent.SMALL_TALK, "bye"),
        IntentSample(QueryIntent.SMALL_TALK, "goodbye"),
        IntentSample(QueryIntent.SMALL_TALK, "ok"),
        IntentSample(QueryIntent.SMALL_TALK, "cool"),
        IntentSample(QueryIntent.SMALL_TALK, "hmm"),
        IntentSample(QueryIntent.SMALL_TALK, "nice"),
        IntentSample(QueryIntent.SMALL_TALK, "great"),
        IntentSample(QueryIntent.SMALL_TALK, "sorry"),

        // DATE_TIME
        IntentSample(QueryIntent.DATE_TIME, "time", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "time?", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "what time is it", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "what's the time right now", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "current time", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "tell me the time", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "time now", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "what is the time", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "clock", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "tell time", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "today's date", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "todays date", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "date today", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "what is today's date", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "current date", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "what date is it today", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "date now", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "date", DateTimeOp.TODAY_DATE),
        IntentSample(QueryIntent.DATE_TIME, "what day is it", DateTimeOp.WHAT_DAY),
        IntentSample(QueryIntent.DATE_TIME, "what day is today", DateTimeOp.WHAT_DAY),
        IntentSample(QueryIntent.DATE_TIME, "day today", DateTimeOp.WHAT_DAY),
        IntentSample(QueryIntent.DATE_TIME, "which day is today", DateTimeOp.WHAT_DAY),
        IntentSample(QueryIntent.DATE_TIME, "what day", DateTimeOp.WHAT_DAY),
        IntentSample(QueryIntent.DATE_TIME, "tomorrow", DateTimeOp.TOMORROW),
        IntentSample(QueryIntent.DATE_TIME, "what day is tomorrow", DateTimeOp.TOMORROW),
        IntentSample(QueryIntent.DATE_TIME, "date tomorrow", DateTimeOp.TOMORROW),
        IntentSample(QueryIntent.DATE_TIME, "yesterday", DateTimeOp.YESTERDAY),
        IntentSample(QueryIntent.DATE_TIME, "what day was yesterday", DateTimeOp.YESTERDAY),
        IntentSample(QueryIntent.DATE_TIME, "date yesterday", DateTimeOp.YESTERDAY),
        IntentSample(QueryIntent.DATE_TIME, "midnight", DateTimeOp.CURRENT_TIME),
        IntentSample(QueryIntent.DATE_TIME, "week number", DateTimeOp.WEEK_OF_YEAR),
        IntentSample(QueryIntent.DATE_TIME, "current month", DateTimeOp.MONTH_AND_YEAR),
        IntentSample(QueryIntent.DATE_TIME, "current year", DateTimeOp.MONTH_AND_YEAR),
        IntentSample(QueryIntent.DATE_TIME, "days left in month", DateTimeOp.DAYS_LEFT_IN_MONTH),
        IntentSample(QueryIntent.DATE_TIME, "days left in year", DateTimeOp.DAYS_LEFT_IN_YEAR),
        IntentSample(QueryIntent.DATE_TIME, "is today monday", DateTimeOp.IS_TODAY_DAY),
        IntentSample(QueryIntent.DATE_TIME, "is today sunday", DateTimeOp.IS_TODAY_DAY),
        IntentSample(QueryIntent.DATE_TIME, "is leap year", DateTimeOp.IS_LEAP_YEAR),

        // STEPS_QUERY
        IntentSample(QueryIntent.STEPS_QUERY, "how many steps today"),
        IntentSample(QueryIntent.STEPS_QUERY, "steps count"),
        IntentSample(QueryIntent.STEPS_QUERY, "walking distance"),
        IntentSample(QueryIntent.STEPS_QUERY, "how much did I walk"),
        IntentSample(QueryIntent.STEPS_QUERY, "my steps today"),
        IntentSample(QueryIntent.STEPS_QUERY, "step counter"),
        IntentSample(QueryIntent.STEPS_QUERY, "total steps"),
        IntentSample(QueryIntent.STEPS_QUERY, "did I walk today"),
        IntentSample(QueryIntent.STEPS_QUERY, "how many steps did I take"),
        IntentSample(QueryIntent.STEPS_QUERY, "walk count"),
        IntentSample(QueryIntent.STEPS_QUERY, "daily steps"),
        IntentSample(QueryIntent.STEPS_QUERY, "steps taken"),
        IntentSample(QueryIntent.STEPS_QUERY, "steps"),
        IntentSample(QueryIntent.STEPS_QUERY, "steps?"),
        IntentSample(QueryIntent.STEPS_QUERY, "mera step count"),
        IntentSample(QueryIntent.STEPS_QUERY, "aaj kitne steps hue"),
        IntentSample(QueryIntent.STEPS_QUERY, "total walk"),
        IntentSample(QueryIntent.STEPS_QUERY, "weekly steps"),
        IntentSample(QueryIntent.STEPS_QUERY, "average steps"),
        IntentSample(QueryIntent.STEPS_QUERY, "most active day"),

        // PERSONAL_PROFILE
        IntentSample(QueryIntent.PERSONAL_PROFILE, "what is my name"),
        IntentSample(QueryIntent.PERSONAL_PROFILE, "what's my name"),
        IntentSample(QueryIntent.PERSONAL_PROFILE, "who am i"),
        IntentSample(QueryIntent.PERSONAL_PROFILE, "my name"),
        IntentSample(QueryIntent.PERSONAL_PROFILE, "tell me my name"),

        // DEVICE_INFO / DEVICE_STATS
        IntentSample(QueryIntent.DEVICE_INFO, "battery?"),
        IntentSample(QueryIntent.DEVICE_INFO, "battery percentage"),
        IntentSample(QueryIntent.DEVICE_INFO, "kitni battery hai"),
        IntentSample(QueryIntent.DEVICE_INFO, "battery status"),
        IntentSample(QueryIntent.DEVICE_INFO, "charging state"),
        IntentSample(QueryIntent.DEVICE_INFO, "ram"),
        IntentSample(QueryIntent.DEVICE_INFO, "ram kitni hai"),
        IntentSample(QueryIntent.DEVICE_INFO, "available ram"),
        IntentSample(QueryIntent.DEVICE_INFO, "memory info"),
        IntentSample(QueryIntent.DEVICE_INFO, "storage"),
        IntentSample(QueryIntent.DEVICE_INFO, "how much storage free"),
        IntentSample(QueryIntent.DEVICE_INFO, "free space"),
        IntentSample(QueryIntent.DEVICE_INFO, "rom"),
        IntentSample(QueryIntent.DEVICE_INFO, "android version"),
        IntentSample(QueryIntent.DEVICE_INFO, "kernel"),
        IntentSample(QueryIntent.DEVICE_INFO, "os version"),
        IntentSample(QueryIntent.DEVICE_INFO, "phone specs"),
        IntentSample(QueryIntent.DEVICE_INFO, "tell me about my phone"),
        IntentSample(QueryIntent.DEVICE_INFO, "internet speed?"),
        IntentSample(QueryIntent.DEVICE_INFO, "wifi connection?"),
        IntentSample(QueryIntent.DEVICE_INFO, "wifi connection info?"),
        IntentSample(QueryIntent.DEVICE_INFO, "display info"),
        IntentSample(QueryIntent.DEVICE_INFO, "screen resolution"),
        IntentSample(QueryIntent.DEVICE_INFO, "cpu architecture"),
        IntentSample(QueryIntent.DEVICE_INFO, "uptime"),
        IntentSample(QueryIntent.DEVICE_INFO, "system language"),
        IntentSample(QueryIntent.DEVICE_INFO, "time zone"),
        IntentSample(QueryIntent.DEVICE_INFO, "network status"),
        IntentSample(QueryIntent.DEVICE_INFO, "wifi link speed"),
        IntentSample(QueryIntent.DEVICE_INFO, "camera details"),
        IntentSample(QueryIntent.DEVICE_INFO, "sensors list"),
        IntentSample(QueryIntent.DEVICE_INFO, "volume levels"),
        IntentSample(QueryIntent.DEVICE_INFO, "bluetooth status"),
        IntentSample(QueryIntent.DEVICE_INFO, "gps status"),

        // DEVICE_STATS
        IntentSample(QueryIntent.DEVICE_STATS, "photos?"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many photos"),
        IntentSample(QueryIntent.DEVICE_STATS, "total pictures"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many videos"),
        IntentSample(QueryIntent.DEVICE_STATS, "videos count"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many songs"),
        IntentSample(QueryIntent.DEVICE_STATS, "audio count"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many pdfs"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many documents"),
        IntentSample(QueryIntent.DEVICE_STATS, "how many files"),
        IntentSample(QueryIntent.DEVICE_STATS, "biggest file"),
        IntentSample(QueryIntent.DEVICE_STATS, "largest photo"),
        IntentSample(QueryIntent.DEVICE_STATS, "newest video"),
        IntentSample(QueryIntent.DEVICE_STATS, "oldest document"),
        IntentSample(QueryIntent.DEVICE_STATS, "contacts count"),

        // SMS_QUERY
        IntentSample(QueryIntent.SMS_QUERY, "how many sms"),
        IntentSample(QueryIntent.SMS_QUERY, "text messages count"),
        IntentSample(QueryIntent.SMS_QUERY, "inbox messages"),
        IntentSample(QueryIntent.SMS_QUERY, "total sms"),
        IntentSample(QueryIntent.SMS_QUERY, "recent messages"),

        // CALL_QUERY
        IntentSample(QueryIntent.CALL_QUERY, "call log"),
        IntentSample(QueryIntent.CALL_QUERY, "missed calls"),
        IntentSample(QueryIntent.CALL_QUERY, "recent calls"),
        IntentSample(QueryIntent.CALL_QUERY, "total calls"),

        // APP_QUERY
        IntentSample(QueryIntent.APP_QUERY, "installed apps"),
        IntentSample(QueryIntent.APP_QUERY, "how many applications"),
        IntentSample(QueryIntent.APP_QUERY, "user apps"),
        IntentSample(QueryIntent.APP_QUERY, "system apps")
    )

    private val KEYWORD_TYPOS = mapOf(
        "tiem" to "time",
        "tme" to "time",
        "tym" to "time",
        "clok" to "clock",
        "todai" to "today",
        "tday" to "today",
        "yestarday" to "yesterday",
        "tomorow" to "tomorrow",
        "stps" to "steps",
        "stepz" to "steps",
        "wlk" to "walk",
        "storag" to "storage",
        "memry" to "memory"
    )

    fun normalizeQuery(raw: String): String {
        val q = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val words = q.split(" ").map { word ->
            KEYWORD_TYPOS[word] ?: word
        }
        return words.joinToString(" ")
    }

    fun isSmallTalk(rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        val greetings = setOf(
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening", "good night",
            "namaste", "shubh prabhat", "kaise ho", "how are you", "who are you", "what can you do",
            "help", "thanks", "thank you", "dhanyawad", "bye", "goodbye", "ok", "cool", "hmm", "nice", "great", "sorry", "good"
        )
        return greetings.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    fun isOutOfScope(rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        val outOfScopeKeywords = listOf(
            "prime minister", "capital of", "photosynthesis", "weather in", "recipe for",
            "distance to", "president of", "population of", "who wrote", "tallest mountain",
            "latest news", "stock market", "score of", "highest mountain", "currency of",
            "who won", "temperature in", "how to bake", "speed of light", "meaning of life", "who invented"
        )
        return outOfScopeKeywords.any { q.contains(it) }
    }

    fun extractOpenEndedDocumentIdentity(rawQuery: String): String? {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.startsWith("number of ") || q.startsWith("count of ") || q.startsWith("how many ")) return null

        val docKeywords = listOf(
            "certificate", "card", "receipt", "bill", "marksheet", "statement", "letter",
            "policy", "licence", "license", "passport", "degree", "visa", "agreement", "proof"
        )

        for (kw in docKeywords) {
            if (q.contains(kw)) {
                var cleaned = q.replace(Regex("""^(?:where\s+is\s+|show\s+me\s+|find\s+my\s+|find\s+|is\s+there\s+a\s+|my\s+|the\s+)"""), "")
                    .replace("?", "").trim()
                while (cleaned.startsWith("my ") || cleaned.startsWith("the ")) {
                    cleaned = cleaned.removePrefix("my ").removePrefix("the ").trim()
                }
                if (cleaned.isNotBlank() && cleaned.length < 40) {
                    return cleaned
                }
                return kw
            }
        }
        return null
    }

    fun classifySemanticIntent(rawQuery: String): ParsedQuery? {
        val normalized = normalizeQuery(rawQuery)
        if (normalized.isBlank()) return null

        if (isSmallTalk(rawQuery)) {
            return ParsedQuery(
                intent = QueryIntent.SMALL_TALK,
                rawQuery = rawQuery
            )
        }

        if (isOutOfScope(rawQuery)) {
            return ParsedQuery(
                intent = QueryIntent.OUT_OF_SCOPE,
                rawQuery = rawQuery
            )
        }

        val userTokens = tokenize(normalized)
        var bestMatch: IntentSample? = null
        var bestScore = 0.0

        for (sample in TRAINING_PHRASES) {
            val sampleTokens = tokenize(sample.phrase)
            val score = computeSimilarityScore(normalized, userTokens, sample.phrase, sampleTokens)
            if (score > bestScore) {
                bestScore = score
                bestMatch = sample
            }
        }

        if (bestMatch != null && bestScore >= 0.65) {
            val qLower = rawQuery.lowercase(Locale.ROOT)
            val dateFilter = when {
                qLower.contains("today") -> "today"
                qLower.contains("yesterday") -> "yesterday"
                qLower.contains("this week") -> "week"
                qLower.contains("this month") -> "month"
                else -> null
            }

            val filterType = when {
                qLower.contains("photo") || qLower.contains("picture") || qLower.contains("image") || qLower.contains("pic") -> "photo"
                qLower.contains("video") || qLower.contains("movie") || qLower.contains("clip") -> "video"
                qLower.contains("audio") || qLower.contains("song") || qLower.contains("music") || qLower.contains("gaane") -> "audio"
                qLower.contains("pdf") || qLower.contains("document") || qLower.contains("doc") -> "document"
                qLower.contains("contact") -> "contacts"
                qLower.contains("app") -> "app"
                else -> "storage"
            }

            return ParsedQuery(
                intent = bestMatch.intent,
                dateTimeOp = bestMatch.dateTimeOp ?: DateTimeOp.CURRENT_TIME,
                filterType = filterType,
                dateFilter = dateFilter,
                rawQuery = rawQuery
            )
        }

        return null
    }

    private fun tokenize(text: String): Set<String> {
        return text.split(" ").filter { it.isNotBlank() }.toSet()
    }

    private fun computeSimilarityScore(
        qRaw: String,
        qTokens: Set<String>,
        sRaw: String,
        sTokens: Set<String>
    ): Double {
        if (qRaw == sRaw) return 1.0

        val intersection = qTokens.intersect(sTokens).size.toDouble()
        val union = qTokens.union(sTokens).size.toDouble()
        val jaccard = if (union > 0) intersection / union else 0.0

        val substringBonus = if (qRaw.contains(sRaw) || sRaw.contains(qRaw)) 0.3 else 0.0

        return (jaccard + substringBonus).coerceAtMost(1.0)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
