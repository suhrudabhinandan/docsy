package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.understanding.ComparisonOutcome
import com.suhrud.docsy.understanding.ConflictDetector
import com.suhrud.docsy.understanding.CrossDocumentComparator
import com.suhrud.docsy.understanding.DocumentRelationshipIndex
import com.suhrud.docsy.understanding.DuplicateDetector
import com.suhrud.docsy.understanding.DuplicateStatus
import com.suhrud.docsy.understanding.EntityResolver
import com.suhrud.docsy.understanding.LinkStrength
import com.suhrud.docsy.understanding.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase5CrossDocumentTest {

    @Test
    fun testEntityResolutionExactIdentifierAndNameMatch() {
        val matchResult = EntityResolver.resolveEntities(
            entityTypeA = "person",
            nameA = "Rahul Sharma",
            entityTypeB = "person",
            nameB = "RAHUL SHARMA",
            corroboratingIdA = "241023",
            corroboratingIdB = "241023"
        )

        assertEquals(MatchOutcome.EXACT_MATCH, matchResult.outcome)
        assertEquals(1.0f, matchResult.score, 1e-3f)
        assertTrue(matchResult.isIdentifierMatch)
    }

    @Test
    fun testEntityResolutionOrgAliasMatch() {
        val aliasResult = EntityResolver.resolveEntities(
            entityTypeA = "institution",
            nameA = "GEC",
            entityTypeB = "institution",
            nameB = "Gandhi Engineering College"
        )

        assertEquals(MatchOutcome.HIGH_CONFIDENCE_PROBABLE, aliasResult.outcome)
        assertEquals(0.90f, aliasResult.score, 1e-3f)
    }

    @Test
    fun testEntityResolutionConflictingIdentifiersRejection() {
        val conflictResult = EntityResolver.resolveEntities(
            entityTypeA = "person",
            nameA = "Rahul Sharma",
            entityTypeB = "person",
            nameB = "Rahul Sharma",
            corroboratingIdA = "241023",
            corroboratingIdB = "999999"
        )

        assertEquals(MatchOutcome.EXPLICITLY_CONFLICTING, conflictResult.outcome)
        assertEquals(0.0f, conflictResult.score, 1e-3f)
    }

    @Test
    fun testDifferentEntityTypesAreNeverMerged() {
        val diffTypes = EntityResolver.resolveEntities(
            entityTypeA = "person",
            nameA = "Tech Corp",
            entityTypeB = "institution",
            nameB = "Tech Corp"
        )

        assertEquals(MatchOutcome.UNRESOLVED, diffTypes.outcome)
        assertEquals(0.0f, diffTypes.score, 1e-3f)
    }

    @Test
    fun testDocumentRelationshipIndexCreation() {
        val link = DocumentRelationshipIndex.createLink(
            docIdA = 101L,
            docNameA = "marksheet_12th.pdf",
            docIdB = 102L,
            docNameB = "degree_cert.pdf",
            sharedKey = "REGISTRATION_NUMBER",
            sharedValue = "REG2024CS1023",
            isExactIdentifier = true
        )

        assertEquals(LinkStrength.STRONG_LINK, link.linkStrength)
        assertEquals(1.0f, link.score, 1e-3f)
        assertEquals("REGISTRATION_NUMBER", link.sharedIdentifierKey)
        assertTrue(link.evidenceText.contains("REG2024CS1023"))
    }

    @Test
    fun testCrossDocumentComparator() {
        val factA = FactEntity(documentId = 1L, key = "STUDENT_NAME", value = "Rahul Sharma", normalizedValue = "RAHUL SHARMA")
        val factB = FactEntity(documentId = 2L, key = "STUDENT_NAME", value = "Rahul Sharma", normalizedValue = "RAHUL SHARMA")

        val match = CrossDocumentComparator.compareFacts("STUDENT_NAME", factA, "doc1.pdf", factB, "doc2.pdf")
        assertEquals(ComparisonOutcome.EXACT_AGREEMENT, match.outcome)

        val factDiff = FactEntity(documentId = 3L, key = "STUDENT_NAME", value = "Suresh Sharma", normalizedValue = "SURESH SHARMA")
        val mismatch = CrossDocumentComparator.compareFacts("STUDENT_NAME", factA, "doc1.pdf", factDiff, "doc3.pdf")
        assertEquals(ComparisonOutcome.CONFIRMED_INCONSISTENCY, mismatch.outcome)
    }

    @Test
    fun testDuplicateDetectorExactAndSemesterSeparation() {
        val doc1 = DocumentEntity(
            id = 1L, path = "/path/m1.pdf", name = "m1.pdf", ext = "pdf", mime = "application/pdf",
            size = 1000L, modified = 100L, contentHash = "hash123", docType = "MARKSHEET",
            extractedText = "Class 10th Semester 1 Marksheet"
        )

        val doc1Copy = DocumentEntity(
            id = 2L, path = "/path/m1_copy.pdf", name = "m1_copy.pdf", ext = "pdf", mime = "application/pdf",
            size = 1000L, modified = 100L, contentHash = "hash123", docType = "MARKSHEET",
            extractedText = "Class 10th Semester 1 Marksheet"
        )

        val docSem2 = DocumentEntity(
            id = 3L, path = "/path/m2.pdf", name = "m2.pdf", ext = "pdf", mime = "application/pdf",
            size = 1000L, modified = 100L, contentHash = "hash999", docType = "MARKSHEET",
            extractedText = "Class 10th Semester 2 Marksheet"
        )

        val exactDup = DuplicateDetector.checkDuplicate(doc1, doc1Copy)
        assertEquals(DuplicateStatus.EXACT_DUPLICATE, exactDup.status)

        val semDiff = DuplicateDetector.checkDuplicate(doc1, docSem2)
        assertEquals(DuplicateStatus.DISTINCT, semDiff.status)
        assertTrue("Different semesters must be distinct", semDiff.explanation.contains("different academic semesters"))
    }

    @Test
    fun testConflictDetector() {
        val factsA = listOf(FactEntity(documentId = 1L, key = "STUDENT_NAME", value = "Rahul Sharma"))
        val factsB = listOf(FactEntity(documentId = 2L, key = "STUDENT_NAME", value = "Amit Sharma"))

        val conflicts = ConflictDetector.detectConflicts(
            sharedKey = "REGISTRATION_NUMBER",
            sharedValue = "REG2024CS1023",
            docNameA = "marksheet_12th.pdf",
            factsA = factsA,
            docNameB = "degree_cert.pdf",
            factsB = factsB
        )

        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals("STUDENT_NAME", conflict.conflictingKey)
        assertEquals("Rahul Sharma", conflict.valueA)
        assertEquals("Amit Sharma", conflict.valueB)
        assertTrue(conflict.neutralDescription.contains("Observed discrepancy"))
    }
}
