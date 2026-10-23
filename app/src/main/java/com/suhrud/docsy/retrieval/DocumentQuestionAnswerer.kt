package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Answers a question about ONE already-selected document, in a fixed order:
 *   1. load the whole card (fields + subjects)
 *   2. decide what is being asked (list subjects / result / calculate / highest-lowest / one field / summary)
 *   3. validate before answering (every subject has marks and a maximum, totals agree, nothing low-confidence)
 *   4. calculate ONLY in code (BigDecimal, half-up, 2 decimals) and show one line of working
 *   5. never guess: if something is missing, say exactly what is missing
 *
 * File names are never used. Values come only from the stored fields/subjects of the document.
 */
object DocumentQuestionAnswerer {

    enum class QuestionType { LIST_SUBJECTS, RESULT, CALCULATE, EXTREME, SUMMARY }

    // Generic English words that are never the name of a field the user is asking for.
    private val IGNORED_TERMS = setOf(
        "sem", "semester", "my", "the", "what", "whats", "is", "are", "tell", "me", "show", "give", "get",
        "of", "and", "for", "in", "please", "result", "results", "score", "marks", "mark", "was", "were",
        "your", "were", "had", "have", "did", "how", "much", "many", "can", "you", "calculate",
        "number", "overall", "over", "all"
    )

    fun classify(query: String): QuestionType {
        val q = query.lowercase()
        return when {
            Regex("""\b(highest|lowest|best|worst|top|weakest|strongest)\b""").containsMatchIn(q) -> QuestionType.EXTREME
            Regex("""percent|%|calculate|\btotal\b|\bsum\b|average|\bavg\b""").containsMatchIn(q) -> QuestionType.CALCULATE
            Regex("""\bsubjects?\b|\bpapers?\b|\bcourses?\b""").containsMatchIn(q) -> QuestionType.LIST_SUBJECTS
            Regex("""\bresults?\b|\bpass(ed)?\b|\bfail(ed)?\b|\bstatus\b""").containsMatchIn(q) -> QuestionType.RESULT
            else -> QuestionType.SUMMARY
        }
    }

    private fun num(s: String?): BigDecimal? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.replace(",", "")
        return Regex("""-?\d+(?:\.\d+)?""").find(cleaned)?.value?.toBigDecimalOrNull()
    }

    private fun fmt(v: BigDecimal): String = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun subjectLine(s: SubjectEntity): String {
        val marks = s.marks?.takeIf { it.isNotBlank() }?.let { m ->
            " — $m" + (s.maxMarks?.takeIf { it.isNotBlank() }?.let { mx -> "/$mx" } ?: "")
        } ?: ""
        val grade = s.grade?.takeIf { it.isNotBlank() }?.let { g -> " ($g)" } ?: ""
        return "• ${s.name}$marks$grade"
    }

    private fun fieldLine(f: FieldEntity): String = "${f.label}: ${f.value}"

    private fun header(fields: List<FieldEntity>): String {
        val owner = fields.firstOrNull { it.key == "studentName" || it.key == "student_name" || it.key == "owner" || it.key == "name" }?.value
        val exam = fields.firstOrNull { it.key == "examination" }?.value
        return listOfNotNull(exam, owner).joinToString(" • ")
    }

    private fun withHeader(fields: List<FieldEntity>, body: String): String {
        val h = header(fields)
        return if (h.isNotBlank()) "$h\n$body" else body
    }

    /** Fields whose label/key matches a meaningful word from the query (e.g. "sgpa", "roll", "board", "use cases"). */
    private fun matchFields(fields: List<FieldEntity>, query: String): List<FieldEntity> {
        val terms = query.lowercase().split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 && it !in IGNORED_TERMS && !it.matches(Regex("""\d+(st|nd|rd|th)?""")) }
        if (terms.isEmpty()) return emptyList()
        val expanded = terms.flatMap { t ->
            when (t) {
                "roll" -> listOf("roll", "seat", "enrol", "regist")
                "usecase", "usecases", "usage", "used" -> listOf("use_cases", "use cases", "usecases")
                else -> listOf(t)
            }
        }
        return fields.filter { f ->
            val label = f.label.lowercase()
            val key = f.key.lowercase()
            f.key != "text_preview" && f.key != "read_by" && expanded.any { t ->
                label == t || key == t || label.contains(t) || key.contains(t) ||
                    (t.endsWith("gpa") && (label.endsWith("gpa") || key.endsWith("gpa")))
            }
        }
    }

    suspend fun answer(db: DocsyDatabase, documentId: Long, query: String): String {
        val fields = try { db.fieldDao().getFieldsForDocument(documentId) } catch (_: Exception) { emptyList() }
        val subjects = try { db.subjectDao().getSubjectsForDocument(documentId) } catch (_: Exception) { emptyList() }

        val body = when (classify(query)) {
            QuestionType.CALCULATE -> calculate(fields, subjects)
            QuestionType.EXTREME -> extreme(fields, subjects, query)
            QuestionType.LIST_SUBJECTS -> listSubjects(fields, subjects)
            QuestionType.RESULT -> result(fields, subjects)
            QuestionType.SUMMARY -> {
                val matched = matchFields(fields, query)
                if (matched.isNotEmpty()) withHeader(fields, matched.joinToString("\n") { fieldLine(it) })
                else summary(fields, subjects)
            }
        }
        val note = fields.firstOrNull { it.key == "read_by" }?.value
        return if (note != null) "$body\n\n($note)" else body
    }

    // ---- question handlers -------------------------------------------------------------------

    private fun listSubjects(fields: List<FieldEntity>, subjects: List<SubjectEntity>): String {
        if (subjects.isEmpty()) {
            return withHeader(
                fields,
                "I can see this document, but no subject table was extracted from it, so I can't list the subjects. " +
                    "You can check the extracted text in the Extraction Inspector."
            )
        }
        return withHeader(fields, "Subjects:\n" + subjects.joinToString("\n") { subjectLine(it) })
    }

    private fun result(fields: List<FieldEntity>, subjects: List<SubjectEntity>): String {
        val outcomeKeys = listOf("result", "status", "remark", "division", "gpa", "percent", "total", "grade")
        val outcome = fields.filter { f ->
            f.key != "text_preview" && outcomeKeys.any { f.key.contains(it) || f.label.lowercase().contains(it) }
        }
        val parts = mutableListOf<String>()
        if (outcome.isNotEmpty()) parts.add(outcome.joinToString("\n") { fieldLine(it) })
        if (subjects.isNotEmpty()) parts.add("Subjects:\n" + subjects.joinToString("\n") { subjectLine(it) })
        if (parts.isEmpty()) return summary(fields, subjects)
        return withHeader(fields, parts.joinToString("\n\n"))
    }

    private fun summary(fields: List<FieldEntity>, subjects: List<SubjectEntity>): String {
        val shown = fields.filter { it.key != "text_preview" && it.key != "read_by" && it.value.length <= 80 }.take(12)
        val parts = mutableListOf<String>()
        if (shown.isNotEmpty()) parts.add(shown.joinToString("\n") { fieldLine(it) })
        if (subjects.isNotEmpty()) parts.add("Subjects:\n" + subjects.joinToString("\n") { subjectLine(it) })
        if (parts.isEmpty()) return "This document was read, but no structured fields were extracted from it."
        return withHeader(fields, parts.joinToString("\n\n"))
    }

    private data class Row(val name: String, val marks: BigDecimal, val max: BigDecimal, val confidence: Float)

    private fun numericRows(subjects: List<SubjectEntity>): List<Row> = subjects.mapNotNull { s ->
        val m = num(s.marks)
        val mx = num(s.maxMarks)
        if (m != null && mx != null && mx.signum() > 0) Row(s.name, m, mx, s.confidence) else null
    }

    private fun printedGradePoints(fields: List<FieldEntity>): String? {
        val gp = fields.filter { it.key.endsWith("gpa") || it.label.lowercase().endsWith("gpa") }
        return if (gp.isEmpty()) null else gp.joinToString(", ") { "${it.label} ${it.value}" }
    }

    private fun calculate(fields: List<FieldEntity>, subjects: List<SubjectEntity>): String {
        if (subjects.isEmpty()) {
            val gp = printedGradePoints(fields)
            val printedPct = fields.firstOrNull { it.key.contains("percent") }
            return withHeader(
                fields,
                when {
                    printedPct != null -> "No subject table was extracted, so I can't calculate it myself. " +
                        "The printed ${printedPct.label} on the document is ${printedPct.value}."
                    gp != null -> "No subject marks were extracted, so I can't calculate a percentage. " +
                        "The document prints $gp."
                    else -> "No subject marks were extracted from this document, so I can't calculate a percentage."
                }
            )
        }

        val rows = numericRows(subjects)

        // Grade-point documents (SGPA/CGPA with letter grades, no numeric marks): never invent a percentage.
        if (rows.isEmpty()) {
            val gp = printedGradePoints(fields)
            return withHeader(
                fields,
                "This document reports grades, not marks out of a maximum, so I can't calculate a percentage from it." +
                    (if (gp != null) " It prints $gp." else "") +
                    "\n\nSubjects:\n" + subjects.joinToString("\n") { subjectLine(it) }
            )
        }

        // Validation: every subject must have usable marks and a maximum, and none may be low-confidence.
        val missing = subjects.size - rows.size
        val lowConfidence = rows.filter { it.confidence < 0.7f }
        if (missing > 0 || lowConfidence.isNotEmpty()) {
            val why = buildString {
                if (missing > 0) append("$missing subject(s) have no readable marks or maximum. ")
                if (lowConfidence.isNotEmpty()) append("${lowConfidence.size} mark(s) were read with low confidence. ")
            }
            return withHeader(
                fields,
                "I don't want to give you a wrong number. $why" +
                    "Please check these values and confirm them:\n" + subjects.joinToString("\n") { subjectLine(it) }
            )
        }

        val sum = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.marks }
        val max = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.max }
        val pct = sum.multiply(BigDecimal(100)).divide(max, 2, RoundingMode.HALF_UP)

        val lines = mutableListOf<String>()
        lines.add("Percentage: ${pct.toPlainString()}%")
        lines.add("Working: ${fmt(sum)} out of ${fmt(max)} = ${pct.toPlainString()}%")

        if (rows.size > 5) {
            val best5 = rows.sortedByDescending { it.marks.multiply(BigDecimal(1000)).divide(it.max, 4, RoundingMode.HALF_UP) }.take(5)
            val s5 = best5.fold(BigDecimal.ZERO) { acc, r -> acc + r.marks }
            val m5 = best5.fold(BigDecimal.ZERO) { acc, r -> acc + r.max }
            val p5 = s5.multiply(BigDecimal(100)).divide(m5, 2, RoundingMode.HALF_UP)
            lines.add("Best five subjects: ${fmt(s5)} out of ${fmt(m5)} = ${p5.toPlainString()}%")
            lines.add("All ${rows.size} subjects: ${pct.toPlainString()}%")
        }

        val printedTotal = fields.firstOrNull { it.key.contains("total") && !it.key.contains("max") }?.let { num(it.value) }
        if (printedTotal != null && printedTotal.compareTo(sum) != 0) {
            lines.add("Note: the document prints a total of ${fmt(printedTotal)}, which differs from the sum of the subjects (${fmt(sum)}). Please check the marks.")
        }
        val printedPct = fields.firstOrNull { it.key.contains("percent") }
        if (printedPct != null) {
            val printed = num(printedPct.value)
            val mismatch = printed != null && printed.subtract(pct).abs() > BigDecimal("0.05")
            lines.add("Printed on the document: ${printedPct.value}" + if (mismatch) " (this differs from the calculated value)" else "")
        }
        val gp = printedGradePoints(fields)
        if (gp != null) lines.add("Also printed: $gp")

        return withHeader(fields, lines.joinToString("\n"))
    }

    private fun extreme(fields: List<FieldEntity>, subjects: List<SubjectEntity>, query: String): String {
        val rows = numericRows(subjects)
        if (rows.isEmpty()) {
            return withHeader(fields, "I can't compare subjects because no numeric marks with a maximum were extracted from this document.")
        }
        fun ratio(r: Row) = r.marks.multiply(BigDecimal(1000)).divide(r.max, 4, RoundingMode.HALF_UP)
        val low = Regex("""\b(lowest|worst|weakest)\b""").containsMatchIn(query.lowercase())
        val pick = if (low) rows.minByOrNull { ratio(it) } else rows.maxByOrNull { ratio(it) }
        return withHeader(
            fields,
            if (pick == null) "I couldn't compare the subjects."
            else "${if (low) "Lowest" else "Highest"}: ${pick.name} — ${fmt(pick.marks)}/${fmt(pick.max)}"
        )
    }
}
