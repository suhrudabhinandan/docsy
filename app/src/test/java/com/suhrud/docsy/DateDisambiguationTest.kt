package com.suhrud.docsy

import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DateDisambiguationTest {

    @Test
    fun testContrastiveDatePairs() {
        val dob = QueryParser.parse("my date of birth")
        val today = QueryParser.parse("today's date")
        assertEquals(QueryIntent.FIND_PERSONAL_INFO, dob.intent)
        assertEquals(QueryIntent.DATE_TIME, today.intent)

        val dobShort = QueryParser.parse("dob")
        val bareDate = QueryParser.parse("date")
        assertEquals(QueryIntent.FIND_PERSONAL_INFO, dobShort.intent)
        assertEquals(QueryIntent.DATE_TIME, bareDate.intent)

        val expiry = QueryParser.parse("expiry date of my passport")
        val whatDate = QueryParser.parse("what is the date")
        assertEquals(QueryIntent.FIND_DOCUMENT_INFO, expiry.intent)
        assertEquals(QueryIntent.DATE_TIME, whatDate.intent)

        val dueDate = QueryParser.parse("due date of my bill")
        val dateToday = QueryParser.parse("date today")
        assertEquals(QueryIntent.FIND_DOCUMENT_INFO, dueDate.intent)
        assertEquals(QueryIntent.DATE_TIME, dateToday.intent)
    }
}
