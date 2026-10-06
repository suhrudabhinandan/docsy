package com.suhrud.docsy

import com.suhrud.docsy.domain.query.SemanticIntentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenEndedDocumentTest {

    @Test
    fun testOpenEndedDocumentIdentityExtraction() {
        val identity1 = SemanticIntentClassifier.extractOpenEndedDocumentIdentity("where is my marriage certificate")
        assertEquals("marriage certificate", identity1)

        val identity2 = SemanticIntentClassifier.extractOpenEndedDocumentIdentity("show me my driving licence")
        assertEquals("driving licence", identity2)

        val identity3 = SemanticIntentClassifier.extractOpenEndedDocumentIdentity("my vehicle rc card")
        assertEquals("vehicle rc card", identity3)

        val identity4 = SemanticIntentClassifier.extractOpenEndedDocumentIdentity("find my passport")
        assertEquals("passport", identity4)
    }
}
