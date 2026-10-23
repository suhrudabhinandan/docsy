package com.suhrud.docsy.eval

data class DocumentFixture(
    val id: String,
    val fileName: String,
    val extension: String,
    val rawContent: String,
    val expectedCategory: String,
    val expectedType: String,
    val expectedEntities: Map<String, String>,
    val expectedWorksheetsCount: Int = 1,
    val expectedTableCount: Int = 0
)

object EvaluationCorpus {

    fun getSyntheticFixtures(): List<DocumentFixture> {
        return listOf(
            DocumentFixture(
                id = "fix_01",
                fileName = "cbse_marksheet_class12.pdf",
                extension = "pdf",
                rawContent = """
                    Central Board of Secondary Education
                    Statement of Marks
                    Senior School Certificate Examination 2024
                    Candidate's Name: Rahul Sharma
                    Father's Name: Ramesh Sharma
                    Mother's Name: Sunita Sharma
                    Roll No: 241023
                    Registration No: REG2024CS1023
                    Subject: Mathematics Marks: 95
                    Subject: Physics Marks: 92
                    Result: PASS
                """.trimIndent(),
                expectedCategory = "ACADEMIC",
                expectedType = "MARKSHEET",
                expectedEntities = mapOf(
                    "STUDENT_NAME" to "Rahul Sharma",
                    "PARENT_NAME" to "Ramesh Sharma",
                    "MOTHER_NAME" to "Sunita Sharma",
                    "ROLL_NUMBER" to "241023",
                    "REGISTRATION_NUMBER" to "REG2024CS1023"
                ),
                expectedTableCount = 1
            ),
            DocumentFixture(
                id = "fix_02",
                fileName = "commercial_tax_invoice.docx",
                extension = "docx",
                rawContent = """
                    === Headers ===
                    Tech Solutions Pvt Ltd - Tax Invoice

                    Tax Invoice
                    Invoice No: INV-2024-889
                    Date of Issue: 15/10/2024
                    Billed To: Global Enterprises
                    Total Amount Payable: ₹ 45,000.00
                """.trimIndent(),
                expectedCategory = "FINANCIAL",
                expectedType = "INVOICE",
                expectedEntities = mapOf(
                    "INVOICE_NUMBER" to "INV-2024-889",
                    "ISSUE_DATE" to "15/10/2024",
                    "AMOUNT" to "₹ 45,000.00"
                )
            ),
            DocumentFixture(
                id = "fix_03",
                fileName = "software_engineer_cv.docx",
                extension = "docx",
                rawContent = """
                    Curriculum Vitae
                    Rahul Sharma
                    Email: rahul.sharma@example.com
                    Mobile No: 9876543210
                    Work Experience: Senior Software Engineer at Innovation Labs
                    Education: B.Tech Computer Science
                """.trimIndent(),
                expectedCategory = "EMPLOYMENT",
                expectedType = "RESUME",
                expectedEntities = mapOf(
                    "PERSON_NAME" to "Rahul Sharma",
                    "EMAIL" to "rahul.sharma@example.com",
                    "PHONE_NUMBER" to "9876543210"
                )
            ),
            DocumentFixture(
                id = "fix_04",
                fileName = "annual_budget_summary.xlsx",
                extension = "xlsx",
                rawContent = """
                    === Worksheet: Budget2024 ===
                    [A1] Category [B1] Allocation [C1] Spent
                    [A2] Infrastructure [B2] 100000 [C2=SUM(B2:B2)] 100000
                """.trimIndent(),
                expectedCategory = "TECHNICAL",
                expectedType = "SPREADSHEET",
                expectedEntities = mapOf(
                    "AMOUNT" to "100000"
                ),
                expectedWorksheetsCount = 1
            ),
            DocumentFixture(
                id = "fix_05",
                fileName = "unknown_random_notes.txt",
                extension = "txt",
                rawContent = "Meeting notes from Monday discussion on cafeteria menu options.",
                expectedCategory = "OTHER",
                expectedType = "UNKNOWN",
                expectedEntities = emptyMap()
            )
        )
    }
}
