package com.suhrud.docsy.extractor

import java.text.Normalizer
import java.util.regex.Pattern

object TextNormalizer {

    private val MULTI_SPACE = Pattern.compile("[ \\t]+")
    private val MULTI_NEWLINE = Pattern.compile("\\n{3,}")
    private val HYPHENATED_LINE_BREAK = Pattern.compile("(\\w+)-\\n(\\w+)")

    /**
     * Applies Unicode NFC normalization, collapses excessive whitespace,
     * joins hyphenated line breaks, and repairs common OCR digit/letter confusions.
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Unicode NFC normalization
        val nfc = Normalizer.normalize(rawText, Normalizer.Form.NFC)

        // 2. Repair hyphenated line wraps (e.g., "docu-\nment" -> "document")
        val dehyphenated = HYPHENATED_LINE_BREAK.matcher(nfc).replaceAll("$1$2")

        // 3. Clean line feeds and normalize tabs/spaces
        val lines = dehyphenated.lines().map { line ->
            MULTI_SPACE.matcher(line.trim()).replaceAll(" ")
        }

        val cleaned = lines.joinToString("\n")
        return MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n").trim()
    }

    /**
     * Target repairs for numeric and ID sequences where OCR commonly swaps
     * O/0, I/1, B/8, S/5 in numeric contexts.
     */
    fun repairNumericCandidate(candidate: String): String {
        val trimmed = candidate.trim()
        val digitsCount = trimmed.count { it.isDigit() }
        val lettersCount = trimmed.count { it.isLetter() }

        // If predominantly digits, swap obvious letter glyphs
        if (digitsCount > lettersCount && lettersCount > 0) {
            return trimmed
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('S', '5')
                .replace('s', '5')
                .replace('B', '8')
        }
        return trimmed
    }
}
