package com.suhrud.docsy

import com.suhrud.docsy.domain.ai.llm.ResponseComposer
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseComposerTest {

    @Test
    fun testVarietyEngineProducesAtLeast6DistinctRepliesFor10Invocations() {
        val replies = mutableSetOf<String>()
        for (i in 0 until 10) {
            val reply = ResponseComposer.composeSmallTalk("hi", "User")
            replies.add(reply.text)
        }

        assertTrue("10 calls should produce at least 6 distinct phrasings (produced ${replies.size})", replies.size >= 6)
    }

    @Test
    fun testHinglishDetection() {
        assertTrue(ResponseComposer.isHinglish("namaste ji kaise ho"))
        assertTrue(ResponseComposer.isHinglish("mera bill batao"))
    }
}
