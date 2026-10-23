package com.suhrud.docsy

import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.FieldResolver
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.QueryNormalizer
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.ui.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase15EvaluationSuiteTest {

    @Test
    fun testEvaluationSuiteIntentClassificationAccuracy() {
        val testCases = listOf(
            // Greetings and small talk
            "hi" to DocsyIntent.GREETING,
            "hello" to DocsyIntent.GREETING,
            "good morning" to DocsyIntent.GREETING,
            "how are you" to DocsyIntent.HOW_ARE_YOU,
            "who are you" to DocsyIntent.WHO_ARE_YOU,
            "thank you very much" to DocsyIntent.GRATITUDE,
            "bye" to DocsyIntent.FAREWELL,

            // Profile
            "what is my name" to DocsyIntent.USER_NAME_QUERY,
            "who am i" to DocsyIntent.USER_NAME_QUERY,

            // Time vs DOB (R8)
            "what time is it" to DocsyIntent.SYSTEM_TIME,
            "current time" to DocsyIntent.SYSTEM_TIME,
            "what is today's date" to DocsyIntent.SYSTEM_DATE,
            "my date of birth" to DocsyIntent.DOCUMENT_FACT_QUERY,
            "when is my birthday" to DocsyIntent.DOCUMENT_FACT_QUERY,

            // Hardware & telemetry
            "how much battery left" to DocsyIntent.BATTERY_STATUS,
            "is wifi connected" to DocsyIntent.NETWORK_STATUS,
            "is bluetooth on" to DocsyIntent.BLUETOOTH_STATUS,
            "how much ram is used" to DocsyIntent.MEMORY_RAM_STATUS,
            "how many steps today" to DocsyIntent.STEP_COUNT_QUERY,

            // Media & Storage Collision
            "how many videos on my device" to DocsyIntent.MEDIA_CATEGORY_COUNT,
            "how many photos" to DocsyIntent.MEDIA_CATEGORY_COUNT,
            "what is my largest file" to DocsyIntent.STORAGE_SUPERLATIVE_LARGEST,
            "what is my newest file" to DocsyIntent.STORAGE_SUPERLATIVE_NEWEST,

            // Documents & Facts
            "where is my 12th marksheet" to DocsyIntent.DOCUMENT_FACT_QUERY,
            "find my aadhaar number" to DocsyIntent.DOCUMENT_ID_LOOKUP,
            "where is my pan card" to DocsyIntent.DOCUMENT_ID_LOOKUP,
            "show my resume" to DocsyIntent.DOCUMENT_FACT_QUERY,
            "bijli bill amount" to DocsyIntent.DOCUMENT_FACT_QUERY,

            // Calculator
            "calculate 2500 * 18" to DocsyIntent.CALCULATOR_EVALUATION,
            "average of 80, 90, 85" to DocsyIntent.CALCULATOR_EVALUATION,

            // Out of Scope
            "what is the weather in Delhi" to DocsyIntent.OUT_OF_SCOPE,
            "cricket score today" to DocsyIntent.OUT_OF_SCOPE
        )

        var passed = 0
        for ((query, expectedIntent) in testCases) {
            val decision = IntentRouter.route(query)
            if (decision.intent == expectedIntent) {
                passed++
            }
        }

        val accuracy = (passed.toDouble() / testCases.size.toDouble()) * 100.0
        assertTrue("Intent classification accuracy must be >= 95% (got $accuracy%)", accuracy >= 95.0)
    }

    @Test
    fun testHinglishQuerySuite() {
        val hinglishQueries = listOf(
            "mera roll number kya hai",
            "bijli bill kitna hai",
            "janamdin kab hai",
            "kitne videos hain",
            "tareekh kya hai",
            "samay kya hai"
        )

        for (q in hinglishQueries) {
            val decision = IntentRouter.route(q)
            assertTrue("Hinglish query '$q' should resolve successfully", decision.outcome != RouterOutcome.UNRECOGNISED)
        }
    }

    @Test
    fun testMotionAnimationTokens() {
        assertEquals(120, Motion.DURATION_FAST)
        assertEquals(200, Motion.DURATION_NORMAL)
        assertEquals(320, Motion.DURATION_EMPHASIZED)
        assertNotNull(Motion.springStiff)
        assertNotNull(Motion.springGentle)
    }
}
