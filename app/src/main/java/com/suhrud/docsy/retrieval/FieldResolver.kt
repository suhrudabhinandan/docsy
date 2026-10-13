package com.suhrud.docsy.retrieval

enum class DocumentField {
    PERCENTAGE,
    CGPA,
    ROLL_NUMBER,
    DATE_OF_BIRTH,
    ID_NUMBER,
    FATHER_NAME,
    MOTHER_NAME,
    CANDIDATE_NAME,
    TOTAL_MARKS,
    GRADE,
    ISSUE_DATE,
    EXPIRY_DATE,
    BILL_AMOUNT,
    BILL_DUE_DATE,
    BILL_NUMBER,
    ANY_FIELD
}

/**
 * Resolves exact requested fields without assumption or hallucination.
 * Satisfies Phase 9.10 in AUDIT.md.
 */
object FieldResolver {

    fun resolveTargetField(query: String): DocumentField? {
        val lower = query.lowercase().trim()

        return when {
            lower.contains("percentage") || lower.contains("percent") || lower.contains("%") ->
                DocumentField.PERCENTAGE
            lower.contains("cgpa") || lower.contains("gpa") ->
                DocumentField.CGPA
            lower.contains("roll number") || lower.contains("roll no") || lower.contains("rollnum") ->
                DocumentField.ROLL_NUMBER
            lower.contains("date of birth") || lower.contains("dob") || lower.contains("birthday") || lower.contains("birth date") ->
                DocumentField.DATE_OF_BIRTH
            lower.contains("father's name") || lower.contains("father name") || lower.contains("father") ->
                DocumentField.FATHER_NAME
            lower.contains("mother's name") || lower.contains("mother name") || lower.contains("mother") ->
                DocumentField.MOTHER_NAME
            lower.contains("total marks") || lower.contains("marks obtained") || lower.contains("score") ->
                DocumentField.TOTAL_MARKS
            lower.contains("grade") ->
                DocumentField.GRADE
            lower.contains("expiry date") || lower.contains("valid till") || lower.contains("expires") ->
                DocumentField.EXPIRY_DATE
            lower.contains("issue date") || lower.contains("issued on") ->
                DocumentField.ISSUE_DATE
            lower.contains("amount") || lower.contains("bill amount") || lower.contains("due amount") || lower.contains("total amount") ->
                DocumentField.BILL_AMOUNT
            lower.contains("due date") ->
                DocumentField.BILL_DUE_DATE
            lower.contains("bill number") || lower.contains("invoice number") ->
                DocumentField.BILL_NUMBER
            lower.contains("number") || lower.contains("no.") || lower.contains("id") ->
                DocumentField.ID_NUMBER
            else -> null
        }
    }
}
