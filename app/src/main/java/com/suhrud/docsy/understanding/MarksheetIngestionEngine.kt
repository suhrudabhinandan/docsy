package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.entity.MarksheetEntity
import com.suhrud.docsy.data.db.entity.MarksheetSubjectEntity

object MarksheetIngestionEngine {

    fun buildMarksheetEntities(
        docId: Long,
        parsed: ParsedMarksheet,
        layoutSheet: LayoutMarksheet,
        cleanText: String,
        readBy: String
    ): Pair<MarksheetEntity, List<MarksheetSubjectEntity>> {
        val level = when {
            parsed.gradeClass.equals("10th", ignoreCase = true) -> "10TH"
            parsed.gradeClass.equals("12th", ignoreCase = true) -> "12TH"
            parsed.gradeClass?.lowercase()?.contains("sem") == true ||
            parsed.gradeClass?.lowercase()?.contains("b.tech") == true -> "SEMESTER"
            else -> "OTHER"
        }

        val rollNo = parsed.rollNumber ?: layoutSheet.field("rollNo")
        val studentName = parsed.studentName ?: layoutSheet.field("studentName")
        val result = parsed.resultStatus ?: layoutSheet.field("result")
        val printedPct = parsed.printedPercentage?.toDouble()
            ?: layoutSheet.field("printedPercentage")?.replace("%", "")?.toDoubleOrNull()
        val sgpa = parsed.sgpa?.toDoubleOrNull() ?: layoutSheet.field("sgpa")?.toDoubleOrNull()
        val cgpa = parsed.cgpa?.toDoubleOrNull() ?: layoutSheet.field("cgpa")?.toDoubleOrNull()

        val validationFlags = mutableListOf<String>()

        if (rollNo != null && (rollNo.length < 4 || rollNo.length > 24)) {
            validationFlags.add("ROLL_NO_SHAPE")
        }

        val subjectEntities = mutableListOf<MarksheetSubjectEntity>()

        // Combine subjects from layoutSheet and parsed
        if (layoutSheet.subjects.isNotEmpty()) {
            for ((idx, sub) in layoutSheet.subjects.withIndex()) {
                val marksVal = sub.marks?.toDoubleOrNull()
                val maxVal = sub.maxMarks?.toDoubleOrNull() ?: 100.0

                if (marksVal != null && marksVal > maxVal) {
                    if ("OVER_MAX" !in validationFlags) validationFlags.add("OVER_MAX")
                }

                subjectEntities.add(
                    MarksheetSubjectEntity(
                        marksheetId = 0,
                        rowNo = idx + 1,
                        subjectCode = sub.code,
                        subjectName = sub.name,
                        subjectKind = "MAIN",
                        totalMarks = marksVal,
                        maxMarks = maxVal,
                        grade = sub.grade,
                        gradePoint = sub.gradePoint?.toDoubleOrNull(),
                        credits = sub.credits?.toDoubleOrNull(),
                        source = "TABLE",
                        confidence = sub.confidence
                    )
                )
            }
        } else if (parsed.subjects.isNotEmpty()) {
            for ((idx, sub) in parsed.subjects.withIndex()) {
                val marksVal = sub.marksObtained.toDouble()
                val maxVal = sub.maxMarks.toDouble()

                if (marksVal > maxVal) {
                    if ("OVER_MAX" !in validationFlags) validationFlags.add("OVER_MAX")
                }

                subjectEntities.add(
                    MarksheetSubjectEntity(
                        marksheetId = 0,
                        rowNo = idx + 1,
                        subjectName = sub.subjectName,
                        subjectKind = "MAIN",
                        totalMarks = marksVal,
                        maxMarks = maxVal,
                        source = "TEXT",
                        confidence = 0.85f
                    )
                )
            }
        }

        if ((level == "10TH" || level == "12TH") && subjectEntities.size in 1..4) {
            validationFlags.add("SUBJECT_COUNT_LOW")
        }

        val totalObtained = if (subjectEntities.isNotEmpty() && subjectEntities.all { it.totalMarks != null }) {
            subjectEntities.sumOf { it.totalMarks ?: 0.0 }
        } else null

        val totalMax = if (subjectEntities.isNotEmpty() && subjectEntities.all { it.maxMarks != null }) {
            subjectEntities.sumOf { it.maxMarks ?: 100.0 }
        } else null

        val percentageAll = if (totalObtained != null && totalMax != null && totalMax > 0) {
            (totalObtained / totalMax) * 100.0
        } else null

        val percentageBest5 = if (subjectEntities.size >= 5) {
            val top5 = subjectEntities.mapNotNull { sub ->
                val m = sub.totalMarks ?: return@mapNotNull null
                val mx = sub.maxMarks ?: 100.0
                Pair(m, mx)
            }.sortedByDescending { (m, mx) -> m / mx }.take(5)

            if (top5.size == 5) {
                val obt5 = top5.sumOf { it.first }
                val max5 = top5.sumOf { it.second }
                (obt5 / max5) * 100.0
            } else null
        } else percentageAll

        val needsReview = validationFlags.isNotEmpty()

        val marksheet = MarksheetEntity(
            documentId = docId,
            level = level,
            board = parsed.board,
            studentName = studentName,
            rollNo = rollNo,
            result = result,
            totalObtained = totalObtained,
            totalMax = totalMax,
            percentagePrinted = printedPct,
            percentageAll = percentageAll,
            percentageBest5 = percentageBest5,
            sgpa = sgpa,
            cgpa = cgpa,
            readBy = readBy,
            confidence = if (needsReview) 0.7f else 0.95f,
            needsReview = needsReview,
            validationFlags = if (validationFlags.isNotEmpty()) validationFlags.joinToString(",") else null,
            extractedAt = System.currentTimeMillis()
        )

        return Pair(marksheet, subjectEntities)
    }
}
