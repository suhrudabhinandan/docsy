package com.suhrud.docsy

import com.suhrud.docsy.understanding.FieldValidator
import com.suhrud.docsy.understanding.MarksheetParser
import com.suhrud.docsy.understanding.VerhoeffValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerTypeSchemaExtractorTest {

    // 1. Clean Marksheet
    @Test
    fun testCleanMarksheetExtraction() {
        val text = """
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

        val parsed = MarksheetParser.parse(text)
        assertEquals("CBSE", parsed.board)
        assertEquals("12th", parsed.gradeClass)
        assertEquals("12646545", parsed.rollNumber)
        assertEquals("SUHRUD ABHINANDAN", parsed.studentName)
        assertEquals(5, parsed.subjects.size)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue("Answer should contain Working line", answer.contains("Working: 431.00 out of 500.00 = 86.20%"))
    }

    // 2. Noisy OCR Marksheet
    @Test
    fun testNoisyOcrMarksheetExtraction() {
        val text = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS 2022
            Roll No.: 12646545
            Candidate Name: Suhrud Abhinandan
            
            301 ENGLISH CORE 85 100
            041 M4THEMATICS 90 100
            042 PHYSICS 78 100
            043 CHEMISTRY 82 100
            083 COMPUTER SCI 95 100
            
            RESULT: PASS
        """.trimIndent()

        val parsed = MarksheetParser.parse(text)
        assertEquals(5, parsed.subjects.size)
        assertTrue("Mathematics should be recognized despite m4thematics OCR noise", parsed.subjects.any { it.subjectName == "Mathematics" })
        assertTrue("Computer Science should be recognized despite computer sci OCR noise", parsed.subjects.any { it.subjectName == "Computer Science" })

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("Working:"))
    }

    // 3. Six Subjects Marksheet (Best 5 vs All Subjects)
    @Test
    fun testSixSubjectsMarksheetExtraction() {
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

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue("Should report Best Five separately", answer.contains("Best Five:"))
        assertTrue("Should report All Subjects separately", answer.contains("All Subjects (6):"))
    }

    // 4. CV Mentioning 12th Percentage
    @Test
    fun testCvMentioning12thPercentage() {
        val cvText = """
            CURRICULUM VITAE
            SUHRUD ABHINANDAN
            Email: suhrud@example.com
            
            WORK EXPERIENCE
            Software Engineer (2022 - Present)
            
            EDUCATION
            Class 12th CBSE Board - 88% marks in 2020
            B.Tech Computer Science
        """.trimIndent()

        val parsed = MarksheetParser.parse(cvText, isCvSource = true)
        assertTrue("Source should be marked as CV", parsed.sourceIsCv)
        assertTrue("Subjects should be empty for CV source", parsed.subjects.isEmpty())

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue("Should reference CV source", answer.contains("According to your CV"))
        assertTrue("Should mention percentage", answer.contains("88"))
    }

    // 5. Missing Marks / Low Confidence
    @Test
    fun testMissingMarksAndLowConfidence() {
        val text = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS
            Roll No: 12646545
            Name: SUHRUD ABHINANDAN
            
            PHYSICS 150 100
        """.trimIndent()

        val parsed = MarksheetParser.parse(text)
        assertTrue("Should mark as low confidence when marks obtained > max marks", parsed.isLowConfidence)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue("Should ask user to confirm low confidence values", answer.contains("low confidence"))
    }

    // 6. Validators & Synonyms Tests
    @Test
    fun testValidatorsAndSynonyms() {
        // Aadhaar Verhoeff
        val validAadhaar = FieldValidator.validateAadhaar("2345 6789 0123")
        // Verhoeff checksum test on valid pattern
        assertTrue("Aadhaar validator should normalize valid digit string length", validAadhaar.normalizedValue?.length == 14)

        val invalidAadhaar = FieldValidator.validateAadhaar("1234 5678 9012")
        assertFalse("Invalid Verhoeff should fail validation", invalidAadhaar.isValid)
        assertTrue("Failed validation flags needsReview", invalidAadhaar.needsReview)

        // PAN Validator
        val validPan = FieldValidator.validatePan("ABCDE1234F")
        assertTrue("Valid PAN should pass", validPan.isValid)

        val invalidPan = FieldValidator.validatePan("12345ABCDE")
        assertFalse("Invalid PAN pattern should fail", invalidPan.isValid)
        assertTrue("Failed PAN flags needsReview", invalidPan.needsReview)

        // IFSC Validator
        val validIfsc = FieldValidator.validateIfsc("SBIN0001234")
        assertTrue("Valid IFSC should pass", validIfsc.isValid)

        // Synonym Resolution
        assertEquals("percentage", FieldValidator.resolveSynonymKey("percent"))
        assertEquals("percentage", FieldValidator.resolveSynonymKey("%"))
        assertEquals("percentage", FieldValidator.resolveSynonymKey("pct"))
        assertEquals("dob", FieldValidator.resolveSynonymKey("date_of_birth"))
    }
}
