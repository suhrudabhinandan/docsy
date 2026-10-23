package com.suhrud.docsy

import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.QueryClass
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.retrieval.TopLevelQueryClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TopLevelQueryClassifierTest {

    @Test
    fun testChitchatPhrases() {
        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("hi"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("hi").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("hello there"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("hello there").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("hey docsy"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("hey docsy").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("good morning"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("good morning").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("good evening"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("good evening").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("namaste"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("namaste").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("ssup bro"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("ssup bro").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("yo"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("yo").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("kya haal hai"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("kya haal hai").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("kaise ho"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("kaise ho").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("thanks"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("thanks").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("thank you very much"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("thank you very much").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("shukriya"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("shukriya").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("dhanyawad"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("dhanyawad").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("tell me a joke"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("tell me a joke").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("say something funny"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("say something funny").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("who are you"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("who are you").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("tum kaun ho"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("tum kaun ho").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("you are awesome"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("you are awesome").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("cool thanks"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("cool thanks").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("are you single"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("are you single").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("do you like music"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("do you like music").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("bye bye"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("bye bye").outcome)

        assertEquals(QueryClass.CHITCHAT, TopLevelQueryClassifier.classify("alvida"))
        assertEquals(RouterOutcome.SMALL_TALK, IntentRouter.route("alvida").outcome)
    }

    @Test
    fun testPhoneQuery20Phrases() {
        val phonePhrases = listOf(
            "mera 12th marksheet",
            "where is my aadhaar card",
            "how many steps walked today",
            "battery kitni hai",
            "call log of rahul",
            "show unread sms",
            "find my pan card",
            "what time is it",
            "calculate 200 + 500",
            "what is my date of birth",
            "tell me my father's name",
            "what is the largest file on my phone",
            "ram usage status",
            "wifi connectivity status",
            "list my contacts",
            "what is my profile name",
            "how many photos on my phone",
            "show my electricity bill",
            "where is my driving license",
            "show phone hardware specs",
            "messages from mom",
            "how much storage space is left"
        )

        for (phrase in phonePhrases) {
            val qClass = TopLevelQueryClassifier.classify(phrase)
            assertEquals("Expected PHONE_QUERY for '$phrase'", QueryClass.PHONE_QUERY, qClass)

            val decision = IntentRouter.route(phrase)
            assertNotEquals("Phone query should not be SMALL_TALK for '$phrase'", RouterOutcome.SMALL_TALK, decision.outcome)
            assertNotEquals("Phone query should not be GENERAL_KNOWLEDGE for '$phrase'", RouterOutcome.GENERAL_KNOWLEDGE, decision.outcome)
        }
    }

    @Test
    fun testGeneralQuestion20Phrases() {
        val generalPhrases = listOf(
            "explain quantum physics",
            "write a poem about rain",
            "what is photosynthesis",
            "how does a car engine work",
            "what is gravity",
            "write an essay on pollution",
            "what is the capital of France",
            "who wrote Hamlet",
            "tell me a story about space",
            "how to bake a chocolate cake",
            "what is python programming language",
            "explain machine learning",
            "write a python script for binary search",
            "summarize theory of relativity",
            "what is inflation in economics",
            "how do airplanes fly",
            "translate hello to Spanish",
            "what is the speed of light",
            "why is the sky blue",
            "how does solar energy work",
            "explain how photosynthesis works",
            "write an essay on climate change"
        )

        for (phrase in generalPhrases) {
            val qClass = TopLevelQueryClassifier.classify(phrase)
            assertEquals("Expected GENERAL_QUESTION for '$phrase'", QueryClass.GENERAL_QUESTION, qClass)

            val decision = IntentRouter.route(phrase)
            assertEquals("Expected GENERAL_KNOWLEDGE outcome for '$phrase'", RouterOutcome.GENERAL_KNOWLEDGE, decision.outcome)
            assertNotEquals("GENERAL_QUESTION must never touch document search", DocsyIntent.DOCUMENT_SEARCH_LEXICAL, decision.intent)
            assertNotEquals("GENERAL_QUESTION must never touch document facts", DocsyIntent.DOCUMENT_FACT_QUERY, decision.intent)
        }
    }

    @Test
    fun testAmbiguousDeviceQueryAsksClarification() {
        val ambiguousPhrases = listOf(
            "my certificate",
            "my document",
            "find file",
            "search file"
        )

        for (phrase in ambiguousPhrases) {
            val qClass = TopLevelQueryClassifier.classify(phrase)
            assertEquals("Expected AMBIGUOUS for '$phrase'", QueryClass.AMBIGUOUS, qClass)

            val decision = IntentRouter.route(phrase)
            assertEquals("Expected NEEDS_CLARIFICATION for '$phrase'", RouterOutcome.NEEDS_CLARIFICATION, decision.outcome)
            assertEquals("Which document or certificate would you like me to look for?", decision.targetField)
        }
    }
}
