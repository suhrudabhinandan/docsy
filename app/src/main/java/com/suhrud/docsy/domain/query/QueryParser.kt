package com.suhrud.docsy.domain.query

import com.suhrud.docsy.data.model.DocumentCategories
import java.util.Locale
import java.util.regex.Pattern

enum class QueryIntent {
    PERSONAL_PROFILE,
    SMALL_TALK,
    DATE_TIME,
    STEPS_QUERY,
    DEVICE_INFO,
    DEVICE_STATS,
    FOLDER_STATS,
    FILE_SUPERLATIVE,
    SMS_QUERY,
    CALL_QUERY,
    APP_QUERY,
    STORAGE_QUERY,
    FIND_DOCUMENT_INFO,
    FIND_PERSONAL_INFO,
    COMPARE_DOCUMENT_VALUES,
    FIND_SCHEDULE,
    OUT_OF_SCOPE,
    UNKNOWN
}

enum class DateTimeOp {
    CURRENT_TIME,
    TODAY_DATE,
    WHAT_DAY,
    TOMORROW,
    YESTERDAY,
    OFFSET_DAYS,
    NEXT_DAY_OF_WEEK,
    MONTH_AND_YEAR,
    DAYS_UNTIL,
    WEEK_OF_YEAR,
    IS_TODAY_DAY,
    DAY_OF_WEEK_FOR_DATE,
    DAYS_BETWEEN,
    IS_LEAP_YEAR,
    DAYS_LEFT_IN_MONTH,
    DAYS_LEFT_IN_YEAR,
    HOURS_UNTIL_TIME,
    TIME_OFFSET_HOURS
}

enum class AggregationOp {
    NONE,
    MAX,
    MIN,
    SUM,
    COUNT,
    LATEST
}

data class ParsedQuery(
    val intent: QueryIntent,
    val documentType: String? = null,
    val namedDocument: String? = null,
    val utilityOrIssuer: String? = null,
    val month: String? = null,
    val year: Int? = null,
    val requestedField: String? = null,
    val operation: AggregationOp = AggregationOp.NONE,
    val filterType: String? = null,
    val standardOrClass: String? = null,
    val board: String? = null,
    val dateTimeOp: DateTimeOp? = null,
    val dateTimeTarget: String? = null,
    val dateTimeTarget2: String? = null,
    val dateTimeOffsetDays: Long = 0L,
    val dateTimeOffsetHours: Long = 0L,
    val dateFilter: String? = null,
    val targetFolder: String? = null,
    val folderQueryType: String? = null,
    val hasConstraints: Boolean = true,
    val rawQuery: String = ""
)

object QueryParser {

    private val MONTH_MAP = mapOf(
        "jan" to "January", "january" to "January",
        "feb" to "February", "february" to "February",
        "mar" to "March", "march" to "March",
        "apr" to "April", "april" to "April",
        "may" to "May",
        "jun" to "June", "june" to "June",
        "jul" to "July", "july" to "July",
        "aug" to "August", "august" to "August",
        "sep" to "September", "sept" to "September", "september" to "September",
        "oct" to "October", "october" to "October",
        "nov" to "November", "november" to "November",
        "dec" to "December", "december" to "December"
    )

    fun parse(rawQuery: String, context: ParsedQuery? = null): ParsedQuery {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) {
            return ParsedQuery(intent = QueryIntent.UNKNOWN, rawQuery = rawQuery)
        }

        if (SemanticIntentClassifier.isSmallTalk(rawQuery)) {
            return ParsedQuery(intent = QueryIntent.SMALL_TALK, rawQuery = rawQuery)
        }

        if (SemanticIntentClassifier.isOutOfScope(rawQuery)) {
            return ParsedQuery(intent = QueryIntent.OUT_OF_SCOPE, rawQuery = rawQuery)
        }

        if (isProfileQuery(q)) {
            return ParsedQuery(intent = QueryIntent.PERSONAL_PROFILE, rawQuery = rawQuery)
        }

        val folderParsed = parseFolderStats(q, rawQuery)
        if (folderParsed != null) {
            return folderParsed
        }

        val fileCountType = getFileCountType(q)
        if (fileCountType != null) {
            return ParsedQuery(
                intent = QueryIntent.DEVICE_STATS,
                filterType = fileCountType,
                dateFilter = extractDateFilter(q),
                rawQuery = rawQuery
            )
        }

        if (isPersonalOrDocumentDate(q)) {
            val field = when {
                q.contains("date of birth") || q.contains("dob") || q.contains("birthday") || q.contains("when was i born") || q.contains("janm tithi") -> "dob"
                q.contains("expiry date") || q.contains("validity") -> "expiry_date"
                q.contains("due date") -> "due_date"
                q.contains("exam date") -> "exam_date"
                else -> "dob"
            }
            val targetIntent = if (field == "dob") QueryIntent.FIND_PERSONAL_INFO else QueryIntent.FIND_DOCUMENT_INFO
            return ParsedQuery(
                intent = targetIntent,
                requestedField = field,
                hasConstraints = true,
                rawQuery = rawQuery
            )
        }

        val dateTimeParsed = parseDateTime(q, rawQuery)
        if (dateTimeParsed != null) {
            return dateTimeParsed
        }

        val semanticMatch = SemanticIntentClassifier.classifySemanticIntent(rawQuery)
        if (semanticMatch != null && semanticMatch.intent != QueryIntent.UNKNOWN) {
            return semanticMatch.copy(dateFilter = extractDateFilter(q))
        }

        if (context != null && isFollowUp(q)) {
            val extractedMonth = extractMonth(q)
            val extractedYear = extractYear(q)
            if (extractedMonth != null || extractedYear != null) {
                return context.copy(
                    month = extractedMonth ?: context.month,
                    year = extractedYear ?: context.year,
                    rawQuery = rawQuery
                )
            }
        }

        val dateFilter = extractDateFilter(q)

        if (isStorageQuery(q)) {
            return ParsedQuery(intent = QueryIntent.DEVICE_STATS, filterType = "storage", dateFilter = dateFilter, rawQuery = rawQuery)
        }
        if (isAppsQuery(q)) {
            return ParsedQuery(intent = QueryIntent.DEVICE_STATS, filterType = "app", dateFilter = dateFilter, rawQuery = rawQuery)
        }
        if (isSmsQuery(q)) {
            return ParsedQuery(intent = QueryIntent.SMS_QUERY, filterType = "sms", dateFilter = dateFilter, rawQuery = rawQuery)
        }
        if (isCallLogQuery(q)) {
            val missed = q.contains("missed")
            return ParsedQuery(
                intent = QueryIntent.CALL_QUERY,
                filterType = if (missed) "missed_calls" else "calls",
                dateFilter = dateFilter,
                rawQuery = rawQuery
            )
        }
        if (isContactsQuery(q)) {
            return ParsedQuery(intent = QueryIntent.DEVICE_STATS, filterType = "contacts", dateFilter = dateFilter, rawQuery = rawQuery)
        }
        if (isStepsQuery(q)) {
            val filter = when {
                q.contains("today") -> "today"
                q.contains("week") -> "week"
                q.contains("month") -> "month"
                q.contains("average") || q.contains("avg") -> "average"
                q.contains("most") || q.contains("highest") || q.contains("max") || q.contains("record") -> "most"
                else -> "total"
            }
            return ParsedQuery(intent = QueryIntent.STEPS_QUERY, filterType = filter, dateFilter = dateFilter, rawQuery = rawQuery)
        }

        val superlative = extractSuperlative(q)
        if (superlative != null) {
            val targetType = when {
                q.contains("video") -> "video"
                q.contains("photo") || q.contains("image") || q.contains("pic") -> "photo"
                q.contains("document") || q.contains("doc") || q.contains("pdf") -> "document"
                q.contains("song") || q.contains("audio") || q.contains("music") -> "audio"
                else -> null
            }
            return ParsedQuery(
                intent = QueryIntent.FILE_SUPERLATIVE,
                filterType = superlative,
                requestedField = targetType,
                rawQuery = rawQuery
            )
        }

        if (isAadhaarQuery(q)) {
            return ParsedQuery(
                intent = QueryIntent.FIND_PERSONAL_INFO,
                documentType = DocumentCategories.AADHAAR,
                requestedField = "aadhaar",
                rawQuery = rawQuery
            )
        }
        if (isPanQuery(q)) {
            return ParsedQuery(
                intent = QueryIntent.FIND_PERSONAL_INFO,
                documentType = DocumentCategories.PAN,
                requestedField = "pan",
                rawQuery = rawQuery
            )
        }

        if (isScheduleQuery(q)) {
            val subject = when {
                q.contains("cn") || q.contains("computer network") -> "Computer Networks"
                q.contains("math") -> "Mathematics"
                q.contains("ai") || q.contains("artificial intelligence") -> "Artificial Intelligence"
                q.contains("dbms") || q.contains("database") -> "Database Management Systems"
                q.contains("os") || q.contains("operating system") -> "Operating Systems"
                else -> null
            }
            return ParsedQuery(
                intent = QueryIntent.FIND_SCHEDULE,
                documentType = DocumentCategories.SCHEDULE,
                requestedField = subject,
                month = extractMonth(q),
                year = extractYear(q),
                rawQuery = rawQuery
            )
        }

        if (q.contains("compare") || q.contains("higher than") || q.contains("more than") || q.contains("lower than") || q.contains("less than") || q.contains("difference between")) {
            val utility = extractUtility(q)
            return ParsedQuery(
                intent = QueryIntent.COMPARE_DOCUMENT_VALUES,
                documentType = when (utility) {
                    "Water" -> DocumentCategories.WATER_BILL
                    "Gas" -> DocumentCategories.GAS_BILL
                    else -> DocumentCategories.ELECTRICITY_BILL
                },
                utilityOrIssuer = utility,
                rawQuery = rawQuery
            )
        }

        if (isMarksheetQuery(q)) {
            val standard = extractClass(q)
            val board = extractBoard(q)
            return ParsedQuery(
                intent = QueryIntent.FIND_DOCUMENT_INFO,
                documentType = DocumentCategories.MARKSHEET,
                standardOrClass = standard,
                board = board,
                requestedField = if (q.contains("roll")) "roll_no" else "score",
                rawQuery = rawQuery
            )
        }

        val docType = extractDocumentType(q)
        val month = extractMonth(q)
        val year = extractYear(q)
        val utility = extractUtility(q)
        val op = extractAggregationOp(q)
        val openEndedIdentity = SemanticIntentClassifier.extractOpenEndedDocumentIdentity(rawQuery)

        val resolvedDocType = docType ?: when (utility) {
            "Water" -> DocumentCategories.WATER_BILL
            "Gas" -> DocumentCategories.GAS_BILL
            "Electricity" -> DocumentCategories.ELECTRICITY_BILL
            else -> null
        }

        val hasConstraints = resolvedDocType != null || utility != null || month != null || year != null || openEndedIdentity != null

        return ParsedQuery(
            intent = QueryIntent.FIND_DOCUMENT_INFO,
            documentType = resolvedDocType,
            namedDocument = openEndedIdentity,
            utilityOrIssuer = utility,
            month = month,
            year = year,
            operation = op,
            requestedField = extractRequestedField(q),
            hasConstraints = hasConstraints,
            rawQuery = rawQuery
        )
    }

    private fun extractRequestedField(q: String): String? {
        return when {
            q.contains("amount") || q.contains("bill amount") || q.contains("cost") || q.contains("price") || q.contains("charge") -> "amount"
            q.contains("due date") || q.contains("due") -> "due_date"
            q.contains("roll") || q.contains("roll number") || q.contains("roll no") -> "roll_number"
            q.contains("score") || q.contains("marks") || q.contains("percentage") || q.contains("cgpa") -> "score"
            q.contains("number") || q.contains("no") -> "number"
            else -> null
        }
    }

    fun parseDateTime(q: String, rawQuery: String): ParsedQuery? {
        val isFileOrDeviceQuery = q.contains("file") || q.contains("photo") || q.contains("image") ||
                q.contains("video") || q.contains("song") || q.contains("call") ||
                q.contains("message") || q.contains("sms") || q.contains("how many")

        if (!isFileOrDeviceQuery && (q == "tomorrow" || q.contains("tomorrow"))) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.TOMORROW,
                rawQuery = rawQuery
            )
        }

        if (!isFileOrDeviceQuery && (q == "yesterday" || q.contains("yesterday"))) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.YESTERDAY,
                rawQuery = rawQuery
            )
        }
        val timeAgoRegex = Regex("""(?:what\s+)?time\s+(?:was\s+it\s+)?(\d+)\s+hours?\s+ago""")
        val timeAgoMatch = timeAgoRegex.find(q)
        if (timeAgoMatch != null) {
            val h = timeAgoMatch.groupValues[1].toLongOrNull() ?: 1L
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.TIME_OFFSET_HOURS,
                dateTimeOffsetHours = -h,
                rawQuery = rawQuery
            )
        }

        val timeInRegex = Regex("""(?:what\s+)?time\s+(?:will\s+it\s+be\s+)?in\s+(\d+)\s+hours?""")
        val timeInMatch = timeInRegex.find(q)
        if (timeInMatch != null) {
            val h = timeInMatch.groupValues[1].toLongOrNull() ?: 1L
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.TIME_OFFSET_HOURS,
                dateTimeOffsetHours = h,
                rawQuery = rawQuery
            )
        }

        val hoursUntilRegex = Regex("""(?:how\s+many\s+)?hours\s+until\s+(.+)""")
        val huMatch = hoursUntilRegex.find(q)
        if (huMatch != null) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.HOURS_UNTIL_TIME,
                dateTimeTarget = huMatch.groupValues[1].trim(),
                rawQuery = rawQuery
            )
        }

        val timeRegex = Regex("""\b(what(?:'?s|\s+is)?\s+(?:the\s+)?time(?:\s+now|\s+is\s+it)?|what\s+time\s+is\s+it|tell\s+me\s+the\s+time|current\s+time|time\s+now)\b""")
        if (!q.contains("ago") && !q.contains("until") && (timeRegex.containsMatchIn(q) || q == "time" || q == "what time is it")) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.CURRENT_TIME,
                rawQuery = rawQuery
            )
        }

        if (q.contains("week of the year") || q.contains("which week") || q.contains("what week") || q.contains("week number")) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.WEEK_OF_YEAR,
                rawQuery = rawQuery
            )
        }

        if ((q.contains("month") && q.contains("year")) || q.contains("current month") || q.contains("what month") || q.contains("current year") || q.contains("what year is it")) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.MONTH_AND_YEAR,
                rawQuery = rawQuery
            )
        }

        val daysUntilRegex = Regex("""(?:how\s+many\s+)?days\s+(?:until|to|left\s+(?:for|until))\s+(.+)""")
        val daysUntilMatch = daysUntilRegex.find(q)
        if (daysUntilMatch != null) {
            val target = daysUntilMatch.groupValues[1].trim()
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.DAYS_UNTIL,
                dateTimeTarget = target,
                rawQuery = rawQuery
            )
        }

        val nextDayRegex = Regex("""(?:what(?:'?s|\s+is)?\s+the\s+)?date\s+next\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)|(?:what(?:'?s|\s+is)?\s+)?next\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        val nextDayMatch = nextDayRegex.find(q)
        if (nextDayMatch != null) {
            val dayName = nextDayMatch.groupValues[1].ifEmpty { nextDayMatch.groupValues[2] }
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.NEXT_DAY_OF_WEEK,
                dateTimeTarget = dayName,
                rawQuery = rawQuery
            )
        }

        val afterDaysRegex = Regex("""(?:date\s+)?(?:after|in)\s+(\d+)\s+days|(\d+)\s+days\s+(?:after|from)\s+(?:today|now)""")
        val afterDaysMatch = afterDaysRegex.find(q)
        if (afterDaysMatch != null) {
            val daysStr = afterDaysMatch.groupValues[1].ifEmpty { afterDaysMatch.groupValues[2] }
            val days = daysStr.toLongOrNull() ?: 10L
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.OFFSET_DAYS,
                dateTimeOffsetDays = days,
                rawQuery = rawQuery
            )
        }

        if (q.contains("what day is it") || q.contains("what day is today") || q.contains("which day is today") || q == "what day" || q == "what day is it?") {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.WHAT_DAY,
                rawQuery = rawQuery
            )
        }

        if (q.contains("today's date") || q.contains("todays date") || q.contains("what is the date") || q.contains("what is today's date") || q.contains("what date is it") || q.contains("date today") || q.contains("current date") || q == "date") {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.TODAY_DATE,
                rawQuery = rawQuery
            )
        }

        val isTodayRegex = Regex("""is\s+today\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        val isTodayMatch = isTodayRegex.find(q)
        if (isTodayMatch != null) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.IS_TODAY_DAY,
                dateTimeTarget = isTodayMatch.groupValues[1],
                rawQuery = rawQuery
            )
        }

        val daysBetweenRegex = Regex("""(?:how\s+many\s+)?days\s+between\s+(.+?)\s+and\s+(.+)""")
        val dbMatch = daysBetweenRegex.find(q)
        if (dbMatch != null) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.DAYS_BETWEEN,
                dateTimeTarget = dbMatch.groupValues[1].trim(),
                dateTimeTarget2 = dbMatch.groupValues[2].trim(),
                rawQuery = rawQuery
            )
        }

        val dayOfWeekForDateRegex = Regex("""what\s+day(?:\s+of\s+the\s+week)?\s+(?:is|was|will\s+be)\s+(.+)""")
        val dowMatch = dayOfWeekForDateRegex.find(q)
        if (dowMatch != null && !q.contains("today") && !q.contains("tomorrow") && !q.contains("yesterday")) {
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.DAY_OF_WEEK_FOR_DATE,
                dateTimeTarget = dowMatch.groupValues[1].trim(),
                rawQuery = rawQuery
            )
        }

        if (q.contains("leap year")) {
            val yearMatch = Regex("""\b(\d{4})\b""").find(q)
            return ParsedQuery(
                intent = QueryIntent.DATE_TIME,
                dateTimeOp = DateTimeOp.IS_LEAP_YEAR,
                dateTimeTarget = yearMatch?.groupValues?.get(1),
                rawQuery = rawQuery
            )
        }

        if (q.contains("days left") || q.contains("days remaining")) {
            if (q.contains("month")) {
                return ParsedQuery(
                    intent = QueryIntent.DATE_TIME,
                    dateTimeOp = DateTimeOp.DAYS_LEFT_IN_MONTH,
                    rawQuery = rawQuery
                )
            }
            if (q.contains("year")) {
                return ParsedQuery(
                    intent = QueryIntent.DATE_TIME,
                    dateTimeOp = DateTimeOp.DAYS_LEFT_IN_YEAR,
                    rawQuery = rawQuery
                )
            }
        }

        return null
    }

    fun parseFolderStats(q: String, rawQuery: String): ParsedQuery? {
        val foldersCountRegex = Regex("""how\s+many\s+folders\s+(?:are\s+)?in\s+(?:the\s+)?(?:my\s+)?(.+?)(?:\s+folder|\s+directory)?$""")
        val fcMatch = foldersCountRegex.find(q)
        if (fcMatch != null) {
            val folder = cleanFolderName(fcMatch.groupValues[1])
            return ParsedQuery(
                intent = QueryIntent.FOLDER_STATS,
                targetFolder = folder,
                folderQueryType = "folders_count",
                rawQuery = rawQuery
            )
        }

        val sizeRegex = Regex("""(?:what\s+is\s+the\s+)?(?:size|storage)\s+of\s+(?:the\s+)?(?:my\s+)?(.+?)(?:\s+folder|\s+directory)?$""")
        val sizeMatch = sizeRegex.find(q)
        if (sizeMatch != null) {
            val folder = cleanFolderName(sizeMatch.groupValues[1])
            return ParsedQuery(
                intent = QueryIntent.FOLDER_STATS,
                targetFolder = folder,
                folderQueryType = "size",
                rawQuery = rawQuery
            )
        }

        val superRegex = Regex("""(largest|biggest|newest|oldest|smallest)\s+file\s+in\s+(?:the\s+)?(?:my\s+)?(.+?)(?:\s+folder|\s+directory)?$""")
        val superMatch = superRegex.find(q)
        if (superMatch != null) {
            val superlative = superMatch.groupValues[1]
            val folder = cleanFolderName(superMatch.groupValues[2])
            return ParsedQuery(
                intent = QueryIntent.FOLDER_STATS,
                targetFolder = folder,
                folderQueryType = "superlative",
                filterType = superlative,
                rawQuery = rawQuery
            )
        }

        val countRegex = Regex("""how\s+many\s+(photos|images|pics|pictures|videos|movies|songs|music|audio|documents|docs|pdfs|files)\s+(?:are\s+)?in\s+(?:the\s+)?(?:my\s+)?(.+?)(?:\s+folder|\s+directory)?$""")
        val countMatch = countRegex.find(q)
        if (countMatch != null) {
            val type = normalizeFolderFilterType(countMatch.groupValues[1])
            val folder = cleanFolderName(countMatch.groupValues[2])
            if (folder.lowercase(Locale.ROOT) != "phone" && folder.lowercase(Locale.ROOT) != "device") {
                return ParsedQuery(
                    intent = QueryIntent.FOLDER_STATS,
                    targetFolder = folder,
                    folderQueryType = "count",
                    filterType = type,
                    rawQuery = rawQuery
                )
            }
        }

        val inRegex = Regex("""^(photos|images|pics|pictures|videos|movies|songs|music|audio|documents|docs|pdfs|files)\s+in\s+(?:the\s+)?(?:my\s+)?(.+?)(?:\s+folder|\s+directory)?$""")
        val inMatch = inRegex.find(q)
        if (inMatch != null) {
            val type = normalizeFolderFilterType(inMatch.groupValues[1])
            val folder = cleanFolderName(inMatch.groupValues[2])
            if (folder.lowercase(Locale.ROOT) != "phone" && folder.lowercase(Locale.ROOT) != "device") {
                return ParsedQuery(
                    intent = QueryIntent.FOLDER_STATS,
                    targetFolder = folder,
                    folderQueryType = "count",
                    filterType = type,
                    rawQuery = rawQuery
                )
            }
        }

        val genRegex = Regex("""(?:what(?:'?s|\s+is)?\s+in\s+|show\s+me\s+)?(?:the\s+)?(?:my\s+)?(.+?)\s+folder$""")
        val genMatch = genRegex.find(q)
        if (genMatch != null) {
            val folder = cleanFolderName(genMatch.groupValues[1])
            if (folder.isNotEmpty() && folder != "this" && folder != "that") {
                return ParsedQuery(
                    intent = QueryIntent.FOLDER_STATS,
                    targetFolder = folder,
                    folderQueryType = "count",
                    filterType = "all",
                    rawQuery = rawQuery
                )
            }
        }

        return null
    }

    private fun cleanFolderName(raw: String): String {
        return raw.trim()
            .removePrefix("the ")
            .removePrefix("my ")
            .removeSuffix(" folder")
            .removeSuffix(" directory")
            .trim()
    }

    private fun normalizeFolderFilterType(raw: String): String {
        return when (raw.lowercase(Locale.ROOT)) {
            "photos", "images", "pics", "pictures" -> "photo"
            "videos", "movies" -> "video"
            "songs", "music", "audio" -> "audio"
            "documents", "docs", "pdfs" -> "document"
            else -> "all"
        }
    }

    private fun isPersonalOrDocumentDate(q: String): Boolean {
        if (q.contains("how many") || q.contains("count") || q.startsWith("number of ") || q.endsWith(" count")) return false
        return q.contains("date of birth") || q.contains("dob") || q.contains("birthday") ||
                q.contains("when was i born") || q.contains("janm tithi") || q.contains("expiry date") ||
                q.contains("issue date") || q.contains("due date") || q.contains("exam date") ||
                q.contains("joining date") || q.contains("validity date")
    }

    private fun isProfileQuery(q: String): Boolean {
        return q.contains("my name") || q.contains("who am i") || q.contains("what is my name") || q.contains("what's my name")
    }

    private fun isFollowUp(q: String): Boolean {
        return q.startsWith("what about") || q.startsWith("and ") || q.startsWith("how about") ||
                q.startsWith("what of") || q.startsWith("in ")
    }

    private fun isStorageQuery(q: String): Boolean {
        return q.contains("storage") || q.contains("space") || q.contains("memory") || q.contains("disk") || q.contains("rom")
    }

    private fun isAppsQuery(q: String): Boolean {
        return q.contains("apps") || q.contains("applications") || q.contains("installed apps") ||
                q.contains("how many apps")
    }

    private fun isSmsQuery(q: String): Boolean {
        return q.contains("sms") || q.contains("messages") || q.contains("text messages") ||
                q.contains("inbox") || q.contains("how many messages")
    }

    private fun isCallLogQuery(q: String): Boolean {
        return q.contains("call") || q.contains("calls") || q.contains("call log") ||
                q.contains("missed calls") || q.contains("recent calls")
    }

    private fun isContactsQuery(q: String): Boolean {
        return q.contains("contacts") || q.contains("phone numbers") || q.contains("address book") ||
                q.contains("how many contacts")
    }

    private fun isStepsQuery(q: String): Boolean {
        return q.contains("step") || q.contains("steps") || q.contains("walk") ||
                q.contains("walking") || q.contains("distance walked")
    }

    private fun getFileCountType(q: String): String? {
        val isStorageCue = q.contains("storage") || q.contains("space") || q.contains("memory") || q.contains("size") || q.contains(" gb") || q.contains(" mb")
        if (isStorageCue) return null

        val isCount = q.contains("how many") || q.contains("count") || q.contains("total") || q.contains("number") || q.endsWith("?") ||
                q.contains("photos") || q.contains("videos") || q.contains("music") || q.contains("musics") || q.contains("songs") || q.contains("pdfs")

        if (isCount || q.startsWith("number of ") || q.startsWith("count ") || q.endsWith(" total")) {
            return when {
                q.contains("photo") || q.contains("image") || q.contains("pic") -> "photo"
                q.contains("video") || q.contains("movie") || q.contains("clip") -> "video"
                q.contains("audio") || q.contains("song") || q.contains("music") || q.contains("gaane") -> "audio"
                q.contains("pdf") || q.contains("document") || q.contains("doc") -> "document"
                else -> null
            }
        }
        return null
    }

    private fun extractSuperlative(q: String): String? {
        return when {
            q.contains("biggest") || q.contains("largest") || q.contains("highest") -> "BIGGEST"
            q.contains("smallest") || q.contains("lowest") -> "SMALLEST"
            q.contains("newest") || q.contains("latest") || q.contains("most recent") -> "NEWEST"
            q.contains("oldest") || q.contains("earliest") -> "OLDEST"
            q.contains("longest") -> "LONGEST"
            q.contains("shortest") -> "SHORTEST"
            else -> null
        }
    }

    private fun isAadhaarQuery(q: String): Boolean {
        return q.contains("aadhaar") || q.contains("aadhar") || q.contains("uidai")
    }

    private fun isPanQuery(q: String): Boolean {
        return q.contains("pan card") || q.contains("pan number") || q.contains("pan no") ||
                (q.contains("pan") && !q.contains("company") && !q.contains("japan") && !q.contains("expand"))
    }

    private fun isScheduleQuery(q: String): Boolean {
        return q.contains("schedule") || q.contains("exam") || q.contains("timetable") ||
                q.contains("datesheet") || q.contains("test date")
    }

    private fun isMarksheetQuery(q: String): Boolean {
        return q.contains("marksheet") || q.contains("mark sheet") || q.contains("grade card") ||
                q.contains("report card") || q.contains("score") || q.contains("percentage") ||
                q.contains("cgpa") || q.contains("10th marks") || q.contains("12th marks")
    }

    private fun extractAggregationOp(q: String): AggregationOp {
        return when {
            q.contains("highest") || q.contains("maximum") || q.contains("most expensive") -> AggregationOp.MAX
            q.contains("lowest") || q.contains("minimum") || q.contains("cheapest") -> AggregationOp.MIN
            q.contains("total spent") || q.contains("sum of") || q.contains("total amount spent") -> AggregationOp.SUM
            q.contains("average") -> AggregationOp.SUM
            else -> AggregationOp.NONE
        }
    }

    fun extractMonth(q: String): String? {
        val words = q.replace("?", "").replace(",", "").split(" ")
        for (w in words) {
            val clean = w.trim()
            if (MONTH_MAP.containsKey(clean)) {
                return MONTH_MAP[clean]
            }
        }
        return null
    }

    fun extractYear(q: String): Int? {
        val pattern = Pattern.compile("""\b(20[123][0-9])\b""")
        val m = pattern.matcher(q)
        if (m.find()) {
            return m.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun extractDocumentType(q: String): String? {
        return when {
            q.contains("water bill") || q.contains("water") -> DocumentCategories.WATER_BILL
            q.contains("gas bill") || q.contains("gas") || q.contains("png") || q.contains("lpg") -> DocumentCategories.GAS_BILL
            q.contains("electricity") || q.contains("elec") || q.contains("ele bill") || q.contains("power bill") || q.contains("light bill") -> DocumentCategories.ELECTRICITY_BILL
            q.contains("aadhaar") || q.contains("aadhar") -> DocumentCategories.AADHAAR
            q.contains("pan") -> DocumentCategories.PAN
            q.contains("marksheet") || q.contains("mark sheet") -> DocumentCategories.MARKSHEET
            q.contains("schedule") || q.contains("timetable") || q.contains("datesheet") -> DocumentCategories.SCHEDULE
            q.contains("bank statement") -> DocumentCategories.BANK_STATEMENT
            q.contains("certificate") -> DocumentCategories.CERTIFICATE
            q.contains("resume") || q.contains("cv") -> DocumentCategories.RESUME
            q.contains("invoice") || q.contains("receipt") -> DocumentCategories.INVOICE
            else -> null
        }
    }

    private fun extractUtility(q: String): String? {
        return when {
            q.contains("water") -> "Water"
            q.contains("gas") -> "Gas"
            q.contains("electricity") || q.contains("elec") || q.contains("power") || q.contains("light") || q.contains("bescom") -> "Electricity"
            else -> null
        }
    }

    fun extractClass(q: String): String? {
        val pattern = Pattern.compile("""(?i)\b(10th|12th|class\s+10|class\s+12|class\s+x|class\s+xii|secondary\s+school|senior\s+school)\b""")
        val m = pattern.matcher(q)
        if (m.find()) {
            val matched = m.group(1)!!.lowercase(Locale.ROOT)
            return when {
                matched.contains("10") || matched.contains("x") || matched.contains("secondary") -> "10th"
                matched.contains("12") || matched.contains("xii") || matched.contains("senior") -> "12th"
                else -> matched
            }
        }
        return null
    }

    fun extractBoard(q: String): String? {
        return when {
            q.contains("cbse") -> "CBSE"
            q.contains("icse") || q.contains("isc") -> "ICSE"
            q.contains("state board") || q.contains("state") -> "State Board"
            else -> null
        }
    }

    private fun extractDateFilter(q: String): String? {
        return when {
            q.contains("today") -> "today"
            q.contains("yesterday") -> "yesterday"
            q.contains("this week") -> "week"
            q.contains("this month") -> "month"
            else -> null
        }
    }
}
