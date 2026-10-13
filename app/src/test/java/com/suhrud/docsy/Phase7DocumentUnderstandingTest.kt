package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.ui.common.IdMasker
import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.DocumentSynonymDictionary
import com.suhrud.docsy.understanding.FactConsistencyChecker
import com.suhrud.docsy.understanding.GovernmentIdDetector
import com.suhrud.docsy.understanding.NameMatcher
import com.suhrud.docsy.understanding.OwnerDisambiguation
import com.suhrud.docsy.understanding.OwnerExtractor
import com.suhrud.docsy.understanding.ProvenanceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7DocumentUnderstandingTest {

    @Test
    fun testVerhoeffAadhaarValidation() {
        // Valid Aadhaar generation test using Verhoeff algorithm
        val prefix = "23456789012"
        val checkDigit = GovernmentIdDetector.generateVerhoeffDigit(prefix)
        val validAadhaar = "$prefix$checkDigit"

        assertTrue("Aadhaar with valid Verhoeff checksum should pass", GovernmentIdDetector.validateAadhaar(validAadhaar))

        // Corrupted digit should fail
        val corruptedAadhaar = "${prefix}${((checkDigit + 1) % 10)}"
        assertFalse("Corrupted Aadhaar digit must fail Verhoeff checksum", GovernmentIdDetector.validateAadhaar(corruptedAadhaar))

        // Starting with 0 or 1 must fail
        assertFalse("Aadhaar starting with 0 must fail", GovernmentIdDetector.validateAadhaar("012345678901"))
        assertFalse("Aadhaar starting with 1 must fail", GovernmentIdDetector.validateAadhaar("112345678901"))
    }

    @Test
    fun testPanCardValidation() {
        // Valid PAN for individual (P)
        assertTrue(GovernmentIdDetector.validatePan("ABCDE1234F"))
        // Valid PAN for company (C)
        assertTrue(GovernmentIdDetector.validatePan("XYZPC9876G"))

        // Invalid PAN format (too short, wrong chars)
        assertFalse(GovernmentIdDetector.validatePan("ABCD1234F"))
        assertFalse(GovernmentIdDetector.validatePan("ABCDE12345"))
        assertFalse(GovernmentIdDetector.validatePan("12345ABCDE"))
    }

    @Test
    fun testOtherGovernmentIds() {
        assertTrue(GovernmentIdDetector.validateVoterId("ABC1234567"))
        assertFalse(GovernmentIdDetector.validateVoterId("AB1234567"))

        assertTrue(GovernmentIdDetector.validateIndianPassport("Z1234567"))
        assertFalse(GovernmentIdDetector.validateIndianPassport("12345678"))

        assertTrue(GovernmentIdDetector.validateAbha("12-3456-7890-1234"))
        assertFalse(GovernmentIdDetector.validateAbha("123456"))
    }

    @Test
    fun testGovernmentIdDetectionInText() {
        val sampleText = """
            Customer Verification Form
            Applicant PAN Number: ABCDE1234F
            EPIC Number: XYZ1234567
            Passport No: Z9876543
        """.trimIndent()

        val detected = GovernmentIdDetector.detectIds(sampleText)
        assertTrue(detected.any { it.type == "PAN" && it.normalizedValue == "ABCDE1234F" })
        assertTrue(detected.any { it.type == "VOTER_ID" && it.normalizedValue == "XYZ1234567" })
        assertTrue(detected.any { it.type == "PASSPORT" && it.normalizedValue == "Z9876543" })
    }

    @Test
    fun testDocumentSynonymDictionary() {
        assertEquals("MARKSHEET", DocumentSynonymDictionary.resolveCanonicalCategory("12th marksheet"))
        assertEquals("MARKSHEET", DocumentSynonymDictionary.resolveCanonicalCategory("अंकतालिका"))
        assertEquals("MARKSHEET", DocumentSynonymDictionary.resolveCanonicalCategory("scorecard"))

        assertEquals("CV", DocumentSynonymDictionary.resolveCanonicalCategory("curriculum vitae"))
        assertEquals("CV", DocumentSynonymDictionary.resolveCanonicalCategory("बायोडाटा"))

        assertEquals("AADHAAR", DocumentSynonymDictionary.resolveCanonicalCategory("uidai"))
        assertEquals("AADHAAR", DocumentSynonymDictionary.resolveCanonicalCategory("आधार"))

        assertEquals("BILL", DocumentSynonymDictionary.resolveCanonicalCategory("bijli bill"))
        assertEquals("ADMIT_CARD", DocumentSynonymDictionary.resolveCanonicalCategory("hall ticket"))
    }

    @Test
    fun testDocumentClassifierNegativeEvidence() {
        // CV mentioning 12th marks and CBSE:
        // Must classify as CV, NOT MARKSHEET, because of negative evidence (work experience, skills, projects)
        val cvText = """
            CURRICULUM VITAE
            Abhinandan Suhrud
            Professional Experience: Senior Software Engineer at Tech Corp
            Technical Skills: Kotlin, Android, Jetpack Compose
            Education:
            - B.Tech in Computer Science
            - CBSE 12th Board Examination: 92%
            Projects: Docsy Local Document Assistant
        """.trimIndent()

        val cvResult = DocumentClassifier.classify(cvText, "Abhinandan_Resume.pdf")
        assertEquals("CV", cvResult.category)
        assertTrue("CV classification should be confident", cvResult.isConfident)

        // Marksheet text:
        val marksheetText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            STATEMENT OF MARKS
            SECONDARY SCHOOL EXAMINATION
            Roll No: 1234567
            Candidate Name: Abhinandan Suhrud
            Subject Code  Theory  Practical  Total Marks
            041 Math        70        20         90
            042 Physics     65        25         90
        """.trimIndent()

        val marksheetResult = DocumentClassifier.classify(marksheetText, "12th_marksheet.pdf")
        assertEquals("MARKSHEET", marksheetResult.category)
        assertTrue("Marksheet classification should be confident", marksheetResult.isConfident)
    }

    @Test
    fun testNameMatcherAndHonorificStripping() {
        assertEquals("abhinandan suhrud", NameMatcher.normalizeName("Dr. Abhinandan Suhrud"))
        assertEquals("abhinandan suhrud", NameMatcher.normalizeName("Shri Abhinandan Suhrud"))
        assertEquals("abhinandan suhrud", NameMatcher.normalizeName("Mr. Abhinandan Suhrud, Jr."))

        // Token set match with inverted order:
        assertTrue(
            "Inverted name order should match",
            NameMatcher.isNameMatch("Abhinandan Suhrud", "Suhrud, Abhinandan")
        )

        // Fuzzy match with minor spelling variation:
        assertTrue(
            "Close phonetic/spelling variation should match",
            NameMatcher.isNameMatch("Abhinandan Suhrud", "Abhinandhan Suhrud")
        )

        // Distinct names must not match
        assertFalse(
            "Distinct names must not match",
            NameMatcher.isNameMatch("Abhinandan Suhrud", "Rajesh Sharma")
        )
    }

    @Test
    fun testOwnerExtractor() {
        val certText = """
            CENTRAL BOARD OF SECONDARY EDUCATION
            Candidate's Name: Abhinandan Suhrud
            Father's Name: Sunil Suhrud
            Roll No: 987654
        """.trimIndent()

        val owner = OwnerExtractor.extractOwner(certText)
        assertNotNull(owner)
        assertEquals("Abhinandan Suhrud", owner?.ownerName)
    }

    @Test
    fun testFactConsistencyChecker() {
        val factPan = FactEntity(
            id = 1,
            documentId = 101,
            factType = "PAN",
            fieldName = "date_of_birth",
            fieldValue = "15/08/1998",
            ownerName = "Abhinandan Suhrud"
        )

        val factAadhaar = FactEntity(
            id = 2,
            documentId = 102,
            factType = "AADHAAR",
            fieldName = "date_of_birth",
            fieldValue = "16/08/1998",
            ownerName = "Abhinandan Suhrud"
        )

        val conflicts = FactConsistencyChecker.findDisagreements(listOf(factPan, factAadhaar))
        assertEquals(1, conflicts.size)
        assertEquals("date_of_birth", conflicts[0].fieldName)
        assertEquals("HIGH", conflicts[0].severity.name)
    }

    @Test
    fun testIdMasker() {
        val maskedAadhaar = IdMasker.mask("2345 6789 0123", "AADHAAR")
        assertEquals("•••• •••• 0123", maskedAadhaar)

        val maskedPan = IdMasker.mask("ABCDE1234F", "PAN")
        assertEquals("••••••234F", maskedPan)
    }

    @Test
    fun testProvenanceTracker() {
        val primaryMarksheetFact = FactEntity(
            id = 1,
            documentId = 10,
            factType = "MARKSHEET",
            fieldName = "percentage",
            fieldValue = "92%",
            isPrimarySource = true,
            confidence = 0.98f
        )

        val secondaryCvFact = FactEntity(
            id = 2,
            documentId = 20,
            factType = "CV",
            fieldName = "percentage",
            fieldValue = "92%",
            isPrimarySource = false,
            confidence = 0.85f
        )

        val docNames = mapOf(10L to "12th_marksheet.pdf", 20L to "My_Resume.pdf")
        val provenance = ProvenanceTracker.resolvePrimaryFact(
            listOf(secondaryCvFact, primaryMarksheetFact),
            docNames
        )

        assertNotNull(provenance)
        assertTrue(provenance!!.isPrimarySource)
        assertEquals("12th_marksheet.pdf", provenance.sourceDocumentName)
    }

    @Test
    fun testOwnerDisambiguation() {
        val owners = listOf("Abhinandan Suhrud", "Sunil Suhrud", "Pooja Sharma")
        val candidates = OwnerDisambiguation.findCandidates(
            query = "Abhinandan",
            availableOwners = owners,
            profileName = "Abhinandan Suhrud"
        )

        assertEquals(1, candidates.size)
        assertEquals("Abhinandan Suhrud", candidates[0].ownerName)
        assertTrue(candidates[0].isPrimaryProfile)
    }
}
