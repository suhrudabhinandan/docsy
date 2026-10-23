package com.suhrud.docsy.tools

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DateTimeSnapshot(
    val timeFormatted12h: String,
    val timeFormatted24h: String,
    val dateFormatted: String,
    val dayOfWeek: String,
    val timeZone: String,
    val timeZoneOffset: String,
    val epochMillis: Long,
    val isDobOrPersonalDateQuery: Boolean = false
)

/**
 * System clock snapshot tool.
 * Satisfies Phase 8.1 in AUDIT.md.
 *
 * CRITICAL CONSTRAINT (R8):
 * Date & Time tool handles system clock queries only.
 * DOB, birth dates, anniversaries, and personal historical dates are strictly
 * NOT system clock queries and must be routed to document facts.
 */
object DateTimeTool {

    private val PERSONAL_DATE_KEYWORDS = listOf(
        "dob",
        "date of birth",
        "birth date",
        "birthday",
        "janamdin",
        "janam tithi",
        "anniversary",
        "expiry date",
        "issue date",
        "admission date",
        "joining date"
    )

    /**
     * Determines whether a query is asking for personal/document dates rather than system clock.
     */
    fun isPersonalDateQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        return PERSONAL_DATE_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Returns a snapshot of the current device system clock.
     */
    fun getSnapshot(locale: Locale = Locale.getDefault(), timeZone: TimeZone = TimeZone.getDefault()): DateTimeSnapshot {
        val now = Date()
        val calendar = Calendar.getInstance(timeZone, locale).apply { time = now }

        val format12h = SimpleDateFormat("hh:mm:ss a", locale).apply { this.timeZone = timeZone }
        val format24h = SimpleDateFormat("HH:mm:ss", locale).apply { this.timeZone = timeZone }
        val formatDate = SimpleDateFormat("EEEE, d MMMM yyyy", locale).apply { this.timeZone = timeZone }
        val formatDay = SimpleDateFormat("EEEE", locale).apply { this.timeZone = timeZone }
        val formatOffset = SimpleDateFormat("Z", locale).apply { this.timeZone = timeZone }

        return DateTimeSnapshot(
            timeFormatted12h = format12h.format(now),
            timeFormatted24h = format24h.format(now),
            dateFormatted = formatDate.format(now),
            dayOfWeek = formatDay.format(now),
            timeZone = timeZone.id,
            timeZoneOffset = formatOffset.format(now),
            epochMillis = now.time,
            isDobOrPersonalDateQuery = false
        )
    }

    /**
     * Formats current date/time answer text for assistant response.
     */
    fun formatAnswer(snapshot: DateTimeSnapshot): String {
        return "It's ${snapshot.timeFormatted12h} on ${snapshot.dateFormatted} (${snapshot.timeZone})."
    }
}
