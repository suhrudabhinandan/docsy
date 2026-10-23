package com.suhrud.docsy.classification

import com.suhrud.docsy.extractor.api.IDocumentClassifier
import com.suhrud.docsy.understanding.DocumentClassifier
import java.io.File

/**
 * General document categories in Docsy.
 */
enum class GeneralCategory {
    ACADEMIC,
    IDENTITY,
    FINANCIAL,
    EMPLOYMENT,
    LEGAL,
    MEDICAL,
    GOVERNMENT,
    BUSINESS,
    PERSONAL,
    TECHNICAL,
    OTHER
}

data class ClassificationEvidence(
    val signal: String,
    val weight: Float,
    val matchedLine: String? = null
)

data class CandidateClassification(
    val category: GeneralCategory,
    val specificType: String,
    val score: Float
)

data class HierarchicalClassificationResult(
    val documentPath: String = "",
    val generalCategory: GeneralCategory,
    val specificType: String,
    val confidence: Float, // 0.0f to 1.0f
    val confidenceMethod: String = "HEURISTIC_HYBRID",
    val isConfident: Boolean,
    val candidates: List<CandidateClassification> = emptyList(),
    val evidence: List<ClassificationEvidence> = emptyList(),
    val classifierVersion: String = "2.0-hierarchical",
    val timestamp: Long = System.currentTimeMillis()
)

object HierarchicalDocumentClassifier : IDocumentClassifier {

    override fun classifyDocument(file: File, extractedText: String): String {
        val result = classify(extractedText, file.name)
        return "${result.generalCategory.name}:${result.specificType}"
    }

    fun classify(text: String, fileName: String = "", filePath: String = ""): HierarchicalClassificationResult {
        if (text.isBlank() && fileName.isBlank()) {
            return HierarchicalClassificationResult(
                documentPath = filePath,
                generalCategory = GeneralCategory.OTHER,
                specificType = "UNKNOWN",
                confidence = 0.0f,
                isConfident = false,
                confidenceMethod = "HEURISTIC_HYBRID"
            )
        }

        // Delegate to underlying pattern matcher
        val result = DocumentClassifier.classify(text, fileName)
        val rawCat = result.category.lowercase()

        val generalCat = mapToGeneralCategory(rawCat)
        val specificType = mapToSpecificType(rawCat)

        val evidenceList = mutableListOf<ClassificationEvidence>()
        val textLines = text.lines().take(50)
        for (line in textLines) {
            val lowerLine = line.lowercase()
            if (lowerLine.contains("statement of marks") || lowerLine.contains("marksheet") || lowerLine.contains("board of secondary")) {
                evidenceList.add(ClassificationEvidence("academic_header", 6.0f, line.trim()))
            } else if (lowerLine.contains("tax invoice") || lowerLine.contains("invoice no") || lowerLine.contains("bill of supply")) {
                evidenceList.add(ClassificationEvidence("financial_invoice_header", 6.5f, line.trim()))
            } else if (lowerLine.contains("curriculum vitae") || lowerLine.contains("resume") || lowerLine.contains("work experience")) {
                evidenceList.add(ClassificationEvidence("employment_resume_header", 6.5f, line.trim()))
            } else if (lowerLine.contains("unique identification authority") || lowerLine.contains("aadhaar") || lowerLine.contains("income tax department")) {
                evidenceList.add(ClassificationEvidence("identity_gov_header", 6.0f, line.trim()))
            }
        }

        val candidates = result.candidates.map { candidate ->
            val candidateCat = mapToGeneralCategory(candidate.category.lowercase())
            val candidateType = mapToSpecificType(candidate.category.lowercase())
            CandidateClassification(candidateCat, candidateType, candidate.score)
        }

        val isConfident = result.isConfident && result.confidence >= 0.45f
        val finalGeneralCategory = if (isConfident) generalCat else GeneralCategory.OTHER
        val finalSpecificType = if (isConfident) specificType else "UNKNOWN"

        return HierarchicalClassificationResult(
            documentPath = filePath,
            generalCategory = finalGeneralCategory,
            specificType = finalSpecificType,
            confidence = if (isConfident) result.confidence else 0.0f,
            confidenceMethod = "HEURISTIC_HYBRID",
            isConfident = isConfident,
            candidates = candidates,
            evidence = evidenceList
        )
    }

    fun mapToGeneralCategory(categoryStr: String): GeneralCategory {
        val cat = categoryStr.lowercase()
        return when {
            cat.contains("marksheet") || cat.contains("transcript") || cat.contains("admit card") ||
            cat.contains("degree") || cat.contains("fee receipt") -> GeneralCategory.ACADEMIC

            cat.contains("aadhaar") || cat.contains("pan") || cat.contains("passport") ||
            cat.contains("driving") || cat.contains("voter") || cat.contains("ration") -> GeneralCategory.IDENTITY

            cat.contains("invoice") || cat.contains("bill") || cat.contains("bank statement") ||
            cat.contains("itr") || cat.contains("form 16") || cat.contains("loan") || cat.contains("insurance") -> GeneralCategory.FINANCIAL

            cat.contains("cv") || cat.contains("resume") || cat.contains("offer") ||
            cat.contains("appointment") || cat.contains("experience") -> GeneralCategory.EMPLOYMENT

            cat.contains("rent") || cat.contains("lease") || cat.contains("agreement") || cat.contains("contract") -> GeneralCategory.LEGAL

            cat.contains("prescription") || cat.contains("lab report") || cat.contains("medical") -> GeneralCategory.MEDICAL

            cat.contains("gst") || cat.contains("e-shram") || cat.contains("vehicle") || cat.contains("puc") -> GeneralCategory.GOVERNMENT

            cat.contains("ticket") || cat.contains("warranty") -> GeneralCategory.BUSINESS

            cat.contains("spreadsheet") || cat.contains("csv") || cat.contains("xlsx") -> GeneralCategory.TECHNICAL

            else -> GeneralCategory.OTHER
        }
    }

    fun mapToSpecificType(categoryStr: String): String {
        val cat = categoryStr.lowercase()
        return when {
            cat.contains("marksheet") -> "MARKSHEET"
            cat.contains("degree") -> "DEGREE_CERTIFICATE"
            cat.contains("admit card") -> "ADMIT_CARD"
            cat.contains("fee receipt") -> "FEE_RECEIPT"
            cat.contains("aadhaar") -> "AADHAAR"
            cat.contains("pan") -> "PAN"
            cat.contains("passport") -> "PASSPORT"
            cat.contains("driving") -> "DRIVING_LICENCE"
            cat.contains("voter") -> "VOTER_ID"
            cat.contains("invoice") -> "INVOICE"
            cat.contains("bill") -> "UTILITY_BILL"
            cat.contains("bank statement") -> "BANK_STATEMENT"
            cat.contains("itr") -> "ITR"
            cat.contains("form 16") -> "FORM_16"
            cat.contains("cv") || cat.contains("resume") -> "RESUME"
            cat.contains("offer") -> "OFFER_LETTER"
            cat.contains("rent") || cat.contains("lease") -> "RENT_AGREEMENT"
            cat.contains("prescription") -> "PRESCRIPTION"
            cat.contains("lab report") -> "LAB_REPORT"
            cat.contains("gst") -> "GST_CERTIFICATE"
            cat.contains("vehicle") || cat.contains("puc") -> "VEHICLE_RC"
            cat.contains("spreadsheet") -> "SPREADSHEET"
            else -> "UNKNOWN"
        }
    }
}
