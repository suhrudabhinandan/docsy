package com.suhrud.docsy.retrieval

import com.suhrud.docsy.understanding.DocumentSynonymDictionary

object QueryNormalizer {

    private val HINGLISH_PHRASE_MAPPINGS = listOf(
        "kya hai" to "what is",
        "kya h" to "what is",
        "batao" to "tell me",
        "dikhao" to "show me",
        "kitne" to "how many",
        "kitna" to "how much",
        "kitni" to "how many",
        "kab hai" to "when is",
        "kahan hai" to "where is",
        "mera" to "my",
        "meri" to "my",
        "mere" to "my",
        "apna" to "my",
        "apni" to "my",
        "bijli bill" to "electricity bill",
        "bijli ka bill" to "electricity bill",
        "anktalika" to "marksheet",
        "ank patrika" to "marksheet",
        "janamdin" to "date of birth",
        "janam tithi" to "date of birth",
        "samay" to "time",
        "waqt" to "time",
        "tareekh" to "date"
    )

    private val INTERNAL_VOCABULARY = mapOf(
        "adhar" to "aadhaar",
        "aadhar" to "aadhaar",
        "aadhr" to "aadhaar",
        "adhhar" to "aadhaar",
        "pancard" to "pan card",
        "markshet" to "marksheet",
        "markssheet" to "marksheet",
        "marklist" to "marksheet",
        "resum" to "resume",
        "resme" to "resume",
        "biodata" to "resume",
        "passprt" to "passport",
        "passpost" to "passport",
        "votercard" to "voter id",
        "12th" to "12th",
        "xii" to "12th",
        "twelfth" to "12th",
        "10th" to "10th",
        "tenth" to "10th"
    )

    fun normalize(rawQuery: String): String {
        var query = rawQuery.trim().lowercase()
        if (query.isBlank()) return ""

        // Strip punctuation
        query = query.replace(Regex("""[?!,;:"'()\[\]{}]"""), " ")
        query = query.replace(Regex("""\s+"""), " ").trim()

        // Apply multi-word Hinglish phrase mappings
        for ((hinglish, english) in HINGLISH_PHRASE_MAPPINGS) {
            query = query.replace(hinglish, english)
        }

        // Apply single-word spelling corrections against internal vocabulary
        val words = query.split(" ").map { word ->
            INTERNAL_VOCABULARY[word] ?: correctTypo(word)
        }

        return words.joinToString(" ").trim()
    }

    private fun correctTypo(word: String): String {
        if (word.length <= 3) return word
        var bestMatch = word
        var minDistance = Int.MAX_VALUE

        for (target in INTERNAL_VOCABULARY.values.distinct()) {
            val dist = computeLevenshteinDistance(word, target)
            if (dist <= 2 && dist < minDistance) {
                minDistance = dist
                bestMatch = target
            }
        }
        return bestMatch
    }

    fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
