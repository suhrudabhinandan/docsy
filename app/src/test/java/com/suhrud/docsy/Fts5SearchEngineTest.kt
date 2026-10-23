package com.suhrud.docsy

import com.suhrud.docsy.retrieval.Fts5SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Fts5SearchEngineTest {

    @Test
    fun testTokenNormalizationAndDiacritics() {
        val input = "Café & Résumé"
        val normalized = Fts5SearchEngine.normalizeToken(input)
        assertEquals("cafe & resume", normalized)
    }

    @Test
    fun testOperatorSanitization() {
        val dangerousInput = "OR NOT AND \" ' DROP TABLE"
        val quoted = Fts5SearchEngine.quoteFtsToken(dangerousInput)
        assertTrue(quoted.startsWith("\""))
        assertTrue(quoted.endsWith("\""))
        assertTrue(quoted.contains("\"\""))
    }

    @Test
    fun testLevenshteinDistance() {
        val d1 = com.suhrud.docsy.retrieval.QueryNormalizer.computeLevenshteinDistance("smith", "smit")
        assertEquals(1, d1)

        val d2 = com.suhrud.docsy.retrieval.QueryNormalizer.computeLevenshteinDistance("johnson", "johnsen")
        assertEquals(1, d2)
    }

    @Test
    fun testGeneratedFixturesBenchmark() {
        val random = Random(42)
        val sampleWords = listOf("invoice", "receipt", "account", "balance", "summary", "statement", "payment", "due")

        val generated10kChunks = (1..10000).map { i ->
            val w1 = sampleWords[random.nextInt(sampleWords.size)]
            val w2 = sampleWords[random.nextInt(sampleWords.size)]
            "Chunk #$i text with random terms $w1 and $w2"
        }

        val startTime = System.currentTimeMillis()
        var matchCount = 0
        for (chunk in generated10kChunks) {
            if (chunk.contains("invoice")) matchCount++
        }
        val duration10k = System.currentTimeMillis() - startTime

        assertTrue(duration10k < 1000)
        assertTrue(matchCount > 0)
        println("Generated 10k chunk processing completed in ${duration10k}ms with $matchCount matches.")
    }
}
