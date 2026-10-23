package com.suhrud.docsy.extraction

import com.suhrud.docsy.data.db.entity.FieldEntity

/**
 * Self-contained "Label: Value" extractor used to give EVERY document a structured Field | Value table.
 *
 * - Works on the already-cleaned extracted text (no file names, no per-type word lists).
 * - Never invents values: every row is a literal label/value pair found on one line.
 * - Output feeds the `fields` table, which the Extraction Inspector and chat retrieval read.
 */
object LabelValueExtractor {

    private const val MAX_FIELDS = 60
    private const val MAX_LINES = 600

    private val DATE = Regex("""^\d{1,2}[-/. ](\d{1,2}|[A-Za-z]{3,9})[-/. ]\d{2,4}$""")
    private val AMOUNT = Regex("""^(?:₹|rs\.?|inr)?\s*\d[\d,]*(?:\.\d{1,2})?$""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""^-?\d+(?:\.\d+)?$""")
    private val ID_LIKE = Regex("""^[A-Za-z0-9][A-Za-z0-9 /-]{5,24}$""")

    fun extract(documentId: Long, text: String): List<FieldEntity> {
        val out = mutableListOf<FieldEntity>()
        val seen = HashMap<String, Int>()
        var lineNo = 0
        for (rawLine in text.lineSequence()) {
            lineNo++
            if (lineNo > MAX_LINES || out.size >= MAX_FIELDS) break
            val line = rawLine.trim()
            if (line.length < 4 || line.length > 300) continue
            val sep = line.indexOf(':')
            if (sep < 2) continue
            val label = line.substring(0, sep).trim().trimEnd('.', '-', '*')
            val value = line.substring(sep + 1).trim()
            if (label.length !in 2..40 || value.isEmpty() || value.length > 200) continue
            if (!label.any { it.isLetter() }) continue
            // Skip OCR garbage (e.g. Devanagari read as Latin): needs mostly letters/spaces, vowels, and no odd symbols.
            val letters = label.count { it.isLetter() }
            if (letters.toFloat() / label.length < 0.7f) continue
            if (label.none { it.lowercaseChar() in "aeiou" }) continue
            if (label.any { it !in ' '..'~' }) continue
            if (label.split(' ').any { w -> w.length > 1 && w.drop(1).any { it.isUpperCase() } && w.any { it.isLowerCase() } }) continue
            val lowerLabel = label.lowercase()
            if (lowerLabel.startsWith("http") || lowerLabel.contains("www") || lowerLabel.contains("//")) continue
            if (label.split(' ').size > 6) continue

            var key = lowerLabel.replace(Regex("""[^a-z0-9]+"""), "_").trim('_')
            if (key.isEmpty()) continue
            val n = (seen[key] ?: 0) + 1
            seen[key] = n
            if (n > 1) key = "${key}_$n"

            val valueType = when {
                DATE.matches(value) -> "date"
                AMOUNT.matches(value) && value.any { it.isDigit() } && (value.contains(',') || value.contains('.') || lowerLabel.contains("amount") || lowerLabel.contains("total")) -> "amount"
                NUMBER.matches(value) -> "number"
                ID_LIKE.matches(value) && value.any { it.isDigit() } && value.any { it.isLetter() || value.length >= 8 } -> "id"
                else -> "text"
            }
            out.add(
                FieldEntity(
                    documentId = documentId,
                    key = key,
                    label = label,
                    value = value,
                    valueType = valueType,
                    lineNo = lineNo,
                    confidence = 0.7f
                )
            )
        }
        return out
    }
}
