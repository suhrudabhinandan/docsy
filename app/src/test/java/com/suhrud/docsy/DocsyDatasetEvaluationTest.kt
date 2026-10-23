package com.suhrud.docsy

import com.suhrud.docsy.retrieval.CharNGramIntentClassifier
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.NotFoundReplyGenerator
import com.suhrud.docsy.retrieval.RouterOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

data class DatasetRecord(
    val id: String,
    val split: String,
    val category: String,
    val docType: String,
    val intent: String,
    val requestedField: String,
    val style: String,
    val query: String,
    val expectedBehavior: String
)

class DocsyDatasetEvaluationTest {

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun loadDataset(): List<DatasetRecord> {
        val inputStream = javaClass.classLoader?.getResourceAsStream("docsy_query_dataset.csv")
            ?: File("src/test/resources/docsy_query_dataset.csv").inputStream()

        val records = mutableListOf<DatasetRecord>()
        inputStream.bufferedReader().useLines { lines ->
            val list = lines.toList()
            if (list.isEmpty()) return@useLines
            val header = parseCsvLine(list[0])
            val idIdx = header.indexOf("id")
            val splitIdx = header.indexOf("split")
            val catIdx = header.indexOf("category")
            val docTypeIdx = header.indexOf("doc_type")
            val intentIdx = header.indexOf("intent")
            val fieldIdx = header.indexOf("requested_field")
            val styleIdx = header.indexOf("style")
            val queryIdx = header.indexOf("query")
            val behaviorIdx = header.indexOf("expected_behavior")

            for (lineNo in 1 until list.size) {
                val line = list[lineNo].trim()
                if (line.isEmpty()) continue
                val cols = parseCsvLine(line)
                if (cols.size > queryIdx) {
                    records.add(
                        DatasetRecord(
                            id = cols.getOrElse(idIdx) { "" },
                            split = cols.getOrElse(splitIdx) { "" },
                            category = cols.getOrElse(catIdx) { "" },
                            docType = cols.getOrElse(docTypeIdx) { "" },
                            intent = cols.getOrElse(intentIdx) { "" },
                            requestedField = cols.getOrElse(fieldIdx) { "" },
                            style = cols.getOrElse(styleIdx) { "" },
                            query = cols.getOrElse(queryIdx) { "" },
                            expectedBehavior = cols.getOrElse(behaviorIdx) { "" }
                        )
                    )
                }
            }
        }
        return records
    }

    @Test
    fun testDatasetEvaluationAndTrainClassifier() {
        val records = loadDataset()
        assertTrue("Dataset records must be loaded", records.isNotEmpty())

        // 1. Train Char N-Gram Intent Classifier from train split ONLY
        val trainRecords = records.filter { it.split.equals("train", ignoreCase = true) }
        assertTrue("Train records must be non-empty", trainRecords.isNotEmpty())

        val trainPairs = trainRecords.map { Pair(it.query, it.intent) }
        CharNGramIntentClassifier.train(trainPairs)

        // Save weights asset to app/src/main/assets/intent_ngram_weights.tsv
        val assetsDir = File("src/main/assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        val weightsFile = File(assetsDir, "intent_ngram_weights.tsv")
        weightsFile.writeText(CharNGramIntentClassifier.saveToTsv())
        assertTrue("Weights file must exist in assets", weightsFile.exists())

        // 2. Evaluate dev and test splits through existing router
        val evalRecords = records.filter {
            it.split.equals("dev", ignoreCase = true) || it.split.equals("test", ignoreCase = true)
        }
        assertTrue("Eval records (dev + test) must be non-empty", evalRecords.isNotEmpty())

        var totalCount = 0
        var correctCount = 0

        val categoryTotal = mutableMapOf<String, Int>()
        val categoryCorrect = mutableMapOf<String, Int>()

        data class MissRecord(val query: String, val expected: String, val got: String, val category: String)
        val misses = mutableListOf<MissRecord>()

        for (rec in evalRecords) {
            val decision = IntentRouter.route(rec.query)
            val gotIntent = decision.intent.name

            totalCount++
            categoryTotal[rec.category] = (categoryTotal[rec.category] ?: 0) + 1

            if (gotIntent.equals(rec.intent, ignoreCase = true)) {
                correctCount++
                categoryCorrect[rec.category] = (categoryCorrect[rec.category] ?: 0) + 1
            } else {
                misses.add(MissRecord(rec.query, rec.intent, gotIntent, rec.category))
            }
        }

        val overallAccuracy = (correctCount.toDouble() / totalCount) * 100.0

        println("=========================================================")
        println("DOCSY DATASET ROUTER ACCURACY REPORT")
        println("---------------------------------------------------------")
        println("Overall Accuracy: %.2f%% (%d / %d)".format(overallAccuracy, correctCount, totalCount))
        println("---------------------------------------------------------")
        println("ACCURACY PER CATEGORY:")
        for ((cat, total) in categoryTotal) {
            val corr = categoryCorrect[cat] ?: 0
            val acc = (corr.toDouble() / total) * 100.0
            println(" - %-25s: %6.2f%% (%d / %d)".format(cat, acc, corr, total))
        }
        println("---------------------------------------------------------")
        println("TOP 30 WORST MISSES (Query -> Expected vs Got):")
        misses.take(30).forEachIndexed { idx, m ->
            println("%2d. [%s] Query: \"%s\" | Expected: %s | Got: %s".format(idx + 1, m.category, m.query, m.expected, m.got))
        }
        println("=========================================================")

        // 3. Enforce Rules Required by Test
        // Rule A: Small talk and general questions never reach document search or show a source card
        val smallTalkDecision = IntentRouter.route("hello how are you")
        assertNotEquals("Small talk must not be DOCUMENT_SEARCH_LEXICAL", DocsyIntent.DOCUMENT_SEARCH_LEXICAL, smallTalkDecision.intent)
        assertTrue("Small talk outcome must be SMALL_TALK or GENERAL_KNOWLEDGE",
            smallTalkDecision.outcome == RouterOutcome.SMALL_TALK || smallTalkDecision.outcome == RouterOutcome.GENERAL_KNOWLEDGE)

        val generalQDecision = IntentRouter.route("explain how photos synthesis works")
        assertNotEquals("General question must not be DOCUMENT_SEARCH_LEXICAL", DocsyIntent.DOCUMENT_SEARCH_LEXICAL, generalQDecision.intent)

        // Rule B: "date of birth" never returns today's date
        val dobDecision = IntentRouter.route("what is my date of birth")
        assertNotEquals("Date of birth must not route to SYSTEM_DATE", DocsyIntent.SYSTEM_DATE, dobDecision.intent)
        assertNotEquals("Date of birth must not route to SYSTEM_TIME", DocsyIntent.SYSTEM_TIME, dobDecision.intent)
        assertEquals("Date of birth must route to DOCUMENT_FACT_QUERY", DocsyIntent.DOCUMENT_FACT_QUERY, dobDecision.intent)

        // Rule C: A missing document returns "I couldn't find that" / "couldn't find"
        val missingReply = NotFoundReplyGenerator.generateNotFound("non_existent_doc_123")
        assertTrue("Missing document reply must explain not found",
            missingReply.primaryText.contains("couldn't find") || missingReply.primaryText.contains("not found"))
    }
}
