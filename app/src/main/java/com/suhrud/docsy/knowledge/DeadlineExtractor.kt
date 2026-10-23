package com.suhrud.docsy.knowledge

import com.suhrud.docsy.extractor.models.ProvenanceInfo
import java.util.regex.Pattern

enum class DateSemanticRole {
    DUE_DATE,
    EXPIRY_DATE,
    ISSUE_DATE,
    START_DATE,
    END_DATE,
    EVENT_DATE,
    UNKNOWN_DATE
}

data class ExtractedDateRecord(
    val rawText: String,
    val normalizedDateStr: String,
    val semanticRole: DateSemanticRole,
    val isDeadline: Boolean,
    val evidenceText: String,
    val provenance: ProvenanceInfo? = null
)

object DeadlineExtractor {

    private val DATE_PATTERN = Pattern.compile("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \d{1,2},? \d{4})\b""", Pattern.CASE_INSENSITIVE)

    private val DUE_KEYWORDS = listOf("due date", "payment due", "deadline", "pay by", "last date")
    private val EXPIRY_KEYWORDS = listOf("expiry date", "valid upto", "expires on", "valid until")
    private val ISSUE_KEYWORDS = listOf("date of issue", "issue date", "dated", "bill date", "invoice date")
    private val START_KEYWORDS = listOf("start date", "date of joining", "commencing from")
    private val END_KEYWORDS = listOf("end date", "relieving date", "completion date")

    fun extractDatesAndDeadlines(text: String, sourcePath: String = ""): List<ExtractedDateRecord> {
        val records = mutableListOf<ExtractedDateRecord>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for ((lineNo, line) in lines.withIndex()) {
            val lowerLine = line.lowercase()
            val matcher = DATE_PATTERN.matcher(line)

            while (matcher.find()) {
                val dateStr = matcher.group()
                val role = determineDateRole(lowerLine)
                val isDeadline = (role == DateSemanticRole.DUE_DATE || role == DateSemanticRole.EXPIRY_DATE)

                records.add(
                    ExtractedDateRecord(
                        rawText = dateStr,
                        normalizedDateStr = dateStr,
                        semanticRole = role,
                        isDeadline = isDeadline,
                        evidenceText = line,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = lineNo + 1)
                    )
                )
            }
        }

        return records
    }

    private fun determineDateRole(lowerLine: String): DateSemanticRole {
        return when {
            DUE_KEYWORDS.any { lowerLine.contains(it) } -> DateSemanticRole.DUE_DATE
            EXPIRY_KEYWORDS.any { lowerLine.contains(it) } -> DateSemanticRole.EXPIRY_DATE
            ISSUE_KEYWORDS.any { lowerLine.contains(it) } -> DateSemanticRole.ISSUE_DATE
            START_KEYWORDS.any { lowerLine.contains(it) } -> DateSemanticRole.START_DATE
            END_KEYWORDS.any { lowerLine.contains(it) } -> DateSemanticRole.END_DATE
            else -> DateSemanticRole.UNKNOWN_DATE
        }
    }
}
