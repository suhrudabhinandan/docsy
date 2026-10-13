package com.suhrud.docsy

import com.suhrud.docsy.reasoning.DocsyCalculator
import com.suhrud.docsy.reasoning.SubjectMark
import com.suhrud.docsy.reasoning.ToolExecutionPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

class Phase10CalculatorReasoningTest {

    @Test
    fun testBasicArithmeticOperations() {
        // Sum
        val sumAudit = DocsyCalculator.sum(listOf(BigDecimal("10.5"), BigDecimal("20.25"), BigDecimal("5.0")))
        assertEquals(BigDecimal("35.75"), sumAudit.result)
        assertEquals("10.5 + 20.25 + 5.0", sumAudit.formula)

        // Average
        val avgAudit = DocsyCalculator.average(listOf(BigDecimal("80"), BigDecimal("90"), BigDecimal("85")))
        assertEquals(BigDecimal("85.00"), avgAudit.result)

        // Difference
        val diffAudit = DocsyCalculator.difference(BigDecimal("50000"), BigDecimal("42000"))
        assertEquals(BigDecimal("8000.00"), diffAudit.result)
    }

    @Test
    fun testPercentageCalculationHalfUp() {
        // 435 / 500 = 87.00%
        val pctAudit = DocsyCalculator.percentage(BigDecimal("435"), BigDecimal("500"))
        assertEquals(BigDecimal("87.00"), pctAudit.result)
        assertEquals("87.00%", pctAudit.formattedResult)

        // Half-up rounding: 2 / 3 = 66.6666... -> 66.67%
        val rounded = DocsyCalculator.percentage(BigDecimal("2"), BigDecimal("3"))
        assertEquals(BigDecimal("66.67"), rounded.result)
    }

    @Test
    fun testAgeCalculation() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -24)
        cal.add(Calendar.DAY_OF_YEAR, -5)
        val birthEpoch = cal.timeInMillis

        val age = DocsyCalculator.calculateAge(birthEpoch)
        assertEquals(24, age)
    }

    @Test
    fun testMarksheetPercentageAllVsBestFive() {
        val subjects = listOf(
            SubjectMark("Maths", BigDecimal("95")),
            SubjectMark("Physics", BigDecimal("90")),
            SubjectMark("Chemistry", BigDecimal("85")),
            SubjectMark("English", BigDecimal("80")),
            SubjectMark("Computer Science", BigDecimal("98")),
            SubjectMark("Physical Education", BigDecimal("60")) // 6th subject with low marks
        )

        // All 6 subjects: total = 508 / 600 = 84.67%
        val allCalc = DocsyCalculator.calculateMarksheetPercentage(subjects, bestFiveOnly = false)
        assertEquals(6, allCalc.subjectCount)
        assertEquals(BigDecimal("84.67"), allCalc.percentage)

        // Best 5 subjects: 98, 95, 90, 85, 80 = 448 / 500 = 89.60%
        val best5Calc = DocsyCalculator.calculateMarksheetPercentage(subjects, bestFiveOnly = true)
        assertEquals(5, best5Calc.subjectCount)
        assertEquals(BigDecimal("89.60"), best5Calc.percentage)
    }

    @Test
    fun testCrossDocumentComparison() {
        val salaryJan = mapOf(
            "Basic" to BigDecimal("40000"),
            "HRA" to BigDecimal("15000"),
            "NetPay" to BigDecimal("60000")
        )
        val salaryFeb = mapOf(
            "Basic" to BigDecimal("40000"),
            "HRA" to BigDecimal("15000"),
            "NetPay" to BigDecimal("65000") // 5000 bonus
        )

        val comparisons = DocsyCalculator.compareAttributes(salaryJan, salaryFeb)
        val netDiff = comparisons.first { it.attribute == "NetPay" }

        assertEquals(BigDecimal("5000.00"), netDiff.difference)
        assertEquals(BigDecimal("8.33"), netDiff.percentageChange)
    }

    @Test
    fun testToolExecutionPipelineBounds() = runBlocking {
        val pipeline = ToolExecutionPipeline(maxSteps = 3)

        val step1 = pipeline.executeStep("RetrieveDoc") { "Doc #123" }
        assertTrue(step1.isSuccess)
        assertEquals("Doc #123", step1.outputData)

        val step2 = pipeline.executeStep("ExtractFacts") { listOf("Mark: 95") }
        assertTrue(step2.isSuccess)

        val step3 = pipeline.executeStep("CalculatePercentage") { BigDecimal("95.00") }
        assertTrue(step3.isSuccess)

        // 4th step must be rejected by pipeline step bounds
        val step4 = pipeline.executeStep("ExtraStep") { "Should Fail" }
        assertFalse(step4.isSuccess)
        assertTrue(step4.error?.contains("Maximum step bound") == true)
    }
}
