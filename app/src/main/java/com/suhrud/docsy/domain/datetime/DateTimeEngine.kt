package com.suhrud.docsy.domain.datetime

import android.content.Context
import android.text.format.DateFormat
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.domain.query.DateTimeOp
import com.suhrud.docsy.domain.query.ParsedQuery
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object DateTimeEngine {

    fun answerDateTime(context: Context, parsed: ParsedQuery): ChatMessage {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)
        val locale = Locale.getDefault()
        val is24 = DateFormat.is24HourFormat(context)

        return when (parsed.dateTimeOp) {
            DateTimeOp.CURRENT_TIME -> {
                val timeFormatter = if (is24) {
                    DateTimeFormatter.ofPattern("HH:mm", locale)
                } else {
                    DateTimeFormatter.ofPattern("h:mm a", locale)
                }
                val formattedTime = now.format(timeFormatter)
                ChatMessage(
                    isUser = false,
                    text = "The current time is $formattedTime.",
                    answerHighlight = formattedTime,
                    supportingMetadata = "SYSTEM CLOCK · ${zone.id}",
                    subtext = "Based on your device clock"
                )
            }

            DateTimeOp.TODAY_DATE -> {
                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
                val formattedDate = today.format(dateFormatter)
                val highlight = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                ChatMessage(
                    isUser = false,
                    text = "Today is $formattedDate.",
                    answerHighlight = highlight,
                    supportingMetadata = "SYSTEM CALENDAR",
                    subtext = "Device time zone: ${zone.id}"
                )
            }

            DateTimeOp.WHAT_DAY -> {
                val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                ChatMessage(
                    isUser = false,
                    text = "Today is $dayName.",
                    answerHighlight = dayName,
                    supportingMetadata = "CALENDAR · TODAY"
                )
            }

            DateTimeOp.TOMORROW -> {
                val tomorrow = today.plusDays(1)
                val formatted = tomorrow.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
                val dayName = tomorrow.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                ChatMessage(
                    isUser = false,
                    text = "Tomorrow is $formatted.",
                    answerHighlight = dayName,
                    supportingMetadata = "CALENDAR · TOMORROW",
                    subtext = tomorrow.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                )
            }

            DateTimeOp.YESTERDAY -> {
                val yesterday = today.minusDays(1)
                val formatted = yesterday.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
                val dayName = yesterday.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                ChatMessage(
                    isUser = false,
                    text = "Yesterday was $formatted.",
                    answerHighlight = dayName,
                    supportingMetadata = "CALENDAR · YESTERDAY",
                    subtext = yesterday.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                )
            }

            DateTimeOp.OFFSET_DAYS -> {
                val offset = parsed.dateTimeOffsetDays
                val targetDate = today.plusDays(offset)
                val formatted = targetDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
                val highlight = targetDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                ChatMessage(
                    isUser = false,
                    text = if (offset >= 0) {
                        "The date in $offset days will be $formatted."
                    } else {
                        "The date ${-offset} days ago was $formatted."
                    },
                    answerHighlight = highlight,
                    supportingMetadata = "CALENDAR · ${if (offset >= 0) "+$offset DAYS" else "$offset DAYS"}"
                )
            }

            DateTimeOp.NEXT_DAY_OF_WEEK -> {
                val dayOfWeek = parseDayOfWeek(parsed.dateTimeTarget) ?: DayOfWeek.MONDAY
                val nextDay = today.with(TemporalAdjusters.next(dayOfWeek))
                val formatted = nextDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
                val highlight = nextDay.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                ChatMessage(
                    isUser = false,
                    text = "Next ${dayOfWeek.getDisplayName(TextStyle.FULL, locale)} is $formatted.",
                    answerHighlight = highlight,
                    supportingMetadata = "CALENDAR · NEXT ${dayOfWeek.name}"
                )
            }

            DateTimeOp.MONTH_AND_YEAR -> {
                val monthName = today.month.getDisplayName(TextStyle.FULL, locale)
                val year = today.year
                ChatMessage(
                    isUser = false,
                    text = "The current month is $monthName and the year is $year.",
                    answerHighlight = "$monthName $year",
                    supportingMetadata = "CALENDAR · $monthName $year"
                )
            }

            DateTimeOp.DAYS_UNTIL -> {
                val target = parseTargetDate(parsed.dateTimeTarget, today)
                if (target == null) {
                    ChatMessage(
                        isUser = false,
                        text = "I couldn't identify the target date you asked about.",
                        answerHighlight = "Date Not Recognized",
                        supportingMetadata = "CALENDAR"
                    )
                } else {
                    val days = ChronoUnit.DAYS.between(today, target)
                    val targetFormatted = target.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                    val highlight = "$days ${if (days == 1L) "day" else "days"}"
                    ChatMessage(
                        isUser = false,
                        text = if (days >= 0) {
                            "There are $days days until $targetFormatted."
                        } else {
                            "$targetFormatted was ${-days} days ago."
                        },
                        answerHighlight = highlight,
                        supportingMetadata = "CALENDAR · COUNTDOWN",
                        subtext = "Target: $targetFormatted"
                    )
                }
            }

            DateTimeOp.WEEK_OF_YEAR -> {
                val weekFields = WeekFields.of(locale)
                val weekNumber = today.get(weekFields.weekOfYear())
                ChatMessage(
                    isUser = false,
                    text = "It is currently week $weekNumber of ${today.year}.",
                    answerHighlight = "Week $weekNumber",
                    supportingMetadata = "CALENDAR · WEEK $weekNumber"
                )
            }

            DateTimeOp.IS_TODAY_DAY -> {
                val targetDay = parseDayOfWeek(parsed.dateTimeTarget)
                val currentDay = today.dayOfWeek
                val currentDayName = currentDay.getDisplayName(TextStyle.FULL, locale)
                if (targetDay != null && targetDay == currentDay) {
                    ChatMessage(
                        isUser = false,
                        text = "Yes, today is $currentDayName, ${today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}.",
                        answerHighlight = "Yes, today is $currentDayName",
                        supportingMetadata = "CALENDAR · TODAY"
                    )
                } else {
                    val targetName = targetDay?.getDisplayName(TextStyle.FULL, locale) ?: parsed.dateTimeTarget ?: "that day"
                    ChatMessage(
                        isUser = false,
                        text = "No, today is $currentDayName, not $targetName.",
                        answerHighlight = "Today is $currentDayName",
                        supportingMetadata = "CALENDAR · TODAY"
                    )
                }
            }

            DateTimeOp.DAY_OF_WEEK_FOR_DATE -> {
                val target = parseTargetDate(parsed.dateTimeTarget, today)
                if (target == null) {
                    ChatMessage(
                        isUser = false,
                        text = "I couldn't identify the date '${parsed.dateTimeTarget}'.",
                        answerHighlight = "Date Not Recognized",
                        supportingMetadata = "CALENDAR"
                    )
                } else {
                    val dowName = target.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    val dateFormatted = target.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                    ChatMessage(
                        isUser = false,
                        text = "$dateFormatted is a $dowName.",
                        answerHighlight = dowName,
                        supportingMetadata = "CALENDAR · DAY OF WEEK",
                        subtext = dateFormatted
                    )
                }
            }

            DateTimeOp.DAYS_BETWEEN -> {
                val d1 = parseTargetDate(parsed.dateTimeTarget, today)
                val d2 = parseTargetDate(parsed.dateTimeTarget2, today)
                if (d1 == null || d2 == null) {
                    ChatMessage(
                        isUser = false,
                        text = "I couldn't identify both dates to calculate the difference.",
                        answerHighlight = "Invalid Dates",
                        supportingMetadata = "CALENDAR"
                    )
                } else {
                    val days = kotlin.math.abs(ChronoUnit.DAYS.between(d1, d2))
                    val d1Formatted = d1.format(DateTimeFormatter.ofPattern("d MMMM", locale))
                    val d2Formatted = d2.format(DateTimeFormatter.ofPattern("d MMMM", locale))
                    val highlight = "$days ${if (days == 1L) "day" else "days"}"
                    ChatMessage(
                        isUser = false,
                        text = "There are $days days between $d1Formatted and $d2Formatted.",
                        answerHighlight = highlight,
                        supportingMetadata = "CALENDAR · DURATION",
                        subtext = "$d1Formatted to $d2Formatted"
                    )
                }
            }

            DateTimeOp.IS_LEAP_YEAR -> {
                val y = parsed.dateTimeTarget?.toIntOrNull() ?: today.year
                val isLeap = java.time.Year.of(y).isLeap
                ChatMessage(
                    isUser = false,
                    text = if (isLeap) {
                        "$y is a leap year (it has 366 days)."
                    } else {
                        "$y is not a leap year (it has 365 days)."
                    },
                    answerHighlight = if (isLeap) "Leap Year" else "Not a Leap Year",
                    supportingMetadata = "CALENDAR · LEAP YEAR"
                )
            }

            DateTimeOp.DAYS_LEFT_IN_MONTH -> {
                val left = today.lengthOfMonth() - today.dayOfMonth
                val monthName = today.month.getDisplayName(TextStyle.FULL, locale)
                ChatMessage(
                    isUser = false,
                    text = "There are $left days left in $monthName ${today.year}.",
                    answerHighlight = "$left days left",
                    supportingMetadata = "CALENDAR · MONTH REMAINING"
                )
            }

            DateTimeOp.DAYS_LEFT_IN_YEAR -> {
                val left = today.lengthOfYear() - today.dayOfYear
                ChatMessage(
                    isUser = false,
                    text = "There are $left days left in ${today.year}.",
                    answerHighlight = "$left days left",
                    supportingMetadata = "CALENDAR · YEAR REMAINING"
                )
            }

            DateTimeOp.HOURS_UNTIL_TIME -> {
                val targetTime = parseTargetTime(parsed.dateTimeTarget)
                if (targetTime == null) {
                    ChatMessage(
                        isUser = false,
                        text = "I couldn't identify the time '${parsed.dateTimeTarget}'.",
                        answerHighlight = "Time Not Recognized",
                        supportingMetadata = "SYSTEM CLOCK"
                    )
                } else {
                    var diffMinutes = ChronoUnit.MINUTES.between(now, targetTime)
                    if (diffMinutes < 0) {
                        diffMinutes += 24 * 60
                    }
                    val hours = diffMinutes / 60
                    val mins = diffMinutes % 60
                    val timeFormatter = if (is24) DateTimeFormatter.ofPattern("HH:mm", locale) else DateTimeFormatter.ofPattern("h:mm a", locale)
                    val formattedTarget = targetTime.format(timeFormatter)
                    val highlight = if (hours > 0) "$hours hr ${mins} min" else "$mins minutes"
                    ChatMessage(
                        isUser = false,
                        text = "There are $hours hours and $mins minutes until $formattedTarget.",
                        answerHighlight = highlight,
                        supportingMetadata = "COUNTDOWN · $formattedTarget"
                    )
                }
            }

            DateTimeOp.TIME_OFFSET_HOURS -> {
                val offset = parsed.dateTimeOffsetHours
                val target = now.plusHours(offset)
                val timeFormatter = if (is24) DateTimeFormatter.ofPattern("HH:mm", locale) else DateTimeFormatter.ofPattern("h:mm a", locale)
                val formatted = target.format(timeFormatter)
                ChatMessage(
                    isUser = false,
                    text = if (offset < 0) {
                        "${-offset} hours ago the time was $formatted."
                    } else {
                        "In $offset hours the time will be $formatted."
                    },
                    answerHighlight = formatted,
                    supportingMetadata = "SYSTEM CLOCK · OFFSET"
                )
            }

            null -> {
                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
                ChatMessage(
                    isUser = false,
                    text = "Today is ${today.format(dateFormatter)}.",
                    answerHighlight = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                    supportingMetadata = "SYSTEM CALENDAR"
                )
            }
        }
    }

    fun parseTargetTime(str: String?): LocalTime? {
        val s = str?.trim()?.lowercase(Locale.ROOT) ?: return null
        val pmMatch = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""").find(s)
        if (pmMatch != null) {
            var hour = pmMatch.groupValues[1].toInt()
            val min = pmMatch.groupValues[2].ifEmpty { "0" }.toInt()
            val ampm = pmMatch.groupValues[3]
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return runCatching { LocalTime.of(hour, min) }.getOrNull()
        }
        val hhmmMatch = Regex("""\b(\d{1,2}):(\d{2})\b""").find(s)
        if (hhmmMatch != null) {
            val h = hhmmMatch.groupValues[1].toInt()
            val m = hhmmMatch.groupValues[2].toInt()
            return runCatching { LocalTime.of(h, m) }.getOrNull()
        }
        return null
    }

    fun parseDayOfWeek(str: String?): DayOfWeek? {
        val s = str?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when {
            s.contains("mon") -> DayOfWeek.MONDAY
            s.contains("tue") -> DayOfWeek.TUESDAY
            s.contains("wed") -> DayOfWeek.WEDNESDAY
            s.contains("thu") -> DayOfWeek.THURSDAY
            s.contains("fri") -> DayOfWeek.FRIDAY
            s.contains("sat") -> DayOfWeek.SATURDAY
            s.contains("sun") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    fun parseTargetDate(queryTarget: String?, today: LocalDate): LocalDate? {
        val target = queryTarget?.trim()?.lowercase(Locale.ROOT) ?: return null

        val dow = parseDayOfWeek(target)
        if (dow != null) {
            return today.with(TemporalAdjusters.next(dow))
        }

        if (target.contains("christmas") || target.contains("xmas")) {
            val xmasThisYear = LocalDate.of(today.year, 12, 25)
            return if (xmasThisYear.isBefore(today)) xmasThisYear.plusYears(1) else xmasThisYear
        }
        if (target.contains("new year")) {
            return LocalDate.of(today.year + 1, 1, 1)
        }
        if (target.contains("halloween")) {
            val h = LocalDate.of(today.year, 10, 31)
            return if (h.isBefore(today)) h.plusYears(1) else h
        }

        val isoMatch = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(target)
        if (isoMatch != null) {
            val (y, m, d) = isoMatch.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }

        val dmyMatch = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b""").find(target)
        if (dmyMatch != null) {
            val (d, m, y) = dmyMatch.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }

        val dayMonthRegex = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\s+(\d{4}))?\b""")
        val dmMatch = dayMonthRegex.find(target)
        if (dmMatch != null) {
            val day = dmMatch.groupValues[1].toInt()
            val monthStr = dmMatch.groupValues[2]
            val explicitYear = dmMatch.groupValues[3].toIntOrNull()
            val month = monthFromStr(monthStr)
            if (month in 1..12) {
                val year = explicitYear ?: today.year
                val candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                return if (candidate != null) {
                    if (explicitYear == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
                } else null
            }
        }

        val monthDayRegex = Regex("""\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s+(\d{4}))?\b""")
        val mdMatch = monthDayRegex.find(target)
        if (mdMatch != null) {
            val monthStr = mdMatch.groupValues[1]
            val day = mdMatch.groupValues[2].toInt()
            val explicitYear = mdMatch.groupValues[3].toIntOrNull()
            val month = monthFromStr(monthStr)
            if (month in 1..12) {
                val year = explicitYear ?: today.year
                val candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                return if (candidate != null) {
                    if (explicitYear == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
                } else null
            }
        }

        return null
    }

    private fun monthFromStr(m: String): Int {
        val s = m.lowercase(Locale.ROOT)
        return when {
            s.startsWith("jan") -> 1
            s.startsWith("feb") -> 2
            s.startsWith("mar") -> 3
            s.startsWith("apr") -> 4
            s.startsWith("may") -> 5
            s.startsWith("jun") -> 6
            s.startsWith("jul") -> 7
            s.startsWith("aug") -> 8
            s.startsWith("sep") -> 9
            s.startsWith("oct") -> 10
            s.startsWith("nov") -> 11
            s.startsWith("dec") -> 12
            else -> 0
        }
    }
}
