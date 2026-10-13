package com.suhrud.docsy.understanding

data class DetectedId(
    val type: String,
    val rawValue: String,
    val normalizedValue: String,
    val isValid: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Indian Government IDs detector and validators.
 * Includes Verhoeff checksum algorithm for 12-digit Aadhaar numbers,
 * PAN pattern and entity category checking, EPIC, Passport, DL, and ABHA validation.
 * Satisfies Phase 7.3 in AUDIT.md.
 */
object GovernmentIdDetector {

    // Verhoeff multiplication table (d)
    private val d = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )

    // Verhoeff permutation table (p)
    private val p = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    // Verhoeff inverse table (inv)
    private val inv = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    /**
     * Validates a number using the Verhoeff checksum algorithm.
     */
    fun validateVerhoeff(numStr: String): Boolean {
        var c = 0
        val cleanStr = numStr.filter { it.isDigit() }
        if (cleanStr.isEmpty()) return false
        val len = cleanStr.length
        for (i in 0 until len) {
            val digit = cleanStr[len - 1 - i].digitToInt()
            c = d[c][p[i % 8][digit]]
        }
        return c == 0
    }

    /**
     * Calculates the Verhoeff checksum digit for an 11-digit number.
     */
    fun generateVerhoeffDigit(numStr: String): Int {
        var c = 0
        val cleanStr = numStr.filter { it.isDigit() }
        val len = cleanStr.length
        for (i in 0 until len) {
            val digit = cleanStr[len - 1 - i].digitToInt()
            c = d[c][p[(i + 1) % 8][digit]]
        }
        return inv[c]
    }

    /**
     * Validates an Aadhaar number:
     * - Must be exactly 12 digits
     * - Must not start with 0 or 1
     * - Must satisfy Verhoeff checksum
     */
    fun validateAadhaar(raw: String): Boolean {
        val clean = raw.replace(" ", "").replace("-", "")
        if (clean.length != 12 || !clean.all { it.isDigit() }) return false
        if (clean[0] == '0' || clean[0] == '1') return false
        return validateVerhoeff(clean)
    }

    private val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")

    fun validatePan(raw: String): Boolean {
        val clean = raw.trim().uppercase()
        if (!PAN_REGEX.matches(clean)) return false
        val entityChar = clean[3]
        val validEntities = setOf('P', 'C', 'H', 'F', 'A', 'T', 'B', 'L', 'J', 'G', 'D')
        return entityChar in validEntities
    }

    private val VOTER_ID_REGEX = Regex("^[A-Z]{3}[0-9]{7}$")

    fun validateVoterId(raw: String): Boolean {
        val clean = raw.trim().uppercase().replace("/", "").replace("-", "")
        return VOTER_ID_REGEX.matches(clean)
    }

    private val PASSPORT_REGEX = Regex("^[A-Za-z][1-9][0-9]{6}$")

    fun validateIndianPassport(raw: String): Boolean {
        val clean = raw.trim().uppercase()
        return PASSPORT_REGEX.matches(clean)
    }

    private val DL_REGEX = Regex("^[A-Z]{2}[0-9]{2}[ -]?[0-9]{4}[ -]?[0-9]{7}$")

    fun validateDrivingLicense(raw: String): Boolean {
        val clean = raw.trim().uppercase()
        return DL_REGEX.matches(clean) || (clean.length in 14..16 && clean.substring(0, 2).all { it.isLetter() })
    }

    private val ABHA_REGEX = Regex("^[0-9]{2}-?[0-9]{4}-?[0-9]{4}-?[0-9]{4}$")

    fun validateAbha(raw: String): Boolean {
        val clean = raw.trim()
        val digits = clean.filter { it.isDigit() }
        return digits.length == 14 && ABHA_REGEX.matches(clean)
    }

    /**
     * Scans arbitrary text and extracts all detected government IDs.
     */
    fun detectIds(text: String): List<DetectedId> {
        val results = mutableListOf<DetectedId>()

        // 1. Aadhaar detection (matches XXXX XXXX XXXX or 12 digits)
        val aadhaarPattern = Regex("""\b[2-9][0-9]{3}[\s-]?[0-9]{4}[\s-]?[0-9]{4}\b""")
        for (match in aadhaarPattern.findAll(text)) {
            val raw = match.value
            val clean = raw.replace(" ", "").replace("-", "")
            if (clean.length == 12 && validateVerhoeff(clean)) {
                results.add(
                    DetectedId(
                        type = "AADHAAR",
                        rawValue = raw,
                        normalizedValue = "${clean.substring(0, 4)} ${clean.substring(4, 8)} ${clean.substring(8, 12)}",
                        isValid = true
                    )
                )
            }
        }

        // 2. PAN detection
        val panPattern = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""", RegexOption.IGNORE_CASE)
        for (match in panPattern.findAll(text)) {
            val raw = match.value
            val upper = raw.uppercase()
            if (validatePan(upper)) {
                val entityType = when (upper[3]) {
                    'P' -> "Individual"
                    'C' -> "Company"
                    'H' -> "HUF"
                    'F' -> "Firm"
                    'T' -> "Trust"
                    else -> "Other"
                }
                results.add(
                    DetectedId(
                        type = "PAN",
                        rawValue = raw,
                        normalizedValue = upper,
                        isValid = true,
                        metadata = mapOf("entityType" to entityType)
                    )
                )
            }
        }

        // 3. Voter ID / EPIC
        val epicPattern = Regex("""\b[A-Z]{3}[0-9]{7}\b""", RegexOption.IGNORE_CASE)
        for (match in epicPattern.findAll(text)) {
            val upper = match.value.uppercase()
            results.add(
                DetectedId(
                    type = "VOTER_ID",
                    rawValue = match.value,
                    normalizedValue = upper,
                    isValid = true
                )
            )
        }

        // 4. Passport
        val passportPattern = Regex("""\b[A-Za-z][1-9][0-9]{6}\b""")
        for (match in passportPattern.findAll(text)) {
            val upper = match.value.uppercase()
            results.add(
                DetectedId(
                    type = "PASSPORT",
                    rawValue = match.value,
                    normalizedValue = upper,
                    isValid = true
                )
            )
        }

        // 5. ABHA
        val abhaPattern = Regex("""\b[0-9]{2}-[0-9]{4}-[0-9]{4}-[0-9]{4}\b""")
        for (match in abhaPattern.findAll(text)) {
            results.add(
                DetectedId(
                    type = "ABHA",
                    rawValue = match.value,
                    normalizedValue = match.value,
                    isValid = true
                )
            )
        }

        return results
    }
}
