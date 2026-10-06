package com.suhrud.docsy

import com.suhrud.docsy.domain.query.EnsembleRouter
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.RoutingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class EvaluationHarnessTest {

    @Test
    fun testEvaluationDatasetAccuracyAndSafety() {
        val stream = javaClass.classLoader?.getResourceAsStream("eval_dataset.txt")
            ?: javaClass.getResourceAsStream("/eval_dataset.txt")
        requireNotNull(stream) { "eval_dataset.txt not found in test resources!" }

        val lines = InputStreamReader(stream).readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        println("Loaded ${lines.size} test queries from eval_dataset.txt")
        assertTrue("Dataset must contain at least 150 test queries", lines.size >= 150)

        var correctCount = 0
        var totalInScope = 0
        var nonDocLeaksToDocSearch = 0

        val perIntentTotal = mutableMapOf<QueryIntent, Int>()
        val perIntentCorrect = mutableMapOf<QueryIntent, Int>()

        for (line in lines) {
            val parts = line.split("|")
            if (parts.size != 2) continue
            val query = parts[0].trim()
            val expectedIntent = QueryIntent.valueOf(parts[1].trim())

            totalInScope++
            perIntentTotal[expectedIntent] = (perIntentTotal[expectedIntent] ?: 0) + 1

            val trace = EnsembleRouter.routeQuery(query)
            val actualIntent = trace.finalDecision.intent

            if (actualIntent == expectedIntent) {
                correctCount++
                perIntentCorrect[expectedIntent] = (perIntentCorrect[expectedIntent] ?: 0) + 1
            }

            // Safety Gate Check: Non-document queries must NEVER route to FIND_DOCUMENT_INFO
            val isDocIntent = expectedIntent == QueryIntent.FIND_DOCUMENT_INFO ||
                    expectedIntent == QueryIntent.FIND_PERSONAL_INFO ||
                    expectedIntent == QueryIntent.COMPARE_DOCUMENT_VALUES ||
                    expectedIntent == QueryIntent.FIND_SCHEDULE

            if (!isDocIntent && actualIntent == QueryIntent.FIND_DOCUMENT_INFO) {
                nonDocLeaksToDocSearch++
                println("SAFETY VIOLATION: Non-doc query '$query' ($expectedIntent) leaked to FIND_DOCUMENT_INFO!")
            }
        }

        val accuracy = (correctCount.toDouble() / totalInScope.toDouble()) * 100.0
        println("=== EVALUATION RESULTS ===")
        println("Total Queries Tested: $totalInScope")
        println("Correctly Classified: $correctCount")
        println("Overall Top-1 Accuracy: ${String.format("%.2f", accuracy)}%")
        println("Non-Document Leaks to Document Search: $nonDocLeaksToDocSearch")

        println("\nPer-Intent Accuracy:")
        for ((intent, total) in perIntentTotal) {
            val correct = perIntentCorrect[intent] ?: 0
            val pct = (correct.toDouble() / total.toDouble()) * 100.0
            println("  - $intent: $correct / $total (${String.format("%.1f", pct)}%)")
        }

        // BUILD GAUNTLET SAFETY CHECKS
        assertEquals("Non-document queries leaking to document search must be EXACTLY ZERO!", 0, nonDocLeaksToDocSearch)
        assertTrue("Top-1 overall accuracy must be >= 95.0% (Current: ${String.format("%.2f", accuracy)}%)", accuracy >= 95.0)
    }
}
