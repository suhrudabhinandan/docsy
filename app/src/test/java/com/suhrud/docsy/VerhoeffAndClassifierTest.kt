package com.suhrud.docsy

import com.suhrud.docsy.data.model.DocumentCategories
import com.suhrud.docsy.domain.ai.DocumentClassifier
import com.suhrud.docsy.domain.query.QueryParser
import com.suhrud.docsy.domain.util.Verhoeff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerhoeffAndClassifierTest {

    @Test
    fun testVerhoeffValidation() {
        val num11 = "23456789012"
        val checksumDigit = Verhoeff.generateChecksum(num11)
        val full12DigitAadhaar = "$num11$checksumDigit"

        assertTrue("Generated Verhoeff Aadhaar should be valid", Verhoeff.validateAadhaar(full12DigitAadhaar))
        assertFalse("Altered digit should fail Verhoeff validation", Verhoeff.validateAadhaar("234567890129"))
        assertFalse("Non-digit string should fail", Verhoeff.validateAadhaar("1234ABCD5678"))
        assertFalse("Short string should fail", Verhoeff.validateAadhaar("123456"))
    }

    @Test
    fun testDocumentClassifierAadhaar() {
        val num11 = "98765432101"
        val checksumDigit = Verhoeff.generateChecksum(num11)
        val validAadhaar = "$num11$checksumDigit"

        val text = """
            GOVERNMENT OF INDIA
            Unique Identification Authority of India
            Mera Aadhaar, Meri Pehchan
            $validAadhaar
        """.trimIndent()

        val extracted = DocumentClassifier.classifyAndExtract("Aadhaar.pdf", text)
        assertEquals(DocumentCategories.AADHAAR, extracted.category)
        assertEquals(validAadhaar, extracted.aadhaarNumberFull)
        assertEquals("XXXX-XXXX-${validAadhaar.takeLast(4)}", extracted.aadhaarNumberMasked)
    }

    @Test
    fun testDocumentClassifierPAN() {
        val text = """
            INCOME TAX DEPARTMENT
            GOVT. OF INDIA
            Permanent Account Number Card
            ABCDE1234F
        """.trimIndent()

        val extracted = DocumentClassifier.classifyAndExtract("Pan_Card.pdf", text)
        assertEquals(DocumentCategories.PAN, extracted.category)
        assertEquals("ABCDE1234F", extracted.panNumber)
    }

    @Test
    fun testDocumentClassifierMarksheetAndConstraints() {
        val text = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            Secondary School Examination 2024 (Class 10th)
            Roll No: 12345678
            CGPA / Percentage: 94.2%
        """.trimIndent()

        val extracted = DocumentClassifier.classifyAndExtract("10th_CBSE_Marksheet.pdf", text)
        assertEquals(DocumentCategories.MARKSHEET, extracted.category)
        assertEquals("CBSE", extracted.boardName)
        assertEquals("10th", extracted.classOrStandard)
        assertEquals("94.2%", extracted.percentageOrCgpa)

        val query = QueryParser.parse("10th CBSE marksheet")
        assertEquals(DocumentCategories.MARKSHEET, query.documentType)
        assertEquals("10th", query.standardOrClass)
        assertEquals("CBSE", query.board)
    }

    @Test
    fun testUtilityBillExtraction() {
        val text = """
            BWSSB Water Supply & Sewerage Board
            Billing Month: May 2026
            Amount Payable: ₹650.00
            Due Date: 25/05/2026
        """.trimIndent()

        val extracted = DocumentClassifier.classifyAndExtract("Water_Bill_May_2026.pdf", text)
        assertEquals(DocumentCategories.WATER_BILL, extracted.category)
        assertEquals("May", extracted.billingMonth)
        assertEquals(2026, extracted.billingYear)
        assertEquals(650.0, extracted.amountPayable ?: 0.0, 0.01)
    }
}
