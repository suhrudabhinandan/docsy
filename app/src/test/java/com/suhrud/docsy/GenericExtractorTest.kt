package com.suhrud.docsy

import com.suhrud.docsy.classification.GenericDocumentClassifier
import com.suhrud.docsy.declarative.DocumentTypeRegistry
import com.suhrud.docsy.extraction.GenericExtractor
import com.suhrud.docsy.model.CommonDocumentModel.BoundingBox
import com.suhrud.docsy.model.CommonDocumentModel.Line
import com.suhrud.docsy.model.CommonDocumentModel.PageDoc
import com.suhrud.docsy.model.CommonDocumentModel.Word
import com.suhrud.docsy.reasoning.CalculationInput
import com.suhrud.docsy.reasoning.CalculationOp
import com.suhrud.docsy.reasoning.GenericCalculator
import com.suhrud.docsy.reconstruction.TextReconstructionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

class GenericExtractorTest {

    private val random = Random(42)

    @Test
    fun testGeneratedFixturesDevAndHeldOutAccuracy() {
        val devFixtures = generateFixtures(count = 20, seed = 101)
        val heldOutFixtures = generateFixtures(count = 20, seed = 202)

        var devCorrect = 0
        for (fixture in devFixtures) {
            val classified = GenericDocumentClassifier.classify(fixture.text)
            if (classified.typeId == fixture.expectedType) {
                devCorrect++
            }
        }
        val devAccuracy = devCorrect.toFloat() / devFixtures.size

        var heldOutCorrect = 0
        for (fixture in heldOutFixtures) {
            val classified = GenericDocumentClassifier.classify(fixture.text)
            if (classified.typeId == fixture.expectedType) {
                heldOutCorrect++
            }
        }
        val heldOutAccuracy = heldOutCorrect.toFloat() / heldOutFixtures.size

        println("Dev-split accuracy: ${(devAccuracy * 100).toInt()}%")
        println("Held-out accuracy: ${(heldOutAccuracy * 100).toInt()}%")

        assertTrue("Dev accuracy should be >= 80%", devAccuracy >= 0.8f)
        assertTrue("Held-out accuracy should be >= 80%", heldOutAccuracy >= 0.8f)
    }

    @Test
    fun testGenericCalculatorBigDecimalAndFormula() {
        val inputs = listOf(
            CalculationInput("Item A", "85.50", BigDecimal("85.50")),
            CalculationInput("Item B", "14.50", BigDecimal("14.50"))
        )
        val sumResult = GenericCalculator.calculate(CalculationOp.SUM, inputs)
        assertEquals(BigDecimal("100.00"), sumResult.numericResult)
        assertEquals("100.00", sumResult.formattedResult)
        assertTrue(sumResult.formulaWithInputs.contains("85.50"))

        val avgResult = GenericCalculator.calculate(CalculationOp.AVERAGE, inputs)
        assertEquals(BigDecimal("50.00"), avgResult.numericResult)
    }

    @Test
    fun testTextReconstructionEngineGeometryGrouping() {
        val words = listOf(
            Word("Candidate", BoundingBox(10f, 10f, 50f, 25f)),
            Word("Name:", BoundingBox(55f, 10f, 80f, 25f)),
            Word("Alex", BoundingBox(90f, 10f, 120f, 25f)),
            Word("Marks", BoundingBox(10f, 40f, 50f, 55f)),
            Word("Obtained:", BoundingBox(55f, 40f, 100f, 55f)),
            Word("88", BoundingBox(105f, 40f, 120f, 55f))
        )

        val pageDoc = TextReconstructionEngine.reconstructPage(1, words)
        assertEquals(1, pageDoc.pageNumber)
        assertTrue(pageDoc.blocks.isNotEmpty())

        val text = pageDoc.blocks.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(text.contains("Candidate Name: Alex"))
        assertTrue(text.contains("Marks Obtained: 88"))
    }

    @Test
    fun testExtractionMissingFieldsStayMissing() {
        val text = "Statement of Marks\nCandidate Name: Jordan Smith\nRoll No: 987654"
        val words = text.split(" ").mapIndexed { idx, word ->
            Word(word, BoundingBox(10f + idx * 20f, 10f, 25f + idx * 20f, 25f))
        }
        val pageDoc = TextReconstructionEngine.reconstructPage(1, words)
        val output = GenericExtractor.extractDocument(listOf(pageDoc), text)

        assertEquals("MARKSHEET", output.docType)
        val percentageFact = output.facts.firstOrNull { it.factKey == "percentage" }
        // Missing fields must stay absent, never guessed or zero-filled
        assertEquals(null, percentageFact)
    }

    private data class TestFixture(
        val expectedType: String,
        val text: String
    )

    private fun generateFixtures(count: Int, seed: Int): List<TestFixture> {
        val rand = Random(seed)
        val fixtures = mutableListOf<TestFixture>()

        for (i in 0 until count) {
            val isMarksheet = rand.nextBoolean()
            if (isMarksheet) {
                val name = "Student_${rand.nextInt(1000, 9999)}"
                val roll = rand.nextInt(100000, 999999).toString()
                val marks = rand.nextInt(50, 100)
                val text = "Statement of Marks Marksheet Transcript\nCandidate Name: $name\nRoll No: $roll\nMarks Obtained: $marks\nMax Marks: 100"
                fixtures.add(TestFixture("MARKSHEET", text))
            } else {
                val invNum = "INV-${rand.nextInt(1000, 9999)}"
                val amt = rand.nextInt(100, 5000)
                val text = "Tax Invoice Bill Receipt\nInvoice No: $invNum\nAmount Due: $amt\nTotal Amount: $amt\nBill To: Client_${rand.nextInt(10, 99)}"
                fixtures.add(TestFixture("INVOICE", text))
            }
        }
        return fixtures
    }
}
