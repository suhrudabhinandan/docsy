package com.suhrud.docsy

import com.suhrud.docsy.domain.ai.llm.ResponseComposer
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import com.suhrud.docsy.domain.query.SemanticIntentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingsAndSmallTalkTest {

    @Test
    fun test40GreetingsAndSmallTalkPhrases() {
        val phrases = listOf(
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening", "good night",
            "namaste", "shubh prabhat", "kaise ho", "how are you", "who are you", "what can you do",
            "help", "thanks", "thank you", "dhanyawad", "bye", "goodbye", "ok", "cool", "hmm",
            "HI!", "Hello Docsy", "hey there", "Good Morning!", "good morning docsy", "namaste ji",
            "kaise ho aap", "how are you doing", "what can you do for me", "thank you so much",
            "dhanyawad ji", "bye bye", "ok cool", "nice", "great", "sorry", "hello!", "hey docsy"
        )

        assertTrue("Must have at least 40 test phrases", phrases.size >= 40)

        for (phrase in phrases) {
            val isST = SemanticIntentClassifier.isSmallTalk(phrase)
            val parsed = QueryParser.parse(phrase)

            assertTrue("Phrase '$phrase' should be classified as SMALL_TALK", isST || parsed.intent == QueryIntent.SMALL_TALK)
            assertEquals("Phrase '$phrase' parsed intent must be SMALL_TALK", QueryIntent.SMALL_TALK, parsed.intent)

            val reply = ResponseComposer.composeSmallTalk(phrase, "TestUser")
            assertNull("Small talk reply for '$phrase' must NOT contain source document", reply.sourceDocument)
            assertFalse("Small talk reply for '$phrase' must NOT contain answer highlight card", reply.answerHighlight != null)
        }
    }

    @Test
    fun testZeroConstraintsQueryHasNoDocumentSearch() {
        val parsed = QueryParser.parse("unusual text string")
        assertFalse("Query with no document keywords should have hasConstraints = false", parsed.hasConstraints)
    }
}
