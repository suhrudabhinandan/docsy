package com.suhrud.docsy

import com.suhrud.docsy.reconstruction.TextReconstructionEngine
import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.MarksheetParser
import com.suhrud.docsy.understanding.OwnerExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredExtractionTest {

    @Test
    fun testFragmentedLinesRepaired() {
        val rawFragmented = "na / me / suhrud ab / hinandan"
        val repaired = TextReconstructionEngine.repairFragmentedSlashes(rawFragmented)
        assertTrue("Repaired text should contain name: $repaired", repaired.contains("name"))
        assertTrue("Repaired text should contain abhinandan: $repaired", repaired.contains("abhinandan"))
    }

    @Test
    fun testLabelValuePairingAndRollNumber() {
        val text = "CENTRAL BOARD OF SECONDARY EDUCATION\nRoll No.: 12646545\nCandidate Name: SUHRUD ABHINANDAN"
        val parsed = MarksheetParser.parse(text)
        assertEquals("12646545", parsed.rollNumber)
        assertEquals("SUHRUD ABHINANDAN", parsed.studentName)
    }

    @Test
    fun testMarksheetJsonFromNoisyText() {
        val noisyText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS - SENIOR SCHOOL CERTIFICATE EXAMINATION 2022
            Roll No: 12646545
            Name of Candidate: SUHRUD ABHINANDAN
            Father's Name: SANJEEV ABHINANDAN
            School: ST XAVIER SCHOOL
            
            SUB CODE   SUBJECT NAME      MARKS   MAX MARKS   GRADE
            301        ENGLISH CORE      085     100         A1
            041        MATHEMATICS       092     100         A1
            042        PHYSICS           078     100         A2
            043        CHEMISTRY         081     100         A2
            083        COMPUTER SCIENCE  095     100         A1
            
            RESULT: PASS
        """.trimIndent()

        val parsed = MarksheetParser.parse(noisyText)
        assertEquals("CBSE", parsed.board)
        assertEquals("12th", parsed.gradeClass)
        assertEquals("12646545", parsed.rollNumber)
        assertEquals("SUHRUD ABHINANDAN", parsed.studentName)
        assertEquals(5, parsed.subjects.size)
        assertEquals("PASS", parsed.resultStatus)
    }

    @Test
    fun testCvNotClassifiedAsMarksheet() {
        val cvText = """
            CURRICULUM VITAE
            SUHRUD ABHINANDAN
            Email: suhrud@example.com | Phone: +91 9876543210
            
            PROFESSIONAL EXPERIENCE
            Senior Software Engineer at Tech Corp (2022 - Present)
            
            TECHNICAL SKILLS
            Kotlin, Android, SQLite, Java, Jetpack Compose
            
            EDUCATION
            Class 12th CBSE Board - 88%
            B.Tech Computer Science
        """.trimIndent()

        val classification = DocumentClassifier.classify(cvText)
        assertEquals("CV", classification.category)
        assertNotEquals("MARKSHEET", classification.category)

        val owner = OwnerExtractor.extractOwner(cvText, documentType = "CV")
        assertNotNull(owner)
        assertEquals("SUHRUD ABHINANDAN", owner?.ownerName)
    }

    @Test
    fun testParentNotTakenAsOwner() {
        val marksheetText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS
            Candidate Name: SUHRUD ABHINANDAN
            Father's Name: SANJEEV ABHINANDAN
            Mother's Name: ANITA ABHINANDAN
        """.trimIndent()

        val owner = OwnerExtractor.extractOwner(marksheetText, documentType = "MARKSHEET")
        assertNotNull(owner)
        assertEquals("SUHRUD ABHINANDAN", owner?.ownerName)
        assertNotEquals("SANJEEV ABHINANDAN", owner?.ownerName)
    }

    @Test
    fun testSixSubjectMarksheet() {
        val text = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS 2022
            Roll No: 12646545
            Name: SUHRUD ABHINANDAN
            
            301 ENGLISH CORE 085 100
            041 MATHEMATICS 090 100
            042 PHYSICS 078 100
            043 CHEMISTRY 082 100
            083 COMPUTER SCIENCE 095 100
            048 PHYSICAL EDUCATION 090 100
            
            RESULT: PASS
        """.trimIndent()

        val parsed = MarksheetParser.parse(text)
        assertEquals(6, parsed.subjects.size)
        val formatted = MarksheetParser.formatAnswer(parsed)
        assertTrue("Answer formatted should contain Best Five or All Subjects", formatted.contains("Best Five") || formatted.contains("Working"))
    }
}
