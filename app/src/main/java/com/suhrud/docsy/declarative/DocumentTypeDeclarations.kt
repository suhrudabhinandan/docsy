package com.suhrud.docsy.declarative

data class Signal(
    val pattern: String,
    val weight: Float,
    val isStructural: Boolean = false,
    val isNegative: Boolean = false
)

data class FieldSchema(
    val key: String,
    val labelIds: List<String>,
    val dataType: String, // STRING, NUMBER, DATE, PERCENTAGE
    val required: Boolean = false,
    val validatorIds: List<String> = emptyList()
)

data class TableSchema(
    val tableKey: String,
    val headerSynonyms: Map<String, List<String>>,
    val numericColumns: List<String>,
    val scoringRules: String? = null
)

data class OwnerRule(
    val candidateFields: List<String>,
    val excludeRoles: List<String> = listOf("father", "guardian", "teacher", "issuer")
)

data class DocumentTypeDefinition(
    val typeId: String,
    val displayName: String,
    val useCases: List<String> = emptyList(),
    val positiveSignals: List<Signal>,
    val negativeSignals: List<Signal> = emptyList(),
    val fields: List<FieldSchema>,
    val tables: List<TableSchema> = emptyList(),
    val ownerRule: OwnerRule = OwnerRule(listOf("name", "student name", "holder name", "candidate name", "customer name"))
)

object DocumentTypeRegistry {

    private val registry = mutableMapOf<String, DocumentTypeDefinition>()

    init {
        registerDefaultTypes()
    }

    fun registerType(definition: DocumentTypeDefinition) {
        registry[definition.typeId] = definition
    }

    fun getType(typeId: String): DocumentTypeDefinition? = registry[typeId] ?: registry.values.firstOrNull {
        it.typeId.equals(typeId, ignoreCase = true) || it.displayName.equals(typeId, ignoreCase = true)
    }

    fun getAllTypes(): List<DocumentTypeDefinition> = registry.values.toList()

    private fun registerDefaultTypes() {
        // 1. AADHAAR
        registerType(
            DocumentTypeDefinition(
                typeId = "AADHAAR",
                displayName = "Aadhaar Card",
                useCases = listOf("Identity Proof", "Address Proof", "Date of Birth Proof", "Govt KYC"),
                positiveSignals = listOf(
                    Signal("unique identification authority of india", 5.0f, isStructural = true),
                    Signal("uidai", 4.0f),
                    Signal("mera aadhaar", 4.0f),
                    Signal("aadhaar", 3.0f)
                ),
                fields = listOf(
                    FieldSchema("aadhaar_number", listOf("aadhaar no", "aadhaar number", "uid"), "STRING", required = true),
                    FieldSchema("name", listOf("name", "cardholder name"), "STRING", required = true),
                    FieldSchema("dob", listOf("dob", "date of birth", "yob", "year of birth"), "DATE"),
                    FieldSchema("gender", listOf("gender", "male", "female"), "STRING"),
                    FieldSchema("address", listOf("address"), "STRING")
                ),
                ownerRule = OwnerRule(listOf("name", "cardholder name"))
            )
        )

        // 2. PAN
        registerType(
            DocumentTypeDefinition(
                typeId = "PAN",
                displayName = "PAN Card",
                useCases = listOf("Identity Proof", "Taxpayer ID", "Financial KYC", "Bank Account Opening"),
                positiveSignals = listOf(
                    Signal("income tax department", 5.0f, isStructural = true),
                    Signal("permanent account number", 5.0f, isStructural = true),
                    Signal("pan card", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("pan_number", listOf("pan", "pan no", "permanent account number"), "STRING", required = true),
                    FieldSchema("name", listOf("name", "cardholder name"), "STRING", required = true),
                    FieldSchema("father_name", listOf("father's name", "father name"), "STRING"),
                    FieldSchema("dob", listOf("date of birth", "dob"), "DATE")
                ),
                ownerRule = OwnerRule(listOf("name", "cardholder name"))
            )
        )

        // 3. PASSPORT
        registerType(
            DocumentTypeDefinition(
                typeId = "PASSPORT",
                displayName = "Indian Passport",
                useCases = listOf("International Travel Pass", "Identity Proof", "Nationality Proof", "Address Proof"),
                positiveSignals = listOf(
                    Signal("republic of india passport", 5.0f, isStructural = true),
                    Signal("passport no", 4.0f),
                    Signal("type p", 3.0f)
                ),
                fields = listOf(
                    FieldSchema("passport_number", listOf("passport no", "passport number"), "STRING", required = true),
                    FieldSchema("given_name", listOf("given name", "name"), "STRING"),
                    FieldSchema("surname", listOf("surname"), "STRING"),
                    FieldSchema("nationality", listOf("nationality"), "STRING"),
                    FieldSchema("dob", listOf("date of birth", "dob"), "DATE"),
                    FieldSchema("expiry_date", listOf("date of expiry", "expiry date"), "DATE")
                ),
                ownerRule = OwnerRule(listOf("given_name", "name"))
            )
        )

        // 4. DRIVING LICENCE
        registerType(
            DocumentTypeDefinition(
                typeId = "DRIVING_LICENCE",
                displayName = "Driving Licence",
                useCases = listOf("Vehicle Driving Authorisation", "Identity Proof", "Address Proof", "Age Proof"),
                positiveSignals = listOf(
                    Signal("licence to drive motor vehicles", 5.0f, isStructural = true),
                    Signal("transport department", 4.0f),
                    Signal("driving licence", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("dl_number", listOf("dl no", "driving licence no"), "STRING", required = true),
                    FieldSchema("name", listOf("name", "holder name"), "STRING"),
                    FieldSchema("valid_till", listOf("valid till", "expiry date"), "DATE"),
                    FieldSchema("cov", listOf("cov", "class of vehicle"), "STRING")
                ),
                ownerRule = OwnerRule(listOf("name", "holder name"))
            )
        )

        // 5. MARKSHEET
        registerType(
            DocumentTypeDefinition(
                typeId = "MARKSHEET",
                displayName = "Marksheet / Transcript",
                useCases = listOf("Academic Qualifications Proof", "Result Verification", "College & Job Eligibility", "Age Proof"),
                positiveSignals = listOf(
                    Signal("statement of marks", 4.0f, isStructural = true),
                    Signal("marksheet", 4.0f, isStructural = true),
                    Signal("roll no", 3.0f),
                    Signal("marks obtained", 3.0f, isStructural = true),
                    Signal("max marks", 2.5f)
                ),
                negativeSignals = listOf(
                    Signal("tax invoice", 5.0f, isNegative = true),
                    Signal("curriculum vitae", 5.0f, isNegative = true)
                ),
                fields = listOf(
                    FieldSchema("name", listOf("name", "candidate name", "student name"), "STRING", required = true),
                    FieldSchema("class", listOf("class", "course", "grade", "standard", "semester"), "STRING"),
                    FieldSchema("board", listOf("board", "university", "institution", "school"), "STRING"),
                    FieldSchema("roll_number", listOf("roll no", "roll number", "reg no", "seat no"), "STRING"),
                    FieldSchema("percentage", listOf("percentage", "aggregate", "grand total"), "PERCENTAGE"),
                    FieldSchema("cgpa_sgpa", listOf("cgpa", "sgpa", "cpi", "spi"), "STRING"),
                    FieldSchema("result", listOf("result", "status", "division"), "STRING")
                ),
                tables = listOf(
                    TableSchema(
                        tableKey = "marks_table",
                        headerSynonyms = mapOf(
                            "subject" to listOf("subject", "paper", "course"),
                            "marks" to listOf("marks", "obtained", "score"),
                            "max_marks" to listOf("max", "maximum", "out of"),
                            "grade" to listOf("grade", "credits", "gp")
                        ),
                        numericColumns = listOf("marks", "max_marks")
                    )
                ),
                ownerRule = OwnerRule(listOf("name", "candidate name", "student name"))
            )
        )

        // 6. COMPETITIVE EXAM
        registerType(
            DocumentTypeDefinition(
                typeId = "COMPETITIVE_EXAM",
                displayName = "Competitive Exam Scorecard / Admit Card",
                useCases = listOf("Entrance Exam Scorecard", "College Admission Eligibility", "All India Rank Proof", "Exam Entry Pass"),
                positiveSignals = listOf(
                    Signal("nta score", 4.5f, isStructural = true),
                    Signal("percentile score", 4.5f, isStructural = true),
                    Signal("all india rank", 4.0f),
                    Signal("gate score", 4.5f),
                    Signal("admit card", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("candidate_name", listOf("candidate name", "name", "student name"), "STRING"),
                    FieldSchema("roll_no", listOf("roll no", "application no", "registration no"), "STRING"),
                    FieldSchema("exam_name", listOf("exam name", "examination"), "STRING"),
                    FieldSchema("score_percentile", listOf("percentile", "nta score", "gate score", "marks"), "STRING"),
                    FieldSchema("rank", listOf("all india rank", "air", "category rank"), "STRING"),
                    FieldSchema("status", listOf("qualifying status", "result"), "STRING")
                ),
                ownerRule = OwnerRule(listOf("candidate_name", "candidate name", "name"))
            )
        )

        // 7. OFFER / APPOINTMENT LETTER
        registerType(
            DocumentTypeDefinition(
                typeId = "OFFER_LETTER",
                displayName = "Job Offer / Appointment Letter",
                useCases = listOf("Job Offer Proof", "Employment Verification", "Designation & CTC Proof"),
                positiveSignals = listOf(
                    Signal("offer letter", 5.0f, isStructural = true),
                    Signal("letter of appointment", 5.0f, isStructural = true),
                    Signal("employment contract", 4.0f),
                    Signal("ctc per annum", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("candidate_name", listOf("dear", "candidate name", "employee name", "name"), "STRING"),
                    FieldSchema("employer_name", listOf("company name", "organization", "employer"), "STRING"),
                    FieldSchema("designation", listOf("designation", "position", "role"), "STRING"),
                    FieldSchema("joining_date", listOf("date of joining", "joining date", "start date"), "DATE"),
                    FieldSchema("ctc", listOf("ctc", "salary", "remuneration"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("candidate_name", "name"))
            )
        )

        // 8. SALARY SLIP / PAYSLIP
        registerType(
            DocumentTypeDefinition(
                typeId = "PAYSLIP",
                displayName = "Salary Slip / Payslip",
                useCases = listOf("Monthly Income Proof", "Loan & Credit Card Application", "Financial KYC"),
                positiveSignals = listOf(
                    Signal("pay slip", 5.0f, isStructural = true),
                    Signal("payslip", 5.0f, isStructural = true),
                    Signal("salary slip", 5.0f, isStructural = true),
                    Signal("gross pay", 4.0f),
                    Signal("net pay", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("employee_name", listOf("employee name", "name"), "STRING"),
                    FieldSchema("employer_name", listOf("company", "employer"), "STRING"),
                    FieldSchema("month_year", listOf("pay period", "month", "for the month of"), "STRING"),
                    FieldSchema("gross_pay", listOf("gross pay", "gross salary", "gross earnings"), "NUMBER"),
                    FieldSchema("net_pay", listOf("net pay", "net salary", "take home"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("employee_name", "name"))
            )
        )

        // 9. ELECTRICITY BILL
        registerType(
            DocumentTypeDefinition(
                typeId = "ELECTRICITY_BILL",
                displayName = "Electricity Bill",
                useCases = listOf("Address Proof", "Utility Expense Tracking", "Bill Payment Due Reminder"),
                positiveSignals = listOf(
                    Signal("electricity bill", 5.0f, isStructural = true),
                    Signal("electricity distribution", 4.0f),
                    Signal("consumer number", 4.0f),
                    Signal("units consumed", 3.5f)
                ),
                fields = listOf(
                    FieldSchema("consumer_name", listOf("consumer name", "name", "bill to"), "STRING"),
                    FieldSchema("consumer_no", listOf("consumer no", "account no", "ca no", "k no"), "STRING"),
                    FieldSchema("bill_date", listOf("bill date", "date of issue"), "DATE"),
                    FieldSchema("due_date", listOf("due date", "pay by date"), "DATE"),
                    FieldSchema("amount_due", listOf("amount payable", "total amount", "net bill amount"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("consumer_name", "name", "bill to"))
            )
        )

        // 10. WATER / GAS BILL
        registerType(
            DocumentTypeDefinition(
                typeId = "UTILITY_BILL",
                displayName = "Water / Gas / Utility Bill",
                useCases = listOf("Address Proof", "Utility Expense Tracking", "Bill Payment Due Reminder"),
                positiveSignals = listOf(
                    Signal("water bill", 5.0f, isStructural = true),
                    Signal("gas bill", 5.0f, isStructural = true),
                    Signal("piped natural gas", 4.0f),
                    Signal("lpg refill", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("customer_name", listOf("customer name", "consumer name", "name"), "STRING"),
                    FieldSchema("connection_no", listOf("consumer no", "bp no", "connection no"), "STRING"),
                    FieldSchema("bill_date", listOf("bill date", "date"), "DATE"),
                    FieldSchema("due_date", listOf("due date"), "DATE"),
                    FieldSchema("amount", listOf("total amount", "amount due", "net amount"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("customer_name", "name"))
            )
        )

        // 11. MOBILE / BROADBAND BILL
        registerType(
            DocumentTypeDefinition(
                typeId = "TELECOM_BILL",
                displayName = "Mobile / Broadband Bill",
                useCases = listOf("Address Proof", "Telecom Expense Tracking", "Payment Due Reminder"),
                positiveSignals = listOf(
                    Signal("postpaid bill", 5.0f, isStructural = true),
                    Signal("broadband bill", 5.0f, isStructural = true),
                    Signal("mobile bill", 5.0f, isStructural = true),
                    Signal("telecom", 3.5f)
                ),
                fields = listOf(
                    FieldSchema("subscriber_name", listOf("subscriber name", "name", "customer name"), "STRING"),
                    FieldSchema("mobile_no", listOf("mobile no", "account no", "telephone no"), "STRING"),
                    FieldSchema("bill_period", listOf("bill period", "billing cycle"), "STRING"),
                    FieldSchema("due_date", listOf("due date"), "DATE"),
                    FieldSchema("total_amount", listOf("total amount", "total due", "amount payable"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("subscriber_name", "name"))
            )
        )

        // 12. RENT AGREEMENT
        registerType(
            DocumentTypeDefinition(
                typeId = "RENT_AGREEMENT",
                displayName = "Rent / Lease Agreement",
                useCases = listOf("Address Proof", "Rental Lease Proof", "HRA Tax Exemption Claim"),
                positiveSignals = listOf(
                    Signal("rent agreement", 5.0f, isStructural = true),
                    Signal("leave and license agreement", 5.0f, isStructural = true),
                    Signal("tenancy agreement", 4.5f),
                    Signal("landlord", 4.0f),
                    Signal("tenant", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("tenant_name", listOf("tenant", "lessee", "second part"), "STRING"),
                    FieldSchema("landlord_name", listOf("landlord", "lessor", "first part"), "STRING"),
                    FieldSchema("monthly_rent", listOf("monthly rent", "rent amount"), "NUMBER"),
                    FieldSchema("security_deposit", listOf("security deposit", "deposit"), "NUMBER"),
                    FieldSchema("property_address", listOf("demised premises", "property address", "situated at"), "STRING")
                ),
                ownerRule = OwnerRule(listOf("tenant_name", "tenant"))
            )
        )

        // 13. BANK STATEMENT / LOAN
        registerType(
            DocumentTypeDefinition(
                typeId = "BANK_STATEMENT",
                displayName = "Bank Statement / Loan EMI",
                useCases = listOf("Financial Statement", "Bank Balance Proof", "Income Verification", "Loan EMI Tracking"),
                positiveSignals = listOf(
                    Signal("bank statement", 5.0f, isStructural = true),
                    Signal("account statement", 5.0f, isStructural = true),
                    Signal("loan account", 4.0f),
                    Signal("ifsc code", 4.0f)
                ),
                fields = listOf(
                    FieldSchema("account_holder", listOf("account holder", "name", "customer name"), "STRING"),
                    FieldSchema("account_number", listOf("account no", "account number", "loan account no"), "STRING"),
                    FieldSchema("bank_name", listOf("bank name", "branch"), "STRING"),
                    FieldSchema("closing_balance", listOf("closing balance", "balance", "sanctioned amount"), "NUMBER"),
                    FieldSchema("emi_amount", listOf("emi amount", "monthly instalment"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("account_holder", "name"))
            )
        )

        // 14. HEALTH / MEDICAL
        registerType(
            DocumentTypeDefinition(
                typeId = "MEDICAL",
                displayName = "Medical Prescription / Lab Report",
                useCases = listOf("Medical Prescription", "Pharmacy Purchase", "Health Diagnostic Report", "Insurance Claim"),
                positiveSignals = listOf(
                    Signal("prescription", 5.0f, isStructural = true),
                    Signal("diagnostic report", 5.0f, isStructural = true),
                    Signal("pathology report", 4.5f),
                    Signal("doctor", 3.5f)
                ),
                fields = listOf(
                    FieldSchema("patient_name", listOf("patient name", "name"), "STRING"),
                    FieldSchema("doctor_name", listOf("doctor name", "dr."), "STRING"),
                    FieldSchema("date", listOf("date"), "DATE"),
                    FieldSchema("diagnosis", listOf("diagnosis", "test name"), "STRING")
                ),
                ownerRule = OwnerRule(listOf("patient_name", "name"))
            )
        )

        // 15. TICKET
        registerType(
            DocumentTypeDefinition(
                typeId = "TICKET",
                displayName = "Train / Flight / Bus Ticket",
                useCases = listOf("Travel Pass", "Proof of Journey", "Travel Expense Claim"),
                positiveSignals = listOf(
                    Signal("electronic reservation slip", 5.0f, isStructural = true),
                    Signal("irctc", 4.5f),
                    Signal("boarding pass", 5.0f, isStructural = true),
                    Signal("pnr number", 4.5f)
                ),
                fields = listOf(
                    FieldSchema("passenger_name", listOf("passenger name", "name"), "STRING"),
                    FieldSchema("pnr", listOf("pnr", "pnr no", "booking id"), "STRING"),
                    FieldSchema("train_flight_no", listOf("train no", "flight no", "bus no"), "STRING"),
                    FieldSchema("travel_date", listOf("date of journey", "date", "departure date"), "DATE"),
                    FieldSchema("amount", listOf("total fare", "total amount", "ticket fare"), "NUMBER")
                ),
                ownerRule = OwnerRule(listOf("passenger_name", "name"))
            )
        )
    }
}
