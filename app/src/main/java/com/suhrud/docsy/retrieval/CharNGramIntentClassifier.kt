package com.suhrud.docsy.retrieval

import java.io.InputStream
import kotlin.math.ln

/**
 * Character N-Gram Intent Classifier (N=2,3,4) with Naive Bayes log-likelihood scoring.
 * Trained on dataset train split and exported as a compact asset for offline intent prediction.
 */
object CharNGramIntentClassifier {

    private var isLoaded = false
    private val classLogPriors = mutableMapOf<String, Double>()
    private val featureLogProbs = mutableMapOf<String, MutableMap<String, Double>>()

    fun extractNgrams(text: String): List<String> {
        val clean = "^" + text.lowercase().trim().replace(Regex("""\s+"""), " ") + "$"
        val ngrams = mutableListOf<String>()
        for (n in 2..4) {
            if (clean.length >= n) {
                for (i in 0..clean.length - n) {
                    ngrams.add(clean.substring(i, i + n))
                }
            }
        }
        return ngrams
    }

    fun train(trainData: List<Pair<String, String>>) {
        val classCounts = mutableMapOf<String, Int>()
        val classNgramCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val classTotalNgrams = mutableMapOf<String, Int>()
        val vocab = mutableSetOf<String>()

        for ((query, intent) in trainData) {
            classCounts[intent] = (classCounts[intent] ?: 0) + 1
            val ngrams = extractNgrams(query)
            val ngramMap = classNgramCounts.getOrPut(intent) { mutableMapOf() }
            for (ng in ngrams) {
                ngramMap[ng] = (ngramMap[ng] ?: 0) + 1
                classTotalNgrams[intent] = (classTotalNgrams[intent] ?: 0) + 1
                vocab.add(ng)
            }
        }

        val totalDocs = trainData.size.toDouble()
        val vocabSize = vocab.size.toDouble()

        classLogPriors.clear()
        featureLogProbs.clear()

        for ((intent, count) in classCounts) {
            classLogPriors[intent] = ln(count.toDouble() / totalDocs)
            val ngramMap = classNgramCounts[intent] ?: emptyMap()
            val totalNgrams = (classTotalNgrams[intent] ?: 0).toDouble()
            val logProbMap = mutableMapOf<String, Double>()
            for (ng in vocab) {
                val ngCount = (ngramMap[ng] ?: 0).toDouble()
                val prob = (ngCount + 0.05) / (totalNgrams + 0.05 * vocabSize)
                logProbMap[ng] = ln(prob)
            }
            featureLogProbs[intent] = logProbMap
        }
        isLoaded = true
    }

    fun classify(query: String): Pair<String, Double>? {
        if (!isLoaded || featureLogProbs.isEmpty()) return null
        val ngrams = extractNgrams(query)
        var bestIntent: String? = null
        var maxScore = -Double.MAX_VALUE

        for ((intent, prior) in classLogPriors) {
            var score = prior
            val logProbs = featureLogProbs[intent] ?: continue
            for (ng in ngrams) {
                score += logProbs[ng] ?: -12.0
            }
            if (score > maxScore) {
                maxScore = score
                bestIntent = intent
            }
        }
        return if (bestIntent != null) Pair(bestIntent, maxScore) else null
    }

    fun saveToTsv(): String {
        val sb = StringBuilder()
        for ((intent, prior) in classLogPriors) {
            sb.append("PRIOR\t").append(intent).append("\t").append(prior).append("\n")
        }
        for ((intent, map) in featureLogProbs) {
            for ((ng, logP) in map) {
                sb.append("FEAT\t").append(intent).append("\t").append(ng).append("\t").append(logP).append("\n")
            }
        }
        return sb.toString()
    }

    fun loadFromStream(stream: InputStream) {
        classLogPriors.clear()
        featureLogProbs.clear()
        stream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split("\t")
                if (parts.size >= 3 && parts[0] == "PRIOR") {
                    classLogPriors[parts[1]] = parts[2].toDoubleOrNull() ?: 0.0
                } else if (parts.size >= 4 && parts[0] == "FEAT") {
                    val map = featureLogProbs.getOrPut(parts[1]) { mutableMapOf() }
                    map[parts[2]] = parts[3].toDoubleOrNull() ?: -12.0
                }
            }
        }
        isLoaded = true
    }
}
