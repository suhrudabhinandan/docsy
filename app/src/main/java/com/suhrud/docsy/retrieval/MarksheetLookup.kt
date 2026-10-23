package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.understanding.MarksheetParser

/**
 * Content-based marksheet lookup for queries such as "my 10th score" or "my 12th percentage".
 *
 * - File names are never used.
 * - The profile name is never used as a filter.
 * - The requested class (10 / 12) must match the document's own class; a 10th query is never answered
 *   with a 12th document and vice versa.
 * - Text is read from the stored lines, not from documents.extractedText (which list queries return as NULL).
 */
object MarksheetLookup {

    /** Returns 10 or 12 when the query names a class, otherwise null. */
    fun requestedClass(query: String): Int? {
        val q = query.lowercase()
        Regex("""(?<![0-9])(10|12)\s*(?:st|nd|rd|th)?(?![0-9])""").find(q)?.let { return it.groupValues[1].toInt() }
        if (Regex("""\+\s*2""").containsMatchIn(q) || Regex("""\bxii\b""").containsMatchIn(q)) return 12
        return null
    }

    private val ORDINAL_WORDS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4,
        "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8
    )

    /** Returns the semester number named in the query ("2nd sem", "semester 2", "second semester"), else null. */
    fun requestedSemester(query: String): Int? {
        val q = query.lowercase()
        Regex("""(?<![0-9])(\d{1,2})\s*(?:st|nd|rd|th)?\s*(?:sem|semester)\b""").find(q)?.let { return it.groupValues[1].toInt() }
        Regex("""\b(?:sem|semester)\s*[-:]?\s*(\d{1,2})\b""").find(q)?.let { return it.groupValues[1].toInt() }
        Regex("""\b(first|second|third|fourth|fifth|sixth|seventh|eighth)\s+(?:sem|semester)\b""").find(q)
            ?.let { return ORDINAL_WORDS[it.groupValues[1]] }
        return null
    }

    private fun semesterNumber(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val v = value.lowercase()
        Regex("""\d{1,2}""").find(v)?.let { return it.value.toInt() }
        return ORDINAL_WORDS.entries.firstOrNull { v.contains(it.key) }?.value
    }

    // Chat context: the document the last answer was about, so a follow-up such as
    // "what were the subjects" or "calculate percentage" does not need the semester/class repeated.
    @Volatile private var activeDocId: Long? = null
    @Volatile private var activeAt: Long = 0L
    private const val CONTEXT_MS = 10 * 60 * 1000L

    fun getActiveDocId(): Long? = activeDocId

    fun setActiveDocId(id: Long?) {
        if (id != null && id > 0) {
            remember(id)
        } else {
            resetContext()
        }
    }

    private fun remember(id: Long) {
        activeDocId = id
        activeAt = System.currentTimeMillis()
    }

    /** Call when a new chat starts so context never leaks between chats. */
    fun resetContext() {
        activeDocId = null
        activeAt = 0L
    }

    private suspend fun answerSemester(db: DocsyDatabase, query: String, semester: Int): String {
        val fieldDao = db.fieldDao()
        val semesterFields = try { fieldDao.getFieldsByKey("semester") } catch (_: Exception) { emptyList() }
        val docIds = semesterFields.filter { semesterNumber(it.value) == semester }.map { it.documentId }.distinct()
        if (docIds.isEmpty()) return "I couldn't find a semester $semester result in the files I've read."

        remember(docIds.first())
        val blocks = docIds.take(3).map { id -> DocumentQuestionAnswerer.answer(db, id, query) }
        return blocks.joinToString("\n\n")
    }

    private fun noSubjectsReply(parsed: com.suhrud.docsy.understanding.ParsedMarksheet, cls: Int): String {
        val ord = if (cls == 10) "10th" else "12th"
        return "I found your $ord marksheet, but I couldn't read the subject-wise marks from the scan, so I can't give a score or percentage without guessing. " +
            "Open Settings → Extraction Inspector to see what was read from it."
    }

    private fun classNumber(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return Regex("""(10|12)""").find(text)?.groupValues?.get(1)?.toInt()
    }

    /**
     * Returns the reply text, or null when the query is not a class-specific marksheet question
     * (the caller then continues with its normal path).
     */
    suspend fun answer(db: DocsyDatabase, query: String): String? {
        requestedSemester(query)?.let { return answerSemester(db, query, it) }
        val wanted = requestedClass(query)
        if (wanted == null) {
            // Follow-up without naming the semester/class: reuse the document from the last answer.
            val id = activeDocId
            if (id != null && System.currentTimeMillis() - activeAt < CONTEXT_MS) {
                val qt = DocumentQuestionAnswerer.classify(query)
                if (qt != DocumentQuestionAnswerer.QuestionType.SUMMARY) {
                    remember(id)
                    return DocumentQuestionAnswerer.answer(db, id, query)
                }
            }
            return null
        }
        val q = query.lowercase()
        val looksLikeMarksQuery = listOf("marksheet", "mark sheet", "mark", "score", "marks", "percent", "result", "grade", "cgpa", "subject", "sgpa", "gpa", "roll", "seat")
            .any { q.contains(it) }
        if (!looksLikeMarksQuery) return null

        val docDao = db.documentDao()
        val fieldDao = db.fieldDao()
        val lineDao = db.lineDao()

        val docs = try { docDao.getAllDocumentsSnapshot() } catch (_: Exception) { emptyList() }
        val classFieldDocIds = try { fieldDao.getFieldsByKey("class").map { it.documentId }.toSet() } catch (_: Exception) { emptySet() }

        val marksheetDocs = docs.filter { d ->
            d.docType?.contains("marksheet", ignoreCase = true) == true || d.id in classFieldDocIds
        }

        data class Match(val docId: Long, val ownerName: String?, val reply: String)
        val matches = mutableListOf<Match>()

        for (d in marksheetDocs) {
            val fields = try { fieldDao.getFieldsForDocument(d.id) } catch (_: Exception) { emptyList() }
            val text = try { lineDao.getLinesForDocument(d.id).joinToString("\n") { it.text } } catch (_: Exception) { "" }
            val parsed = if (text.isNotBlank()) MarksheetParser.parse(text) else null

            val docClass = classNumber(fields.firstOrNull { it.key == "class" }?.value)
                ?: classNumber(parsed?.gradeClass)
                ?: continue
            if (docClass != wanted) continue
            if (parsed == null) continue

            val hasSubjectRows = try { db.subjectDao().getSubjectsForDocument(d.id).isNotEmpty() } catch (_: Exception) { false }
            // A sheet with no subject rows can still answer roll number, SGPA/CGPA, percentage or result questions.
            val hasOutcomeFields = fields.any { it.key in setOf("rollNo", "sgpa", "cgpa", "gpa", "printedPercentage", "result") }
            val reply = if (hasSubjectRows || hasOutcomeFields) DocumentQuestionAnswerer.answer(db, d.id, query) else noSubjectsReply(parsed, wanted)
            val cleanOwner = (parsed.studentName ?: d.ownerName)?.takeIf { it.length in 3..40 && !it.uppercase().contains("CERTIFY") }
            matches.add(Match(d.id, cleanOwner, reply))
        }

        val ordinal = if (wanted == 10) "10th" else "12th"
        matches.firstOrNull()?.let { remember(it.docId) }
        val unique = matches.distinctBy { it.reply }
        matches.clear(); matches.addAll(unique)
        return when {
            matches.isEmpty() ->
                "I couldn't find a $ordinal marksheet in the files I've read."
            matches.size == 1 -> matches[0].reply
            else -> matches.take(3).joinToString("\n\n") { m ->
                val who = m.ownerName?.takeIf { it.isNotBlank() }?.let { "$it:\n" } ?: ""
                who + m.reply
            }
        }
    }
}
