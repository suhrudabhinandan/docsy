package com.suhrud.docsy

import com.suhrud.docsy.domain.query.EnsembleRouter
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.RoutingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTraceTest {

    @Test
    fun testRouterTracesForFailingQueries() {
        val testQueries = listOf(
            "hi" to QueryIntent.SMALL_TALK,
            "good" to QueryIntent.SMALL_TALK,
            "internet speed?" to QueryIntent.DEVICE_INFO,
            "wifi connection?" to QueryIntent.DEVICE_INFO,
            "wifi connection info?" to QueryIntent.DEVICE_INFO,
            "display info" to QueryIntent.DEVICE_INFO,
            "photos?" to QueryIntent.DEVICE_STATS
        )

        for ((query, expectedIntent) in testQueries) {
            val trace = EnsembleRouter.routeQuery(query)
            println("Query: '$query' -> Final Decision: ${trace.finalDecision.intent} (${trace.reason})")

            assertTrue("Query '$query' decision type should be ANSWERABLE or SMALL_TALK",
                trace.finalDecision.type == RoutingType.ANSWERABLE || trace.finalDecision.type == RoutingType.SMALL_TALK)
            assertEquals("Query '$query' should map to $expectedIntent", expectedIntent, trace.finalDecision.intent)
            assertFalse("Non-document query '$query' must never map to FIND_DOCUMENT_INFO",
                trace.finalDecision.intent == QueryIntent.FIND_DOCUMENT_INFO)
        }
    }
}
