package com.suhrud.docsy.reasoning

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CalculationOp {
    SUM, AVERAGE, MIN, MAX, COUNT, RATIO, PERCENTAGE, PERCENTAGE_CHANGE, DIFFERENCE, AGE, DAYS_BETWEEN
}

data class CalculationInput(
    val label: String,
    val rawValue: String,
    val bdValue: BigDecimal?,
    val dateValue: LocalDate? = null,
    val needsConfirm: Boolean = false,
    val sourceFile: String? = null
)

data class CalculationResult(
    val operation: CalculationOp,
    val numericResult: BigDecimal?,
    val formattedResult: String,
    val formulaWithInputs: String,
    val assumptions: List<String> = emptyList(),
    val needsUserConfirmation: Boolean = false,
    val promptMessage: String? = null
)

object GenericCalculator {

    fun calculate(
        operation: CalculationOp,
        inputs: List<CalculationInput>
    ): CalculationResult {
        if (inputs.any { it.needsConfirm }) {
            val unconfirmed = inputs.filter { it.needsConfirm }.joinToString(", ") { it.label }
            return CalculationResult(
                operation = operation,
                numericResult = null,
                formattedResult = "Confirmation needed",
                formulaWithInputs = "Blocked by unconfirmed inputs: $unconfirmed",
                needsUserConfirmation = true,
                promptMessage = "Input value(s) for '$unconfirmed' require your confirmation before calculation."
            )
        }

        val validBdInputs = inputs.mapNotNull { it.bdValue }

        return when (operation) {
            CalculationOp.SUM -> {
                if (validBdInputs.isEmpty()) errorResult(operation, "No numeric inputs for SUM")
                else {
                    val sum = validBdInputs.reduce { acc, bd -> acc.add(bd) }.setScale(2, RoundingMode.HALF_UP)
                    val formula = inputs.joinToString(" + ") { "${it.label} (${it.rawValue})" } + " = $sum"
                    CalculationResult(operation, sum, sum.toPlainString(), formula)
                }
            }

            CalculationOp.AVERAGE -> {
                if (validBdInputs.isEmpty()) errorResult(operation, "No numeric inputs for AVERAGE")
                else {
                    val sum = validBdInputs.reduce { acc, bd -> acc.add(bd) }
                    val avg = sum.divide(BigDecimal(validBdInputs.size), 2, RoundingMode.HALF_UP)
                    val formula = "(${inputs.joinToString(" + ") { "${it.label} (${it.rawValue})" }}) / ${validBdInputs.size} = $avg"
                    CalculationResult(operation, avg, avg.toPlainString(), formula)
                }
            }

            CalculationOp.MIN -> {
                val minVal = validBdInputs.minOrNull()?.setScale(2, RoundingMode.HALF_UP)
                CalculationResult(operation, minVal, minVal?.toPlainString() ?: "N/A", "MIN of ${inputs.map { it.rawValue }}")
            }

            CalculationOp.MAX -> {
                val maxVal = validBdInputs.maxOrNull()?.setScale(2, RoundingMode.HALF_UP)
                CalculationResult(operation, maxVal, maxVal?.toPlainString() ?: "N/A", "MAX of ${inputs.map { it.rawValue }}")
            }

            CalculationOp.COUNT -> {
                val countBd = BigDecimal(inputs.size).setScale(2, RoundingMode.HALF_UP)
                CalculationResult(operation, countBd, countBd.toInt().toString(), "COUNT = ${inputs.size}")
            }

            CalculationOp.DIFFERENCE -> {
                if (validBdInputs.size < 2) errorResult(operation, "Requires 2 inputs for DIFFERENCE")
                else {
                    val diff = validBdInputs[0].subtract(validBdInputs[1]).setScale(2, RoundingMode.HALF_UP)
                    val formula = "${inputs[0].label} (${validBdInputs[0]}) - ${inputs[1].label} (${validBdInputs[1]}) = $diff"
                    CalculationResult(operation, diff, diff.toPlainString(), formula)
                }
            }

            CalculationOp.PERCENTAGE -> {
                if (validBdInputs.size < 2 || validBdInputs[1] == BigDecimal.ZERO) {
                    errorResult(operation, "Invalid inputs for PERCENTAGE")
                } else {
                    val pct = validBdInputs[0].multiply(BigDecimal(100)).divide(validBdInputs[1], 2, RoundingMode.HALF_UP)
                    val formula = "(${validBdInputs[0]} / ${validBdInputs[1]}) * 100 = $pct%"
                    CalculationResult(operation, pct, "$pct%", formula)
                }
            }

            CalculationOp.PERCENTAGE_CHANGE -> {
                if (validBdInputs.size < 2 || validBdInputs[0] == BigDecimal.ZERO) {
                    errorResult(operation, "Invalid base value for PERCENTAGE_CHANGE")
                } else {
                    val change = validBdInputs[1].subtract(validBdInputs[0])
                    val pctChange = change.multiply(BigDecimal(100)).divide(validBdInputs[0], 2, RoundingMode.HALF_UP)
                    val formula = "((${validBdInputs[1]} - ${validBdInputs[0]}) / ${validBdInputs[0]}) * 100 = $pctChange%"
                    CalculationResult(operation, pctChange, "$pctChange%", formula)
                }
            }

            CalculationOp.RATIO -> {
                if (validBdInputs.size < 2 || validBdInputs[1] == BigDecimal.ZERO) {
                    errorResult(operation, "Invalid inputs for RATIO")
                } else {
                    val ratio = validBdInputs[0].divide(validBdInputs[1], 2, RoundingMode.HALF_UP)
                    val formula = "${validBdInputs[0]} : ${validBdInputs[1]} = $ratio"
                    CalculationResult(operation, ratio, "$ratio", formula)
                }
            }

            CalculationOp.AGE -> {
                val birthDate = inputs.firstOrNull()?.dateValue
                if (birthDate == null) errorResult(operation, "Date input required for AGE")
                else {
                    val ageYears = ChronoUnit.YEARS.between(birthDate, LocalDate.now())
                    val ageBd = BigDecimal(ageYears).setScale(2, RoundingMode.HALF_UP)
                    CalculationResult(operation, ageBd, "$ageYears years", "Current Date - Birth Date ($birthDate)")
                }
            }

            CalculationOp.DAYS_BETWEEN -> {
                val d1 = inputs.getOrNull(0)?.dateValue
                val d2 = inputs.getOrNull(1)?.dateValue ?: LocalDate.now()
                if (d1 == null) errorResult(operation, "Date input required for DAYS_BETWEEN")
                else {
                    val days = ChronoUnit.DAYS.between(d1, d2)
                    val daysBd = BigDecimal(days).setScale(2, RoundingMode.HALF_UP)
                    CalculationResult(operation, daysBd, "$days days", "Days between $d1 and $d2")
                }
            }
        }
    }

    private fun errorResult(op: CalculationOp, msg: String): CalculationResult {
        return CalculationResult(
            operation = op,
            numericResult = null,
            formattedResult = "Error",
            formulaWithInputs = msg
        )
    }
}
