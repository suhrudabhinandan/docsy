package com.suhrud.docsy

import com.suhrud.docsy.classification.GeneralCategory
import com.suhrud.docsy.classification.HierarchicalDocumentClassifier
import com.suhrud.docsy.extraction.ExtractedStructuredEntity
import com.suhrud.docsy.extraction.FieldDisambiguator
import com.suhrud.docsy.extraction.RelationshipResolver
import com.suhrud.docsy.extraction.StructuredEntityExtractor
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.ExtractedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase2ExtractionAndClassificationTest {

    @Test
    fun testHierarchicalClassification() {
        val academicText = """
            Central Board of Secondary Education
            Statement of Marks
            Class XII Examination 2024
            Roll No: 1234567
            Student Name: Rahul Sharma
            Subject: Mathematics Marks: 95
        """.trimIndent()

        val academicResult = HierarchicalDocumentClassifier.classify(academicText, "marksheet_2024.pdf")
        assertEquals(GeneralCategory.ACADEMIC, academicResult.generalCategory)
        assertEquals("MARKSHEET", academicResult.specificType)
        assertTrue(academicResult.isConfident)
        assertTrue(academicResult.confidence > 0.5f)

        val invoiceText = """
            TAX INVOICE
            Invoice No: INV-2024-001
            GSTIN: 07AAAAA0000A1Z5
            Total Amount Payable: ₹ 15,500.00
            Due Date: 15/10/2024
        """.trimIndent()

        val invoiceResult = HierarchicalDocumentClassifier.classify(invoiceText, "tax_invoice.pdf")
        assertEquals(GeneralCategory.FINANCIAL, invoiceResult.generalCategory)
        assertEquals("INVOICE", invoiceResult.specificType)

        val cvText = """
            Curriculum Vitae
            Rahul Sharma
            Email: rahul.sharma@example.com
            Work Experience: Senior Software Engineer at Tech Corp
            Education: B.Tech Computer Science
        """.trimIndent()

        val cvResult = HierarchicalDocumentClassifier.classify(cvText, "resume_2024.pdf")
        assertEquals(GeneralCategory.EMPLOYMENT, cvResult.generalCategory)
        assertEquals("RESUME", cvResult.specificType)

        val unknownText = "Random notes about weekly grocery shopping and apples."
        val unknownResult = HierarchicalDocumentClassifier.classify(unknownText, "notes.txt")
        assertEquals(GeneralCategory.OTHER, unknownResult.generalCategory)
        assertEquals("UNKNOWN", unknownResult.specificType)
        assertFalse(unknownResult.isConfident)
    }

    @Test
    fun testFieldDisambiguationStudentVsFatherName() {
        val sampleText = """
            Candidate's Name: Rahul Sharma
            Father's Name: Ramesh Sharma
            Mother's Name: Sunita Sharma
        """.trimIndent()

        val fields = FieldDisambiguator.disambiguateText(sampleText, "doc_1.pdf")

        val studentField = fields.firstOrNull { it.fieldType == "STUDENT_NAME" }
        assertNotNull("Student name must be extracted", studentField)
        assertEquals("Rahul Sharma", studentField?.rawValue)
        assertEquals("RAHUL SHARMA", studentField?.normalizedValue)

        val fatherField = fields.firstOrNull { it.fieldType == "PARENT_NAME" }
        assertNotNull("Father's name must be extracted as PARENT_NAME", fatherField)
        assertEquals("Ramesh Sharma", fatherField?.rawValue)
        assertEquals("RAMESH SHARMA", fatherField?.normalizedValue)

        // Ensure Father's Name was NOT assigned to Student Name
        assertTrue("Student Name and Father Name must remain distinct", studentField?.rawValue != fatherField?.rawValue)
    }

    @Test
    fun testFieldDisambiguationRollNoVsPhoneVsRegNo() {
        val sampleText = """
            Roll No: 9876543210
            Registration No: REG2024CS1023
            Mobile No: 9876500000
        """.trimIndent()

        val fields = FieldDisambiguator.disambiguateText(sampleText, "doc_2.pdf")

        val rollField = fields.firstOrNull { it.fieldType == "ROLL_NUMBER" }
        assertNotNull("Roll number must be extracted", rollField)
        assertEquals("9876543210", rollField?.rawValue)

        val regField = fields.firstOrNull { it.fieldType == "REGISTRATION_NUMBER" }
        assertNotNull("Registration number must be extracted", regField)
        assertEquals("REG2024CS1023", regField?.rawValue)

        val phoneField = fields.firstOrNull { it.fieldType == "PHONE_NUMBER" }
        assertNotNull("Phone number must be extracted", phoneField)
        assertEquals("9876500000", phoneField?.rawValue)

        // Verify Roll No 10-digit number was NOT assigned as Phone Number
        assertTrue("Roll No must not be assigned as Phone Number", rollField?.fieldType != phoneField?.fieldType)
    }

    @Test
    fun testDateDisambiguationBirthVsDocumentDate() {
        val sampleText = """
            Date of Birth: 12/05/2002
            Date of Issue: 01/01/2024
        """.trimIndent()

        val fields = FieldDisambiguator.disambiguateText(sampleText, "doc_3.pdf")

        val dobField = fields.firstOrNull { it.fieldType == "BIRTH_DATE" }
        assertNotNull("DOB must be extracted as BIRTH_DATE", dobField)
        assertEquals("12/05/2002", dobField?.rawValue)

        val issueField = fields.firstOrNull { it.fieldType == "ISSUE_DATE" }
        assertNotNull("Issue date must be extracted as ISSUE_DATE", issueField)
        assertEquals("01/01/2024", issueField?.rawValue)
    }

    @Test
    fun testCurrencyAmountExtraction() {
        val sampleText = "Total Amount Payable: ₹ 15,500.00"
        val fields = FieldDisambiguator.disambiguateText(sampleText, "bill.pdf")

        val amtField = fields.firstOrNull { it.fieldType == "AMOUNT" }
        assertNotNull("Amount must be extracted", amtField)
        assertTrue("Currency symbol must be retained", amtField?.normalizedValue?.contains("₹") == true)
        assertTrue("Numeric amount must be preserved", amtField?.normalizedValue?.contains("15,500.00") == true)
    }

    @Test
    fun testEntityRelationshipResolution() {
        val entities = listOf(
            ExtractedStructuredEntity("STUDENT_NAME", "Rahul Sharma", "RAHUL SHARMA", 0.95f),
            ExtractedStructuredEntity("ROLL_NUMBER", "241023", "241023", 0.95f),
            ExtractedStructuredEntity("REGISTRATION_NUMBER", "REG2024CS1023", "REG2024CS1023", 0.95f),
            ExtractedStructuredEntity("INSTITUTION_NAME", "Delhi University", "DELHI UNIVERSITY", 0.90f)
        )

        val relationships = RelationshipResolver.resolveRelationships(
            entities = entities,
            docType = "MARKSHEET",
            sourcePath = "marksheet.pdf"
        )

        assertTrue("Relationships list must not be empty", relationships.isNotEmpty())

        val rollRel = relationships.firstOrNull { it.relationType == "HAS_ROLL_NO" }
        assertNotNull("Roll number relationship must be resolved", rollRel)
        assertEquals("RAHUL SHARMA", rollRel?.subjectEntity)
        assertEquals("241023", rollRel?.objectEntity)

        val instRel = relationships.firstOrNull { it.relationType == "ENROLLED_AT" }
        assertNotNull("Institution relationship must be resolved", instRel)
        assertEquals("RAHUL SHARMA", instRel?.subjectEntity)
        assertEquals("DELHI UNIVERSITY", instRel?.objectEntity)
    }

    @Test
    fun testStructuredEntityExtractorIntegration() {
        val fullText = """
            Central Board of Secondary Education
            Statement of Marks
            Candidate's Name: Rahul Sharma
            Father's Name: Ramesh Sharma
            Roll No: 241023
            Registration No: REG2024CS1023
            Total Marks: 475 / 500
        """.trimIndent()

        val extractionResult = ExtractionResult(
            contentKey = "key_1",
            extension = "pdf",
            pageCount = 1,
            fullText = fullText,
            pages = listOf(ExtractedPage(pageNumber = 1, text = fullText)),
            contentHash = "hash1",
            durationMs = 120L
        )

        val docStructured = StructuredEntityExtractor.extractStructuredDocument(
            extractionResult = extractionResult,
            filePath = "/storage/emulated/0/Documents/marksheet.pdf"
        )

        assertEquals(GeneralCategory.ACADEMIC, docStructured.classification.generalCategory)
        assertEquals("MARKSHEET", docStructured.classification.specificType)
        assertTrue("Entities must be extracted", docStructured.entities.isNotEmpty())

        val studentEntity = docStructured.entities.firstOrNull { it.entityType == "STUDENT_NAME" }
        assertNotNull(studentEntity)
        assertEquals("Rahul Sharma", studentEntity?.rawValue)

        val parentEntity = docStructured.entities.firstOrNull { it.entityType == "PARENT_NAME" }
        assertNotNull(parentEntity)
        assertEquals("Ramesh Sharma", parentEntity?.rawValue)
    }
}
