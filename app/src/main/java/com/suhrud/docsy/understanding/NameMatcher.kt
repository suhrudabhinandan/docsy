package com.suhrud.docsy.understanding

import kotlin.math.max
import kotlin.math.min

/**
 * Name normalization and fuzzy matching using Jaro-Winkler and Token Set matching.
 * Handles honorific stripping, name inversion, and phonetic tolerance.
 * Satisfies Phase 7.7 in AUDIT.md.
 */
object NameMatcher {

    private val HONORIFICS = listOf(
        "mr.", "mr", "mrs.", "mrs", "ms.", "ms", "miss", "dr.", "dr",
        "shri", "smt.", "smt", "sh.", "sh", "prof.", "prof", "master",
        "kumari", "late", "adv.", "adv", "jr", "jr.", "sr", "sr."
    )

    /**
     * Strips honorifics, extra whitespace, and punctuation from a name.
     */
    fun normalizeName(name: String): String {
        var clean = name.lowercase().trim()
        clean = clean.replace(Regex("""[,.\-_/()]"""), " ")
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val filteredTokens = tokens.filter { token ->
            token !in HONORIFICS
        }
        return filteredTokens.joinToString(" ").trim()
    }

    /**
     * Calculates Jaro distance between two strings (0.0 to 1.0).
     */
    fun jaroSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0f

        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0f

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toFloat()
        return ((m / len1) + (m / len2) + ((m - transpositions / 2f) / m)) / 3.0f
    }

    /**
     * Calculates Jaro-Winkler similarity with prefix bonus.
     */
    fun jaroWinklerSimilarity(s1: String, s2: String, prefixScaling: Float = 0.1f): Float {
        val jaro = jaroSimilarity(s1, s2)
        if (jaro < 0.7f) return jaro

        var prefixLen = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefixLen++ else break
        }

        return jaro + prefixLen * prefixScaling * (1.0f - jaro)
    }

    /**
     * Token Set comparison: splits into word sets and tests containment or set similarity.
     * Handles "Suhrud, Abhinandan" matching "Abhinandan Suhrud" with 1.0 score.
     */
    fun tokenSetMatch(name1: String, name2: String): Float {
        val norm1 = normalizeName(name1)
        val norm2 = normalizeName(name2)
        if (norm1 == norm2) return 1.0f

        val tokens1 = norm1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = norm2.split(" ").filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val jaccard = intersection.toFloat() / union.toFloat()

        val jw = jaroWinklerSimilarity(norm1, norm2)
        return max(jaccard, jw)
    }

    /**
     * Determines whether two names refer to the same person.
     */
    fun isNameMatch(name1: String, name2: String, threshold: Float = 0.82f): Boolean {
        if (name1.isBlank() || name2.isBlank()) return false
        val score = tokenSetMatch(name1, name2)
        return score >= threshold
    }
}
