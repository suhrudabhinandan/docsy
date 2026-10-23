package com.suhrud.docsy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.understanding.NameMatcher
import com.suhrud.docsy.understanding.RelationalUnderstandingEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RelationalUnderstandingTest {

    private lateinit var db: DocsyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DocsyDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Same person across three documents
    @Test
    fun testSamePersonAcrossThreeDocuments() = runBlocking {
        val personName = "Suhrud Abhinandan"

        // Doc 1: Marksheet
        RelationalUnderstandingEngine.processDocumentRelations(
            db = db,
            docId = 1L,
            docType = "marksheet 10/12/semester",
            cleanText = "STATEMENT OF MARKS CBSE Board Class XII Suhrud Abhinandan",
            fields = listOf(
                FieldEntity(documentId = 1L, key = "roll_no", value = "12646545")
            ),
            ownerName = personName
        )

        // Doc 2: CV
        RelationalUnderstandingEngine.processDocumentRelations(
            db = db,
            docId = 2L,
            docType = "CV",
            cleanText = "CURRICULUM VITAE Suhrud Abhinandan B.Tech Computer Science",
            fields = emptyList(),
            ownerName = personName
        )

        // Doc 3: Aadhaar
        RelationalUnderstandingEngine.processDocumentRelations(
            db = db,
            docId = 3L,
            docType = "Aadhaar",
            cleanText = "Mera Aadhaar Meri Pehchan Suhrud Abhinandan 2345 6789 0123",
            fields = listOf(
                FieldEntity(documentId = 3L, key = "aadhaar", value = "2345 6789 0123")
            ),
            ownerName = personName
        )

        val entity = db.entityDao().getEntityByTypeAndName("person", "suhrud abhinandan")
        assertNotNull("Person entity must exist in entities table", entity)

        val docIds = db.docEntityDao().getDocIdsForEntity(entity!!.id)
        assertEquals("Entity must link to all 3 documents", 3, docIds.size)
        assertTrue("Linked docs must contain doc 1", docIds.contains(1L))
        assertTrue("Linked docs must contain doc 2", docIds.contains(2L))
        assertTrue("Linked docs must contain doc 3", docIds.contains(3L))
    }

    // 2. Semester ordering, "previous" and "compare" queries
    @Test
    fun testSemesterOrderingAndCompareQueries() {
        val doc1Fields = mapOf("semester" to "Semester 1", "percentage" to "82.5%", "total_marks" to "412")
        val doc2Fields = mapOf("semester" to "Semester 2", "percentage" to "88.0%", "total_marks" to "440")
        val doc3Fields = mapOf("semester" to "Semester 3", "percentage" to "85.0%", "total_marks" to "425")

        val docs = listOf(
            Triple(3L, "marksheet 10/12/semester", doc3Fields),
            Triple(1L, "marksheet 10/12/semester", doc1Fields),
            Triple(2L, "marksheet 10/12/semester", doc2Fields)
        )

        val timeline = RelationalUnderstandingEngine.buildTimeline("Suhrud Abhinandan", docs)

        assertEquals("Timeline must be ordered by semester sequence", 3, timeline.size)
        assertEquals("First item must be Semester 1", 1, timeline[0].sequenceNumber)
        assertEquals("Second item must be Semester 2", 2, timeline[1].sequenceNumber)
        assertEquals("Third item must be Semester 3", 3, timeline[2].sequenceNumber)

        // Test "previous" query
        val prevResult = RelationalUnderstandingEngine.resolveTimelineQuery(timeline, "what was my previous semester result")
        assertNotNull(prevResult)
        assertEquals("Target item must be latest (Semester 3)", 3, prevResult!!.targetItem?.sequenceNumber)
        assertEquals("Comparison item must be previous (Semester 2)", 2, prevResult.comparisonItem?.sequenceNumber)

        // Test "compare" query
        val compareResult = RelationalUnderstandingEngine.resolveTimelineQuery(timeline, "compare 1st sem with 2nd sem")
        assertNotNull(compareResult)
        assertTrue("Working formula must be computed in code", compareResult!!.working.contains("-"))
    }

    // 3. Conflicting names that must NOT merge
    @Test
    fun testConflictingNamesDoNotMerge() {
        val name1 = "Suhrud Abhinandan"
        val name2 = "Rahul Sharma"

        assertFalse("Different names must not match", NameMatcher.isNameMatch(name1, name2))

        val norm1 = NameMatcher.normalizeName(name1)
        val norm2 = NameMatcher.normalizeName(name2)
        assertFalse("Normalized different names must not match", norm1 == norm2)
    }

    // 4. CV mention must NOT link to a marksheet as owner/subject
    @Test
    fun testCvMentionDoesNotLinkToMarksheet() = runBlocking {
        // CV text mentioning a 12th marksheet
        val cvText = "CURRICULUM VITAE Rahul Sharma. Education: Class 12th CBSE Board 88% marksheet attached."
        val docType = "CV"

        RelationalUnderstandingEngine.processDocumentRelations(
            db = db,
            docId = 10L,
            docType = docType,
            cleanText = cvText,
            fields = emptyList(),
            ownerName = "Rahul Sharma"
        )

        val docEntities = db.docEntityDao().getEntitiesForDoc(10L)
        val ownerEntity = docEntities.find { it.role == "owner" }
        assertNotNull("CV owner must be Rahul Sharma", ownerEntity)

        val entityRec = db.entityDao().getEntityById(ownerEntity!!.entityId)
        assertEquals("Owner canonical name must be rahul sharma", "rahul sharma", entityRec?.canonicalName)
    }

    // 5. Derived facts calculation: Age from DOB and Tenure
    @Test
    fun testDerivedFactsCalculation() {
        val fields = mapOf(
            "dob" to "15/08/1998",
            "start_date" to "01/01/2020",
            "end_date" to "01/01/2023"
        )

        val derived = RelationalUnderstandingEngine.computeDerivedFacts(fields)
        assertTrue("Derived facts must contain age", derived.containsKey("age"))
        assertTrue("Derived facts must contain tenure", derived.containsKey("tenure"))
        assertEquals("36 months", derived["tenure"])
    }
}
