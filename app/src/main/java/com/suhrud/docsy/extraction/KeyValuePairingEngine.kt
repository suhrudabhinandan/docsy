package com.suhrud.docsy.extraction

import com.suhrud.docsy.model.CommonDocumentModel.BoundingBox
import com.suhrud.docsy.model.CommonDocumentModel.Line
import com.suhrud.docsy.model.CommonDocumentModel.PageDoc
import com.suhrud.docsy.model.CommonDocumentModel.TableDoc
import kotlin.math.abs

data class ExtractedPair(
    val keyLabel: String,
    val valueText: String,
    val confidence: Float,
    val pageNumber: Int,
    val boundingBox: BoundingBox? = null,
    val isRoleOrRelationship: Boolean = false
)

object KeyValuePairingEngine {

    private val labelVocabulary = mapOf(
        "total" to listOf("total", "total amount", "grand total", "net total", "amount due", "final mark", "overall percentage"),
        "date" to listOf("date", "issued date", "invoice date", "bill date", "dob", "date of birth", "timestamp"),
        "name" to listOf("name", "student name", "customer name", "patient name", "holder name", "account holder"),
        "id" to listOf("id", "roll no", "roll number", "invoice no", "registration no", "account no", "id number"),
        "father" to listOf("father name", "father's name", "guardian name", "parent name"),
        "board" to listOf("board", "university", "school", "institution", "organization", "issuer")
    )

    private val roleLabels = setOf("father", "guardian", "parent", "teacher", "principal", "manager", "issuer", "signed by")

    fun extractPairs(pageDoc: PageDoc): List<ExtractedPair> {
        val pairs = mutableListOf<ExtractedPair>()
        val allLines = pageDoc.blocks.flatMap { it.lines }

        for (i in allLines.indices) {
            val line = allLines[i]
            val text = line.text.trim()

            // 1. Same-line after separator (: or - or =)
            val sepIndex = text.indexOfAny(charArrayOf(':', '=', '-'))
            if (sepIndex > 0 && sepIndex < text.length - 1) {
                val candidateKey = text.substring(0, sepIndex).trim()
                val candidateValue = text.substring(sepIndex + 1).trim()

                val matchedKey = matchLabel(candidateKey)
                if (matchedKey != null && candidateValue.isNotEmpty() && !isKnownLabel(candidateValue)) {
                    val isRole = roleLabels.any { candidateKey.lowercase().contains(it) }
                    pairs.add(
                        ExtractedPair(
                            keyLabel = matchedKey,
                            valueText = candidateValue,
                            confidence = 0.95f,
                            pageNumber = pageDoc.pageNumber,
                            boundingBox = line.box,
                            isRoleOrRelationship = isRole
                        )
                    )
                    continue
                }
            }

            // 2. Nearest text below with similar left edge
            val matchedKey = matchLabel(text)
            if (matchedKey != null && i + 1 < allLines.size) {
                val nextLine = allLines[i + 1]
                val nextText = nextLine.text.trim()
                if (nextText.isNotEmpty() && !isKnownLabel(nextText)) {
                    val isRole = roleLabels.any { text.lowercase().contains(it) }
                    pairs.add(
                        ExtractedPair(
                            keyLabel = matchedKey,
                            valueText = nextText,
                            confidence = 0.85f,
                            pageNumber = pageDoc.pageNumber,
                            boundingBox = nextLine.box,
                            isRoleOrRelationship = isRole
                        )
                    )
                }
            }
        }
        return pairs
    }

    fun matchLabel(rawText: String): String? {
        val clean = rawText.lowercase().trim().replace(":", "").replace("-", "")
        for ((key, synonyms) in labelVocabulary) {
            if (synonyms.any { clean == it || clean.contains(it) || fuzzyMatch(clean, it) }) {
                return key
            }
        }
        return if (rawText.length in 2..30 && rawText.any { it.isLetter() }) rawText.lowercase() else null
    }

    private fun isKnownLabel(text: String): Boolean {
        val clean = text.lowercase().trim()
        return labelVocabulary.values.flatten().any { clean == it }
    }

    private fun fuzzyMatch(s1: String, s2: String): Boolean {
        if (abs(s1.length - s2.length) > 3) return false
        val dist = levenshteinDistance(s1, s2)
        return dist <= 2
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
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
