package com.suhrud.docsy.domain.query

import java.util.Locale

object Normalizer {

    private val KEYWORD_VARIANTS = mapOf(
        "wi-fi" to "wifi",
        "wi fi" to "wifi",
        "wireless" to "wifi",
        "bat" to "battery",
        "batt" to "battery",
        "battry" to "battery",
        "info" to "information",
        "specs" to "specification",
        "spec" to "specification",
        "msg" to "message",
        "msgs" to "messages",
        "pic" to "photo",
        "pics" to "photos",
        "img" to "photo",
        "imgs" to "photos",
        "sec" to "second",
        "min" to "minute",
        "hr" to "hour",
        "hrs" to "hours",
        "storag" to "storage",
        "memry" to "memory"
    )

    private val HINGLISH_MAP = mapOf(
        "kitni" to "how many",
        "kitna" to "how much",
        "kaun" to "who",
        "kab" to "when",
        "mera" to "my",
        "meri" to "my",
        "batao" to "tell me",
        "dikhao" to "show me",
        "kya" to "what",
        "kaise" to "how"
    )

    private val SLANG_PROFANITY_TOKENS = setOf(
        "damn", "dammit", "fuck", "fucking", "fucked", "fuk", "shit", "wtf", "crap",
        "hell", "dead", "crazy", "insane", "bloody", "bastard", "bitch", "ass", "bro", "dude", "man", "mate"
    )

    private val INTERNAL_VOCABULARY = setOf(
        "battery", "wifi", "network", "display", "screen", "ram", "storage",
        "photos", "videos", "audio", "steps", "documents", "sms", "calls",
        "time", "date", "hello", "hi", "thanks", "apps", "system", "kernel",
        "android", "uptime", "camera", "volume", "bluetooth", "location"
    )

    fun normalize(rawQuery: String): String {
        var q = rawQuery.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (q.isBlank()) return ""

        for ((variant, target) in KEYWORD_VARIANTS) {
            q = q.replace(Regex("""\b$variant\b"""), target)
        }

        val rawTokens = q.split(" ")
        val filteredTokens = rawTokens.filter { token ->
            token.lowercase(Locale.ROOT) !in SLANG_PROFANITY_TOKENS || rawTokens.size <= 2
        }

        val tokens = filteredTokens.map { token ->
            val hMapped = HINGLISH_MAP[token]
            if (hMapped != null) return@map hMapped

            if (token in INTERNAL_VOCABULARY) return@map token

            val corrected = INTERNAL_VOCABULARY.find { vocabWord ->
                levenshteinDistance(token, vocabWord) == 1 && token.length > 3
            }
            corrected ?: token
        }

        return tokens.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
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
