package com.suhrud.docsy.understanding

data class ExtractedOwner(
    val ownerName: String,
    val confidence: Float,
    val sourceLine: String,
    val relation: String? = null
)

/**
 * Grounded document owner extraction from OCR text.
 * Finds candidate owner names from certificates, marksheets, IDs, CVs, and bills.
 * Satisfies Phase 7.6 in AUDIT.md.
 */
object OwnerExtractor {

    private val OWNER_PREFIXES = listOf(
        "candidate's name",
        "candidate name",
        "student's name",
        "student name",
        "name of the candidate",
        "name of the student",
        "name of candidate",
        "consumer name",
        "subscriber name",
        "name of holder",
        "cardholder name",
        "name:"
    )

    private val RELATION_PREFIXES = listOf(
        "father's name",
        "father name",
        "mother's name",
        "mother name",
        "husband's name",
        "guardian's name"
    )

    /**
     * Extracts the primary owner and any recorded relations from document text.
     */
    fun extractOwner(text: String, documentType: String = ""): ExtractedOwner? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // 1. Look for explicit owner prefixes
        for (line in lines) {
            val lowerLine = line.lowercase()
            for (prefix in OWNER_PREFIXES) {
                if (lowerLine.startsWith(prefix) || lowerLine.contains("$prefix:")) {
                    val rawName = cleanExtractedNameLine(line, prefix)
                    if (isValidName(rawName)) {
                        val normName = NameMatcher.normalizeName(rawName).uppercase()
                        return ExtractedOwner(
                            ownerName = if (normName.isNotBlank()) normName else rawName,
                            confidence = 0.95f,
                            sourceLine = line
                        )
                    }
                }
            }
        }

        // 2. Look for "This is to certify that [Name]" pattern
        val certifyPattern = Regex("""certify that\s+([A-Z][a-zA-Z\s]{3,35})\b""", RegexOption.IGNORE_CASE)
        val certifyMatch = certifyPattern.find(text)
        if (certifyMatch != null) {
            val candidate = certifyMatch.groupValues[1].trim()
            if (isValidName(candidate)) {
                val normName = NameMatcher.normalizeName(candidate).uppercase()
                return ExtractedOwner(
                    ownerName = if (normName.isNotBlank()) normName else candidate,
                    confidence = 0.90f,
                    sourceLine = certifyMatch.value
                )
            }
        }

        // 3. For CV: the top 1-3 lines usually contain the person's name
        if (documentType == "CV") {
            for (i in 0 until minOf(5, lines.size)) {
                val line = lines[i]
                if (line.lowercase().contains("curriculum vitae") || line.lowercase().contains("resume")) {
                    continue
                }
                if (isValidName(line) && line.length in 4..35 && line.split(" ").size in 2..4) {
                    val normName = NameMatcher.normalizeName(line).uppercase()
                    return ExtractedOwner(
                        ownerName = if (normName.isNotBlank()) normName else line,
                        confidence = 0.85f,
                        sourceLine = line
                    )
                }
            }
        }

        // 4. Aadhaar / PAN specific heuristics
        for (i in 0 until lines.size) {
            val line = lines[i]
            if (line.contains("Government of India") || line.contains("INCOME TAX DEPARTMENT")) {
                // Often the name is within the next 3 lines
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val candidate = lines[i + j]
                        if (isValidName(candidate) && candidate.split(" ").size in 2..4) {
                            val normName = NameMatcher.normalizeName(candidate).uppercase()
                            return ExtractedOwner(
                                ownerName = if (normName.isNotBlank()) normName else candidate,
                                confidence = 0.80f,
                                sourceLine = candidate
                            )
                        }
                    }
                }
            }
        }

        return null
    }

    private fun cleanExtractedNameLine(line: String, prefix: String): String {
        var clean = line
        val colonIdx = clean.indexOf(':')
        clean = if (colonIdx != -1 && colonIdx < clean.length - 1) {
            clean.substring(colonIdx + 1).trim()
        } else {
            val idx = clean.lowercase().indexOf(prefix)
            if (idx != -1) {
                clean.substring(idx + prefix.length).trim()
            } else clean
        }
        return clean.replace(Regex("""^[:\-.\s]+"""), "").trim()
    }

    private fun isValidName(name: String): Boolean {
        val clean = name.trim()
        if (clean.length < 3 || clean.length > 40) return false
        if (clean.any { it.isDigit() }) return false
        val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size !in 1..5) return false
        val nonLetterCount = clean.count { !it.isLetter() && !it.isWhitespace() }
        return nonLetterCount <= 2
    }
}
