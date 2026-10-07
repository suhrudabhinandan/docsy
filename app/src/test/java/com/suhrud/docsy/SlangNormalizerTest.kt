package com.suhrud.docsy

import com.suhrud.docsy.domain.query.Normalizer
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SlangNormalizerTest {

    @Test
    fun testProfanityQueryNormalization() {
        val norm1 = Normalizer.normalize("where the fuck is my 12th marksheet")
        assertEquals("where the is my 12th marksheet", norm1)

        val parsed1 = QueryParser.parse("where the fuck is my 12th marksheet")
        assertEquals(QueryIntent.FIND_DOCUMENT_INFO, parsed1.intent)

        val parsed2 = QueryParser.parse("damn how many steps today")
        assertEquals(QueryIntent.STEPS_QUERY, parsed2.intent)

        val parsed3 = QueryParser.parse("wtf is my battery")
        assertEquals(QueryIntent.DEVICE_INFO, parsed3.intent)
    }
}
