package com.suhrud.docsy.retrieval

import android.content.Context
import com.suhrud.docsy.BuildConfig
import java.io.InputStream
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

data class ChitchatRecord(
    val id: String,
    val split: String,
    val intent: String,
    val query: String
)

data class ChitchatResponseRecord(
    val intent: String,
    val tone: String,
    val response: String
)

data class ChitchatMatchResult(
    val intent: String,
    val confidence: Float,
    val isFirm: Boolean
)

/**
 * On-device embedding model and centroid-based intent matcher for Chitchat.
 * Satisfies Chitchat Response Bank Requirements 1, 2, and 4.
 */
object ChitchatEmbeddingEngine {

    private var isInitialized = false
    private val featureMap = mutableMapOf<String, Int>()
    private val idfMap = mutableMapOf<Int, Float>()
    private val intentCentroids = mutableMapOf<String, FloatArray>()
    private val intentTones = mutableMapOf<String, String>()

    private const val FIRM_MIN_THRESHOLD = 0.20f
    private const val NORMAL_MIN_THRESHOLD = 0.28f

    fun isReady(): Boolean = isInitialized

    fun initFromContext(context: Context) {
        try {
            val datasetStream = context.assets.open("docsy_chitchat_dataset.csv")
            val responseStream = context.assets.open("docsy_chitchat_responses.csv")
            initFromStreams(datasetStream, responseStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initFromStreams(datasetStream: InputStream, responseStream: InputStream) {
        val datasetRecords = parseDatasetCsv(datasetStream)
        val responseRecords = parseResponsesCsv(responseStream)
        trainAndBuildCentroids(datasetRecords, responseRecords)
    }

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

    private fun parseDatasetCsv(stream: InputStream): List<ChitchatRecord> {
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

    private fun parseResponsesCsv(stream: InputStream): List<ChitchatResponseRecord> {
        val records = mutableListOf<ChitchatResponseRecord>()
        stream.bufferedReader().useLines { lines ->
            val list = lines.toList()
            if (list.isEmpty()) return@useLines
            val header = parseCsvLine(list[0])
            val intentIdx = header.indexOf("intent")
            val toneIdx = header.indexOf("tone")
            val responseIdx = header.indexOf("response")

            for (i in 1 until list.size) {
                val line = list[i].trim()
                if (line.isEmpty()) continue
                val cols = parseCsvLine(line)
                if (cols.size > responseIdx) {
                    records.add(
                        ChitchatResponseRecord(
                            intent = cols.getOrElse(intentIdx) { "" },
                            tone = cols.getOrElse(toneIdx) { "" },
                            response = cols.getOrElse(responseIdx) { "" }
                        )
                    )
                }
            }
        }
        return records
    }

    fun extractFeatures(text: String): List<String> {
        val clean = "^" + text.lowercase().trim().replace(Regex("""\s+"""), " ") + "$"
        val features = mutableListOf<String>()

        // Words and word bigrams
        val words = clean.replace("^", "").replace("$", "").split(" ").filter { it.isNotBlank() }
        features.addAll(words)
        for (i in 0 until words.size - 1) {
            features.add("${words[i]}_${words[i + 1]}")
        }

        // Character n-grams (n=2,3,4)
        for (n in 2..4) {
            if (clean.length >= n) {
                for (i in 0..clean.length - n) {
                    features.add(clean.substring(i, i + n))
                }
            }
        }
        return features
    }

    private fun trainAndBuildCentroids(
        datasetRecords: List<ChitchatRecord>,
        responseRecords: List<ChitchatResponseRecord>
    ) {
        val trainRecords = datasetRecords.filter { it.split.equals("train", ignoreCase = true) }
        val allTrainQueries = trainRecords.ifEmpty { datasetRecords }

        featureMap.clear()
        idfMap.clear()
        intentCentroids.clear()
        intentTones.clear()

        // Store tones
        for (resp in responseRecords) {
            if (resp.tone.equals("firm", ignoreCase = true)) {
                intentTones[resp.intent] = "firm"
            } else if (!intentTones.containsKey(resp.intent)) {
                intentTones[resp.intent] = resp.tone
            }
        }

        // 1. Build Vocabulary and Document Frequency (DF)
        val docFreq = mutableMapOf<String, Int>()
        val queryFeatures = mutableListOf<Pair<ChitchatRecord, List<String>>>()

        for (rec in allTrainQueries) {
            val feats = extractFeatures(rec.query)
            queryFeatures.add(Pair(rec, feats))
            val uniqueFeats = feats.toSet()
            for (f in uniqueFeats) {
                docFreq[f] = (docFreq[f] ?: 0) + 1
            }
        }

        val totalDocs = allTrainQueries.size.toDouble()
        var featureIdx = 0
        for ((f, df) in docFreq) {
            featureMap[f] = featureIdx
            val idf = ln(1.0 + (totalDocs / (df.toDouble() + 1.0))).toFloat()
            idfMap[featureIdx] = idf
            featureIdx++
        }

        val dim = featureMap.size
        if (dim == 0) return

        // 2. Embed each train query and aggregate by intent to compute centroids
        val intentSums = mutableMapOf<String, FloatArray>()
        val intentCounts = mutableMapOf<String, Int>()

        for ((rec, feats) in queryFeatures) {
            val vec = embedFeatures(feats, dim)
            val sumVec = intentSums.getOrPut(rec.intent) { FloatArray(dim) }
            for (i in 0 until dim) {
                sumVec[i] += vec[i]
            }
            intentCounts[rec.intent] = (intentCounts[rec.intent] ?: 0) + 1
        }

        // 3. Normalize intent centroids
        for ((intent, sumVec) in intentSums) {
            val count = intentCounts[intent] ?: 1
            for (i in 0 until dim) {
                sumVec[i] /= count.toFloat()
            }
            normalizeInPlace(sumVec)
            intentCentroids[intent] = sumVec
        }

        // Store responses in ChitchatResponseBank
        ChitchatResponseBank.loadResponses(responseRecords)

        isInitialized = true
    }

    fun embed(query: String): FloatArray {
        val dim = featureMap.size
        if (dim == 0) return FloatArray(0)
        val feats = extractFeatures(query)
        return embedFeatures(feats, dim)
    }

    private fun embedFeatures(feats: List<String>, dim: Int): FloatArray {
        val vec = FloatArray(dim)
        val termFreq = mutableMapOf<Int, Int>()
        for (f in feats) {
            val idx = featureMap[f] ?: continue
            termFreq[idx] = (termFreq[idx] ?: 0) + 1
        }
        for ((idx, tf) in termFreq) {
            val idf = idfMap[idx] ?: 1.0f
            vec[idx] = tf.toFloat() * idf
        }
        normalizeInPlace(vec)
        return vec
    }

    private fun normalizeInPlace(vec: FloatArray) {
        var sumSq = 0.0f
        for (v in vec) {
            sumSq += v * v
        }
        val norm = sqrt(sumSq)
        if (norm > 1e-6f) {
            for (i in vec.indices) {
                vec[i] /= norm
            }
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0.0f
        var dot = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot
    }

    /**
     * Match user query to the nearest intent by embedding similarity.
     * Evaluates firm priority intents (self_harm, emergency) first with a lower threshold.
     */
    fun matchIntent(query: String): ChitchatMatchResult? {
        if (!isInitialized || intentCentroids.isEmpty()) return null

        val queryVec = embed(query)
        if (queryVec.isEmpty()) return null

        val similarities = mutableMapOf<String, Float>()
        var maxSim = -1.0f
        var bestIntent: String? = null

        for ((intent, centroid) in intentCentroids) {
            val sim = cosineSimilarity(queryVec, centroid)
            similarities[intent] = sim
            if (sim > maxSim) {
                maxSim = sim
                bestIntent = intent
            }
        }

        // Requirement 4: self_harm and emergency take priority and use a lower threshold
        val selfHarmSim = similarities["self_harm"] ?: 0.0f
        val emergencySim = similarities["emergency"] ?: 0.0f
        val maxFirmSim = maxOf(selfHarmSim, emergencySim)

        if (maxFirmSim >= FIRM_MIN_THRESHOLD && maxFirmSim >= maxSim - 0.12f) {
            val firmIntent = if (selfHarmSim >= emergencySim) "self_harm" else "emergency"
            return ChitchatMatchResult(firmIntent, maxFirmSim, isFirm = true)
        }

        // Requirement 2: Match to nearest intent by embedding similarity
        if (bestIntent != null && maxSim >= NORMAL_MIN_THRESHOLD) {
            val isFirm = intentTones[bestIntent] == "firm"
            return ChitchatMatchResult(bestIntent, maxSim, isFirm = isFirm)
        }

        // Below threshold -> return null so caller asks user to rephrase
        return null
    }
}

/**
 * Handles response selection and runtime placeholder formatting.
 * Satisfies Requirement 3 & 4.
 */
object ChitchatResponseBank {

    private val responsesByIntent = mutableMapOf<String, MutableList<ChitchatResponseRecord>>()
    private val lastUsedVariantMap = mutableMapOf<Pair<String, String>, String>()

    fun loadResponses(records: List<ChitchatResponseRecord>) {
        responsesByIntent.clear()
        for (rec in records) {
            responsesByIntent.getOrPut(rec.intent) { mutableListOf() }.add(rec)
        }
    }

    /**
     * Retrieves response for intent, handling random selection (avoiding immediate repeats)
     * and filling runtime placeholders: {name}, {coin}, {dice}, {version}.
     */
    fun getReply(
        intent: String,
        userName: String = "",
        sessionId: String = "default",
        versionName: String = BuildConfig.VERSION_NAME
    ): String {
        val candidates = responsesByIntent[intent]
        if (candidates.isNullOrEmpty()) {
            val nameStr = if (userName.isNotBlank()) " $userName" else ""
            return when (intent) {
                "greetings" -> "Hello$nameStr! I'm DOCSY, your on-device personal assistant. How can I help you today?"
                "how_are_you" -> "I'm doing great, thank you! How can I help you today?"
                "who_are_you" -> "I'm DOCSY, your on-device personal knowledge store."
                else -> "I'm not sure what you mean. Could you please rephrase that?"
            }
        }

        val firstRecord = candidates.first()
        val isFirm = firstRecord.tone.equals("firm", ignoreCase = true)

        val template = if (isFirm || candidates.size == 1) {
            firstRecord.response
        } else {
            val key = Pair(sessionId, intent)
            val lastUsed = lastUsedVariantMap[key]
            val available = candidates.filter { it.response != lastUsed }
            val chosen = if (available.isNotEmpty()) {
                available[Random.nextInt(available.size)].response
            } else {
                candidates[Random.nextInt(candidates.size)].response
            }
            lastUsedVariantMap[key] = chosen
            chosen
        }

        // Fill runtime placeholders
        val nameReplacement = if (userName.isNotBlank()) userName else "friend"
        val coinReplacement = if (Random.nextBoolean()) "Heads" else "Tails"
        val diceReplacement = (1..6).random().toString()
        val versionReplacement = if (versionName.isNotBlank()) versionName else "1.2.6"

        return template
            .replace("{name}", nameReplacement)
            .replace("{coin}", coinReplacement)
            .replace("{dice}", diceReplacement)
            .replace("{version}", versionReplacement)
    }
}
