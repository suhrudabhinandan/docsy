package com.suhrud.docsy.understanding

import com.suhrud.docsy.extractor.LayoutPageAnalysisResult
import java.math.BigDecimal
import java.math.RoundingMode

data class LayoutSubject(
    val name: String,
    val code: String?,
    val marks: String?,
    val maxMarks: String?,
    val grade: String?,
    val credits: String?,
    val gradePoint: String?,
    val confidence: Float = 0.9f
)

data class LayoutMarksheetField(
    val key: String,
    val label: String,
    val value: String,
    val confidence: Float = 0.9f,
    val derived: Boolean = false,
    val formula: String? = null
)

data class LayoutMarksheet(
    val fields: List<LayoutMarksheetField>,
    val subjects: List<LayoutSubject>
) {
    fun field(key: String): String? = fields.firstOrNull { it.key == key }?.value
}

/**
 * Understands a marksheet from its layout instead of from loose text lines:
 *  - subject rows come from table columns named by the header row (Subject / Marks / Grade / Credits ...)
 *  - roll number, SGPA, CGPA, result and percentage come from label -> value pairs (right of, or below, a label)
 *  - the same fields are also searched in the plain text as a last resort
 */
object LayoutMarksheetExtractor {

    private enum class Col { CODE, NAME, THEORY, PRACTICAL, MARKS, MAX, GRADE, GRADE_POINT, CREDIT, RESULT, OTHER }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun classifyHeader(raw: String): Col {
        val s = normalize(raw)
        return when {
            s.isBlank() -> Col.OTHER
            s.contains("words") -> Col.OTHER // "TOTAL (IN WORDS)" is the total spelled out
            s.contains("grade point") || s.contains("grade pt") || s == "gp" || s.startsWith("gp ") || s == "g p" -> Col.GRADE_POINT
            s.contains("credit") || s == "cr" || s == "ch" || s.contains("credit hours") -> Col.CREDIT
            s.contains("grade") -> Col.GRADE
            s.contains("max") || s.contains("full marks") || s.contains("out of") -> Col.MAX
            s.contains("code") -> Col.CODE
            s.contains("subject") || s.contains("paper") || s.contains("course") || s.contains("title") ||
                s.contains("particulars") || s.contains("name of") -> Col.NAME
            s.contains("theory") -> Col.THEORY
            s.contains("practical") || s.contains("internal") || s == "ia" || s == "pr" || s == "ia pr" -> Col.PRACTICAL
            s.contains("obtained") || s.contains("secured") || s.contains("total") || s.contains("marks") || s.contains("score") -> Col.MARKS
            s.contains("result") || s.contains("status") || s.contains("remark") -> Col.RESULT
            else -> Col.OTHER
        }
    }

    private val NOT_A_SUBJECT = Regex("(?i)^(grand\\s+)?total|sgpa|cgpa|^gpa|result|percentage|aggregate|^credits?\\b|semester|^s\\.?no|^sr\\b|^additional subject$")
    private val INTEGER = Regex("""^\d{1,4}$""")

    /** Subject codes printed on CBSE certificates. Used when the OCR could not read the printed name. */
    private val CBSE_SUBJECTS = mapOf(
        "184" to "ENGLISH LNG & LIT.", "101" to "ENGLISH COMMUNICATIVE", "002" to "HINDI COURSE-A",
        "085" to "HINDI COURSE-B", "041" to "MATHEMATICS STANDARD", "241" to "MATHEMATICS BASIC",
        "086" to "SCIENCE", "087" to "SOCIAL SCIENCE", "402" to "INFORMATION TECHNOLOGY",
        "165" to "COMPUTER APPLICATIONS", "417" to "ARTIFICIAL INTELLIGENCE", "122" to "SANSKRIT"
    )
    private val NUMBER = Regex("""^\d{1,3}(?:\.\d{1,2})?$""")
    private val MARKS_OVER_MAX = Regex("""^(\d{1,3}(?:\.\d{1,2})?)\s*/\s*(\d{1,3})$""")
    private val GRADE_TOKEN = Regex("""^[A-Z][A-Z]?[+\-]?$|^[A-Z]\d$""")

    /** "094" -> "94", "000" -> "0", "75.50" stays. Marks are printed zero-padded on some certificates. */
    private fun trimZeros(n: String?): String? {
        if (n == null) return null
        val t = n.trimStart('0')
        return when {
            t.isEmpty() -> "0"
            t.startsWith(".") -> "0$t"
            else -> t
        }
    }

    fun extract(pages: List<LayoutPageAnalysisResult>, fullText: String): LayoutMarksheet {
        val fields = LinkedHashMap<String, LayoutMarksheetField>()

        // 1. Label -> value pairs found by layout.
        for (page in pages) {
            for (kv in page.keyValues) {
                val key = canonicalKey(kv.key) ?: continue
                val value = cleanValue(key, kv.value) ?: continue
                if (key !in fields) {
                    val f = field(key, value, kv.confidence)
                    // "Registration Number" stays "Registration Number" rather than becoming "Roll No".
                    fields[key] = if (key == "rollNo" && kv.key.count { it.isLetter() } in 4..30) f.copy(label = kv.key.trim().trimEnd(':')) else f
                }
            }
        }

        // 2. Plain-text fallbacks for anything still missing.
        textFallbacks(fullText).forEach { (key, value) ->
            if (key !in fields) fields[key] = field(key, value, 0.8f)
        }

        // 3. Subject rows from tables.
        val subjects = extractSubjects(pages).ifEmpty { subjectsFromText(fullText) }
            .map { it.copy(marks = trimZeros(it.marks), maxMarks = trimZeros(it.maxMarks)) }

        // 4. SGPA that the page did not print can be derived from credits x grade points.
        if ("sgpa" !in fields) {
            computeSgpa(subjects)?.let {
                fields["sgpa"] = LayoutMarksheetField(
                    key = "sgpa", label = "SGPA", value = it, confidence = 0.85f,
                    derived = true, formula = "sum(credits x grade point) / sum(credits)"
                )
            }
        }

        return LayoutMarksheet(fields.values.toList(), subjects)
    }

    /** True when at least one subject row could be read from the layout. */
    fun hasSubjects(pages: List<LayoutPageAnalysisResult>): Boolean = extractSubjects(pages).isNotEmpty()

    // -----------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------

    private fun field(key: String, value: String, confidence: Float) = LayoutMarksheetField(
        key = key,
        label = when (key) {
            "rollNo" -> "Roll No"
            "studentName" -> "Student Name"
            "sgpa" -> "SGPA"
            "cgpa" -> "CGPA"
            "gpa" -> "GPA"
            "cpi" -> "CPI"
            "spi" -> "SPI"
            "rank" -> "Rank"
            "percentile" -> "Percentile"
            "result" -> "Result"
            "printedPercentage" -> "Percentage"
            "semester" -> "Semester"
            "examination" -> "Examination"
            "totalMarks" -> "Total Marks"
            "totalCredits" -> "Total Credits"
            "division" -> "Division"
            else -> key
        },
        value = value,
        confidence = confidence
    )

    private fun canonicalKey(rawKey: String): String? {
        val k = normalize(rawKey)
        return when {
            k in setOf("roll no", "roll number", "roll", "seat no", "seat number", "enrollment no", "enrolment no",
                "enrollment number", "registration no", "registration number", "reg no", "hall ticket no", "application no") -> "rollNo"
            k in setOf("name", "student name", "name of student", "name of candidate", "candidate name",
                "name of the student", "candidate s name") -> "studentName"
            k == "sgpa" -> "sgpa"
            k == "cgpa" -> "cgpa"
            k == "gpa" -> "gpa"
            k == "cpi" -> "cpi"
            k == "spi" -> "spi"
            k in setOf("rank", "air", "all india rank", "category rank") -> "rank"
            k in setOf("percentile", "nta score", "gate score", "percentile score") -> "percentile"
            k in setOf("result", "final result", "result status", "status") -> "result"
            k in setOf("percentage", "percent", "overall percentage", "aggregate", "aggregate percentage") -> "printedPercentage"
            k in setOf("semester", "sem") -> "semester"
            k in setOf("examination", "exam") -> "examination"
            k in setOf("total marks", "grand total") -> "totalMarks"
            k == "division" -> "division"
            k in setOf("total credits", "total credit") -> "totalCredits"
            // "Total Credits 22 SGPA" -> the label is whatever the key ends with
            k.endsWith(" sgpa") -> "sgpa"
            k.endsWith(" cgpa") -> "cgpa"
            k.endsWith(" result") -> "result"
            k.endsWith(" percentage") -> "printedPercentage"
            else -> null
        }
    }

    /** Rejects values that cannot belong to the field (a name that is a number, a GPA of 85, ...). */
    private fun cleanValue(key: String, raw: String): String? {
        val v = raw.trim().trim('|', ':', '-').trim()
        if (v.isEmpty() || v.length > 60) return null
        return when (key) {
            "rollNo" -> Regex("""[A-Za-z0-9][A-Za-z0-9/\-]{3,23}""").findAll(v).map { it.value }.firstOrNull { t -> t.count { c -> c.isDigit() } >= 3 }
            "studentName" -> v.takeIf { it.count { c -> c.isLetter() } >= 3 && it.none { c -> c.isDigit() } && it.length <= 45 }
            "sgpa", "cgpa", "gpa" -> Regex("""\d{1,2}(?:\.\d{1,3})?""").find(v)?.value?.takeIf { (it.toDoubleOrNull() ?: 99.0) in 0.0..10.0 }
            "printedPercentage" -> Regex("""\d{1,3}(?:\.\d{1,2})?""").find(v)?.value?.takeIf { (it.toDoubleOrNull() ?: 999.0) in 0.0..100.0 }?.let { "$it%" }
            "result" -> v.takeIf { it.length <= 30 && it.any { c -> c.isLetter() } }
            "totalCredits" -> v.takeIf { it.matches(Regex("""\d{1,3}(?:\.\d)?""")) }
            "semester", "examination", "division", "totalMarks" -> v
            else -> v
        }
    }

    private fun textFallbacks(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun find(key: String, regex: Regex) {
            regex.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                cleanValue(key, raw)?.let { out[key] = it }
            }
        }
        find("sgpa", Regex("""(?i)\bS\.?\s?G\.?\s?P\.?\s?A\.?\b\s*[:=\-]?\s*(\d{1,2}(?:\.\d{1,3})?)"""))
        find("cgpa", Regex("""(?i)\bC\.?\s?G\.?\s?P\.?\s?A\.?\b\s*[:=\-]?\s*(\d{1,2}(?:\.\d{1,3})?)"""))
        find("rollNo", Regex("""(?i)\b(?:roll|seat|enrol+ment|registration|reg)\.?\s*(?:no|number|#)\.?\s*[:\-]?\s*([A-Z0-9][A-Z0-9/\-]{3,23})"""))
        find("printedPercentage", Regex("""(?i)\b(?:overall\s+)?(?:percentage|percent|aggregate)\s*[:\-]?\s*(\d{1,3}(?:\.\d{1,2})?)"""))
        find("result", Regex("""(?i)\bresult\s*[:\-]?\s*(pass(?:ed)?|fail(?:ed)?|promoted|qualified|distinction|compartment)"""))
        return out
    }

    // -----------------------------------------------------------------------------------------
    // Subjects
    // -----------------------------------------------------------------------------------------

    private fun extractSubjects(pages: List<LayoutPageAnalysisResult>): List<LayoutSubject> {
        val out = ArrayList<LayoutSubject>()
        for (page in pages) {
            val byTable = page.tables.groupBy { it.tableId }
            for ((_, cells) in byTable) {
                val rows = cells.groupBy { it.row }.toSortedMap()
                if (rows.size < 2) continue

                // Header row: the first row whose cells name at least two kinds of column.
                var headerRow = -1
                var headerMap: Map<Int, Col> = emptyMap()
                for ((rowIdx, rowCells) in rows) {
                    val map = rowCells.associate { it.col to classifyHeader(it.text) }.filterValues { it != Col.OTHER }
                    val kinds = map.values.toSet()
                    if (kinds.size >= 2 && (Col.NAME in kinds || kinds.size >= 3)) { headerRow = rowIdx; headerMap = map; break }
                }
                if (headerRow < 0) continue

                val nameCol = headerMap.entries.firstOrNull { it.value == Col.NAME }?.key
                    ?: guessNameColumn(rows, headerRow)
                    ?: continue
                val codeCol = headerMap.entries.firstOrNull { it.value == Col.CODE }?.key
                val marksCol = headerMap.entries.firstOrNull { it.value == Col.MARKS }?.key
                    ?: headerMap.entries.lastOrNull { it.value == Col.THEORY }?.key
                val maxCol = headerMap.entries.firstOrNull { it.value == Col.MAX }?.key
                val gradeCol = headerMap.entries.firstOrNull { it.value == Col.GRADE }?.key
                val gpCol = headerMap.entries.firstOrNull { it.value == Col.GRADE_POINT }?.key
                val creditCol = headerMap.entries.firstOrNull { it.value == Col.CREDIT }?.key

                val body = cells.filter { it.row > headerRow }
                val tableSubjects = ArrayList<LayoutSubject>()

                for (group in logicalRows(body)) {
                    // Cells of one logical row, top to bottom, so a wrapped name reads in order.
                    val byCol = HashMap<Int, String>()
                    for (c in group.sortedWith(compareBy({ (it.top + it.bottom) / 2 }, { it.col }))) {
                        byCol[c.col] = ((byCol[c.col] ?: "") + " " + c.text.trim()).trim()
                    }

                    val code = codeCol?.let { byCol[it] }?.takeIf { it.isNotBlank() }
                    var name = byCol[nameCol].orEmpty().replace(Regex("(?i)^additional subject\\s*"), "").trim()
                    if (name.count { it.isLetter() } < 3 && code != null) name = CBSE_SUBJECTS[code].orEmpty()
                    if (name.count { it.isLetter() } < 3 || NOT_A_SUBJECT.containsMatchIn(name)) continue

                    var marks: String? = null
                    var max: String? = maxCol?.let { byCol[it] }?.takeIf { NUMBER.matches(it) }
                    val marksCell = byCol[marksCol ?: -1]
                    if (marksCell != null) {
                        val m = MARKS_OVER_MAX.find(marksCell)
                        if (m != null) {
                            marks = m.groupValues[1]
                            if (max == null) max = m.groupValues[2]
                        } else if (NUMBER.matches(marksCell)) {
                            marks = marksCell
                        }
                    }
                    val grade = gradeCol?.let { byCol[it] }?.trim()?.takeIf { GRADE_TOKEN.matches(it) }
                    val gp = gpCol?.let { byCol[it] }?.trim()?.takeIf { NUMBER.matches(it) && (it.toDoubleOrNull() ?: 99.0) <= 10.0 }
                    val credits = creditCol?.let { byCol[it] }?.trim()?.takeIf { NUMBER.matches(it) && (it.toDoubleOrNull() ?: 99.0) <= 30.0 }

                    // A real subject row carries a result of some kind; description or footer rows do not.
                    if (marks == null && grade == null && gp == null) continue

                    tableSubjects.add(LayoutSubject(name, code, marks, max, grade, credits, gp))
                }

                // Marks with no "maximum" column (CBSE certificates): every subject is out of 100.
                val assumeHundred = maxCol == null && tableSubjects.isNotEmpty() &&
                    tableSubjects.all { it.marks != null && (it.marks.toDoubleOrNull() ?: 999.0) <= 100.0 }
                out.addAll(
                    if (assumeHundred) tableSubjects.map { it.copy(maxMarks = "100", confidence = 0.85f) } else tableSubjects
                )
            }
        }
        return out
    }

    /**
     * Groups the body cells of a table into logical rows. Wrapped cells are often centred vertically, so a name
     * can sit one line above and below its serial number. When a column holds whole numbers (serial numbers or
     * subject codes) each of those numbers anchors a row, and every other cell joins the nearest anchor.
     * Without such a column, each physical row is a logical row.
     */
    private fun logicalRows(body: List<com.suhrud.docsy.extractor.LayoutTableCell>): List<List<com.suhrud.docsy.extractor.LayoutTableCell>> {
        val hasGeometry = body.any { it.bottom > it.top }
        var anchorCol: Int? = null
        if (hasGeometry) {
            for (ci in body.map { it.col }.distinct().sorted()) {
                val values = body.filter { it.col == ci }
                val ints = values.count { INTEGER.matches(it.text.trim()) }
                if (ints >= 3 && ints >= values.size * 0.6) { anchorCol = ci; break }
            }
        }
        if (anchorCol == null) {
            return body.groupBy { it.row }.toSortedMap().values.toList()
        }

        fun cy(c: com.suhrud.docsy.extractor.LayoutTableCell) = (c.top + c.bottom) / 2.0
        val anchors = body.filter { it.col == anchorCol && INTEGER.matches(it.text.trim()) }.sortedBy { cy(it) }
        val groups = anchors.map { mutableListOf(it) }
        for (c in body) {
            if (c in anchors) continue
            val nearest = anchors.indices.minByOrNull { Math.abs(cy(c) - cy(anchors[it])) } ?: continue
            val distance = Math.abs(cy(c) - cy(anchors[nearest]))
            val height = (c.bottom - c.top).coerceAtLeast(1)
            // Close enough to be a wrapped line of that row; footers ("Total Credits", SGPA) are further away.
            if (distance <= height * 1.0 && !NOT_A_SUBJECT.containsMatchIn(c.text.trim())) groups[nearest].add(c)
        }
        return groups
    }

    /** No header names the subject column: use the column holding the longest words. */
    private fun guessNameColumn(rows: Map<Int, List<com.suhrud.docsy.extractor.LayoutTableCell>>, headerRow: Int): Int? {
        val score = HashMap<Int, Int>()
        for ((rowIdx, cells) in rows) {
            if (rowIdx <= headerRow) continue
            for (c in cells) score[c.col] = (score[c.col] ?: 0) + c.text.count { it.isLetter() }
        }
        return score.maxByOrNull { it.value }?.takeIf { it.value >= 6 }?.key
    }

    // -----------------------------------------------------------------------------------------
    // Subjects from plain text (when the layout has no usable table)
    // -----------------------------------------------------------------------------------------

    private val UNIVERSITY_ROW = Regex("""(\d{2}[A-Z]{2}\d[A-Z]{3}\d{3})\s+([A-Z][A-Z0-9 &\-/().,]*?)\s+([TP])\s+(\d{1,2})\s+([A-Z][+\-]?)(?=\s|$)""")
    private val CBSE_ROW = Regex("""\b(\d{3})\s+([A-Z][A-Z &.\-:/()]+?)\s+(\d{3})\s+(\d{2,3})\s+(\d{3})\s+[A-Z ]{6,40}?\s+([A-E][12]?)\b""")

    /**
     * Reads subject rows straight from text, tolerant of line breaks inside a row. Two printed styles:
     *  - university: CODE  NAME (may wrap)  T|P  CREDIT  GRADE
     *  - CBSE: CODE  SUBJECT  THEORY  IA  TOTAL  TOTAL IN WORDS  GRADE
     */
    private fun subjectsFromText(text: String): List<LayoutSubject> {
        val flat = text.replace(Regex("""\[\[PAGE \d+\]\]"""), " ").replace(Regex("""\s+"""), " ")
        val university = UNIVERSITY_ROW.findAll(flat).map { m ->
            LayoutSubject(
                name = m.groupValues[2].trim(), code = m.groupValues[1], marks = null, maxMarks = null,
                grade = m.groupValues[5], credits = m.groupValues[4], gradePoint = null, confidence = 0.85f
            )
        }.toList()
        if (university.size >= 2) return university

        val cbse = CBSE_ROW.findAll(flat).map { m ->
            LayoutSubject(
                name = m.groupValues[2].trim(), code = m.groupValues[1], marks = m.groupValues[5], maxMarks = "100",
                grade = m.groupValues[6], credits = null, gradePoint = null, confidence = 0.8f
            )
        }.toList()
        return if (cbse.size >= 2) cbse else emptyList()
    }

    private fun computeSgpa(subjects: List<LayoutSubject>): String? {
        val rated = subjects.filter { it.credits != null && it.gradePoint != null }
        if (rated.size < 2 || rated.size != subjects.size) return null // every subject must be readable
        var weighted = BigDecimal.ZERO
        var credits = BigDecimal.ZERO
        for (s in rated) {
            val c = s.credits!!.toBigDecimalOrNull() ?: return null
            val g = s.gradePoint!!.toBigDecimalOrNull() ?: return null
            weighted += c * g
            credits += c
        }
        if (credits.signum() == 0) return null
        return weighted.divide(credits, 2, RoundingMode.HALF_UP).toPlainString()
    }
}
