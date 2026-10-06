package com.suhrud.docsy

import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import com.suhrud.docsy.domain.query.SemanticIntentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticIntentTest {

    @Test
    fun testDateTimeParaphrases() {
        val paraphrases = listOf(
            "time?",
            "what's the time right now",
            "current time",
            "tell me the time",
            "time now",
            "what is the time",
            "clock",
            "tell time",
            "what time is it",
            "current clock time"
        )

        for (phrase in paraphrases) {
            val parsed = QueryParser.parse(phrase)
            assertEquals("Phrase '$phrase' should resolve to DATE_TIME intent", QueryIntent.DATE_TIME, parsed.intent)
        }
    }

    @Test
    fun testStepsParaphrases() {
        val paraphrases = listOf(
            "how many steps today",
            "steps count",
            "walking distance",
            "how much did I walk",
            "my steps today",
            "step counter",
            "total steps",
            "did I walk today",
            "how many steps did I take",
            "walk count"
        )

        for (phrase in paraphrases) {
            val parsed = QueryParser.parse(phrase)
            assertEquals("Phrase '$phrase' should resolve to STEPS_QUERY intent", QueryIntent.STEPS_QUERY, parsed.intent)
        }
    }

    @Test
    fun testTypoToleranceAndNormalization() {
        val norm = SemanticIntentClassifier.normalizeQuery("what tiem is it?")
        assertEquals("what time is it", norm)

        val norm2 = SemanticIntentClassifier.normalizeQuery("how many stps today")
        assertEquals("how many steps today", norm2)
    }
}
