package com.suhrud.docsy.understanding

data class ClassificationCandidate(
    val category: String,
    val score: Float
)

data class ClassificationResult(
    val category: String,
    val confidence: Float,
    val margin: Float,
    val isConfident: Boolean,
    val candidates: List<ClassificationCandidate>
)

/**
 * Whole-document classifier with positive evidence, negative evidence, and margin analysis.
 * Prevents CV/marksheet confusion and enforces Rule R4.
 * Satisfies Phase 7.1 in AUDIT.md.
 */
object DocumentClassifier {

    private data class CategoryRule(
        val category: String,
        val positivePatterns: List<Pair<String, Float>>,
        val negativePatterns: List<Pair<String, Float>>
    )

    private val RULES: List<CategoryRule> = listOf(
        CategoryRule(
            category = "MARKSHEET",
            positivePatterns = listOf(
                "statement of marks" to 5.0f,
                "marksheet" to 4.5f,
                "mark sheet" to 4.5f,
                "board of secondary education" to 4.0f,
                "central board of secondary education" to 4.5f,
                "roll no" to 3.0f,
                "roll number" to 3.0f,
                "subject code" to 3.5f,
                "theory" to 2.0f,
                "practical" to 2.0f,
                "total marks" to 3.0f,
                "passing certificate" to 4.0f,
                "cgpa" to 2.5f,
                "grade point" to 2.5f,
                "अंकतालिका" to 5.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 6.0f,
                "resume" to 5.0f,
                "work experience" to 4.5f,
                "professional experience" to 4.5f,
                "skills" to 3.0f,
                "projects" to 2.5f,
                "declaration" to 2.0f
            )
        ),
        CategoryRule(
            category = "CV",
            positivePatterns = listOf(
                "curriculum vitae" to 6.0f,
                "resume" to 5.5f,
                "biodata" to 5.0f,
                "work experience" to 4.0f,
                "technical skills" to 4.0f,
                "education" to 2.0f,
                "projects" to 3.0f,
                "certifications" to 2.5f,
                "career objective" to 3.5f,
                "professional summary" to 4.0f
            ),
            negativePatterns = listOf(
                "statement of marks" to 5.0f,
                "income tax department" to 6.0f,
                "unique identification authority" to 6.0f,
                "election commission" to 6.0f,
                "electricity bill" to 6.0f
            )
        ),
        CategoryRule(
            category = "AADHAAR",
            positivePatterns = listOf(
                "unique identification authority of india" to 6.0f,
                "uidai" to 5.0f,
                "government of india" to 2.0f,
                "mera aadhaar" to 5.0f,
                "meri pehchan" to 5.0f,
                "aadhaar" to 4.5f,
                "aadhar" to 4.0f,
                "आधार" to 5.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 5.0f,
                "statement of marks" to 5.0f
            )
        ),
        CategoryRule(
            category = "PAN",
            positivePatterns = listOf(
                "income tax department" to 6.0f,
                "permanent account number" to 6.0f,
                "pan card" to 5.0f,
                "govt. of india" to 2.0f,
                "पैन" to 5.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 5.0f,
                "uidai" to 5.0f
            )
        ),
        CategoryRule(
            category = "BILL",
            positivePatterns = listOf(
                "electricity bill" to 5.0f,
                "power distribution" to 4.5f,
                "consumer no" to 4.0f,
                "ca number" to 4.0f,
                "bill date" to 3.5f,
                "due date" to 3.5f,
                "amount payable" to 4.0f,
                "meter number" to 3.5f,
                "units consumed" to 4.0f,
                "tariff" to 2.5f,
                "invoice" to 3.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 5.0f,
                "statement of marks" to 5.0f
            )
        ),
        CategoryRule(
            category = "ADMIT_CARD",
            positivePatterns = listOf(
                "admit card" to 6.0f,
                "hall ticket" to 5.5f,
                "examination hall" to 4.0f,
                "test center" to 4.0f,
                "exam date" to 3.5f,
                "reporting time" to 4.0f,
                "gate closure" to 4.0f,
                "common admission test" to 5.0f,
                "cat 20" to 4.5f,
                "jee (main)" to 5.0f,
                "neet (ug)" to 5.0f,
                "प्रवेश पत्र" to 5.0f
            ),
            negativePatterns = listOf(
                "statement of marks" to 4.0f,
                "curriculum vitae" to 5.0f
            )
        ),
        CategoryRule(
            category = "VOTER_ID",
            positivePatterns = listOf(
                "election commission of india" to 6.0f,
                "elector's photo identity card" to 6.0f,
                "epic no" to 5.0f,
                "voter" to 3.0f,
                "भारत निर्वाचन आयोग" to 6.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 5.0f
            )
        ),
        CategoryRule(
            category = "DRIVING_LICENSE",
            positivePatterns = listOf(
                "driving licence" to 6.0f,
                "driving license" to 6.0f,
                "union of india" to 2.5f,
                "motor vehicles act" to 5.0f,
                "transport department" to 4.0f,
                "licence to drive" to 5.0f,
                "valid till" to 3.0f
            ),
            negativePatterns = listOf(
                "curriculum vitae" to 5.0f
            )
        )
    )

    /**
     * Classifies a document based on extracted text and optional file name.
     */
    fun classify(text: String, fileName: String = ""): ClassificationResult {
        val lowerText = (text + "\n" + fileName).lowercase()
        val scores = mutableListOf<ClassificationCandidate>()

        for (rule in RULES) {
            var score = 0.0f
            for ((pattern, weight) in rule.positivePatterns) {
                if (lowerText.contains(pattern)) {
                    score += weight
                }
            }
            for ((pattern, penalty) in rule.negativePatterns) {
                if (lowerText.contains(pattern)) {
                    score -= penalty
                }
            }
            if (score > 0f) {
                scores.add(ClassificationCandidate(rule.category, score))
            }
        }

        scores.sortByDescending { it.score }

        if (scores.isEmpty() || scores[0].score < 2.0f) {
            return ClassificationResult(
                category = "GENERAL_DOCUMENT",
                confidence = 0.3f,
                margin = 0.0f,
                isConfident = false,
                candidates = scores
            )
        }

        val top1 = scores[0]
        val top2Score = if (scores.size > 1) scores[1].score else 0.0f
        val margin = top1.score - top2Score
        val isConfident = margin >= 1.5f || (scores.size == 1 && top1.score >= 3.0f)
        val confidence = (top1.score / (top1.score + 5.0f)).coerceIn(0.1f, 0.99f)

        return ClassificationResult(
            category = top1.category,
            confidence = confidence,
            margin = margin,
            isConfident = isConfident,
            candidates = scores
        )
    }
}
