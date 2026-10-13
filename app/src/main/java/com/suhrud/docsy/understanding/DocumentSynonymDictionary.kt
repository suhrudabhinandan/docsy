package com.suhrud.docsy.understanding

/**
 * Multilingual synonym dictionary for document understanding and query normalization.
 * Maps English, Hinglish, and Devanagari terms to canonical document categories.
 * Satisfies Phase 7.2 in AUDIT.md.
 */
object DocumentSynonymDictionary {

    val CATEGORY_SYNONYMS: Map<String, List<String>> = mapOf(
        "MARKSHEET" to listOf(
            "marksheet", "mark sheet", "result", "report card", "scorecard",
            "10th marksheet", "12th marksheet", "cbse marksheet", "icse marksheet",
            "statement of marks", "passing certificate", "grade card", "transcript",
            "अंकतालिका", "मार्कशीट", "रिजल्ट", "नतीजा"
        ),
        "CV" to listOf(
            "cv", "resume", "biodata", "curriculum vitae", "profile",
            "bio-data", "बायोडाटा", "रेज़्यूमे", "सीवी"
        ),
        "AADHAAR" to listOf(
            "aadhaar", "aadhar", "uidai", "unique identification",
            "aadhaar card", "aadhar card", "पहचान पत्र", "आधार", "आधार कार्ड"
        ),
        "PAN" to listOf(
            "pan", "pan card", "pancard", "permanent account number",
            "income tax pan", "पैन", "पैन कार्ड"
        ),
        "DRIVING_LICENSE" to listOf(
            "driving license", "driving licence", "dl", "driver license",
            "driving permit", "ड्राइविंग लाइसेंस", "डीएल"
        ),
        "VOTER_ID" to listOf(
            "voter id", "voter card", "epic", "election card",
            "matdata pehchan patra", "मतदाता पहचान पत्र", "वोटर कार्ड", "वोटर आईडी"
        ),
        "PASSPORT" to listOf(
            "passport", "indian passport", "travel document", "पासपोर्ट"
        ),
        "BILL" to listOf(
            "bill", "electricity bill", "bijli bill", "water bill", "gas bill",
            "utility bill", "phone bill", "mobile bill", "broadband bill",
            "invoice", "receipt", "receipts", "बिजली बिल", "पानी बिल", "बिल", "रसीद", "चालान"
        ),
        "ADMIT_CARD" to listOf(
            "admit card", "hall ticket", "entry pass", "pravesh patra",
            "exam admit card", "cat admit card", "jee admit card", "neet admit card",
            "प्रवेश पत्र", "हॉल टिकट", "एडमिट कार्ड"
        ),
        "SALARY_SLIP" to listOf(
            "salary slip", "payslip", "pay slip", "salary certificate",
            "salary statement", "वेतन पर्ची", "सैलरी स्लिप"
        ),
        "BANK_STATEMENT" to listOf(
            "bank statement", "passbook", "account statement", "bank passbook",
            "खाता विवरण", "पासबुक", "बैंक स्टेटमेंट"
        )
    )

    /**
     * Resolves a query or document keyword into its canonical category if matched.
     */
    fun resolveCanonicalCategory(term: String): String? {
        val clean = term.trim().lowercase()
        for ((category, synonyms) in CATEGORY_SYNONYMS) {
            if (synonyms.any { clean == it || clean.contains(it) || it.contains(clean) }) {
                return category
            }
        }
        return null
    }

    /**
     * Returns all synonyms for a given canonical category.
     */
    fun getSynonyms(category: String): List<String> {
        return CATEGORY_SYNONYMS[category.uppercase()] ?: emptyList()
    }
}
