package com.suhrud.docsy

import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.MarksheetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksheetUnitTest {

    @Test
    fun testCleanMarksheet() {
        val ocrText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS - SENIOR SECONDARY EXAMINATION 2023
            Roll No: 1234567
            Student Name: Abhinandan Suhrud
            
            041 Mathematics    85   100
            042 Physics        90   100
            043 Chemistry      80   100
            301 English Core   95   100
            083 Computer Sci   90   100
            
            Result: PASS
            Percentage: 88.00%
        """.trimIndent()

        val classification = DocumentClassifier.classify(ocrText, "file_001.pdf")
        assertEquals("MARKSHEET", classification.category)

        val parsed = MarksheetParser.parse(ocrText)
        assertEquals("12th", parsed.gradeClass)
        assertEquals(5, parsed.subjects.size)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("88.00%"))
        assertTrue(answer.contains("Working: 440.00 out of 500.00 = 88.00%"))
    }

    @Test
    fun testNoisyOcrMarksheet() {
        val noisyText = """
            c3ntral b0ard 0f s3condary 3ducation
            st4tement of m4rks - class xii
            r0ll n0: 9876543
            
            301 english    82  100
            041 m4thematics 78  100
            042 physics    85  100
            043 chemistry  75  100
            083 biology    80  100
            
            p4ssed
        """.trimIndent()

        val parsed = MarksheetParser.parse(noisyText)
        assertEquals("12th", parsed.gradeClass)
        assertEquals(5, parsed.subjects.size)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("80.00%"))
    }

    @Test
    fun testSixSubjectsBestFiveAndAllSubjects() {
        val sixSubText = """
            STATEMENT OF MARKS
            CLASS XII SENIOR SECONDARY
            
            English Core    90 100
            Mathematics     95 100
            Physics         85 100
            Chemistry       70 100
            Computer Sci    100 100
            Physical Edu    60 100
        """.trimIndent()

        val parsed = MarksheetParser.parse(sixSubText)
        assertEquals(6, parsed.subjects.size)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("Best Five"))
        assertTrue(answer.contains("All Subjects (6)"))
    }

    @Test
    fun testCvMentioning12thNeverClassifiedAsMarksheet() {
        val cvText = """
            CURRICULUM VITAE
            Abhinandan Suhrud
            Professional Experience: Senior Software Engineer at Tech Corp
            Technical Skills: Kotlin, Java, Android
            Education:
            - B.Tech in CS
            - CBSE 12th Board Examination: Scored 91.5%
            Projects: Docsy
        """.trimIndent()

        val classification = DocumentClassifier.classify(cvText, "My_CV.pdf")
        assertEquals("CV", classification.category)

        val parsed = MarksheetParser.parse(cvText, isCvSource = true)
        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("According to your CV"))
    }

    @Test
    fun testMissingMarksTriggersConfirmationPrompt() {
        val incompleteText = """
            STATEMENT OF MARKS
            CLASS 12
            Mathematics --
            Physics --
        """.trimIndent()

        val parsed = MarksheetParser.parse(incompleteText)
        assertTrue(parsed.subjects.isEmpty() || parsed.isLowConfidence)

        val answer = MarksheetParser.formatAnswer(parsed)
        assertTrue(answer.contains("could not be extracted") || answer.contains("confirm"))
    }
}
