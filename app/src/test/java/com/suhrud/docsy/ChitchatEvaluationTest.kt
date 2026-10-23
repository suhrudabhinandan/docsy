package com.suhrud.docsy

import com.suhrud.docsy.retrieval.ChitchatEmbeddingEngine
import com.suhrud.docsy.retrieval.ChitchatRecord
import com.suhrud.docsy.retrieval.ChitchatResponseBank
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.RouterOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ChitchatEvaluationTest {

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

    private fun loadDataset(): List<ChitchatRecord> {
        val stream = javaClass.classLoader?.getResourceAsStream("docsy_chitchat_dataset.csv")
            ?: File("src/test/resources/docsy_chitchat_dataset.csv").inputStream()

        val records = mutableListOf<ChitchatRecord>()
        stream.bufferedReader().useLines { lines ->
            val list = lines.toList()
            if (list.isEmpty()) return@useLines
            val header = parseCsvLine(list[0])
            val idIdx = header.indexOf("id")
            val splitIdx = header.indexOf("split")
            val intentIdx = header.indexOf("intent")
            val queryIdx = header.indexOf("query")

            for (i in 1 until list.size) {
                val line = list[i].trim()
                if (line.isEmpty()) continue
                val cols = parseCsvLine(line)
                if (cols.size > queryIdx) {
                    records.add(
                        ChitchatRecord(
                            id = cols.getOrElse(idIdx) { "" },
                            split = cols.getOrElse(splitIdx) { "" },
                            intent = cols.getOrElse(intentIdx) { "" },
                            query = cols.getOrElse(queryIdx) { "" }
                        )
                    )
                }
            }
        }
        return records
    }

    @Before
    fun setUp() {
        val datasetStream = javaClass.classLoader?.getResourceAsStream("docsy_chitchat_dataset.csv")
            ?: File("src/test/resources/docsy_chitchat_dataset.csv").inputStream()
        val responseStream = javaClass.classLoader?.getResourceAsStream("docsy_chitchat_responses.csv")
            ?: File("src/test/resources/docsy_chitchat_responses.csv").inputStream()

        ChitchatEmbeddingEngine.initFromStreams(datasetStream, responseStream)
        assertTrue("ChitchatEmbeddingEngine must be ready", ChitchatEmbeddingEngine.isReady())
    }

    @Test
    fun testChitchatEvaluationDevAndTestSplits() {
        val records = loadDataset()
        assertTrue("Dataset records must not be empty", records.isNotEmpty())

        val evalRecords = records.filter {
            it.split.equals("dev", ignoreCase = true) || it.split.equals("test", ignoreCase = true)
        }
        assertTrue("Eval records (dev + test) must not be empty", evalRecords.isNotEmpty())

        var totalCount = 0
        var correctCount = 0
        val intentTotal = mutableMapOf<String, Int>()
        val intentCorrect = mutableMapOf<String, Int>()

        for (rec in evalRecords) {
            val match = ChitchatEmbeddingEngine.matchIntent(rec.query)
            val gotIntent = match?.intent ?: "UNKNOWN"

            totalCount++
            intentTotal[rec.intent] = (intentTotal[rec.intent] ?: 0) + 1

            if (gotIntent.equals(rec.intent, ignoreCase = true)) {
                correctCount++
                intentCorrect[rec.intent] = (intentCorrect[rec.intent] ?: 0) + 1
            }
        }

        val overallAccuracy = (correctCount.toDouble() / totalCount.toDouble()) * 100.0

        println("=========================================================")
        println("DOCSY CHITCHAT INTENT EMBEDDING ACCURACY REPORT")
        println("---------------------------------------------------------")
        println("Overall Accuracy: %.2f%% (%d / %d)".format(overallAccuracy, correctCount, totalCount))
        println("---------------------------------------------------------")
        println("ACCURACY PER INTENT:")
        for ((intent, total) in intentTotal) {
            val corr = intentCorrect[intent] ?: 0
            val acc = (corr.toDouble() / total.toDouble()) * 100.0
            println(" - %-20s: %6.2f%% (%d / %d)".format(intent, acc, corr, total))
        }
        println("=========================================================")

        // Requirement 6 Assertion 1: Every intent must have at least one passing query
        for ((intent, _) in intentTotal) {
            val corr = intentCorrect[intent] ?: 0
            assertTrue("Intent '$intent' must have at least one passing query", corr > 0)
        }

        // Requirement 6 Assertion 2: Unseen paraphrases of "how old are you" must map to the age intent
        val ageParaphrases = listOf(
            "how old are you",
            "what's your age",
            "what is your age",
            "how old are you docsy",
            "how many years old are you",
            "tell me your age"
        )
        for (q in ageParaphrases) {
            val match = ChitchatEmbeddingEngine.matchIntent(q)
            assertNotNull("Query '$q' should match an intent", match)
            assertEquals("Unseen age paraphrase '$q' must map to 'age' intent", "age", match?.intent)
        }
    }

    @Test
    fun testRandomReplySelectionAndPlaceholders() {
        // Requirement 3: Fill {name}, {coin}, {dice}, {version} at runtime
        val coinReply = ChitchatResponseBank.getReply("coin_flip", userName = "Sam", versionName = "1.0.9")
        assertTrue("Coin reply must contain user name Sam", coinReply.contains("Sam"))
        assertTrue("Coin reply must contain Heads or Tails", coinReply.contains("Heads") || coinReply.contains("Tails"))

        val diceReply = ChitchatResponseBank.getReply("dice_roll", userName = "Sam", versionName = "1.0.9")
        assertTrue("Dice reply must contain user name Sam", diceReply.contains("Sam"))
        val hasDiceNumber = (1..6).any { diceReply.contains(it.toString()) }
        assertTrue("Dice reply must contain a number between 1 and 6", hasDiceNumber)

        val versionReply = ChitchatResponseBank.getReply("version", versionName = "1.0.9")
        assertTrue("Version reply must contain 1.0.9", versionReply.contains("1.0.9"))

        // Requirement 3: Pick a reply variant at random, never the same one twice in a row per chat
        val session = "test_chat_session_1"
        val r1 = ChitchatResponseBank.getReply("greetings", userName = "Alice", sessionId = session)
        val r2 = ChitchatResponseBank.getReply("greetings", userName = "Alice", sessionId = session)
        assertTrue("Consecutive replies for greetings must not be identical", r1 != r2)
    }

    @Test
    fun testFirmToneIntentsEmergencyAndSelfHarm() {
        // Requirement 4: Intents tagged tone=firm (emergency, self_harm) take priority and match lower threshold
        val selfHarmMatch = ChitchatEmbeddingEngine.matchIntent("i feel like ending my life")
        assertNotNull(selfHarmMatch)
        assertEquals("self_harm", selfHarmMatch?.intent)
        assertTrue("self_harm must be marked as firm", selfHarmMatch?.isFirm == true)

        val emergencyMatch = ChitchatEmbeddingEngine.matchIntent("call 911 emergency help")
        assertNotNull(emergencyMatch)
        assertEquals("emergency", emergencyMatch?.intent)
        assertTrue("emergency must be marked as firm", emergencyMatch?.isFirm == true)

        val selfHarmReply1 = ChitchatResponseBank.getReply("self_harm")
        val selfHarmReply2 = ChitchatResponseBank.getReply("self_harm")
        assertEquals("Firm intents must always return fixed reply", selfHarmReply1, selfHarmReply2)
        assertTrue("Self harm reply must mention helpline", selfHarmReply1.contains("988") || selfHarmReply1.contains("hotline"))
    }

    @Test
    fun testBelowThresholdAsksToRephrase() {
        // Requirement 2: Below threshold, do not reply with canned line, ask user to rephrase
        val lowSimChitchatQuery = "hello zzz123 xyz987 randomqwerty"
        val match = ChitchatEmbeddingEngine.matchIntent(lowSimChitchatQuery)
        assertNull("Low similarity chitchat query should return null (below threshold)", match)

        val decision = IntentRouter.route(lowSimChitchatQuery)
        val replyText = com.suhrud.docsy.planner.DeterministicReplyComposer.composeSmallTalk(
            intent = decision.intent,
            targetCategory = decision.targetCategory
        )
        assertTrue("Below threshold query must ask user to rephrase", replyText.contains("rephrase"))
    }
}
