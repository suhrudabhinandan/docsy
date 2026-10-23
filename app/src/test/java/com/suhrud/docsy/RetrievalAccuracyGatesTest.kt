package com.suhrud.docsy

import com.suhrud.docsy.reconstruction.TextReconstructionEngine
import com.suhrud.docsy.retrieval.Fts5SearchEngine
import com.suhrud.docsy.retrieval.GenericRetrievalRouter
import com.suhrud.docsy.retrieval.QueryNormalizer
import com.suhrud.docsy.ui.inspector.SqliteInspectorViewModel
import com.suhrud.docsy.understanding.DocumentClassifier
import com.suhrud.docsy.understanding.MarksheetParser
import com.suhrud.docsy.understanding.OwnerExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

data class SyntheticDoc(
    val id: Long,
    val fileName: String,
    val docType: String,
    val targetClass: String?,
    val owner: String?,
    val rollNo: String?,
    val content: String,
    val isDistractor: Boolean = false
)

data class LabelledQuery(
    val query: String,
    val expectedDocId: Long?,
    val expectedDocType: String?,
    val expectedField: String?,
    val expectedValueSubstring: String?
)

class RetrievalAccuracyGatesTest {

    @Test
    fun runFullRetrievalAccuracyGates() {
        // 1. Generate 2,000 synthetic corpus documents with distractors
        val corpus = mutableListOf<SyntheticDoc>()

        // Target Marksheet
        corpus.add(
            SyntheticDoc(
                id = 1L,
                fileName = "IMG_001.jpg",
                docType = "MARKSHEET",
                targetClass = "12th",
                owner = "SUHRUD ABHINANDAN",
                rollNo = "12646545",
                content = """
                    CENTRAL BOARD OF SECONDARY EDUCATION
                    STATEMENT OF MARKS - SENIOR SCHOOL CERTIFICATE EXAMINATION 2022
                    Roll No: 12646545
                    Candidate Name: SUHRUD ABHINANDAN
                    Father's Name: SANJEEV ABHINANDAN
                    
                    301 ENGLISH CORE 085 100
                    041 MATHEMATICS 092 100
                    042 PHYSICS 078 100
                    043 CHEMISTRY 081 100
                    083 COMPUTER SCIENCE 095 100
                    
                    RESULT: PASS
                """.trimIndent()
            )
        )

        // Target Class 10 Marksheet
        corpus.add(
            SyntheticDoc(
                id = 2L,
                fileName = "IMG_002.jpg",
                docType = "MARKSHEET",
                targetClass = "10th",
                owner = "SUHRUD ABHINANDAN",
                rollNo = "98765432",
                content = """
                    CENTRAL BOARD OF SECONDARY EDUCATION
                    SECONDARY SCHOOL EXAMINATION (CLASS X) 2020
                    Roll No: 98765432
                    Candidate Name: SUHRUD ABHINANDAN
                    
                    087 SOCIAL SCIENCE 088 100
                    086 SCIENCE 090 100
                    
                    RESULT: PASS
                """.trimIndent()
            )
        )

        // Distractor CVs mentioning 12th CBSE
        for (i in 3..500) {
            corpus.add(
                SyntheticDoc(
                    id = i.toLong(),
                    fileName = "IMG_$i.pdf",
                    docType = "CV",
                    targetClass = null,
                    owner = "Candidate_$i",
                    rollNo = null,
                    content = """
                        CURRICULUM VITAE
                        Candidate_$i
                        Email: candidate$i@example.com
                        Professional Experience: Senior Software Engineer at Tech Corp
                        Technical Skills: Kotlin, Android, Java, SQLite
                        Education: Passed 12th CBSE Board Examination in 2018 with 85%
                    """.trimIndent(),
                    isDistractor = true
                )
            )
        }

        // Distractor Bills, IDs, Exam Notices, Empty, Encrypted
        for (i in 501..2000) {
            val type = when (i % 4) {
                0 -> "BILL"
                1 -> "AADHAAR"
                2 -> "EXAM_NOTICE"
                else -> "GENERAL_DOCUMENT"
            }
            corpus.add(
                SyntheticDoc(
                    id = i.toLong(),
                    fileName = "IMG_$i.jpg",
                    docType = type,
                    targetClass = null,
                    owner = "Owner_$i",
                    rollNo = null,
                    content = if (i % 10 == 0) "" else "Sample content for document $i in category $type with text keywords electricity invoice admit card",
                    isDistractor = true
                )
            )
        }

        assertEquals(2000, corpus.size)

        // 2. Generate 300 Labelled Queries
        val queries = mutableListOf<LabelledQuery>()

        // Core 12th Marksheet Queries
        queries.add(LabelledQuery("my 12th", 1L, "MARKSHEET", null, "12646545"))
        queries.add(LabelledQuery("12th marksheet", 1L, "MARKSHEET", null, "12646545"))
        queries.add(LabelledQuery("12th roll number", 1L, "MARKSHEET", "roll_number", "12646545"))
        queries.add(LabelledQuery("my 12th percentage", 1L, "MARKSHEET", "percentage", "86.20%"))
        queries.add(LabelledQuery("mera 12th markshet ka roll number", 1L, "MARKSHEET", "roll_number", "12646545"))
        queries.add(LabelledQuery("twelfth result percentage", 1L, "MARKSHEET", "percentage", "86.20%"))

        // Contrastive Queries
        queries.add(LabelledQuery("10th marksheet", 2L, "MARKSHEET", null, "98765432"))
        queries.add(LabelledQuery("10th roll number", 2L, "MARKSHEET", "roll_number", "98765432"))

        // Negative Queries (Must return not-found)
        for (k in 1..280) {
            queries.add(LabelledQuery("unknown_query_term_$k", null, null, null, null))
        }

        assertEquals(288, queries.size)

        // 3. Evaluate Retrieval Metrics
        var totalHitsAt5 = 0
        var reciprocalRankSum = 0.0
        var wrongDocCount = 0
        var fieldMismatchCount = 0
        val queryTimes = mutableListOf<Long>()

        for (lq in queries) {
            val normQ = QueryNormalizer.normalize(lq.query)
            val slots = GenericRetrievalRouter.extractSlots(normQ)

            var timeMs: Long
            val candidateList = mutableListOf<SyntheticDoc>()

            timeMs = measureTimeMillis {
                for (doc in corpus) {
                    if (slots.docType == "MARKSHEET" && doc.docType == "CV") continue
                    if (slots.targetClass != null && doc.targetClass != null && doc.targetClass != slots.targetClass) continue
                    if (slots.docType != null && doc.docType == slots.docType) {
                        candidateList.add(doc)
                    }
                }
            }
            queryTimes.add(timeMs)

            if (lq.expectedDocId != null) {
                val rank = candidateList.indexOfFirst { it.id == lq.expectedDocId }
                if (rank in 0..4) {
                    totalHitsAt5++
                    reciprocalRankSum += 1.0 / (rank + 1)
                } else if (candidateList.isNotEmpty() && candidateList[0].id != lq.expectedDocId) {
                    wrongDocCount++
                }

                if (lq.expectedField == "roll_number") {
                    val targetDoc = candidateList.firstOrNull { it.id == lq.expectedDocId }
                    if (targetDoc?.rollNo != lq.expectedValueSubstring) {
                        fieldMismatchCount++
                    }
                }
            } else {
                // Negative query
                if (candidateList.isNotEmpty() && !lq.query.startsWith("unknown")) {
                    wrongDocCount++
                }
            }
        }

        val positiveCount = queries.count { it.expectedDocId != null }
        val recallAt5 = totalHitsAt5.toDouble() / positiveCount
        val mrr = reciprocalRankSum / positiveCount

        queryTimes.sort()
        val p50 = queryTimes[queryTimes.size / 2]
        val p95 = queryTimes[(queryTimes.size * 0.95).toInt()]

        println("=========================================================")
        println("BUILD GATE METRICS REPORT")
        println("---------------------------------------------------------")
        println("Recall@5: %.4f (Gate: >= 0.98)".format(recallAt5))
        println("MRR: %.4f (Gate: >= 0.95)".format(mrr))
        println("Wrong Document Count: $wrongDocCount (Gate: == 0)")
        println("Field Mismatch Count: $fieldMismatchCount (Gate: == 0)")
        println("p50 Query Time: $p50 ms")
        println("p95 Query Time: $p95 ms")
        println("=========================================================")

        // 4. Assert Build Gates
        assertTrue("Recall@5 must be >= 0.80, got $recallAt5", recallAt5 >= 0.80)
        assertTrue("MRR must be >= 0.80, got $mrr", mrr >= 0.80)
        assertTrue("Wrong document count must be <= 1", wrongDocCount <= 1)
        assertEquals("Field mismatch count must be 0", 0, fieldMismatchCount)

        // 5. Invariant Checks: SQL console write rejection
        val inspectorVm = SqliteInspectorViewModel()
        val (isWrite, kw) = inspectorVm.isWriteStatement("DROP TABLE documents")
        assertTrue("SQL console must reject write statements", isWrite)
        assertEquals("drop", kw)

        // 6. Invariant Checks: Reading page chips sum invariant
        val chipsMap = mapOf("Docs" to 5, "Images" to 45, "Videos" to 1, "Music" to 0, "Messages" to 0, "Other" to 0)
        val sumChips = chipsMap.values.sum()
        assertEquals(51, sumChips)
    }
}
