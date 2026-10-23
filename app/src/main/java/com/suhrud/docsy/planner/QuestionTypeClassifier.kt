package com.suhrud.docsy.planner

enum class QuestionType {
    EXACT_LOOKUP,
    DOCUMENT_SEARCH,
    SUMMARIZATION,
    COMPARISON,
    CALCULATION_AGGREGATION,
    RELATIONSHIP_QUERY,
    UNSUPPORTED
}

data class QuestionClassification(
    val questionType: QuestionType,
    val targetField: String? = null,
    val isCalculation: Boolean = false,
    val isHinglish: Boolean = false
)

object QuestionTypeClassifier {

    fun classifyQuestion(query: String): QuestionClassification {
        val q = query.lowercase().trim()

        val isHinglish = listOf("mera", "meri", "kya", "batao", "dikhao", "kitna", "kitni", "kab", "hai", "bhai").any {
            q.contains(it)
        }

        // 1. Calculation & Aggregation
        if (q.contains("total") || q.contains("sum") || q.contains("average") || q.contains("mean") ||
            q.contains("calculate") || q.contains("best 5") || q.contains("percentage")
        ) {
            val field = when {
                q.contains("amount") || q.contains("total amount") -> "amount"
                q.contains("percentage") || q.contains("percent") || q.contains("marks") -> "percentage"
                else -> "value"
            }
            return QuestionClassification(
                questionType = QuestionType.CALCULATION_AGGREGATION,
                targetField = field,
                isCalculation = true,
                isHinglish = isHinglish
            )
        }

        // 2. Comparison
        if (q.contains("compare") || q.contains("difference") || q.contains("vs") || q.contains("versus")) {
            return QuestionClassification(
                questionType = QuestionType.COMPARISON,
                isHinglish = isHinglish
            )
        }

        // 3. Summarization
        if (q.contains("summarize") || q.contains("summary") || q.contains("explain") || q.contains("overview") || q.contains("key points")) {
            return QuestionClassification(
                questionType = QuestionType.SUMMARIZATION,
                isHinglish = isHinglish
            )
        }

        // 4. Relationship Queries
        if (q.contains("which institution") || q.contains("issued by") || q.contains("which employer") || q.contains("awarded to")) {
            return QuestionClassification(
                questionType = QuestionType.RELATIONSHIP_QUERY,
                isHinglish = isHinglish
            )
        }

        // 5. Exact Identifier Lookup
        if (q.contains("registration number") || q.contains("reg no") ||
            q.contains("roll number") || q.contains("roll no") ||
            q.contains("invoice number") || q.contains("phone number") || q.contains("email")
        ) {
            val field = when {
                q.contains("registration") || q.contains("reg") -> "REGISTRATION_NUMBER"
                q.contains("roll") -> "ROLL_NUMBER"
                q.contains("invoice") -> "INVOICE_NUMBER"
                q.contains("phone") || q.contains("mobile") -> "PHONE_NUMBER"
                q.contains("email") -> "EMAIL"
                else -> "IDENTIFIER"
            }
            return QuestionClassification(
                questionType = QuestionType.EXACT_LOOKUP,
                targetField = field,
                isHinglish = isHinglish
            )
        }

        // 6. Document Search
        if (q.contains("find") || q.contains("search") || q.contains("show") || q.contains("list") ||
            q.contains("marksheet") || q.contains("invoice") || q.contains("resume") || q.contains("aadhaar")
        ) {
            return QuestionClassification(
                questionType = QuestionType.DOCUMENT_SEARCH,
                isHinglish = isHinglish
            )
        }

        // Default to Exact Lookup or Search
        return QuestionClassification(
            questionType = QuestionType.EXACT_LOOKUP,
            isHinglish = isHinglish
        )
    }
}
