package com.suhrud.docsy.extractor

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

data class LayoutBlock(
    val page: Int = 1,
    val order: Int,
    val type: String, // "TEXT", "HEADER", "TABLE", "KEY_VALUE"
    val text: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val confidence: Float = 1.0f
)

data class LayoutTableCell(
    val page: Int = 1,
    val tableId: Int = 0,
    val row: Int,
    val col: Int,
    val text: String,
    val confidence: Float = 1.0f,
    /** Vertical extent on the page. Lets a wrapped cell be matched to the table row it belongs to. */
    val top: Int = 0,
    val bottom: Int = 0
)

data class LayoutKeyValue(
    val key: String,
    val value: String,
    val page: Int = 1,
    val lineNo: Int = 0,
    val confidence: Float = 1.0f
)

data class LayoutLine(
    val page: Int = 1,
    val lineNo: Int,
    val yTop: Float,
    val xLeft: Float,
    val text: String
)

data class LayoutPageAnalysisResult(
    val pageNumber: Int = 1,
    val fullText: String,
    val blocks: List<LayoutBlock>,
    val tables: List<LayoutTableCell>,
    val keyValues: List<LayoutKeyValue>,
    val lines: List<LayoutLine>
)

object LayoutModelAnalyzer {

    fun analyzeMlKitText(mlKitText: Text, pageNumber: Int = 1): LayoutPageAnalysisResult {
        val rawBlocks = mutableListOf<RawTextUnit>()

        for (textBlock in mlKitText.textBlocks) {
            for (line in textBlock.lines) {
                val box = line.boundingBox ?: Rect(0, 0, 100, 20)
                rawBlocks.add(
                    RawTextUnit(
                        text = line.text,
                        rect = box,
                        confidence = 1.0f
                    )
                )
            }
        }

        return analyzeUnits(rawBlocks, pageNumber)
    }

    fun analyzeTextLines(linesWithBoxes: List<Pair<String, Rect>>, pageNumber: Int = 1): LayoutPageAnalysisResult {
        val rawUnits = linesWithBoxes.map { (text, box) ->
            RawTextUnit(text = text, rect = box, confidence = 1.0f)
        }
        return analyzeUnits(rawUnits, pageNumber)
    }

    fun analyzeUnits(units: List<RawTextUnit>, pageNumber: Int = 1): LayoutPageAnalysisResult {
        if (units.isEmpty()) {
            return LayoutPageAnalysisResult(
                pageNumber = pageNumber,
                fullText = "",
                blocks = emptyList(),
                tables = emptyList(),
                keyValues = emptyList(),
                lines = emptyList()
            )
        }

        // 1. Rows first (units that share a baseline), then left-to-right inside each row.
        //    This replaces a comparator sort that could violate Java's sort contract and throw.
        val rows = clusterRows(units)
        val sortedUnits = rows.flatten()

        // 2. Label -> value pairs: "Label: value", a label unit with its value to the right, or one below it.
        val keyValues = detectKeyValues(rows, pageNumber)

        // 3. Tables: rows that share aligned columns become cells with a consistent (row, col).
        val tables = detectTables(rows, pageNumber)

        // 4. Construct Blocks
        val blocks = mutableListOf<LayoutBlock>()
        for ((idx, unit) in sortedUnits.withIndex()) {
            val type = when {
                keyValues.any { it.key in unit.text } -> "KEY_VALUE"
                tables.any { it.text == unit.text } -> "TABLE"
                unit.text == unit.text.uppercase() && unit.text.length in 3..40 -> "HEADER"
                else -> "TEXT"
            }

            blocks.add(
                LayoutBlock(
                    page = pageNumber,
                    order = idx + 1,
                    type = type,
                    text = unit.text,
                    x = unit.rect.left.toFloat(),
                    y = unit.rect.top.toFloat(),
                    w = unit.rect.width().toFloat(),
                    h = unit.rect.height().toFloat(),
                    confidence = unit.confidence
                )
            )
        }

        // 5. Build ordered lines
        val lines = sortedUnits.mapIndexed { idx, unit ->
            LayoutLine(
                page = pageNumber,
                lineNo = idx + 1,
                yTop = unit.rect.top.toFloat(),
                xLeft = unit.rect.left.toFloat(),
                text = unit.text
            )
        }

        val fullTextBuilder = StringBuilder()
        for (l in lines) {
            fullTextBuilder.append(l.text).append("\n")
        }

        return LayoutPageAnalysisResult(
            pageNumber = pageNumber,
            fullText = fullTextBuilder.toString().trim(),
            blocks = blocks,
            tables = tables,
            keyValues = keyValues,
            lines = lines
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Rows
    // ---------------------------------------------------------------------------------------------

    private fun cy(u: RawTextUnit): Double = (u.rect.top + u.rect.bottom) / 2.0
    private fun uh(u: RawTextUnit): Double = (u.rect.bottom - u.rect.top).coerceAtLeast(1).toDouble()

    /** Groups units that sit on the same line, top to bottom, each row sorted left to right. */
    internal fun clusterRows(units: List<RawTextUnit>): List<List<RawTextUnit>> {
        if (units.isEmpty()) return emptyList()
        val sorted = units.sortedBy { cy(it) }
        val rows = ArrayList<MutableList<RawTextUnit>>()
        var rowCy = 0.0
        var rowH = 0.0
        for (u in sorted) {
            val h = uh(u)
            val c = cy(u)
            if (rows.isNotEmpty() && Math.abs(c - rowCy) <= 0.6 * Math.min(h, rowH).coerceAtLeast(6.0)) {
                val row = rows.last()
                row.add(u)
                rowCy = row.map { cy(it) }.average()
                rowH = row.map { uh(it) }.average()
            } else {
                rows.add(mutableListOf(u))
                rowCy = c
                rowH = h
            }
        }
        return rows.map { r -> r.sortedBy { it.rect.left } }
    }

    // ---------------------------------------------------------------------------------------------
    // Key-value pairs
    // ---------------------------------------------------------------------------------------------

    private val KEY_VALUE_REGEX = Regex("""^([A-Za-z0-9\s/._-]{2,30}):\s*(.+)$""")
    private val LABEL_ONLY_REGEX = Regex("""^([A-Za-z][A-Za-z0-9\s/._()'-]{1,40}?)\s*:$""")

    /** Labels that appear on result documents even without a colon ("SGPA   8.52", "Roll No" above a number). */
    private val KNOWN_LABELS = listOf(
        "roll number", "roll no", "roll", "seat number", "seat no", "enrollment no", "enrolment no",
        "enrollment number", "registration no", "registration number", "reg no", "hall ticket no",
        "student name", "name of student", "name of candidate", "candidate name", "name of the student", "name",
        "sgpa", "cgpa", "gpa", "result", "final result", "result status", "percentage", "percent",
        "total credits", "total credit",
        "overall percentage", "aggregate", "total marks", "grand total", "division", "semester", "sem",
        "examination", "exam", "programme", "program", "branch", "date of birth", "dob"
    )

    private fun normalizeLabel(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Returns the label if the whole text is a known label ("SGPA", "Roll No."), else null. */
    private fun exactKnownLabel(text: String): String? {
        val n = normalizeLabel(text)
        return if (n.isNotEmpty() && n in KNOWN_LABELS) n else null
    }

    /** Splits "SGPA 8.52" or "Roll No 123456" into label and value, else null. */
    private fun inlineKnownLabel(text: String): Pair<String, String>? {
        val t = text.trim()
        val lower = t.lowercase()
        for (label in KNOWN_LABELS.sortedByDescending { it.length }) {
            if (lower.startsWith(label) && t.length > label.length) {
                val rest = t.substring(label.length)
                if (rest.isNotEmpty() && !rest[0].isLetterOrDigit()) {
                    val value = rest.trimStart(' ', ':', '-', '.', '=', '\t').trim()
                    val wordValueOk = (label == "result" || label == "final result") &&
                        value.length <= 20 && value.all { it.isLetter() || it == ' ' }
                    if (value.isNotEmpty() && (value.any { it.isDigit() } || wordValueOk)) {
                        return t.substring(0, label.length).trim() to value
                    }
                }
            }
        }
        return null
    }

    private fun looksLikeLabel(text: String): Boolean =
        exactKnownLabel(text) != null || LABEL_ONLY_REGEX.matches(text.trim())

    private fun horizontalOverlap(a: RawTextUnit, b: RawTextUnit): Int =
        Math.min(a.rect.right, b.rect.right) - Math.max(a.rect.left, b.rect.left)

    /** The value for a label: the next unit on the same row, otherwise the unit right below it. */
    private fun findValue(rows: List<List<RawTextUnit>>, ri: Int, ci: Int): RawTextUnit? {
        val row = rows[ri]
        val unit = row[ci]
        row.getOrNull(ci + 1)?.let { right ->
            if (right.text.isNotBlank() && !looksLikeLabel(right.text)) return right
        }
        val below = rows.getOrNull(ri + 1) ?: return null
        val rowH = uh(unit)
        var best: RawTextUnit? = null
        var bestOverlap = 0
        for (cand in below) {
            if (cand.rect.top - unit.rect.bottom > rowH * 3) continue
            val ov = horizontalOverlap(unit, cand)
            val minW = Math.min(unit.rect.width(), cand.rect.width()).coerceAtLeast(1)
            if (ov >= minW * 0.3 && ov > bestOverlap && !looksLikeLabel(cand.text)) {
                best = cand
                bestOverlap = ov
            }
        }
        return best
    }

    private val LABEL_START_REGEX = Regex("""(?<![A-Za-z0-9])([A-Za-z][A-Za-z0-9 /._-]{1,30}?):\s*""")

    /** "Registration Number: 2301 Year: 1st Semester: 2nd" -> three pairs. Returns empty unless there are 2+. */
    private fun splitMultiPairs(text: String): List<Pair<String, String>> {
        val matches = LABEL_START_REGEX.findAll(text).toList()
        if (matches.size < 2) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for ((i, m) in matches.withIndex()) {
            val valueEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val value = text.substring(m.range.last + 1, valueEnd).trim()
            if (value.isNotEmpty()) out.add(m.groupValues[1].trim() to value)
        }
        return if (out.size >= 2) out else emptyList()
    }

    private fun detectKeyValues(rows: List<List<RawTextUnit>>, pageNumber: Int): List<LayoutKeyValue> {
        val out = ArrayList<LayoutKeyValue>()
        val seen = HashSet<String>()
        var idx = 0

        fun add(key: String, value: String, lineNo: Int, conf: Float) {
            val k = key.trim().trimEnd(':').trim()
            val v = value.trim()
            if (k.isBlank() || v.isBlank()) return
            if (seen.add(k.lowercase() + "\u0000" + v)) {
                out.add(LayoutKeyValue(key = k, value = v, page = pageNumber, lineNo = lineNo, confidence = conf))
            }
        }

        for ((ri, row) in rows.withIndex()) {
            for ((ci, unit) in row.withIndex()) {
                idx++
                val t = unit.text.trim()
                if (t.isEmpty()) continue

                val multi = splitMultiPairs(t)
                if (multi.isNotEmpty()) {
                    multi.forEach { add(it.first, it.second, idx, unit.confidence) }
                    continue
                }

                val same = KEY_VALUE_REGEX.find(t)
                if (same != null) {
                    add(same.groupValues[1], same.groupValues[2], idx, unit.confidence)
                    continue
                }

                val labelOnly = LABEL_ONLY_REGEX.find(t)
                if (labelOnly != null) {
                    findValue(rows, ri, ci)?.let { add(labelOnly.groupValues[1], it.text, idx, Math.min(unit.confidence, it.confidence)) }
                    continue
                }

                val inline = inlineKnownLabel(t)
                if (inline != null) {
                    add(inline.first, inline.second, idx, unit.confidence)
                    continue
                }

                if (exactKnownLabel(t) != null) {
                    findValue(rows, ri, ci)?.let { add(t, it.text, idx, Math.min(unit.confidence, it.confidence)) }
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Tables
    // ---------------------------------------------------------------------------------------------

    /**
     * Finds tables by geometry, not by counting items per row:
     *  1. a region is a run of tabular rows; single-line rows between them are kept (wrapped cells)
     *  2. a column is an x-range that most rows of the region cover. Gaps that stay empty in almost every row
     *     separate columns, so one odd row (a "Total ... SGPA" footer) cannot glue two columns together
     *  3. every piece of text goes to the column it overlaps, so (row, col) means the same thing on every row
     */
    private fun detectTables(rows: List<List<RawTextUnit>>, pageNumber: Int): List<LayoutTableCell> {
        val allUnits = rows.flatten()
        if (allUnits.size < 4) return emptyList()

        val medianH = allUnits.map { uh(it) }.sorted().let { it[it.size / 2] }.coerceAtLeast(8.0)

        fun isTabular(row: List<RawTextUnit>): Boolean {
            if (row.size < 2) return false
            for (i in 0 until row.size - 1) {
                val gap = row[i + 1].rect.left - row[i].rect.right
                if (gap >= medianH * 0.6) return true
            }
            return false
        }

        val regions = ArrayList<List<List<RawTextUnit>>>()
        var current: MutableList<List<RawTextUnit>>? = null
        val pending = ArrayList<List<RawTextUnit>>()
        var prevBottom = Int.MIN_VALUE
        for (row in rows) {
            // A big vertical gap ends the table: two unrelated rows far apart are not one table.
            val rowTop = row.minOf { it.rect.top }
            if (prevBottom != Int.MIN_VALUE && rowTop - prevBottom > medianH * 1.5) {
                current?.let { regions.add(it) }
                current = null
                pending.clear()
            }
            prevBottom = row.maxOf { it.rect.bottom }

            val open = current
            if (isTabular(row)) {
                val region = open ?: ArrayList<List<RawTextUnit>>().also { current = it }
                region.addAll(pending)
                pending.clear()
                region.add(row)
            } else if (open != null && row.size == 1 && pending.size < 3) {
                pending.add(row) // may be one line of a wrapped cell
            } else {
                current?.let { regions.add(it) }
                current = null
                pending.clear()
            }
        }
        current?.let { regions.add(it) }

        val cells = ArrayList<LayoutTableCell>()
        var tableId = 0
        for (region in regions) {
            if (region.count { it.size >= 2 } < 2) continue

            val units = region.flatten()
            val minLeft = units.minOf { it.rect.left }
            val maxRight = units.maxOf { it.rect.right }
            val width = maxRight - minLeft + 1
            if (width <= 1 || width > 20000) continue

            // How many rows cover each x position (a row counts once, however many units overlap).
            val coverage = IntArray(width)
            for (row in region) {
                val covered = BooleanArray(width)
                for (u in row) {
                    for (x in (u.rect.left - minLeft).coerceAtLeast(0) until (u.rect.right - minLeft).coerceAtMost(width)) covered[x] = true
                }
                for (x in 0 until width) if (covered[x]) coverage[x]++
            }
            val tolerance = Math.max(1, (region.size * 0.12).toInt())

            val columns = ArrayList<IntArray>() // [from, to] in page coordinates
            var runStart = -1
            for (x in 0 until width) {
                if (coverage[x] > tolerance) {
                    if (runStart < 0) runStart = x
                } else if (runStart >= 0) {
                    columns.add(intArrayOf(runStart + minLeft, x - 1 + minLeft))
                    runStart = -1
                }
            }
            if (runStart >= 0) columns.add(intArrayOf(runStart + minLeft, width - 1 + minLeft))
            if (columns.size < 2) continue
            tableId++

            fun columnOf(u: RawTextUnit): Int {
                var best = -1
                var bestOverlap = 0
                for ((i, c) in columns.withIndex()) {
                    val ov = Math.min(u.rect.right, c[1]) - Math.max(u.rect.left, c[0])
                    if (ov > bestOverlap) { best = i; bestOverlap = ov }
                }
                if (best >= 0) return best
                val centre = (u.rect.left + u.rect.right) / 2
                return columns.indices.minByOrNull { Math.abs(centre - (columns[it][0] + columns[it][1]) / 2) } ?: 0
            }

            for ((rowIndex, row) in region.withIndex()) {
                val byCol = java.util.TreeMap<Int, MutableList<RawTextUnit>>()
                for (u in row) byCol.getOrPut(columnOf(u)) { ArrayList() }.add(u)
                for ((col, cellUnits) in byCol) {
                    val text = cellUnits.sortedBy { it.rect.left }.joinToString(" ") { it.text.trim() }.trim()
                    if (text.isEmpty()) continue
                    cells.add(
                        LayoutTableCell(
                            page = pageNumber,
                            tableId = tableId,
                            row = rowIndex,
                            col = col,
                            text = text,
                            confidence = cellUnits.minOf { it.confidence },
                            top = cellUnits.minOf { it.rect.top },
                            bottom = cellUnits.maxOf { it.rect.bottom }
                        )
                    )
                }
            }
        }
        return cells
    }
}

data class RawTextUnit(
    val text: String,
    val rect: Rect,
    val confidence: Float = 1.0f
)
