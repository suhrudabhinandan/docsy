package com.suhrud.docsy

import com.suhrud.docsy.domain.ai.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class IndianGovernmentIdTest {

    @Test
    fun testPanExtraction() {
        val text = "INCOME TAX DEPARTMENT GOVT OF INDIA ABCDE1234F"
        val pan = DocumentClassifier.extractPan(text)
        assertEquals("ABCDE1234F", pan)
    }

    @Test
    fun testRelationshipLineExclusionFromOwner() {
        val text = """
            GOVERNMENT OF INDIA
            Name: Alex Johnson
            S/O: Ramesh Johnson
            DOB: 15/08/1998
        """.trimIndent()

        val owner = DocumentClassifier.extractOwnerName(text)
        assertEquals("Alex Johnson", owner)
    }
}
