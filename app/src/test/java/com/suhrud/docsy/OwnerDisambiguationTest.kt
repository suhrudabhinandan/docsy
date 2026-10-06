package com.suhrud.docsy

import com.suhrud.docsy.domain.ai.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerDisambiguationTest {

    @Test
    fun testResumeOwnerExtraction() {
        val cvText1 = """
            Alex Johnson
            Software Engineer
            alex@example.com
            Experience: 5 years
        """.trimIndent()

        val cvText2 = """
            Maddy Smith
            Senior Architect
            maddy@example.com
            Experience: 8 years
        """.trimIndent()

        val owner1 = DocumentClassifier.extractOwnerName(cvText1)
        val owner2 = DocumentClassifier.extractOwnerName(cvText2)

        assertEquals("Alex Johnson", owner1)
        assertEquals("Maddy Smith", owner2)
    }
}
