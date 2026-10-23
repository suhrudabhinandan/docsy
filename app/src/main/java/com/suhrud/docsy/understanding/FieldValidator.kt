package com.suhrud.docsy.understanding

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

data class ValidationResult(
    val isValid: Boolean,
    val normalizedValue: String?,
    val confidencePenalty: Float = 0.0f,
    val needsReview: Boolean = false,
    val failureReason: String? = null
)

object FieldValidator {

    private val SYNONYM_MAP = mapOf(
        "percent" to "percentage",
        "%" to "percentage",
        "pct" to "percentage",
        "aggregate_percent" to "percentage",
        "date_of_birth" to "dob",
        "birth_date" to "dob",
        "roll_no" to "roll_number",
        "rollnum" to "roll_number",
        "total_marks" to "total_marks",
        "grand_total" to "total_marks",
        "candidate_name" to "student_name",
        "name_of_candidate" to "student_name"
    )

    fun resolveSynonymKey(key: String): String {
        val clean = key.lowercase(Locale.ROOT).trim().replace(" ", "_")
        return SYNONYM_MAP[clean] ?: clean
    }

    fun validateAadhaar(value: String): ValidationResult {
        val clean = value.replace(Regex("""[\s\-]"""), "")
        val isValid = VerhoeffValidator.validateAadhaar(clean)
        return if (isValid) {
            ValidationResult(
                isValid = true,
                normalizedValue = "${clean.substring(0, 4)} ${clean.substring(4, 8)} ${clean.substring(8, 12)}",
                confidencePenalty = 0.0f,
                needsReview = false
            )
        } else {
            ValidationResult(
                isValid = false,
                normalizedValue = value, // Never silently fix values
                confidencePenalty = 0.5f,
                needsReview = true,
                failureReason = "Invalid Aadhaar 12-digit format or Verhoeff checksum"
            )
        }
    }

    fun validatePan(value: String): ValidationResult {
        val clean = value.trim().uppercase(Locale.ROOT)
        val regex = Regex("""^[A-Z]{5}[0-9]{4}[A-Z]$""")
        val isValid = regex.matches(clean)
        return if (isValid) {
            ValidationResult(
                isValid = true,
                normalizedValue = clean,
                confidencePenalty = 0.0f,
                needsReview = false
            )
        } else {
            ValidationResult(
                isValid = false,
                normalizedValue = value,
                confidencePenalty = 0.5f,
                needsReview = true,
                failureReason = "Invalid PAN 10-character alphanumeric pattern"
            )
        }
    }

    fun validateIfsc(value: String): ValidationResult {
        val clean = value.trim().uppercase(Locale.ROOT)
        val regex = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""")
        val isValid = regex.matches(clean)
        return if (isValid) {
            ValidationResult(
                isValid = true,
                normalizedValue = clean,
                confidencePenalty = 0.0f,
                needsReview = false
            )
        } else {
            ValidationResult(
                isValid = false,
                normalizedValue = value,
                confidencePenalty = 0.5f,
                needsReview = true,
                failureReason = "Invalid IFSC code format"
            )
        }
    }

    fun validateDate(value: String): ValidationResult {
        val clean = value.trim()
        val formats = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd.MM.yyyy",
            "d/M/yyyy", "d-M-yyyy", "dd MMM yyyy", "dd MMMM yyyy"
        )

        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH).apply { isLenient = false }
                val parsed = sdf.parse(clean)
                if (parsed != null) {
                    val outSdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    return ValidationResult(
                        isValid = true,
                        normalizedValue = outSdf.format(parsed),
                        confidencePenalty = 0.0f,
                        needsReview = false
                    )
                }
            } catch (_: Exception) {}
        }

        return ValidationResult(
            isValid = false,
            normalizedValue = value,
            confidencePenalty = 0.4f,
            needsReview = true,
            failureReason = "Unparseable or invalid date"
        )
    }

    fun validateAmount(value: String): ValidationResult {
        val clean = value.replace(Regex("""[₹$,\s]"""), "")
        val bd = clean.toBigDecimalOrNull()
        return if (bd != null && bd.compareTo(BigDecimal.ZERO) >= 0) {
            ValidationResult(
                isValid = true,
                normalizedValue = bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                confidencePenalty = 0.0f,
                needsReview = false
            )
        } else {
            ValidationResult(
                isValid = false,
                normalizedValue = value,
                confidencePenalty = 0.4f,
                needsReview = true,
                failureReason = "Invalid currency amount format"
            )
        }
    }

    fun validateMarksSum(obtainedSum: BigDecimal, declaredTotal: BigDecimal): ValidationResult {
        val isMatch = obtainedSum.compareTo(declaredTotal) == 0
        return if (isMatch) {
            ValidationResult(
                isValid = true,
                normalizedValue = obtainedSum.toPlainString(),
                confidencePenalty = 0.0f,
                needsReview = false
            )
        } else {
            ValidationResult(
                isValid = false,
                normalizedValue = obtainedSum.toPlainString(),
                confidencePenalty = 0.5f,
                needsReview = true,
                failureReason = "Sum of subject marks ($obtainedSum) does not match total ($declaredTotal)"
            )
        }
    }
}
