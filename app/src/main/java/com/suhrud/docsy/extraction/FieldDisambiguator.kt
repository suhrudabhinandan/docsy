package com.suhrud.docsy.extraction

import com.suhrud.docsy.extractor.models.ProvenanceInfo
import java.util.regex.Pattern

data class DisambiguatedField(
    val fieldType: String,
    val rawValue: String,
    val normalizedValue: String,
    val confidence: Float,
    val isAmbiguous: Boolean,
    val provenance: ProvenanceInfo? = null,
    val supportingLabel: String? = null,
    val alternativeTypes: List<String> = emptyList()
)

object FieldDisambiguator {

    private val PHONE_PATTERN = Pattern.compile("""(?<!\d)[6-9]\d{9}(?!\d)""")
    private val EMAIL_PATTERN = Pattern.compile("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}""")
    private val CURRENCY_AMOUNT_PATTERN = Pattern.compile("""(₹|\$|EUR|GBP|USD|INR|Rs\.?)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{2})?|\d+(?:\.\d{2})?)""", Pattern.CASE_INSENSITIVE)

    private val STUDENT_NAME_LABELS = listOf("student name", "student's name", "candidate name", "candidate's name", "name of candidate", "name of student", "name:")
    private val FATHER_NAME_LABELS = listOf("father's name", "father name", "s/o", "d/o", "w/o", "parent's name", "parent name", "guardian's name", "guardian name")
    private val MOTHER_NAME_LABELS = listOf("mother's name", "mother name", "c/o mother")

    private val ROLL_NO_LABELS = listOf("roll no", "roll number", "roll_no", "rollno", "hall ticket no", "seat no")
    private val REG_NO_LABELS = listOf("reg no", "registration no", "registration number", "reg_no", "reg. no", "enrollment no", "enrolment no")
    private val PHONE_LABELS = listOf("phone", "phone no", "mobile", "mobile no", "contact no", "tel")

    private val DOB_LABELS = listOf("date of birth", "dob", "birth date", "born on")
    private val ISSUE_DATE_LABELS = listOf("date of issue", "issue date", "dated", "bill date", "invoice date")

    /**
     * Extracts and disambiguates fields from document text or structured blocks.
     */
    fun disambiguateText(
        text: String,
        sourcePath: String = "",
        documentType: String = ""
    ): List<DisambiguatedField> {
        val fields = mutableListOf<DisambiguatedField>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for ((lineIdx, line) in lines.withIndex()) {
            val lowerLine = line.lowercase()

            // 1. Name Disambiguation: Student vs Father vs Mother
            extractNamesFromLine(line, lowerLine, lineIdx + 1, sourcePath)?.let { fields.addAll(it) }

            // 2. Identifier Disambiguation: Roll No vs Registration No vs Phone No
            extractIdentifiersFromLine(line, lowerLine, lineIdx + 1, sourcePath)?.let { fields.addAll(it) }

            // 3. Email Extraction
            val emailMatcher = EMAIL_PATTERN.matcher(line)
            while (emailMatcher.find()) {
                val email = emailMatcher.group()
                fields.add(
                    DisambiguatedField(
                        fieldType = "EMAIL",
                        rawValue = email,
                        normalizedValue = email.lowercase(),
                        confidence = 0.98f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = 1, startCharOffset = emailMatcher.start(), endCharOffset = emailMatcher.end()),
                        supportingLabel = "email_regex"
                    )
                )
            }

            // 4. Currency Amount Disambiguation
            val amtMatcher = CURRENCY_AMOUNT_PATTERN.matcher(line)
            while (amtMatcher.find()) {
                val symbol = amtMatcher.group(1) ?: ""
                val amountStr = amtMatcher.group(2) ?: ""
                val normVal = "$symbol $amountStr".trim()
                fields.add(
                    DisambiguatedField(
                        fieldType = "AMOUNT",
                        rawValue = amtMatcher.group(),
                        normalizedValue = normVal,
                        confidence = 0.92f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = 1),
                        supportingLabel = "currency_regex"
                    )
                )
            }

            // 5. Date Disambiguation: Birth Date vs Document Date
            extractDatesFromLine(line, lowerLine, lineIdx + 1, sourcePath)?.let { fields.addAll(it) }
        }

        return fields
    }

    private fun extractNamesFromLine(
        line: String,
        lowerLine: String,
        pageNo: Int,
        sourcePath: String
    ): List<DisambiguatedField>? {
        val result = mutableListOf<DisambiguatedField>()

        // Check Father/Mother/Guardian labels FIRST to prevent false assignment to Student
        val fatherLabel = FATHER_NAME_LABELS.firstOrNull { lowerLine.contains(it) }
        if (fatherLabel != null) {
            val nameVal = extractValueAfterLabel(line, fatherLabel)
            if (isValidNameValue(nameVal)) {
                result.add(
                    DisambiguatedField(
                        fieldType = "PARENT_NAME",
                        rawValue = nameVal,
                        normalizedValue = nameVal.uppercase(),
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = fatherLabel
                    )
                )
            }
            return result
        }

        val motherLabel = MOTHER_NAME_LABELS.firstOrNull { lowerLine.contains(it) }
        if (motherLabel != null) {
            val nameVal = extractValueAfterLabel(line, motherLabel)
            if (isValidNameValue(nameVal)) {
                result.add(
                    DisambiguatedField(
                        fieldType = "MOTHER_NAME",
                        rawValue = nameVal,
                        normalizedValue = nameVal.uppercase(),
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = motherLabel
                    )
                )
            }
            return result
        }

        // Check Student / Candidate labels
        val studentLabel = STUDENT_NAME_LABELS.firstOrNull { lowerLine.contains(it) }
        if (studentLabel != null) {
            val nameVal = extractValueAfterLabel(line, studentLabel)
            if (isValidNameValue(nameVal)) {
                result.add(
                    DisambiguatedField(
                        fieldType = "STUDENT_NAME",
                        rawValue = nameVal,
                        normalizedValue = nameVal.uppercase(),
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = studentLabel
                    )
                )
            }
        }

        return if (result.isNotEmpty()) result else null
    }

    private fun extractIdentifiersFromLine(
        line: String,
        lowerLine: String,
        pageNo: Int,
        sourcePath: String
    ): List<DisambiguatedField>? {
        val result = mutableListOf<DisambiguatedField>()

        val rollLabel = ROLL_NO_LABELS.firstOrNull { lowerLine.contains(it) }
        if (rollLabel != null) {
            val valStr = extractValueAfterLabel(line, rollLabel)
            if (valStr.isNotBlank()) {
                result.add(
                    DisambiguatedField(
                        fieldType = "ROLL_NUMBER",
                        rawValue = valStr,
                        normalizedValue = valStr.uppercase(),
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = rollLabel
                    )
                )
                return result
            }
        }

        val regLabel = REG_NO_LABELS.firstOrNull { lowerLine.contains(it) }
        if (regLabel != null) {
            val valStr = extractValueAfterLabel(line, regLabel)
            if (valStr.isNotBlank()) {
                result.add(
                    DisambiguatedField(
                        fieldType = "REGISTRATION_NUMBER",
                        rawValue = valStr,
                        normalizedValue = valStr.uppercase(),
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = regLabel
                    )
                )
                return result
            }
        }

        val phoneLabel = PHONE_LABELS.firstOrNull { lowerLine.contains(it) }
        if (phoneLabel != null) {
            val valStr = extractValueAfterLabel(line, phoneLabel)
            val phoneMatch = PHONE_PATTERN.matcher(valStr)
            if (phoneMatch.find()) {
                val num = phoneMatch.group()
                result.add(
                    DisambiguatedField(
                        fieldType = "PHONE_NUMBER",
                        rawValue = num,
                        normalizedValue = "+91$num",
                        confidence = 0.95f,
                        isAmbiguous = false,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        supportingLabel = phoneLabel
                    )
                )
                return result
            }
        }

        // Unlabeled 10-digit number disambiguation
        val phoneMatch = PHONE_PATTERN.matcher(line)
        while (phoneMatch.find()) {
            val num = phoneMatch.group()
            // If line contains roll/reg keywords elsewhere, do NOT label as phone
            val containsRollReg = ROLL_NO_LABELS.any { lowerLine.contains(it) } || REG_NO_LABELS.any { lowerLine.contains(it) }
            if (!containsRollReg) {
                result.add(
                    DisambiguatedField(
                        fieldType = "PHONE_NUMBER",
                        rawValue = num,
                        normalizedValue = "+91$num",
                        confidence = 0.60f, // Lower confidence due to lack of explicit label
                        isAmbiguous = true,
                        provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                        alternativeTypes = listOf("ROLL_NUMBER", "REGISTRATION_NUMBER")
                    )
                )
            }
        }

        return if (result.isNotEmpty()) result else null
    }

    private fun extractDatesFromLine(
        line: String,
        lowerLine: String,
        pageNo: Int,
        sourcePath: String
    ): List<DisambiguatedField>? {
        val datePattern = Pattern.compile("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")
        val matcher = datePattern.matcher(line)
        if (!matcher.find()) return null

        val dateStr = matcher.group()
        val dobLabel = DOB_LABELS.firstOrNull { lowerLine.contains(it) }
        val issueLabel = ISSUE_DATE_LABELS.firstOrNull { lowerLine.contains(it) }

        val type = when {
            dobLabel != null -> "BIRTH_DATE"
            issueLabel != null -> "ISSUE_DATE"
            else -> "DOCUMENT_DATE"
        }

        return listOf(
            DisambiguatedField(
                fieldType = type,
                rawValue = dateStr,
                normalizedValue = dateStr,
                confidence = if (dobLabel != null || issueLabel != null) 0.95f else 0.60f,
                isAmbiguous = dobLabel == null && issueLabel == null,
                provenance = ProvenanceInfo(sourcePath, pageNumber = pageNo),
                supportingLabel = dobLabel ?: issueLabel ?: "unlabeled_date"
            )
        )
    }

    private fun extractValueAfterLabel(line: String, label: String): String {
        val lower = line.lowercase()
        val idx = lower.indexOf(label)
        if (idx == -1) return ""
        val remainder = line.substring(idx + label.length).trim()
        val clean = remainder.replace(Regex("""^[:\-.\s]+"""), "").trim()
        return clean.split(Regex("""\s{2,}""")).firstOrNull() ?: clean
    }

    private fun isValidNameValue(valStr: String): Boolean {
        val clean = valStr.trim()
        if (clean.length < 3 || clean.length > 40) return false
        if (clean.any { it.isDigit() }) return false
        return clean.split(Regex("""\s+""")).size in 1..5
    }
}
