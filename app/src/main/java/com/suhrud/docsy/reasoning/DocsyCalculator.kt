package com.suhrud.docsy.reasoning

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Date

data class SubjectMark(
    val subjectName: String,
    val marksObtained: BigDecimal,
    val maxMarks: BigDecimal = BigDecimal("100")
)

data class CalculationAudit(
    val operation: String,
    val formula: String,
    val inputs: List<String>,
    val result: BigDecimal,
    val formattedResult: String
)

data class MarksheetCalculation(
    val totalObtained: BigDecimal,
    val totalMaximum: BigDecimal,
    val percentage: BigDecimal,
    val subjectCount: Int,
    val auditTrail: CalculationAudit
)

data class AttributeComparison(
    val attribute: String,
    val valueA: BigDecimal,
    val valueB: BigDecimal,
    val difference: BigDecimal,
    val percentageChange: BigDecimal
)

/**
 * Deterministic Kotlin BigDecimal calculator with half-up rounding and formula audit trail.
 * Satisfies Phase 10.1, 10.2, and 10.3 in AUDIT.md.
 */
object DocsyCalculator {

    private val TWO_DECIMALS = 2
    private val ROUNDING_MODE = RoundingMode.HALF_UP

    /**
     * Sum of numbers with audit trail.
     */
    fun sum(numbers: List<BigDecimal>): CalculationAudit {
        val total = numbers.fold(BigDecimal.ZERO) { acc, num -> acc.add(num) }
        val formula = numbers.joinToString(" + ") { it.toPlainString() }
        return CalculationAudit(
            operation = "SUM",
            formula = formula,
            inputs = numbers.map { it.toPlainString() },
            result = total.setScale(TWO_DECIMALS, ROUNDING_MODE),
            formattedResult = total.setScale(TWO_DECIMALS, ROUNDING_MODE).toPlainString()
        )
    }

    /**
     * Arithmetic mean / average with half-up rounding.
     */
    fun average(numbers: List<BigDecimal>): CalculationAudit {
        if (numbers.isEmpty()) {
            return CalculationAudit("AVERAGE", "0 / 0", emptyList(), BigDecimal.ZERO, "0.00")
        }
        val sum = numbers.fold(BigDecimal.ZERO) { acc, num -> acc.add(num) }
        val count = BigDecimal(numbers.size)
        val avg = sum.divide(count, TWO_DECIMALS, ROUNDING_MODE)
        val formula = "(${numbers.joinToString(" + ") { it.toPlainString() }}) / ${numbers.size}"

        return CalculationAudit(
            operation = "AVERAGE",
            formula = formula,
            inputs = numbers.map { it.toPlainString() },
            result = avg,
            formattedResult = avg.toPlainString()
        )
    }

    /**
     * Difference: A - B
     */
    fun difference(a: BigDecimal, b: BigDecimal): CalculationAudit {
        val diff = a.subtract(b).setScale(TWO_DECIMALS, ROUNDING_MODE)
        return CalculationAudit(
            operation = "DIFFERENCE",
            formula = "${a.toPlainString()} - ${b.toPlainString()}",
            inputs = listOf(a.toPlainString(), b.toPlainString()),
            result = diff,
            formattedResult = diff.toPlainString()
        )
    }

    /**
     * Percentage: (obtained / total) * 100
     */
    fun percentage(obtained: BigDecimal, total: BigDecimal): CalculationAudit {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return CalculationAudit("PERCENTAGE", "0 / 0", emptyList(), BigDecimal.ZERO, "0.00%")
        }
        val pct = obtained
            .multiply(BigDecimal("100"))
            .divide(total, TWO_DECIMALS, ROUNDING_MODE)

        val formula = "(${obtained.toPlainString()} / ${total.toPlainString()}) * 100"
        return CalculationAudit(
            operation = "PERCENTAGE",
            formula = formula,
            inputs = listOf(obtained.toPlainString(), total.toPlainString()),
            result = pct,
            formattedResult = "${pct.toPlainString()}%"
        )
    }

    /**
     * Exact age calculation from date of birth.
     */
    fun calculateAge(birthDateEpoch: Long, referenceDateEpoch: Long = System.currentTimeMillis()): Int {
        val birth = Calendar.getInstance().apply { time = Date(birthDateEpoch) }
        val now = Calendar.getInstance().apply { time = Date(referenceDateEpoch) }

        var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age.coerceAtLeast(0)
    }

    /**
     * Marksheet percentage with optional "Best 5" subject selection (Phase 10.2).
     */
    fun calculateMarksheetPercentage(
        subjects: List<SubjectMark>,
        bestFiveOnly: Boolean = false
    ): MarksheetCalculation {
        if (subjects.isEmpty()) {
            return MarksheetCalculation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                CalculationAudit("MARKSHEET", "0/0", emptyList(), BigDecimal.ZERO, "0.00%")
            )
        }

        val evaluatedSubjects = if (bestFiveOnly && subjects.size > 5) {
            subjects.sortedByDescending { it.marksObtained.divide(it.maxMarks, 4, ROUNDING_MODE) }.take(5)
        } else {
            subjects
        }

        val totalObtained = evaluatedSubjects.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.marksObtained) }
        val totalMax = evaluatedSubjects.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.maxMarks) }
        val audit = percentage(totalObtained, totalMax)

        return MarksheetCalculation(
            totalObtained = totalObtained.setScale(TWO_DECIMALS, ROUNDING_MODE),
            totalMaximum = totalMax.setScale(TWO_DECIMALS, ROUNDING_MODE),
            percentage = audit.result,
            subjectCount = evaluatedSubjects.size,
            auditTrail = audit
        )
    }

    /**
     * Multi-attribute comparison between two documents (Phase 10.3).
     */
    fun compareAttributes(
        docAAttributes: Map<String, BigDecimal>,
        docBAttributes: Map<String, BigDecimal>
    ): List<AttributeComparison> {
        val commonKeys = docAAttributes.keys.intersect(docBAttributes.keys)
        return commonKeys.map { key ->
            val valA = docAAttributes[key] ?: BigDecimal.ZERO
            val valB = docBAttributes[key] ?: BigDecimal.ZERO
            val diff = valB.subtract(valA).setScale(TWO_DECIMALS, ROUNDING_MODE)

            val pctChange = if (valA.compareTo(BigDecimal.ZERO) != 0) {
                diff.multiply(BigDecimal("100")).divide(valA, TWO_DECIMALS, ROUNDING_MODE)
            } else {
                BigDecimal.ZERO
            }

            AttributeComparison(
                attribute = key,
                valueA = valA,
                valueB = valB,
                difference = diff,
                percentageChange = pctChange
            )
        }
    }
}
