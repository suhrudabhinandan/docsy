package com.suhrud.docsy.retrieval

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.dao.FactDao
import com.suhrud.docsy.reasoning.DocsyCalculator
import com.suhrud.docsy.reasoning.SubjectMark
import com.suhrud.docsy.understanding.MarksheetParser
import com.suhrud.docsy.understanding.OwnerExtractor
import java.math.BigDecimal
import java.math.RoundingMode

data class RetrievalSlots(
    val docType: String? = null,
    val targetClass: String? = null,
    val board: String? = null,
    val owner: String? = null,
    val requestedField: String? = null
)

data class RetrievalResponse(
    val answerValue: String,
    val workingLine: String,
    val sourceFileName: String,
    val confidence: Float = 0.9f,
    val executionTimeMs: Long = 0L,
    val needsOwnerDisambiguation: Boolean = false,
    val ownerCandidates: List<String> = emptyList()
)

object GenericRetrievalRouter {

    fun extractSlots(query: String): RetrievalSlots {
        val q = QueryNormalizer.normalize(query).lowercase()

        val docType = when {
            q.contains("marksheet") || q.contains("result") || q.contains("report card") || q.contains("transcript") -> "MARKSHEET"
            q.contains("cv") || q.contains("resume") || q.contains("curriculum vitae") || q.contains("biodata") -> "CV"
            q.contains("aadhaar") || q.contains("aadhar") -> "AADHAAR"
            q.contains("pan") -> "PAN"
            q.contains("bill") || q.contains("invoice") -> "BILL"
            else -> if (q.contains("12th") || q.contains("10th")) "MARKSHEET" else null
        }

        val targetClass = when {
            Regex("""\b(12th|xii|twelfth|class\s*12|class\s*xii)\b""").containsMatchIn(q) -> "12th"
            Regex("""\b(10th|x|tenth|class\s*10|class\s*x)\b""").containsMatchIn(q) -> "10th"
            else -> null
        }

        val requestedField = when {
            q.contains("percentage") || q.contains("percent") || q.contains("%") -> "percentage"
            q.contains("roll") || q.contains("roll number") || q.contains("roll no") -> "roll_number"
            q.contains("board") -> "board"
            q.contains("class") -> "class"
            q.contains("name") || q.contains("student name") -> "student_name"
            else -> null
        }

        return RetrievalSlots(
            docType = docType,
            targetClass = targetClass,
            requestedField = requestedField
        )
    }

    suspend fun processQuery(
        userQuery: String,
        factDao: FactDao,
        documentDao: DocumentDao,
        database: DocsyDatabase? = null
    ): RetrievalResponse {
        val startTime = System.currentTimeMillis()
        val slots = extractSlots(userQuery)

        val allDocs = try {
            documentDao.getAllDocumentsSnapshot()
        } catch (e: Exception) {
            android.util.Log.e("GenericRetrievalRouter", "Error fetching documents snapshot", e)
            emptyList()
        }
        if (allDocs.isEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            return RetrievalResponse(
                answerValue = "No documents found.",
                workingLine = "Reading is in progress or no files have been scanned.",
                sourceFileName = "Search",
                executionTimeMs = duration
            )
        }

        // 1. HARD VALIDATION: filter candidate documents matching required slots
        val candidateDocs = allDocs.filter { doc ->
            // Rejection rules
            if (slots.docType == "MARKSHEET" && doc.docType == "CV") return@filter false
            if (slots.docType != null && doc.docType != null && doc.docType != slots.docType && doc.docType != "GENERAL_DOCUMENT") {
                if (slots.docType == "MARKSHEET" && !doc.extractedText.orEmpty().lowercase().contains("marksheet") && !doc.extractedText.orEmpty().lowercase().contains("statement of marks")) {
                    return@filter false
                }
            }

            // Hard class validation
            if (slots.targetClass != null) {
                val text = doc.extractedText.orEmpty().lowercase()
                val isTarget12 = slots.targetClass == "12th"
                val has12 = text.contains("12th") || text.contains("class xii") || text.contains("senior secondary")
                val has10 = text.contains("10th") || text.contains("class x") || text.contains("secondary school")
                if (isTarget12 && !has12 && has10) return@filter false
                if (!isTarget12 && has10 && !text.contains(slots.targetClass!!.lowercase())) return@filter false
            }
            true
        }

        if (candidateDocs.isEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            return RetrievalResponse(
                answerValue = "Information not found.",
                workingLine = "No document satisfied the requested criteria '${userQuery}'",
                sourceFileName = "Search",
                executionTimeMs = duration
            )
        }

        // Check for multiple owners disambiguation
        val extractedOwners = candidateDocs.mapNotNull { it.ownerName }.distinct()
        if (extractedOwners.size > 1 && slots.owner == null && !userQuery.lowercase().contains("all")) {
            val duration = System.currentTimeMillis() - startTime
            return RetrievalResponse(
                answerValue = "Multiple document owners found.",
                workingLine = "Please select whose document you are asking about:",
                sourceFileName = "Disambiguation",
                needsOwnerDisambiguation = true,
                ownerCandidates = extractedOwners,
                executionTimeMs = duration
            )
        }

        val targetDoc = candidateDocs.first()

        // 2. ANSWER FROM FACTS
        if (slots.requestedField == "roll_number") {
            val rollFact = factDao.getFactsByDocumentAndKey(targetDoc.id, "rollNo").firstOrNull()
                ?: factDao.getFactsByDocumentAndKey(targetDoc.id, "roll_number").firstOrNull()
            
            val rollVal = rollFact?.fieldValue ?: MarksheetParser.parse(targetDoc.extractedText.orEmpty()).rollNumber
            val duration = System.currentTimeMillis() - startTime

            if (rollVal != null) {
                return RetrievalResponse(
                    answerValue = rollVal,
                    workingLine = "Roll Number from ${targetDoc.displayName}",
                    sourceFileName = targetDoc.displayName,
                    executionTimeMs = duration
                )
            }
        }

        if (slots.requestedField == "percentage") {
            val parsed = MarksheetParser.parse(targetDoc.extractedText.orEmpty())
            val duration = System.currentTimeMillis() - startTime

            if (parsed.subjects.isNotEmpty()) {
                val calc = DocsyCalculator.calculateMarksheetPercentage(parsed.subjects, bestFiveOnly = false)
                val totalObt = calc.totalObtained.setScale(2, RoundingMode.HALF_UP).toPlainString()
                val totalMax = calc.totalMaximum.setScale(2, RoundingMode.HALF_UP).toPlainString()
                val pctStr = calc.percentage.toPlainString()

                val workingText = if (parsed.subjects.size > 5) {
                    val b5Calc = DocsyCalculator.calculateMarksheetPercentage(parsed.subjects, bestFiveOnly = true)
                    val b5Obt = b5Calc.totalObtained.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    val b5Max = b5Calc.totalMaximum.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    "Best Five: $b5Obt out of $b5Max = ${b5Calc.percentage.toPlainString()}%\nAll Subjects (${parsed.subjects.size}): $totalObt out of $totalMax = $pctStr%"
                } else {
                    "$totalObt out of $totalMax = $pctStr%"
                }

                return RetrievalResponse(
                    answerValue = "$pctStr%",
                    workingLine = workingText,
                    sourceFileName = targetDoc.displayName,
                    executionTimeMs = duration
                )
            } else if (parsed.printedPercentage != null) {
                return RetrievalResponse(
                    answerValue = "${parsed.printedPercentage.setScale(2, RoundingMode.HALF_UP)}%",
                    workingLine = "Printed percentage in ${targetDoc.displayName}",
                    sourceFileName = targetDoc.displayName,
                    executionTimeMs = duration
                )
            }
        }

        // 3. FTS5 Search fallback for other fields / queries
        if (database != null) {
            val ftsEngine = Fts5SearchEngine(database)
            val hits = ftsEngine.searchFreeText(userQuery)
            val validHit = hits.firstOrNull { candidateDocs.any { d -> d.id == it.documentId } }
            if (validHit != null) {
                val doc = candidateDocs.first { it.id == validHit.documentId }
                val duration = System.currentTimeMillis() - startTime
                return RetrievalResponse(
                    answerValue = validHit.text,
                    workingLine = "Matched in ${validHit.chunkType} (Page ${validHit.page})",
                    sourceFileName = doc.displayName,
                    executionTimeMs = duration
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        return RetrievalResponse(
            answerValue = "Information not found.",
            workingLine = "No matching information was found in the specified document.",
            sourceFileName = "Search",
            executionTimeMs = duration
        )
    }
}
