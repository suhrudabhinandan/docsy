package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.domain.datetime.DateTimeEngine
import com.suhrud.docsy.domain.query.DateTimeOp
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateTimeParserTest {

    @Test
    fun testDateTimeQueriesParsing() {
        val qTime = QueryParser.parse("what time is it")
        assertEquals(QueryIntent.DATE_TIME, qTime.intent)
        assertEquals(DateTimeOp.CURRENT_TIME, qTime.dateTimeOp)

        val qDate = QueryParser.parse("today's date")
        assertEquals(QueryIntent.DATE_TIME, qDate.intent)
        assertEquals(DateTimeOp.TODAY_DATE, qDate.dateTimeOp)

        val qDay = QueryParser.parse("what day is it")
        assertEquals(QueryIntent.DATE_TIME, qDay.intent)
        assertEquals(DateTimeOp.WHAT_DAY, qDay.dateTimeOp)

        val qTomorrow = QueryParser.parse("day tomorrow")
        assertEquals(QueryIntent.DATE_TIME, qTomorrow.intent)
        assertEquals(DateTimeOp.TOMORROW, qTomorrow.dateTimeOp)

        val qYesterday = QueryParser.parse("day yesterday")
        assertEquals(QueryIntent.DATE_TIME, qYesterday.intent)
        assertEquals(DateTimeOp.YESTERDAY, qYesterday.dateTimeOp)

        val qOffset = QueryParser.parse("date after 10 days")
        assertEquals(QueryIntent.DATE_TIME, qOffset.intent)
        assertEquals(DateTimeOp.OFFSET_DAYS, qOffset.dateTimeOp)
        assertEquals(10L, qOffset.dateTimeOffsetDays)

        val qNextMon = QueryParser.parse("what's the date next Monday")
        assertEquals(QueryIntent.DATE_TIME, qNextMon.intent)
        assertEquals(DateTimeOp.NEXT_DAY_OF_WEEK, qNextMon.dateTimeOp)
        assertEquals("monday", qNextMon.dateTimeTarget)

        val qMonthYear = QueryParser.parse("current month and year")
        assertEquals(QueryIntent.DATE_TIME, qMonthYear.intent)
        assertEquals(DateTimeOp.MONTH_AND_YEAR, qMonthYear.dateTimeOp)

        val qDaysUntil = QueryParser.parse("how many days until christmas")
        assertEquals(QueryIntent.DATE_TIME, qDaysUntil.intent)
        assertEquals(DateTimeOp.DAYS_UNTIL, qDaysUntil.dateTimeOp)
        assertEquals("christmas", qDaysUntil.dateTimeTarget)

        val qWeek = QueryParser.parse("which week of the year")
        assertEquals(QueryIntent.DATE_TIME, qWeek.intent)
        assertEquals(DateTimeOp.WEEK_OF_YEAR, qWeek.dateTimeOp)

        val qIsToday = QueryParser.parse("is today monday")
        assertEquals(QueryIntent.DATE_TIME, qIsToday.intent)
        assertEquals(DateTimeOp.IS_TODAY_DAY, qIsToday.dateTimeOp)
        assertEquals("monday", qIsToday.dateTimeTarget)

        val qDaysBetween = QueryParser.parse("days between march 1 and april 15")
        assertEquals(QueryIntent.DATE_TIME, qDaysBetween.intent)
        assertEquals(DateTimeOp.DAYS_BETWEEN, qDaysBetween.dateTimeOp)
        assertEquals("march 1", qDaysBetween.dateTimeTarget)
        assertEquals("april 15", qDaysBetween.dateTimeTarget2)

        val qDayOfWeek = QueryParser.parse("what day of the week is Dec 25")
        assertEquals(QueryIntent.DATE_TIME, qDayOfWeek.intent)
        assertEquals(DateTimeOp.DAY_OF_WEEK_FOR_DATE, qDayOfWeek.dateTimeOp)
        assertEquals("dec 25", qDayOfWeek.dateTimeTarget)

        val qLeapYear = QueryParser.parse("is this year a leap year")
        assertEquals(QueryIntent.DATE_TIME, qLeapYear.intent)
        assertEquals(DateTimeOp.IS_LEAP_YEAR, qLeapYear.dateTimeOp)

        val qDaysLeftMonth = QueryParser.parse("how many days left this month")
        assertEquals(QueryIntent.DATE_TIME, qDaysLeftMonth.intent)
        assertEquals(DateTimeOp.DAYS_LEFT_IN_MONTH, qDaysLeftMonth.dateTimeOp)

        val qDaysLeftYear = QueryParser.parse("days left in the year")
        assertEquals(QueryIntent.DATE_TIME, qDaysLeftYear.intent)
        assertEquals(DateTimeOp.DAYS_LEFT_IN_YEAR, qDaysLeftYear.dateTimeOp)

        val qHoursUntil = QueryParser.parse("how many hours until 5pm")
        assertEquals(QueryIntent.DATE_TIME, qHoursUntil.intent)
        assertEquals(DateTimeOp.HOURS_UNTIL_TIME, qHoursUntil.dateTimeOp)
        assertEquals("5pm", qHoursUntil.dateTimeTarget)

        val qTimeAgo = QueryParser.parse("what time was it 3 hours ago")
        assertEquals(QueryIntent.DATE_TIME, qTimeAgo.intent)
        assertEquals(DateTimeOp.TIME_OFFSET_HOURS, qTimeAgo.dateTimeOp)
        assertEquals(-3L, qTimeAgo.dateTimeOffsetHours)

        val qRelativeFile = QueryParser.parse("how many photos from yesterday")
        assertEquals(QueryIntent.DEVICE_STATS, qRelativeFile.intent)
        assertEquals("yesterday", qRelativeFile.dateFilter)
    }

    @Test
    fun testDateTimeEngineOutputDeterministic() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val qTime = QueryParser.parse("what time is it")
        val ansTime = DateTimeEngine.answerDateTime(context, qTime)
        assertNotNull(ansTime.answerHighlight)
        assertTrue(ansTime.text.contains("time"))

        val qDate = QueryParser.parse("what is today's date")
        val ansDate = DateTimeEngine.answerDateTime(context, qDate)
        assertNotNull(ansDate.answerHighlight)
        assertTrue(ansDate.text.contains("Today is"))

        val qNextFri = QueryParser.parse("date next Friday")
        val ansNextFri = DateTimeEngine.answerDateTime(context, qNextFri)
        assertNotNull(ansNextFri.answerHighlight)
        assertTrue(ansNextFri.text.contains("Friday"))

        val qLeap = QueryParser.parse("is this year a leap year")
        val ansLeap = DateTimeEngine.answerDateTime(context, qLeap)
        assertNotNull(ansLeap.answerHighlight)
        assertTrue(ansLeap.text.contains("leap year"))

        val qMonthLeft = QueryParser.parse("how many days left this month")
        val ansMonthLeft = DateTimeEngine.answerDateTime(context, qMonthLeft)
        assertNotNull(ansMonthLeft.answerHighlight)
        assertTrue(ansMonthLeft.text.contains("days left"))
    }
}
