package com.suhrud.docsy.understanding

import java.util.regex.Pattern

data class RunnerUp(
    val type: String,
    val score: Float
)

data class ClassificationCandidate(
    val category: String,
    val score: Float
)

data class ClassificationResult(
    val category: String,
    val confidence: Float,
    val margin: Float,
    val isConfident: Boolean,
    val candidates: List<ClassificationCandidate> = emptyList(),
    val runnersUp: List<RunnerUp> = emptyList()
)

object DocumentClassifier {

    private data class PatternWeight(
        val pattern: String,
        val weight: Float,
        val isRegex: Boolean = false
    )

    private data class CategoryDefinition(
        val category: String,
        val positiveSignals: List<PatternWeight>,
        val negativeSignals: List<PatternWeight>
    )

    private val CATEGORIES: List<CategoryDefinition> = listOf(
        // 1. Aadhaar
        CategoryDefinition(
            category = "Aadhaar",
            positiveSignals = listOf(
                PatternWeight("unique identification authority of india", 6.0f),
                PatternWeight("uidai", 5.0f),
                PatternWeight("mera aadhaar", 5.0f),
                PatternWeight("meri pehchan", 5.0f),
                PatternWeight("aadhaar", 3.0f),
                PatternWeight("aadhar", 2.5f),
                PatternWeight("आधार", 5.0f),
                PatternWeight("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""", 5.0f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f),
                PatternWeight("offer letter", 8.0f),
                PatternWeight("appointment letter", 8.0f),
                PatternWeight("statement of marks", 8.0f)
            )
        ),

        // 2. PAN
        CategoryDefinition(
            category = "PAN",
            positiveSignals = listOf(
                PatternWeight("income tax department", 6.0f),
                PatternWeight("permanent account number", 6.0f),
                PatternWeight("pan card", 5.0f),
                PatternWeight("govt. of india", 2.0f),
                PatternWeight("पैन", 5.0f),
                PatternWeight("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""", 6.0f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f),
                PatternWeight("offer letter", 8.0f),
                PatternWeight("uidai", 8.0f)
            )
        ),

        // 3. driving licence
        CategoryDefinition(
            category = "driving licence",
            positiveSignals = listOf(
                PatternWeight("licence to drive motor vehicles", 7.0f),
                PatternWeight("motor vehicles act", 6.0f),
                PatternWeight("transport department", 5.5f),
                PatternWeight("union of india driving licence", 7.0f),
                PatternWeight("driving licence", 3.5f),
                PatternWeight("driving license", 3.5f),
                PatternWeight("cov", 2.5f),
                PatternWeight("""\b[A-Z]{2}[- ]?\d{2}[- ]?\d{11}\b""", 6.0f, isRegex = true),
                PatternWeight("""\bDL[- ]?\d{10,15}\b""", 6.0f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f),
                PatternWeight("offer letter", 8.0f),
                PatternWeight("appointment letter", 8.0f)
            )
        ),

        // 4. passport
        CategoryDefinition(
            category = "passport",
            positiveSignals = listOf(
                PatternWeight("republic of india passport", 7.0f),
                PatternWeight("passport", 4.0f),
                PatternWeight("pass port", 3.5f),
                PatternWeight("पासपोर्ट", 5.0f),
                PatternWeight("type p", 4.0f),
                PatternWeight("code ind", 4.0f),
                PatternWeight("""\b[A-Z][0-9]{7}\b""", 5.0f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f),
                PatternWeight("offer letter", 8.0f)
            )
        ),

        // 5. voter ID
        CategoryDefinition(
            category = "voter ID",
            positiveSignals = listOf(
                PatternWeight("election commission of india", 6.0f),
                PatternWeight("elector's photo identity card", 6.0f),
                PatternWeight("voter id", 4.0f),
                PatternWeight("epic no", 5.0f),
                PatternWeight("भारत निर्वाचन आयोग", 6.0f),
                PatternWeight("""\b[A-Z]{3}[0-9]{7}\b""", 5.0f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f),
                PatternWeight("offer letter", 8.0f)
            )
        ),

        // 6. ration card
        CategoryDefinition(
            category = "ration card",
            positiveSignals = listOf(
                PatternWeight("ration card", 6.0f),
                PatternWeight("department of food and civil supplies", 6.0f),
                PatternWeight("pds card", 5.0f),
                PatternWeight("nfsa", 5.0f),
                PatternWeight("bpl card", 4.5f),
                PatternWeight("apl card", 4.5f),
                PatternWeight("राशन कार्ड", 6.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 7. e-Shram card
        CategoryDefinition(
            category = "e-Shram card",
            positiveSignals = listOf(
                PatternWeight("e-shram", 6.5f),
                PatternWeight("eshram", 6.5f),
                PatternWeight("ministry of labour and employment", 6.0f),
                PatternWeight("unorganized worker", 5.5f),
                PatternWeight("uan", 4.5f),
                PatternWeight("ई-श्रम", 6.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 8. Ayushman Bharat / ABHA
        CategoryDefinition(
            category = "Ayushman Bharat / ABHA",
            positiveSignals = listOf(
                PatternWeight("ayushman bharat", 6.5f),
                PatternWeight("pm-jay", 6.5f),
                PatternWeight("abha number", 6.0f),
                PatternWeight("national health authority", 6.0f),
                PatternWeight("ayushman card", 6.0f),
                PatternWeight("आयुष्मान भारत", 6.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 9. MARKSHEET
        CategoryDefinition(
            category = "MARKSHEET",
            positiveSignals = listOf(
                PatternWeight("statement of marks", 6.0f),
                PatternWeight("mark sheet", 5.5f),
                PatternWeight("marksheet", 5.5f),
                PatternWeight("board of secondary education", 5.0f),
                PatternWeight("central board of secondary education", 6.0f),
                PatternWeight("grade card", 5.0f),
                PatternWeight("semester examination", 5.0f),
                PatternWeight("controller of examinations", 4.5f),
                PatternWeight("total marks", 3.5f),
                PatternWeight("roll no", 3.0f),
                PatternWeight("cgpa", 3.0f),
                PatternWeight("sgpa", 3.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 10.0f),
                PatternWeight("resume", 10.0f),
                PatternWeight("work experience", 8.0f),
                PatternWeight("offer letter", 10.0f),
                PatternWeight("appointment letter", 10.0f)
            )
        ),

        // 10. degree/certificates
        CategoryDefinition(
            category = "degree/certificates",
            positiveSignals = listOf(
                PatternWeight("degree certificate", 6.0f),
                PatternWeight("bachelor of", 5.0f),
                PatternWeight("master of", 5.0f),
                PatternWeight("doctor of philosophy", 5.0f),
                PatternWeight("provisional certificate", 5.5f),
                PatternWeight("conferred upon", 5.0f),
                PatternWeight("convocation", 4.5f),
                PatternWeight("diploma in", 4.5f),
                PatternWeight("has successfully completed", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 11. CV
        CategoryDefinition(
            category = "CV",
            positiveSignals = listOf(
                PatternWeight("curriculum vitae", 7.0f),
                PatternWeight("resume", 6.5f),
                PatternWeight("biodata", 5.0f),
                PatternWeight("work experience", 5.0f),
                PatternWeight("professional experience", 5.0f),
                PatternWeight("technical skills", 5.0f),
                PatternWeight("career objective", 4.5f),
                PatternWeight("education qualification", 3.5f),
                PatternWeight("key achievements", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("statement of marks", 6.0f),
                PatternWeight("income tax department", 6.0f),
                PatternWeight("unique identification authority", 6.0f)
            )
        ),

        // 12. offer/salary/experience letter
        CategoryDefinition(
            category = "offer/salary/experience letter",
            positiveSignals = listOf(
                PatternWeight("offer letter", 6.0f),
                PatternWeight("letter of appointment", 6.0f),
                PatternWeight("employment contract", 5.5f),
                PatternWeight("salary certificate", 6.0f),
                PatternWeight("experience letter", 6.0f),
                PatternWeight("relieving letter", 6.0f),
                PatternWeight("service certificate", 5.5f),
                PatternWeight("ctc per annum", 4.5f),
                PatternWeight("date of joining", 4.0f),
                PatternWeight("dear Mr.", 3.0f),
                PatternWeight("dear Ms.", 3.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("statement of marks", 6.0f),
                PatternWeight("curriculum vitae", 5.0f)
            )
        ),

        // 13. bank statement
        CategoryDefinition(
            category = "bank statement",
            positiveSignals = listOf(
                PatternWeight("account statement", 6.0f),
                PatternWeight("bank statement", 6.0f),
                PatternWeight("statement of account", 6.0f),
                PatternWeight("transaction history", 5.0f),
                PatternWeight("opening balance", 4.5f),
                PatternWeight("closing balance", 4.5f),
                PatternWeight("withdrawal", 3.5f),
                PatternWeight("deposit", 3.5f),
                PatternWeight("ifsc code", 4.0f),
                PatternWeight("chq no", 3.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 14. ITR
        CategoryDefinition(
            category = "ITR",
            positiveSignals = listOf(
                PatternWeight("income tax return", 6.0f),
                PatternWeight("itr-v", 6.5f),
                PatternWeight("indian income tax return acknowledgement", 7.0f),
                PatternWeight("assessment year", 4.5f),
                PatternWeight("gross total income", 4.5f),
                PatternWeight("tax payable", 4.0f),
                PatternWeight("acknowledgement number", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 15. Form 16
        CategoryDefinition(
            category = "Form 16",
            positiveSignals = listOf(
                PatternWeight("form no. 16", 7.0f),
                PatternWeight("form 16", 6.5f),
                PatternWeight("certificate under section 203 of the income-tax act", 7.0f),
                PatternWeight("tax deducted at source", 5.0f),
                PatternWeight("tan of employer", 5.0f),
                PatternWeight("pan of employee", 5.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 16. utility and mobile bills
        CategoryDefinition(
            category = "utility and mobile bills",
            positiveSignals = listOf(
                PatternWeight("electricity bill", 6.0f),
                PatternWeight("water bill", 6.0f),
                PatternWeight("gas bill", 6.0f),
                PatternWeight("mobile bill", 6.0f),
                PatternWeight("postpaid bill", 5.5f),
                PatternWeight("broadband bill", 5.5f),
                PatternWeight("consumer number", 4.5f),
                PatternWeight("units consumed", 4.5f),
                PatternWeight("bill date", 3.5f),
                PatternWeight("due date", 3.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 17. insurance
        CategoryDefinition(
            category = "insurance",
            positiveSignals = listOf(
                PatternWeight("insurance policy", 6.0f),
                PatternWeight("policy schedule", 5.5f),
                PatternWeight("life insurance", 5.5f),
                PatternWeight("health insurance", 5.5f),
                PatternWeight("motor insurance", 5.5f),
                PatternWeight("policy number", 4.5f),
                PatternWeight("sum assured", 4.5f),
                PatternWeight("premium amount", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 18. vehicle RC/PUC
        CategoryDefinition(
            category = "vehicle RC/PUC",
            positiveSignals = listOf(
                PatternWeight("certificate of registration", 6.0f),
                PatternWeight("registration certificate", 6.0f),
                PatternWeight("vehicle rc", 5.5f),
                PatternWeight("pollution under control certificate", 6.0f),
                PatternWeight("puc certificate", 6.0f),
                PatternWeight("chassis no", 4.5f),
                PatternWeight("engine no", 4.5f),
                PatternWeight("maker class", 4.0f),
                PatternWeight("""\b[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}\b""", 4.5f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 19. rent agreement
        CategoryDefinition(
            category = "rent agreement",
            positiveSignals = listOf(
                PatternWeight("rent agreement", 6.5f),
                PatternWeight("tenancy agreement", 6.5f),
                PatternWeight("lease agreement", 6.0f),
                PatternWeight("leave and license agreement", 6.5f),
                PatternWeight("landlord", 4.5f),
                PatternWeight("tenant", 4.5f),
                PatternWeight("lessor", 4.0f),
                PatternWeight("lessee", 4.0f),
                PatternWeight("monthly rent", 4.0f),
                PatternWeight("security deposit", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 20. loan
        CategoryDefinition(
            category = "loan",
            positiveSignals = listOf(
                PatternWeight("loan agreement", 6.5f),
                PatternWeight("loan sanction letter", 6.5f),
                PatternWeight("loan account statement", 6.0f),
                PatternWeight("key fact statement", 5.5f),
                PatternWeight("sanctioned amount", 4.5f),
                PatternWeight("emi amount", 4.5f),
                PatternWeight("rate of interest", 4.0f),
                PatternWeight("loan account no", 4.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 21. admit card
        CategoryDefinition(
            category = "admit card",
            positiveSignals = listOf(
                PatternWeight("admit card", 6.5f),
                PatternWeight("hall ticket", 6.5f),
                PatternWeight("examination hall ticket", 6.0f),
                PatternWeight("test center", 4.5f),
                PatternWeight("reporting time", 4.5f),
                PatternWeight("gate closure time", 4.5f),
                PatternWeight("roll number slip", 5.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 22. fee receipt
        CategoryDefinition(
            category = "fee receipt",
            positiveSignals = listOf(
                PatternWeight("fee receipt", 6.5f),
                PatternWeight("tuition fee receipt", 6.5f),
                PatternWeight("payment receipt", 5.0f),
                PatternWeight("fee voucher", 5.5f),
                PatternWeight("fee structure", 4.0f),
                PatternWeight("received with thanks from", 4.5f),
                PatternWeight("fee head", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 23. ticket
        CategoryDefinition(
            category = "ticket",
            positiveSignals = listOf(
                PatternWeight("electronic reservation slip", 6.5f),
                PatternWeight("irctc e-ticket", 6.5f),
                PatternWeight("flight ticket", 6.0f),
                PatternWeight("boarding pass", 6.5f),
                PatternWeight("bus ticket", 6.0f),
                PatternWeight("movie ticket", 5.5f),
                PatternWeight("pnr number", 5.0f),
                PatternWeight("pnr no", 5.0f),
                PatternWeight("seat / berth", 4.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 24. invoice
        CategoryDefinition(
            category = "invoice",
            positiveSignals = listOf(
                PatternWeight("tax invoice", 6.5f),
                PatternWeight("commercial invoice", 6.0f),
                PatternWeight("bill of supply", 5.5f),
                PatternWeight("invoice number", 5.0f),
                PatternWeight("invoice no", 5.0f),
                PatternWeight("gstin", 4.0f),
                PatternWeight("total amount payable", 4.0f),
                PatternWeight("hsn/sac", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 25. warranty
        CategoryDefinition(
            category = "warranty",
            positiveSignals = listOf(
                PatternWeight("warranty card", 6.5f),
                PatternWeight("guarantee card", 6.5f),
                PatternWeight("limited warranty", 6.0f),
                PatternWeight("warranty certificate", 6.0f),
                PatternWeight("warranty period", 4.5f),
                PatternWeight("terms of warranty", 4.5f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 26. prescription
        CategoryDefinition(
            category = "prescription",
            positiveSignals = listOf(
                PatternWeight("medical prescription", 6.5f),
                PatternWeight("rx", 5.0f),
                PatternWeight("doctor's prescription", 6.5f),
                PatternWeight("dosage", 4.5f),
                PatternWeight("once daily", 4.0f),
                PatternWeight("tab.", 3.5f),
                PatternWeight("syrup", 3.5f),
                PatternWeight("diagnosis:", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 27. lab report
        CategoryDefinition(
            category = "lab report",
            positiveSignals = listOf(
                PatternWeight("laboratory report", 6.5f),
                PatternWeight("diagnostic test report", 6.5f),
                PatternWeight("pathology report", 6.5f),
                PatternWeight("blood test report", 6.0f),
                PatternWeight("observed value", 4.5f),
                PatternWeight("reference range", 4.5f),
                PatternWeight("test name", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 28. GST
        CategoryDefinition(
            category = "GST",
            positiveSignals = listOf(
                PatternWeight("form gst reg-06", 7.0f),
                PatternWeight("registration certificate under gst", 7.0f),
                PatternWeight("goods and services tax registration", 6.5f),
                PatternWeight("gstin", 5.0f),
                PatternWeight("constitution of business", 4.5f),
                PatternWeight("""\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b""", 5.5f, isRegex = true)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        ),

        // 29. spreadsheet
        CategoryDefinition(
            category = "spreadsheet",
            positiveSignals = listOf(
                PatternWeight(".xlsx", 5.0f),
                PatternWeight(".csv", 5.0f),
                PatternWeight("comma separated values", 6.0f),
                PatternWeight("worksheet", 4.5f),
                PatternWeight("sheet1", 4.0f)
            ),
            negativeSignals = listOf(
                PatternWeight("curriculum vitae", 8.0f),
                PatternWeight("resume", 8.0f)
            )
        )
    )

    fun getUseCasesForCategory(category: String, text: String = ""): List<String> {
        val cat = category.lowercase()
        val lowerText = text.lowercase()

        return when {
            cat.contains("aadhaar") -> listOf("Identity Proof", "Address Proof", "Date of Birth Proof", "Govt KYC")
            cat.contains("pan") -> listOf("Identity Proof", "Taxpayer Identification", "Financial KYC", "Bank Account Opening")
            cat.contains("passport") -> listOf("International Travel Pass", "Identity Proof", "Nationality Proof", "Address Proof")
            cat.contains("driving") || cat.contains("dl") -> listOf("Vehicle Driving Authorisation", "Identity Proof", "Address Proof", "Age Proof")
            cat.contains("voter") || cat.contains("epic") -> listOf("Electoral Voting Identity Card", "Identity Proof", "Address Proof")
            cat.contains("ration") -> listOf("Family Identity Proof", "Address Proof", "Subsidised Food Grain Ration")
            cat.contains("e-shram") || cat.contains("eshram") -> listOf("Unorganised Worker Identification", "Government Welfare Scheme")
            cat.contains("ayushman") || cat.contains("abha") -> listOf("Health ID Card", "Ayushman Bharat Healthcare Benefits")
            cat.contains("vehicle") || cat.contains("rc") || cat.contains("puc") -> listOf("Vehicle Ownership Certificate", "Traffic Pollution & Fitness Compliance")
            cat.contains("pension") || cat.contains("disability") -> listOf("Pensioner Proof", "Govt Welfare Concession")
            cat.contains("marksheet") || cat.contains("10/12/semester") || cat.contains("transcript") -> listOf("Academic Qualifications Proof", "Result Verification (Pass/Fail)", "College & Job Eligibility", "Age Proof")
            cat.contains("admit card") || cat.contains("competitive") -> listOf("Exam Entry Pass / Hall Ticket", "Entrance Exam Verification", "All India Rank Proof")
            cat.contains("degree") || cat.contains("certificate") -> listOf("Higher Qualification Proof", "Degree Verification")
            cat.contains("cv") || cat.contains("resume") -> listOf("Job Application Resume", "Professional Background & Skills")
            cat.contains("offer") || cat.contains("appointment") || cat.contains("experience") -> listOf("Job Offer Proof", "Employment Verification", "Designation & CTC Proof")
            cat.contains("payslip") || cat.contains("salary") -> listOf("Monthly Salary Income Proof", "Loan & Credit Application", "Financial KYC")
            cat.contains("form 16") -> listOf("Annual Income & Tax Deducted Proof", "Income Tax Return Filing")
            cat.contains("utility") || cat.contains("bill") -> when {
                lowerText.contains("electricity") || lowerText.contains("kwh") || lowerText.contains("bses") || lowerText.contains("bescom") -> listOf("Address Proof", "Electricity Bill Payment Due", "Utility Expense Tracking")
                lowerText.contains("water") || lowerText.contains("jal board") -> listOf("Address Proof", "Water Bill Payment Due", "Utility Expense Tracking")
                lowerText.contains("gas") || lowerText.contains("lpg") || lowerText.contains("png") -> listOf("Address Proof", "Gas Connection Proof", "Utility Expense Tracking")
                lowerText.contains("mobile") || lowerText.contains("broadband") || lowerText.contains("postpaid") -> listOf("Address Proof", "Telecom Expense Tracking")
                else -> listOf("Address Proof", "Utility Expense Tracking", "Bill Payment Due Reminder")
            }
            cat.contains("bank") || cat.contains("statement") -> listOf("Financial Statement", "Bank Balance Proof", "Income Verification", "Loan Application")
            cat.contains("loan") || cat.contains("emi") -> listOf("Loan Sanction Letter", "EMI Repayment Tracking", "Credit Facility Proof")
            cat.contains("itr") || cat.contains("tax return") -> listOf("Income Tax Return Acknowledgement", "Official Income Proof", "Visa / High Loan Verification")
            cat.contains("insurance") -> listOf("Insurance Coverage Policy", "Health / Motor Claim Proof")
            cat.contains("rent") || cat.contains("lease") -> listOf("Address Proof", "Rental Lease Contract", "HRA Tax Exemption Claim")
            cat.contains("prescription") || cat.contains("medical") -> listOf("Medical Prescription", "Pharmacy Medicine Purchase")
            cat.contains("lab") || cat.contains("diagnostic") -> listOf("Health Diagnostic Report", "Medical Pathology Results")
            cat.contains("ticket") || cat.contains("boarding") -> listOf("Travel Reservation Ticket", "Proof of Journey", "Travel Expense Claim")
            cat.contains("gst") -> listOf("Business GST Registration", "Tax Compliance")
            cat.contains("fee") -> listOf("Education Fee Payment Receipt", "Tax / Expense Exemption")
            cat.contains("invoice") -> listOf("Tax Invoice", "Proof of Purchase", "Expense Claim")
            cat.contains("warranty") -> listOf("Product Warranty Card", "Service Claim Proof")
            else -> listOf("Document Proof", "General Records")
        }
    }

    fun classify(text: String, fileName: String = ""): ClassificationResult {
        val rawInput = (text + "\n" + fileName)
        val lowerInput = rawInput.lowercase()

        val categoryScores = mutableMapOf<String, Float>()

        for (def in CATEGORIES) {
            var score = 0.0f

            for (sig in def.positiveSignals) {
                if (sig.isRegex) {
                    val p = Pattern.compile(sig.pattern, Pattern.CASE_INSENSITIVE)
                    if (p.matcher(rawInput).find()) {
                        score += sig.weight
                    }
                } else {
                    if (lowerInput.contains(sig.pattern.lowercase())) {
                        score += sig.weight
                    }
                }
            }

            for (neg in def.negativeSignals) {
                if (neg.isRegex) {
                    val p = Pattern.compile(neg.pattern, Pattern.CASE_INSENSITIVE)
                    if (p.matcher(rawInput).find()) {
                        score -= neg.weight
                    }
                } else {
                    if (lowerInput.contains(neg.pattern.lowercase())) {
                        score -= neg.weight
                    }
                }
            }

            if (score > 0f) {
                categoryScores[def.category] = score
            }
        }

        // HARD RULE: A CV or employment letter that merely mentions marks can NEVER become a marksheet!
        val isCvOrLetter = lowerInput.contains("curriculum vitae") ||
                lowerInput.contains("resume") ||
                lowerInput.contains("work experience") ||
                lowerInput.contains("professional experience") ||
                lowerInput.contains("offer letter") ||
                lowerInput.contains("letter of appointment") ||
                lowerInput.contains("experience letter") ||
                lowerInput.contains("relieving letter")

        val hasBoardOrResultHeader = lowerInput.contains("statement of marks") ||
                lowerInput.contains("board of secondary education") ||
                lowerInput.contains("mark sheet") ||
                lowerInput.contains("marksheet") ||
                lowerInput.contains("university examination") ||
                lowerInput.contains("grade card") ||
                lowerInput.contains("transcript of marks")

        val hasMarksTable = (lowerInput.contains("subject") || lowerInput.contains("code")) &&
                (lowerInput.contains("marks") || lowerInput.contains("grade") || lowerInput.contains("cgpa") || lowerInput.contains("max marks"))

        if (isCvOrLetter && (!hasBoardOrResultHeader || !hasMarksTable)) {
            categoryScores.remove("marksheet 10/12/semester")
        }

        if (categoryScores.containsKey("marksheet 10/12/semester")) {
            if (!hasBoardOrResultHeader && !hasMarksTable) {
                categoryScores.remove("marksheet 10/12/semester")
            }
        }

        val sorted = categoryScores.toList().sortedByDescending { it.second }
        val allCandidates = sorted.map { ClassificationCandidate(it.first, it.second) }

        val top1 = sorted.getOrNull(0)
        val top2 = sorted.getOrNull(1)
        val top3 = sorted.getOrNull(2)

        val runnersUp = listOfNotNull(top2, top3).map { RunnerUp(it.first, it.second) }

        val minThreshold = 3.0f
        if (top1 == null || top1.second < minThreshold) {
            val topRunnersUp = sorted.take(2).map { RunnerUp(it.first, it.second) }
            return ClassificationResult(
                category = "other",
                confidence = 0.0f,
                margin = 0.0f,
                isConfident = false,
                candidates = allCandidates,
                runnersUp = topRunnersUp
            )
        }

        val margin = top1.second - (top2?.second ?: 0.0f)
        val confidence = (top1.second / (top1.second + (top2?.second ?: 0.0f) + 2.0f)).coerceIn(0.40f, 0.99f)

        return ClassificationResult(
            category = top1.first,
            confidence = confidence,
            margin = margin,
            isConfident = margin >= 1.5f,
            candidates = allCandidates,
            runnersUp = runnersUp
        )
    }
}
