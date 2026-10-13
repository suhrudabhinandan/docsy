package com.suhrud.docsy.retrieval

/**
 * Query normalizer providing token cleaning, Hinglish colloquial mapping,
 * and spelling correction against internal vocabularies.
 * Satisfies Phase 9.3 in AUDIT.md.
 */
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

    private val SPELLING_CORRECTIONS = mapOf(
        "adhar" to "aadhaar",
        "aadhar" to "aadhaar",
        "aadhr" to "aadhaar",
        "adhhar" to "aadhaar",
        "pancard" to "pan card",
        "markshet" to "marksheet",
        "marksheet" to "marksheet",
        "marklist" to "marksheet",
        "markssheet" to "marksheet",
        "resum" to "resume",
        "resme" to "resume",
        "biodata" to "resume",
        "passprt" to "passport",
        "passpost" to "passport",
        "votercard" to "voter id",
        "batry" to "battery",
        "battry" to "battery",
        "batteri" to "battery",
        "wif" to "wifi",
        "wi-fi" to "wifi",
        "blutooth" to "bluetooth",
        "blutoth" to "bluetooth",
        "stpes" to "steps",
        "contacs" to "contacts",
        "contct" to "contact",
        "vedios" to "videos",
        "vids" to "videos"
    )

    /**
     * Normalizes a query string by replacing Hinglish phrases and correcting common misspellings.
     */
    fun normalize(rawQuery: String): String {
        var query = rawQuery.trim().lowercase()
        if (query.isBlank()) return ""

        // Strip extraneous punctuation except math symbols
        query = query.replace(Regex("""[?!,;:"'()]"""), " ")
        query = query.replace(Regex("""\s+"""), " ").trim()

        // Apply multi-word Hinglish phrase mappings
        for ((hinglish, english) in HINGLISH_PHRASE_MAPPINGS) {
            query = query.replace(hinglish, english)
        }

        // Apply single-word spelling corrections
        val words = query.split(" ").map { word ->
            SPELLING_CORRECTIONS[word] ?: word
        }

        return words.joinToString(" ").trim()
    }
}
