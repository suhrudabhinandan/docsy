package com.suhrud.docsy

import com.suhrud.docsy.eval.AccuracyEvaluator
import com.suhrud.docsy.eval.EvaluationCorpus
import com.suhrud.docsy.retrieval.LocalEmbeddingManager
import com.suhrud.docsy.retrieval.VectorEntry
import com.suhrud.docsy.retrieval.VectorStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase6AccuracyBenchmarkTest {

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
    fun testEvaluationCorpusAccuracyBenchmark() {
        val fixtures = EvaluationCorpus.getSyntheticFixtures()
        val report = AccuracyEvaluator.evaluateCorpus(fixtures)

        assertTrue("Corpus size must be at least 5 fixtures", report.totalFixturesEvaluated >= 5)
        assertTrue("Category classification accuracy must be >= 80%", report.categoryAccuracy >= 0.80f)
        assertTrue("Specific type classification accuracy must be >= 80%", report.specificTypeAccuracy >= 0.80f)
        assertTrue("Disambiguation accuracy must be 100% on synthetic benchmark", report.disambiguationAccuracy >= 1.0f)

        // Verify entity metrics
        assertNotNull("Entity metrics must be present", report.entityMetrics)
        val studentNameMetric = report.entityMetrics["STUDENT_NAME"]
        if (studentNameMetric != null) {
            assertTrue("Student name precision must be 100%", studentNameMetric.precision >= 1.0f)
            assertTrue("Student name recall must be 100%", studentNameMetric.recall >= 1.0f)
        }
    }

    @Test
    fun testVectorStoreInvalidationOnDocumentDelete() {
        val vec1 = LocalEmbeddingManager.embed("Docsy document search")
        val vec2 = LocalEmbeddingManager.embed("Financial tax invoice record")

        val entry1 = VectorEntry(10L, 100L, vec1, 128, "m1", "1.0", "Text 1")
        val entry2 = VectorEntry(11L, 100L, vec2, 128, "m1", "1.0", "Text 2")
        val entry3 = VectorEntry(12L, 200L, vec1, 128, "m1", "1.0", "Text 3")

        VectorStore.put(entry1)
        VectorStore.put(entry2)
        VectorStore.put(entry3)

        assertEquals(3, VectorStore.size())

        // Invalidate document 100
        VectorStore.removeByDocumentId(100L)

        assertEquals("Deleting document 100 should remove its 2 vectors, leaving 1 vector for document 200", 1, VectorStore.size())
        val remainingResults = VectorStore.search(vec1, topK = 5)
        assertEquals(200L, remainingResults.first().entry.documentId)
    }
}
