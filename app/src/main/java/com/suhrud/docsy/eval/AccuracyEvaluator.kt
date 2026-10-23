package com.suhrud.docsy.eval

import com.suhrud.docsy.classification.HierarchicalDocumentClassifier
import com.suhrud.docsy.extraction.FieldDisambiguator

data class MetricScore(
    val precision: Float,
    val recall: Float,
    val f1Score: Float,
    val totalSamples: Int,
    val correctCount: Int
)

data class AccuracyReport(
    val categoryAccuracy: Float,
    val specificTypeAccuracy: Float,
    val entityMetrics: Map<String, MetricScore>,
    val disambiguationAccuracy: Float,
    val totalFixturesEvaluated: Int
)

object AccuracyEvaluator {

    fun evaluateCorpus(fixtures: List<DocumentFixture>): AccuracyReport {
        if (fixtures.isEmpty()) {
            return AccuracyReport(0f, 0f, emptyMap(), 0f, 0)
        }

        var correctCategories = 0
        var correctTypes = 0

        val entityTruePositives = mutableMapOf<String, Int>()
        val entityFalsePositives = mutableMapOf<String, Int>()
        val entityFalseNegatives = mutableMapOf<String, Int>()

        var totalDisambiguations = 0
        var correctDisambiguations = 0

        for (fixture in fixtures) {
            // 1. Evaluate Classification
            val classResult = HierarchicalDocumentClassifier.classify(
                text = fixture.rawContent,
                fileName = fixture.fileName
            )

            if (classResult.generalCategory.name.equals(fixture.expectedCategory, ignoreCase = true)) {
                correctCategories++
            }

            if (classResult.specificType.equals(fixture.expectedType, ignoreCase = true)) {
                correctTypes++
            }

            // 2. Evaluate Entity Extraction & Disambiguation
            val disambiguatedFields = FieldDisambiguator.disambiguateText(
                text = fixture.rawContent,
                sourcePath = fixture.fileName,
                documentType = classResult.specificType
            )

            val extractedMap = disambiguatedFields.associate { it.fieldType to it.rawValue }

            for ((expKey, expVal) in fixture.expectedEntities) {
                val actualVal = extractedMap[expKey]
                if (actualVal != null) {
                    if (actualVal.equals(expVal, ignoreCase = true) || actualVal.contains(expVal, ignoreCase = true)) {
                        entityTruePositives[expKey] = (entityTruePositives[expKey] ?: 0) + 1
                    } else {
                        entityFalsePositives[expKey] = (entityFalsePositives[expKey] ?: 0) + 1
                    }
                } else {
                    entityFalseNegatives[expKey] = (entityFalseNegatives[expKey] ?: 0) + 1
                }
            }

            // 3. Evaluate Difficult Disambiguation Cases
            if (fixture.expectedEntities.containsKey("STUDENT_NAME") && fixture.expectedEntities.containsKey("PARENT_NAME")) {
                totalDisambiguations++
                val actualStudent = extractedMap["STUDENT_NAME"]
                val actualParent = extractedMap["PARENT_NAME"]
                if (actualStudent == fixture.expectedEntities["STUDENT_NAME"] && actualParent == fixture.expectedEntities["PARENT_NAME"]) {
                    correctDisambiguations++
                }
            }

            if (fixture.expectedEntities.containsKey("ROLL_NUMBER") && fixture.expectedEntities.containsKey("PHONE_NUMBER")) {
                totalDisambiguations++
                val actualRoll = extractedMap["ROLL_NUMBER"]
                val actualPhone = extractedMap["PHONE_NUMBER"]
                if (actualRoll == fixture.expectedEntities["ROLL_NUMBER"] && actualPhone == fixture.expectedEntities["PHONE_NUMBER"]) {
                    correctDisambiguations++
                }
            }
        }

        val total = fixtures.size
        val categoryAcc = correctCategories.toFloat() / total
        val typeAcc = correctTypes.toFloat() / total
        val disambigAcc = if (totalDisambiguations > 0) correctDisambiguations.toFloat() / totalDisambiguations else 1.0f

        val entityMetrics = mutableMapOf<String, MetricScore>()
        val allKeys = (entityTruePositives.keys + entityFalsePositives.keys + entityFalseNegatives.keys).distinct()

        for (key in allKeys) {
            val tp = entityTruePositives[key] ?: 0
            val fp = entityFalsePositives[key] ?: 0
            val fn = entityFalseNegatives[key] ?: 0

            val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
            val recall = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
            val f1 = if (precision + recall > 0) 2 * (precision * recall) / (precision + recall) else 0f

            entityMetrics[key] = MetricScore(
                precision = precision,
                recall = recall,
                f1Score = f1,
                totalSamples = tp + fn,
                correctCount = tp
            )
        }

        return AccuracyReport(
            categoryAccuracy = categoryAcc,
            specificTypeAccuracy = typeAcc,
            entityMetrics = entityMetrics,
            disambiguationAccuracy = disambigAcc,
            totalFixturesEvaluated = total
        )
    }
}
