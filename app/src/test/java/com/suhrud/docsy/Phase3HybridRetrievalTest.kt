package com.suhrud.docsy

import com.suhrud.docsy.extractor.ExtractedPage
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.models.ExtractedCell
import com.suhrud.docsy.extractor.models.ExtractedRow
import com.suhrud.docsy.extractor.models.ExtractedTable
import com.suhrud.docsy.extractor.models.ExtractedWorksheet
import com.suhrud.docsy.retrieval.DocumentChunker
import com.suhrud.docsy.retrieval.LocalEmbeddingManager
import com.suhrud.docsy.retrieval.MatchMethod
import com.suhrud.docsy.retrieval.ModelAvailabilityState
import com.suhrud.docsy.retrieval.VectorEntry
import com.suhrud.docsy.retrieval.VectorStore
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

@RunWith(RobolectricTestRunner::class)
class Phase3HybridRetrievalTest {

    @Before
    fun setUp() {
        VectorStore.clear()
        LocalEmbeddingManager.initBuiltIn(128)
    }

    @After
    fun tearDown() {
        VectorStore.clear()
        LocalEmbeddingManager.close()
    }

    @Test
    fun testLocalEmbeddingManagerLifecycleAndState() {
        assertEquals(ModelAvailabilityState.READY, LocalEmbeddingManager.getState())
        assertNotNull(LocalEmbeddingManager.getModelSpec())
        assertEquals(128, LocalEmbeddingManager.getModelSpec()?.dimension)
        assertTrue(LocalEmbeddingManager.getModelSpec()?.isBuiltIn == true)

        val vec = LocalEmbeddingManager.embed("Docsy offline document intelligence search")
        assertEquals(128, vec.size)

        // Verify L2 normalization
        var sumSq = 0.0f
        for (v in vec) {
            sumSq += v * v
        }
        assertTrue("Vector norm should be approximately 1.0", Math.abs(Math.sqrt(sumSq.toDouble()) - 1.0) < 1e-3)
    }

    @Test
    fun testEmbeddingEngineGracefulFailureWhenClosed() {
        LocalEmbeddingManager.close()
        assertEquals(ModelAvailabilityState.NOT_INSTALLED, LocalEmbeddingManager.getState())

        val vec = LocalEmbeddingManager.embed("test query")
        assertTrue("Closed embedding manager should return empty vector", vec.isEmpty())
    }

    @Test
    fun testVectorOperationsSafetyZeroAndNanVectors() {
        val zeroVec = FloatArray(128) { 0.0f }
        val normalVec = LocalEmbeddingManager.embed("normal query text")

        val simWithZero = LocalEmbeddingManager.cosineSimilarity(zeroVec, normalVec)
        assertEquals(0.0f, simWithZero, 1e-5f)

        val nanVec = FloatArray(128) { Float.NaN }
        val simWithNan = LocalEmbeddingManager.cosineSimilarity(nanVec, normalVec)
        assertEquals(0.0f, simWithNan, 1e-5f)
    }

    @Test
    fun testDocumentChunkingParagraphsAndPages() {
        val page1Text = """
            Central Board of Secondary Education
            Statement of Marks
            Class XII Examination 2024
        """.trimIndent()

        val page2Text = """
            Subject: Mathematics
            Marks Obtained: 95
            Status: PASS
        """.trimIndent()

        val extractionResult = ExtractionResult(
            contentKey = "doc_1",
            extension = "pdf",
            pageCount = 2,
            fullText = "$page1Text\n\n$page2Text",
            pages = listOf(
                ExtractedPage(pageNumber = 1, text = page1Text),
                ExtractedPage(pageNumber = 2, text = page2Text)
            ),
            contentHash = "hash123",
            durationMs = 100L
        )

        val chunks = DocumentChunker.chunkDocument(
            documentId = 101L,
            extractionResult = extractionResult
        )

        assertTrue("Chunks should not be empty", chunks.isNotEmpty())
        assertEquals(101L, chunks.first().documentId)
        assertTrue("Content hash must be calculated", chunks.first().contentHash.isNotBlank())
    }

    @Test
    fun testWorksheetTableChunkingPreservesHeaderContext() {
        val sheet = ExtractedWorksheet(
            sheetName = "Marksheet",
            tables = listOf(
                ExtractedTable(
                    headers = listOf("Subject", "Max Marks", "Obtained Marks"),
                    rows = listOf(
                        ExtractedRow(rowIndex = 1, cells = listOf(
                            ExtractedCell("A1", "Subject"), ExtractedCell("B1", "Max Marks"), ExtractedCell("C1", "Obtained Marks")
                        ), isHeader = true),
                        ExtractedRow(rowIndex = 2, cells = listOf(
                            ExtractedCell("A2", "Mathematics"), ExtractedCell("B2", "100"), ExtractedCell("C2", "95")
                        ), isHeader = false)
                    )
                )
            ),
            cells = emptyList()
        )

        val dummyResult = ExtractionResult("key", "xlsx", 1, "", emptyList(), emptyList(), "h", 10L)
        val chunks = DocumentChunker.chunkDocument(
            documentId = 202L,
            extractionResult = dummyResult,
            worksheets = listOf(sheet)
        )

        assertTrue("Worksheet chunk must exist", chunks.isNotEmpty())
        val chunkText = chunks.first().text
        assertTrue("Chunk text must preserve worksheet name context", chunkText.contains("Worksheet: Marksheet"))
        assertTrue("Chunk text must preserve column headers context", chunkText.contains("Headers: Subject, Max Marks, Obtained Marks"))
        assertTrue("Chunk text must contain cell values across row chunks", chunks.any { it.text.contains("Mathematics") })
    }

    @Test
    fun testVectorStoreInMemSearchAndCosineRanking() {
        val vec1 = LocalEmbeddingManager.embed("Mathematics examination score percentage")
        val vec2 = LocalEmbeddingManager.embed("Electricity utility bill payment invoice")

        val entry1 = VectorEntry(
            chunkId = 1L,
            documentId = 10L,
            vector = vec1,
            dimension = 128,
            modelId = "docsy-ngram-v1",
            modelVersion = "1.0.0",
            text = "Mathematics examination score"
        )

        val entry2 = VectorEntry(
            chunkId = 2L,
            documentId = 11L,
            vector = vec2,
            dimension = 128,
            modelId = "docsy-ngram-v1",
            modelVersion = "1.0.0",
            text = "Electricity utility bill"
        )

        VectorStore.put(entry1)
        VectorStore.put(entry2)

        assertEquals(2, VectorStore.size())

        val queryVec = LocalEmbeddingManager.embed("maths score marksheet")
        val searchResults = VectorStore.search(queryVec, topK = 5, modelId = "docsy-ngram-v1")

        assertTrue("Vector search results must not be empty", searchResults.isNotEmpty())
        assertEquals("Top vector match must be the mathematics document", 1L, searchResults.first().entry.chunkId)
        assertTrue("Similarity score must be positive", searchResults.first().similarityScore > 0f)
    }
}
