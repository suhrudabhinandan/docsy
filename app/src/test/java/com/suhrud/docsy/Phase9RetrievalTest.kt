package com.suhrud.docsy

import com.suhrud.docsy.retrieval.ConversationSessionContext
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.DocumentField
import com.suhrud.docsy.retrieval.FieldResolver
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.NotFoundReplyGenerator
import com.suhrud.docsy.retrieval.QueryNormalizer
import com.suhrud.docsy.retrieval.ReplyType
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.tools.TopicDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase9RetrievalTest {

    @Test
    fun testQueryNormalizerHinglishAndSpelling() {
        // Hinglish translation
        val norm1 = QueryNormalizer.normalize("mera 12th marksheet kya hai?")
        assertTrue(norm1.contains("my 12th marksheet what is"))

        val norm2 = QueryNormalizer.normalize("bijli bill dikhao")
        assertTrue(norm2.contains("electricity bill show me"))

        // Spelling correction
        val norm3 = QueryNormalizer.normalize("where is my aadhr and pancard?")
        assertTrue(norm3.contains("aadhaar"))
        assertTrue(norm3.contains("pan card"))

        val norm4 = QueryNormalizer.normalize("what is my markshet percentage?")
        assertTrue(norm4.contains("marksheet"))
    }

    @Test
    fun testFieldResolver() {
        assertEquals(DocumentField.PERCENTAGE, FieldResolver.resolveTargetField("what is my percentage?"))
        assertEquals(DocumentField.ROLL_NUMBER, FieldResolver.resolveTargetField("tell me my roll no"))
        assertEquals(DocumentField.DATE_OF_BIRTH, FieldResolver.resolveTargetField("when is my date of birth?"))
        assertEquals(DocumentField.FATHER_NAME, FieldResolver.resolveTargetField("what is my father's name?"))
        assertEquals(DocumentField.BILL_AMOUNT, FieldResolver.resolveTargetField("what is the due amount on electricity bill?"))
        assertEquals(DocumentField.TOTAL_MARKS, FieldResolver.resolveTargetField("what is my total marks obtained?"))
    }

    @Test
    fun testIntentRouterDeterministicOutcomes() {
        // 1. Small talk
        val r1 = IntentRouter.route("hello there!")
        assertEquals(RouterOutcome.SMALL_TALK, r1.outcome)
        assertEquals(DocsyIntent.GREETING, r1.intent)

        // 2. Profile query
        val r2 = IntentRouter.route("what is my name?")
        assertEquals(RouterOutcome.ANSWERABLE, r2.outcome)
        assertEquals(DocsyIntent.USER_NAME_QUERY, r2.intent)

        // 3. System clock vs DOB (Rule R8)
        val rClock = IntentRouter.route("what time is it?")
        assertEquals(RouterOutcome.ANSWERABLE, rClock.outcome)
        assertEquals(DocsyIntent.SYSTEM_TIME, rClock.intent)

        val rDob = IntentRouter.route("what is my date of birth?")
        assertEquals(RouterOutcome.ANSWERABLE, rDob.outcome)
        assertEquals(DocsyIntent.DOCUMENT_FACT_QUERY, rDob.intent) // Strictly routed to document facts!

        // 4. Collision resolution (9.6)
        val rMedia = IntentRouter.route("how many videos on my device?")
        assertEquals(RouterOutcome.ANSWERABLE, rMedia.outcome)
        assertEquals(DocsyIntent.MEDIA_CATEGORY_COUNT, rMedia.intent)

        val rSuperlative = IntentRouter.route("what is my largest file?")
        assertEquals(RouterOutcome.ANSWERABLE, rSuperlative.outcome)
        assertEquals(DocsyIntent.STORAGE_SUPERLATIVE_LARGEST, rSuperlative.intent)

        // 5. Device telemetry
        val rBattery = IntentRouter.route("how much battery left?")
        assertEquals(RouterOutcome.ANSWERABLE, rBattery.outcome)
        assertEquals(DocsyIntent.BATTERY_STATUS, rBattery.intent)

        val rSteps = IntentRouter.route("how many steps walked today?")
        assertEquals(RouterOutcome.ANSWERABLE, rSteps.outcome)
        assertEquals(DocsyIntent.STEP_COUNT_QUERY, rSteps.intent)

        // 6. Out of scope
        val rWeather = IntentRouter.route("what is the weather today?")
        assertEquals(RouterOutcome.OUT_OF_SCOPE, rWeather.outcome)
        assertEquals(DocsyIntent.OUT_OF_SCOPE, rWeather.intent)

        // 7. Calculator
        val rCalc = IntentRouter.route("calculate 1500 * 0.18")
        assertEquals(RouterOutcome.ANSWERABLE, rCalc.outcome)
        assertEquals(DocsyIntent.CALCULATOR_EVALUATION, rCalc.intent)
    }

    @Test
    fun testConversationSessionContext() {
        val session = ConversationSessionContext()
        session.updateContext(
            topic = TopicDomain.DOCUMENT_FACT,
            documentId = 42L,
            documentName = "Marksheet_Priya.pdf",
            ownerName = "Priya Sharma"
        )

        assertEquals("Priya Sharma", session.currentReferent.lastOwnerName)
        assertEquals(42L, session.currentReferent.lastDocumentId)

        // Follow-up pronoun resolution
        val resolvedOwner = session.resolveFollowUpOwner("what about her roll number?")
        assertEquals("Priya Sharma", resolvedOwner)
    }

    @Test
    fun testNotFoundReplyGeneratorGrounded() {
        val reply = NotFoundReplyGenerator.generateNotFound(
            subject = "CAT scorecard",
            targetField = "percentile",
            owner = "Abhinandan",
            totalIndexedFiles = 150
        )
        assertEquals(ReplyType.NOT_FOUND, reply.replyType)
        assertTrue(reply.primaryText.contains("CAT scorecard"))
        assertTrue(reply.primaryText.contains("Abhinandan"))
    }
}
