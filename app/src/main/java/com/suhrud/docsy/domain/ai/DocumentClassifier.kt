package com.suhrud.docsy.domain.ai

import com.suhrud.docsy.data.model.DocumentCategories
import com.suhrud.docsy.data.model.DocumentFieldEntity
import com.suhrud.docsy.data.model.ExamScheduleItem
import com.suhrud.docsy.data.model.StructuredFields
import com.suhrud.docsy.domain.util.Verhoeff
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object DocumentClassifier {

    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )

    private val MONTH_ABBR = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    )

    fun classifyAndExtract(fileName: String, content: String): StructuredFields {
        val lowerText = content.lowercase(Locale.ROOT)
        val lowerName = fileName.lowercase(Locale.ROOT)

        val category = classifyContent(lowerText, lowerName)
        val amount = extractAmount(content)
        val month = extractMonth(lowerText, lowerName)
        val year = extractYear(lowerText, lowerName)
        val dueDate = extractDueDate(content)
        val consumerNo = extractConsumerNumber(content)
        val billNo = extractBillNumber(content)
        val provider = extractProvider(content, category)
        val aadhaar = extractAadhaar(content)
        val pan = extractPan(content)
        val rollNo = extractRollNumber(content)
        val board = extractBoard(content)
        val standard = extractClass(content)
        val score = extractPercentageOrCgpa(content)
        val ownerName = extractOwnerName(content)
        val schedule = if (category == DocumentCategories.SCHEDULE) extractScheduleItems(content) else null

        val maskedAadhaar = aadhaar?.let { "XXXX-XXXX-${it.takeLast(4)}" }

        val utility = when (category) {
            DocumentCategories.ELECTRICITY_BILL -> "Electricity"
            DocumentCategories.WATER_BILL -> "Water"
            DocumentCategories.GAS_BILL -> "Gas"
            else -> null
        }

        return StructuredFields(
            category = category,
            billingMonth = month,
            billingYear = year,
            amountPayable = amount,
            dueDate = dueDate,
            billNumber = billNo,
            consumerNumber = consumerNo,
            utilityType = utility,
            issuerOrProvider = provider,
            aadhaarNumberMasked = maskedAadhaar,
            aadhaarNumberFull = aadhaar,
            panNumber = pan,
            rollNumber = rollNo,
            boardName = board,
            classOrStandard = standard,
            percentageOrCgpa = score,
            personName = ownerName,
            exams = schedule
        )
    }

    private fun classifyContent(text: String, name: String): String {
        if (text.contains("unique identification authority") ||
            text.contains("uidai") ||
            text.contains("mera aadhaar") ||
            (text.contains("aadhaar") && (text.contains("government of india") || text.contains("govt of india"))) ||
            extractAadhaar(text) != null
        ) {
            return DocumentCategories.AADHAAR
        }

        if (text.contains("permanent account number") ||
            (text.contains("income tax department") && text.contains("pan")) ||
            extractPan(text) != null
        ) {
            return DocumentCategories.PAN
        }

        if (text.contains("water supply") ||
            text.contains("bwssb") ||
            text.contains("water board") ||
            text.contains("sewerage board") ||
            text.contains("water charge") ||
            text.contains("jal board") ||
            (text.contains("water") && text.contains("bill") && (text.contains("consumption") || text.contains("meter")))
        ) {
            return DocumentCategories.WATER_BILL
        }

        if (text.contains("piped gas") ||
            text.contains("lpg") ||
            text.contains("png") ||
            text.contains("indane") ||
            text.contains("bharat gas") ||
            text.contains("hp gas") ||
            text.contains("indraprastha gas") ||
            text.contains("mahanagar gas") ||
            text.contains("gas distribution")
        ) {
            return DocumentCategories.GAS_BILL
        }

        if (text.contains("electricity") ||
            text.contains("bescom") ||
            text.contains("tneb") ||
            text.contains("mseb") ||
            text.contains("bses") ||
            text.contains("discom") ||
            text.contains("power distribution") ||
            text.contains("electric supply") ||
            text.contains("kwh") ||
            text.contains("units consumed")
        ) {
            return DocumentCategories.ELECTRICITY_BILL
        }

        if (text.contains("statement of marks") ||
            text.contains("grade card") ||
            text.contains("central board of secondary education") ||
            text.contains("cbse") ||
            text.contains("icse") ||
            (text.contains("marksheet") || text.contains("mark sheet")) ||
            (text.contains("examination") && text.contains("marks obtained"))
        ) {
            return DocumentCategories.MARKSHEET
        }

        if (text.contains("exam schedule") ||
            text.contains("examination timetable") ||
            text.contains("examination schedule") ||
            text.contains("time table") ||
            text.contains("timetable") ||
            text.contains("date sheet") ||
            text.contains("datesheet")
        ) {
            return DocumentCategories.SCHEDULE
        }

        if ((text.contains("bank") && text.contains("statement")) ||
            text.contains("account balance") ||
            (text.contains("debit") && text.contains("credit") && text.contains("ifsc"))
        ) {
            return DocumentCategories.BANK_STATEMENT
        }

        if (text.contains("certificate of") ||
            text.contains("certifies that") ||
            text.contains("has successfully completed") ||
            text.contains("completion certificate")
        ) {
            return DocumentCategories.CERTIFICATE
        }

        if (text.contains("tax invoice") ||
            text.contains("gstin") ||
            text.contains("invoice no") ||
            text.contains("receipt no")
        ) {
            return DocumentCategories.INVOICE
        }

        if (text.contains("curriculum vitae") ||
            (text.contains("resume") && (text.contains("experience") || text.contains("education")))
        ) {
            return DocumentCategories.RESUME
        }

        if (text.isBlank()) {
            return DocumentCategories.UNKNOWN
        }

        return DocumentCategories.GENERAL
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?i)(?:net\s+amount|amount\s+payable|total\s+amount|bill\s+amount|balance\s+due|total)[\s:]*(?:₹|rs\.?|inr)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{2})?)"""),
            Pattern.compile("""(?:₹|rs\.?|inr)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{2})?)""", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "")
                val amt = raw?.toDoubleOrNull()
                if (amt != null && amt > 0) return amt
            }
        }
        return null
    }

    private fun extractMonth(text: String, fileName: String): String? {
        for (m in MONTH_NAMES) {
            if (text.contains(m) || fileName.contains(m)) {
                return m.replaceFirstChar { it.uppercase() }
            }
        }
        for ((idx, abbr) in MONTH_ABBR.withIndex()) {
            val regex = "\\b$abbr\\b".toRegex(RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(text) || regex.containsMatchIn(fileName)) {
                return MONTH_NAMES[idx].replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun extractYear(text: String, fileName: String): Int? {
        val yearPattern = Pattern.compile("""\b(20[123][0-9])\b""")
        val mText = yearPattern.matcher(text)
        if (mText.find()) {
            return mText.group(1)?.toIntOrNull()
        }
        val mName = yearPattern.matcher(fileName)
        if (mName.find()) {
            return mName.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun extractDueDate(text: String): String? {
        val pattern = Pattern.compile(
            """(?i)(?:due\s+date|pay\s+before|last\s+date)[\s:]*([0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4}|[0-9]{1,2}\s+[A-Za-z]+\s+[0-9]{4})"""
        )
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)?.trim()
        return null
    }

    private fun extractConsumerNumber(text: String): String? {
        val pattern = Pattern.compile(
            """(?i)(?:consumer\s+no|consumer\s+id|account\s+no|ca\s+no|consumer\s+number)[\s:#.]*([A-Za-z0-9\-_/]{5,20})"""
        )
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)?.trim()
        return null
    }

    private fun extractBillNumber(text: String): String? {
        val pattern = Pattern.compile(
            """(?i)(?:bill\s+no|bill\s+number|invoice\s+no|receipt\s+no)[\s:#.]*([A-Za-z0-9\-_/]{4,20})"""
        )
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)?.trim()
        return null
    }

    private fun extractProvider(text: String, category: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("bescom") -> "BESCOM"
            lower.contains("bwssb") -> "BWSSB"
            lower.contains("tneb") -> "TNEB"
            lower.contains("mseb") -> "MSEB"
            lower.contains("bses") -> "BSES"
            lower.contains("tata power") -> "Tata Power"
            lower.contains("adani") -> "Adani Electricity"
            lower.contains("cbse") -> "CBSE"
            lower.contains("icse") -> "ICSE"
            else -> null
        }
    }

    fun extractAadhaar(text: String): String? {
        val pattern = Pattern.compile("""\b([0-9]{4})\s*([0-9]{4})\s*([0-9]{4})\b""")
        val m = pattern.matcher(text)
        while (m.find()) {
            val candidate = "${m.group(1)}${m.group(2)}${m.group(3)}"
            if (Verhoeff.validateAadhaar(candidate)) {
                return candidate
            }
        }
        return null
    }

    fun extractPan(text: String): String? {
        val pattern = Pattern.compile("""\b([A-Z]{5}[0-9]{4}[A-Z])\b""")
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)
        return null
    }

    fun extractOwnerName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val nameLabelRegex = Regex("""(?i)^(?:name|holder|person|student|employee)[\s:]+(.+)""")
        val relExclusionRegex = Regex("""(?i)^(?:father|mother|spouse|s/o|d/o|w/o|c/o|son of|daughter of|wife of|care of)[\s:]+""")

        for (line in lines) {
            if (relExclusionRegex.containsMatchIn(line)) continue

            val match = nameLabelRegex.find(line)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 3..40 && candidate.all { it.isLetter() || it == ' ' || it == '.' }) {
                    return candidate
                }
            }
        }

        // Fallback for resumes: first prominent line without email/url
        for (line in lines.take(5)) {
            if (relExclusionRegex.containsMatchIn(line)) continue
            if (!line.contains("@") && !line.contains("http") && line.length in 3..35 && line.all { it.isLetter() || it == ' ' || it == '.' }) {
                return line
            }
        }

        return null
    }

    private fun extractRollNumber(text: String): String? {
        val pattern = Pattern.compile("""(?i)(?:roll\s+no|roll\s+number|reg\s+no|seat\s+no)[\s:#.]*([A-Za-z0-9\-_]+)""")
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)?.trim()
        return null
    }

    private fun extractBoard(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("central board of secondary education") || lower.contains("cbse") -> "CBSE"
            lower.contains("council for the indian school certificate") || lower.contains("icse") || lower.contains("isc") -> "ICSE"
            lower.contains("state board") -> "State Board"
            else -> null
        }
    }

    private fun extractClass(text: String): String? {
        val pattern = Pattern.compile("""(?i)\b(10th|12th|class\s+10|class\s+12|class\s+x|class\s+xii|secondary\s+school|senior\s+school)\b""")
        val m = pattern.matcher(text)
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

    private fun extractPercentageOrCgpa(text: String): String? {
        val pattern = Pattern.compile("""(?i)(?:cgpa|percentage|percent|result)[\s:]*([0-9]{1,2}(?:\.[0-9]{1,2})?%?)""")
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1)?.trim()
        return null
    }

    private fun extractScheduleItems(text: String): List<ExamScheduleItem> {
        val items = mutableListOf<ExamScheduleItem>()
        val lines = text.lines()
        val dateRegex = Pattern.compile("""([0-9]{1,2}\s+[A-Za-z]+\s+[0-9]{4}|[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4})""")

        for (line in lines) {
            val m = dateRegex.matcher(line)
            if (m.find()) {
                val date = m.group(1) ?: ""
                val rest = line.replace(date, "").trim()
                if (rest.isNotBlank() && rest.length > 3) {
                    val timeRegex = Pattern.compile("""([0-9]{1,2}:[0-9]{2}\s*(?:am|pm)?)""", Pattern.CASE_INSENSITIVE)
                    val tm = timeRegex.matcher(rest)
                    val time = if (tm.find()) tm.group(1) ?: "" else ""
                    val subject = rest.replace(time, "").replace(Regex("""[-|—:,]"""), " ").trim()
                    items.add(ExamScheduleItem(subject = subject, date = date, time = time))
                }
            }
        }
        return items
    }

    fun toFieldEntities(documentId: String, fields: StructuredFields): List<DocumentFieldEntity> {
        val list = mutableListOf<DocumentFieldEntity>()
        fields.amountPayable?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "amount", fieldValue = "₹$it", numericValue = it))
        }
        fields.billingMonth?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "month", fieldValue = it))
        }
        fields.billingYear?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "year", fieldValue = it.toString(), numericValue = it.toDouble()))
        }
        fields.dueDate?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "due_date", fieldValue = it))
        }
        fields.consumerNumber?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "consumer_no", fieldValue = it))
        }
        fields.billNumber?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "bill_no", fieldValue = it))
        }
        fields.utilityType?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "utility", fieldValue = it))
        }
        fields.issuerOrProvider?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "issuer", fieldValue = it))
        }
        fields.aadhaarNumberMasked?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "aadhaar_masked", fieldValue = it))
        }
        fields.aadhaarNumberFull?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "aadhaar_full", fieldValue = it))
        }
        fields.panNumber?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "pan", fieldValue = it))
        }
        fields.rollNumber?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "roll_no", fieldValue = it))
        }
        fields.boardName?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "board", fieldValue = it))
        }
        fields.classOrStandard?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "class", fieldValue = it))
        }
        fields.percentageOrCgpa?.let {
            list.add(DocumentFieldEntity(documentId = documentId, fieldKey = "score", fieldValue = it))
        }
        return list
    }

    fun toJson(fields: StructuredFields): String {
        val obj = JSONObject()
        obj.put("category", fields.category)
        fields.billingMonth?.let { obj.put("billingMonth", it) }
        fields.billingYear?.let { obj.put("billingYear", it) }
        fields.amountPayable?.let { obj.put("amountPayable", it) }
        fields.dueDate?.let { obj.put("dueDate", it) }
        fields.consumerNumber?.let { obj.put("consumerNumber", it) }
        fields.billNumber?.let { obj.put("billNumber", it) }
        fields.utilityType?.let { obj.put("utilityType", it) }
        fields.issuerOrProvider?.let { obj.put("issuerOrProvider", it) }
        fields.aadhaarNumberMasked?.let { obj.put("aadhaarNumberMasked", it) }
        fields.aadhaarNumberFull?.let { obj.put("aadhaarNumberFull", it) }
        fields.panNumber?.let { obj.put("panNumber", it) }
        fields.rollNumber?.let { obj.put("rollNumber", it) }
        fields.boardName?.let { obj.put("boardName", it) }
        fields.classOrStandard?.let { obj.put("classOrStandard", it) }
        fields.percentageOrCgpa?.let { obj.put("percentageOrCgpa", it) }

        if (fields.exams != null) {
            val arr = JSONArray()
            for (exam in fields.exams) {
                val eObj = JSONObject()
                eObj.put("subject", exam.subject)
                eObj.put("date", exam.date)
                eObj.put("time", exam.time)
                eObj.put("venue", exam.venue)
                arr.put(eObj)
            }
            obj.put("exams", arr)
        }
        return obj.toString()
    }
}
