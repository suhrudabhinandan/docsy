package com.suhrud.docsy

import com.suhrud.docsy.reconstruction.TextReconstructionEngine
import com.suhrud.docsy.retrieval.Fts5SearchEngine
import com.suhrud.docsy.retrieval.GenericRetrievalRouter
import com.suhrud.docsy.retrieval.QueryNormalizer
import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.MarksheetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictRetrievalTest {

    @Test
    fun testNormalizerHinglishAndTypos() {
        val raw = "mera 12th markshet ka roll number kya h?"
        val normalized = QueryNormalizer.normalize(raw)
        assertTrue("Normalized query should correct spelling to marksheet: $normalized", normalized.contains("marksheet"))
        assertTrue("Normalized query should map mera to my: $normalized", normalized.contains("my"))
    }

    @Test
    fun testFtsMatchEscaping() {
        val badInput = "CAT'S OR \"DROP TABLE chunks; --"
        val escaped = Fts5SearchEngine.escapeFtsToken(badInput)
        assertTrue("Escaped term should wrap in double quotes", escaped.startsWith("\"") && escaped.endsWith("\""))
        assertTrue("Escaped term should escape internal quotes", escaped.contains("\"\""))
    }

    @Test
    fun testCvAndOtherClassDistractorsRejected() {
        val cvText = """
            CURRICULUM VITAE
            SUHRUD ABHINANDAN
            Education: Passed 12th CBSE Board Examination with 88%.
            Experience: Senior Android Developer.
        """.trimIndent()

        val cvClass = DocumentClassifier.classify(cvText)
        assertEquals("CV", cvClass.category)
        assertNotEquals("MARKSHEET", cvClass.category)

        val slots12th = GenericRetrievalRouter.extractSlots("my 12th marksheet")
        assertEquals("MARKSHEET", slots12th.docType)
        assertEquals("12th", slots12th.targetClass)
    }

    @Test
    fun testMarksheetRollNumberAndPercentageCalculation() {
        val marksheetText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS - SENIOR SCHOOL CERTIFICATE EXAMINATION 2022
            Roll No: 12646545
            Name of Candidate: SUHRUD ABHINANDAN
            
            SUB CODE   SUBJECT NAME      MARKS   MAX MARKS
            301        ENGLISH CORE      085     100
            041        MATHEMATICS       092     100
            042        PHYSICS           078     100
            043        CHEMISTRY         081     100
            083        COMPUTER SCIENCE  095     100
            
            RESULT: PASS
        """.trimIndent()

        val parsed = MarksheetParser.parse(marksheetText)
        assertEquals("12646545", parsed.rollNumber)
        assertEquals(5, parsed.subjects.size)

        val calc = com.suhrud.docsy.reasoning.DocsyCalculator.calculateMarksheetPercentage(parsed.subjects)
        assertEquals("86.20%", "${calc.percentage}%")
        assertEquals("431.00", calc.totalObtained.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
        assertEquals("500.00", calc.totalMaximum.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    }

    @Test
    fun testSixSubjectMarksheetBestFiveAndAllSubjects() {
        val text = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS
            Roll No: 12646545
            Name: SUHRUD ABHINANDAN
            
            301 ENGLISH CORE 085 100
            041 MATHEMATICS 090 100
            042 PHYSICS 078 100
            043 CHEMISTRY 082 100
            083 COMPUTER SCIENCE 095 100
            048 PHYSICAL EDUCATION 090 100
        """.trimIndent()

        val parsed = MarksheetParser.parse(text)
        assertEquals(6, parsed.subjects.size)

        val b5Calc = com.suhrud.docsy.reasoning.DocsyCalculator.calculateMarksheetPercentage(parsed.subjects, bestFiveOnly = true)
        val allCalc = com.suhrud.docsy.reasoning.DocsyCalculator.calculateMarksheetPercentage(parsed.subjects, bestFiveOnly = false)

        assertTrue("Best five percentage should be >= all subjects percentage", b5Calc.percentage >= allCalc.percentage)
    }

    @Test
    fun testNotFoundForUnknownDocument() {
        val slots = GenericRetrievalRouter.extractSlots("cat exam venue")
        assertEquals(null, slots.targetClass)
        assertEquals(null, slots.requestedField)
    }
}
