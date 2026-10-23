package com.suhrud.docsy

import com.suhrud.docsy.classification.GeneralCategory
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.extractor.DocumentLayoutType
import com.suhrud.docsy.extractor.LayoutDetector
import com.suhrud.docsy.extractor.LayoutPageAnalysisResult
import com.suhrud.docsy.knowledge.DateSemanticRole
import com.suhrud.docsy.knowledge.DeadlineExtractor
import com.suhrud.docsy.knowledge.InsightType
import com.suhrud.docsy.knowledge.ProactiveInsightsManager
import com.suhrud.docsy.knowledge.UnifiedKnowledgeIndex
import com.suhrud.docsy.knowledge.UserKnowledgeManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase7And8IntelligenceTest {

    @Before
    fun setUp() {
        UserKnowledgeManager.clear()
    }

    @After
    fun tearDown() {
        UserKnowledgeManager.clear()
    }

    @Test
    fun testLayoutDetectorSingleColumnVsScannedPage() {
        val emptyAnalysis = LayoutPageAnalysisResult(1, "", emptyList(), emptyList(), emptyList(), emptyList())
        val scannedLayout = LayoutDetector.detectPageLayout(emptyAnalysis)
        assertEquals(DocumentLayoutType.SCANNED_IMAGE_PAGE, scannedLayout.layoutType)

        val textAnalysis = LayoutPageAnalysisResult(
            pageNumber = 1,
            fullText = "Central Board Statement of Marks",
            blocks = emptyList(),
            tables = emptyList(),
            keyValues = emptyList(),
            lines = listOf(
                com.suhrud.docsy.extractor.LayoutLine(1, 1, 10f, 10f, "Central Board Statement of Marks")
            )
        )
        val textLayout = LayoutDetector.detectPageLayout(textAnalysis)
        assertEquals(DocumentLayoutType.SINGLE_COLUMN_TEXT, textLayout.layoutType)
    }

    @Test
    fun testDeadlineExtractorDateRoleDisambiguation() {
        val sampleText = """
            Invoice Date: 01/10/2024
            Payment Due Date: 15/10/2024
            Expiry Date: 31/12/2025
        """.trimIndent()

        val dates = DeadlineExtractor.extractDatesAndDeadlines(sampleText, "invoice.pdf")

        assertEquals(3, dates.size)

        val dueRecord = dates.firstOrNull { it.semanticRole == DateSemanticRole.DUE_DATE }
        assertNotNull("Due date must be extracted", dueRecord)
        assertTrue("Due date must be flagged as deadline", dueRecord?.isDeadline == true)
        assertEquals("15/10/2024", dueRecord?.rawText)

        val expRecord = dates.firstOrNull { it.semanticRole == DateSemanticRole.EXPIRY_DATE }
        assertNotNull("Expiry date must be extracted", expRecord)
        assertTrue("Expiry date must be flagged as deadline", expRecord?.isDeadline == true)

        val issueRecord = dates.firstOrNull { it.semanticRole == DateSemanticRole.ISSUE_DATE }
        assertNotNull("Issue date must be extracted", issueRecord)
        assertFalse("Issue date must NOT be flagged as deadline", issueRecord?.isDeadline == true)
    }

    @Test
    fun testUnifiedKnowledgeIndexAndReviewQueue() {
        val docAcademic = DocumentEntity(
            id = 1L, path = "/m1.pdf", name = "marksheet_12th.pdf", ext = "pdf", mime = "application/pdf",
            size = 1000L, modified = 100L, docType = "ACADEMIC:MARKSHEET", state = "INDEXED",
            extractedText = "Statement of Marks Due Date: 15/10/2024"
        )

        val docFailed = DocumentEntity(
            id = 2L, path = "/failed.pdf", name = "corrupted.pdf", ext = "pdf", mime = "application/pdf",
            size = 500L, modified = 100L, docType = "OTHER:UNKNOWN", state = "FAILED",
            extractedText = ""
        )

        val view = UnifiedKnowledgeIndex.buildIndex(listOf(docAcademic, docFailed))

        assertEquals(2, view.totalDocuments)
        assertTrue("Academic category group must contain doc1", view.categoryGroups[GeneralCategory.ACADEMIC]?.contains(docAcademic) == true)
        assertEquals(1, view.reviewQueue.size)
        assertEquals("corrupted.pdf", view.reviewQueue.first().documentName)

        val insights = ProactiveInsightsManager.generateInsights(view, listOf(docAcademic, docFailed))
        assertTrue("Proactive insights must be generated", insights.isNotEmpty())
        assertTrue("Insights must contain deadline or review items", insights.any { it.insightType == InsightType.APPROACHING_DEADLINE || it.insightType == InsightType.UNREVIEWED_DOCUMENT })
    }

    @Test
    fun testUserKnowledgeManagerMetadataPersistence() {
        val docId = 555L

        val initialMeta = UserKnowledgeManager.getMetadata(docId)
        assertFalse(initialMeta.isPinned)
        assertFalse(initialMeta.isBookmarked)

        val pinned = UserKnowledgeManager.togglePin(docId)
        assertTrue(pinned.isPinned)

        val bookmarked = UserKnowledgeManager.toggleBookmark(docId)
        assertTrue(bookmarked.isBookmarked)

        UserKnowledgeManager.addUserTopic(docId, "Important Academics")
        UserKnowledgeManager.setUserNote(docId, "Verified against physical certificate")

        val finalMeta = UserKnowledgeManager.getMetadata(docId)
        assertTrue(finalMeta.isPinned)
        assertTrue(finalMeta.isBookmarked)
        assertTrue(finalMeta.userTopics.contains("Important Academics"))
        assertEquals("Verified against physical certificate", finalMeta.userNote)
    }
}
