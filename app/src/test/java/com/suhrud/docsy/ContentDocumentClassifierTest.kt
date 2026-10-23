package com.suhrud.docsy

import com.suhrud.docsy.understanding.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentDocumentClassifierTest {

    // 1. Aadhaar Tests
    @Test
    fun testAadhaarPositives() {
        val samples = listOf(
            "GOVERNMENT OF INDIA UNIQUE IDENTIFICATION AUTHORITY OF INDIA Mera Aadhaar Meri Pehchan 2345 6789 0123",
            "UIDAI Government of India Aadhaar Card Address: New Delhi 9876 5432 1098",
            "Mera Aadhaar Meri Pehchan UIDAI UID No: 4567 8901 2345 DOB: 01/01/1990",
            "Unique Identification Authority of India 5678 1234 9012 Govt of India",
            "भारत विशिष्ट पहचान प्राधिकरण मेरा आधार मेरी पहचान 3456 7890 1234"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("Aadhaar", result.category)
            assertTrue(result.confidence > 0f)
        }
    }

    @Test
    fun testAadhaarNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Suhrud Abhinandan Mentioned Aadhaar for verification in job application",
            "INCOME TAX DEPARTMENT Permanent Account Number Card PAN: ABCDE1234F",
            "STATEMENT OF MARKS CBSE Board Class XII Roll No: 12646545"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("Aadhaar", result.category)
        }
    }

    // 2. PAN Tests
    @Test
    fun testPANPositives() {
        val samples = listOf(
            "INCOME TAX DEPARTMENT Permanent Account Number Card PAN Card ABCDE1234F Govt of India",
            "Income Tax Department Govt. of India Permanent Account Number ABCDE5678G",
            "PAN Card Income Tax Department Govt of India Name: Suhrud Abhinandan WXYZ9876K",
            "Permanent Account Number Income Tax Department PAN No: PQRST1234M",
            "आयकर विभाग PAN CARD Govt of India Permanent Account Number KLMNO4321P"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("PAN", result.category)
        }
    }

    @Test
    fun testPANNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Submitted PAN Card to HR for salary account opening",
            "UNIQUE IDENTIFICATION AUTHORITY OF INDIA Mera Aadhaar Meri Pehchan",
            "TAX INVOICE Total Amount Payable GSTIN: 07AAAAA0000A1Z5"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("PAN", result.category)
        }
    }

    // 3. Driving Licence Tests
    @Test
    fun testDrivingLicencePositives() {
        val samples = listOf(
            "UNION OF INDIA DRIVING LICENCE Transport Department Motor Vehicles Act DL-1420110012345 COV: LMV",
            "Driving License Licence to Drive Motor Vehicles Valid Till: 2035 DL No: MH0120150098765",
            "Transport Department Driving Licence Licence to Drive Motor Vehicles Class of Vehicle: LMV MCWG",
            "Motor Vehicles Act Union of India Driving Licence DL-0420201234567 Date of Issue: 10/05/2020",
            "Driving License Transport Department Licence to Drive DL1234567890123"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("driving licence", result.category)
        }
    }

    @Test
    fun testDrivingLicenceNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Personal Details: Have a valid driving licence and 2-wheeler",
            "OFFER LETTER Appointment as Delivery Executive driving license mandatory for role",
            "INCOME TAX DEPARTMENT Permanent Account Number Card ABCDE1234F"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("driving licence", result.category)
        }
    }

    // 4. Passport Tests
    @Test
    fun testPassportPositives() {
        val samples = listOf(
            "REPUBLIC OF INDIA PASSPORT Type P Code IND Passport No: Z1234567 Surname: Abhinandan",
            "Republic of India Passport Type P Code IND Passport No: A9876543 Given Name: Suhrud",
            "PASSPORT Republic of India Type P Code IND Passport No: B5678901 Date of Expiry: 2030",
            "पासपोर्ट REPUBLIC OF INDIA PASSPORT Type P Code IND Passport No: C3456789",
            "Passport Republic of India Pass Port No: D8765432 Nationality: Indian"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("passport", result.category)
        }
    }

    @Test
    fun testPassportNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Personal Details: Passport Size Photo Attached",
            "FLIGHT TICKET Boarding Pass IRCTC Electronic Reservation Slip",
            "ELECTION COMMISSION OF INDIA Elector's Photo Identity Card"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("passport", result.category)
        }
    }

    // 5. Voter ID Tests
    @Test
    fun testVoterIDPositives() {
        val samples = listOf(
            "ELECTION COMMISSION OF INDIA Elector's Photo Identity Card EPIC No: ABC1234567 Voter ID",
            "Election Commission of India Voter ID Card EPIC NO: XYZ9876543 Assembly Constituency: Delhi",
            "भारत निर्वाचन आयोग ELECTION COMMISSION OF INDIA Elector's Photo Identity Card EPIC No: TUV4567890",
            "Voter ID Election Commission of India Elector's Photo Identity Card EPIC No: MNO3456789",
            "Elector's Photo Identity Card Election Commission of India Voter Card EPIC No: PQR6543210"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("voter ID", result.category)
        }
    }

    @Test
    fun testVoterIDNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Submitted Voter ID as address proof during verification",
            "RENT AGREEMENT Landlord Lessee Rent Amount 15000",
            "TAX INVOICE GSTIN 07AAAAA0000A1Z5"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("voter ID", result.category)
        }
    }

    // 6. Marksheet 10/12/Semester Tests
    @Test
    fun testMarksheetPositives() {
        val samples = listOf(
            "CENTRAL BOARD OF SECONDARY EDUCATION STATEMENT OF MARKS CLASS XII Roll No: 12646545 Subject Code 301 English Marks 85 Max Marks 100 Grade A1",
            "STATEMENT OF MARKS BOARD OF SECONDARY EDUCATION Roll No: 987654 Subject Code Marks Obtained Max Marks CGPA 9.2",
            "MARK SHEET University Semester Examination Grade Card Subject Code 041 Mathematics Practical Theory Total Marks 92",
            "Central Board of Secondary Education Statement of Marks Senior Secondary Examination Subject Code 083 Computer Science Marks 95",
            "STATEMENT OF MARKS Marksheet Controller of Examinations Grade Card Semester Examination Subject Code Total Marks 450 Pass"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("MARKSHEET", result.category)
        }
    }

    @Test
    fun testMarksheetNegatives() {
        val samples = listOf(
            // CV mentioning 10th / 12th marks MUST STAY CV and NEVER become marksheet
            "CURRICULUM VITAE Suhrud Abhinandan Education: Class 12 CBSE Board - 88% marks in 2020. Work Experience: Software Engineer at Tech Corp.",
            // Offer letter mentioning marks MUST NOT become marksheet
            "OFFER LETTER Dear Suhrud, We are pleased to offer you the role. Please submit your Class 10 marksheet copy on Date of Joining.",
            // Experience letter mentioning marks
            "EXPERIENCE LETTER Letter of Appointment Suhrud Abhinandan worked as Senior Engineer from 2022 to 2025."
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("marksheet 10/12/semester", result.category)
        }
    }

    // 7. Degree/Certificates Tests
    @Test
    fun testDegreeCertificatesPositives() {
        val samples = listOf(
            "DEGREE CERTIFICATE Bachelor of Technology in Computer Science Conferred upon Suhrud Abhinandan at Convocation",
            "Provisional Certificate Master of Science Diploma in Software Engineering Conferred Upon First Division",
            "Bachelor of Science Degree Certificate University Convocation Doctor of Philosophy Conferred Upon",
            "Provisional Certificate Degree Certificate Bachelor of Engineering Has Successfully Completed Convocation",
            "Diploma in Data Science Certificate of Completion Has Successfully Completed Professional Diploma"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("degree/certificates", result.category)
        }
    }

    @Test
    fun testDegreeCertificatesNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Education: Bachelor of Technology from ABC University",
            "STATEMENT OF MARKS CBSE Board Class XII Roll No: 12646545",
            "OFFER LETTER Dear Candidate, Congratulations on receiving appointment"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("degree/certificates", result.category)
        }
    }

    // 8. CV Tests
    @Test
    fun testCVPositives() {
        val samples = listOf(
            "CURRICULUM VITAE Suhrud Abhinandan Work Experience Technical Skills Education Career Objective Projects",
            "RESUME Suhrud Abhinandan Professional Summary Work Experience Technical Skills Key Achievements Projects",
            "BIODATA Curriculum Vitae Suhrud Abhinandan Professional Experience Technical Skills Education Qualification",
            "CURRICULUM VITAE Resume Technical Skills Projects Work Experience Education Class 12th CBSE Board 88%",
            "RESUME Career Objective Professional Summary Technical Skills Work Experience Education B.Tech CS"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("CV", result.category)
        }
    }

    @Test
    fun testCVNegatives() {
        val samples = listOf(
            "STATEMENT OF MARKS Central Board of Secondary Education Class XII Roll No: 12646545 Subject Code 301 English Marks 85",
            "INCOME TAX DEPARTMENT Permanent Account Number Card PAN ABCDE1234F",
            "UNIQUE IDENTIFICATION AUTHORITY OF INDIA Mera Aadhaar Meri Pehchan"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("CV", result.category)
        }
    }

    // 9. Offer/Salary/Experience Letter Tests
    @Test
    fun testOfferLetterPositives() {
        val samples = listOf(
            "OFFER LETTER Dear Suhrud, Letter of Appointment for Senior Software Engineer CTC Per Annum Date of Joining 01/06/2023",
            "Letter of Appointment Employment Contract Salary Certificate Experience Letter Relieving Letter CTC Per Annum",
            "EXPERIENCE LETTER Relieving Letter Service Certificate Suhrud Abhinandan worked as Software Engineer",
            "Salary Certificate Offer Letter Remuneration Basic Salary CTC Per Annum Date of Joining Notice Period",
            "Letter of Appointment Employment Contract CTC Per Annum Dear Mr. Suhrud Designation Senior Engineer"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("offer/salary/experience letter", result.category)
        }
    }

    @Test
    fun testOfferLetterNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Suhrud Abhinandan Technical Skills Work Experience Projects Education",
            "STATEMENT OF MARKS Central Board of Secondary Education Senior Secondary Examination",
            "TAX INVOICE Total Amount Payable GSTIN: 07AAAAA0000A1Z5"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("offer/salary/experience letter", result.category)
        }
    }

    // 10. Bank Statement Tests
    @Test
    fun testBankStatementPositives() {
        val samples = listOf(
            "ACCOUNT STATEMENT HDFC Bank Bank Statement Statement of Account Opening Balance Closing Balance Withdrawal Deposit IFSC Code",
            "State Bank of India Bank Statement Account Statement Transaction History Opening Balance Closing Balance IFSC Code",
            "ICICI Bank Statement of Account Account Number Opening Balance Closing Balance Withdrawal Deposit Chq No",
            "Bank Statement Transaction History Opening Balance Closing Balance Withdrawal Deposit IFSC Code Account Number",
            "Axis Bank Statement of Account Account Statement Opening Balance Closing Balance Transaction History"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("bank statement", result.category)
        }
    }

    @Test
    fun testBankStatementNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Education: B.Tech Computer Science",
            "TAX INVOICE Total Amount Payable GSTIN 07AAAAA0000A1Z5",
            "OFFER LETTER Salary Certificate CTC Per Annum"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("bank statement", result.category)
        }
    }

    // 11. ITR Tests
    @Test
    fun testITRPositives() {
        val samples = listOf(
            "INDIAN INCOME TAX RETURN ACKNOWLEDGEMENT ITR-V Assessment Year Gross Total Income Tax Payable Acknowledgement Number",
            "Income Tax Return ITR-V Assessment Year Gross Total Income Tax Payable Acknowledgement Number E-Verified",
            "Indian Income Tax Return Acknowledgement ITR-V Assessment Year Total Income Tax Payable PAN ABCDE1234F",
            "Income Tax Return Verification Form ITR-V Assessment Year Gross Total Income Acknowledgement Number",
            "ITR-V Indian Income Tax Return Acknowledgement Assessment Year Tax Payable Gross Total Income"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("ITR", result.category)
        }
    }

    @Test
    fun testITRNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Education Work Experience Projects",
            "INCOME TAX DEPARTMENT Permanent Account Number Card PAN ABCDE1234F",
            "ELECTRICITY BILL Power Distribution Consumer Number Amount Payable"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("ITR", result.category)
        }
    }

    // 12. Form 16 Tests
    @Test
    fun testForm16Positives() {
        val samples = listOf(
            "FORM NO. 16 Form 16 Certificate Under Section 203 of the Income-Tax Act Tax Deducted at Source TAN of Employer PAN of Employee",
            "Form 16 Certificate Under Section 203 Tax Deducted at Source TAN of Employer PAN of Employee Assessment Year",
            "FORM NO. 16 Certificate Under Section 203 of Income-Tax Act Tax Deducted at Source TAN Employer PAN Employee",
            "Form 16 Tax Deducted at Source Certificate Under Section 203 TAN of Employer PAN of Employee Quarterly Statement",
            "FORM NO. 16 TDS Certificate Section 203 Income Tax Act TAN of Employer PAN of Employee"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("Form 16", result.category)
        }
    }

    @Test
    fun testForm16Negatives() {
        val samples = listOf(
            "CURRICULUM VITAE Submitted Form 16 to bank for home loan processing",
            "ACCOUNT STATEMENT HDFC Bank Opening Balance Closing Balance",
            "RENT AGREEMENT Monthly Rent Landlord Tenant Security Deposit"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("Form 16", result.category)
        }
    }

    // 13. Utility and Mobile Bills Tests
    @Test
    fun testUtilityBillsPositives() {
        val samples = listOf(
            "ELECTRICITY BILL Power Distribution Consumer Number Units Consumed Bill Date Due Date Total Amount Payable",
            "Mobile Bill Postpaid Bill Airtel Broadband Bill Consumer Number Bill Date Due Date Amount Payable",
            "Water Bill Gas Bill Consumer Number Meter Number Units Consumed Total Amount Due Bill Date",
            "BSNL Postpaid Bill Mobile Bill Consumer Number Bill Date Due Date Units Consumed Total Amount Payable",
            "Tata Power Electricity Bill Consumer Number Units Consumed Meter Number Bill Date Due Date"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("utility and mobile bills", result.category)
        }
    }

    @Test
    fun testUtilityBillsNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Attached Electricity Bill as address proof",
            "TAX INVOICE Commercial Invoice Total Amount Payable GSTIN 07AAAAA0000A1Z5",
            "RENT AGREEMENT Tenant agrees to pay utility bills separately"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("utility and mobile bills", result.category)
        }
    }

    // 14. Insurance Tests
    @Test
    fun testInsurancePositives() {
        val samples = listOf(
            "INSURANCE POLICY Policy Schedule Life Insurance Policy Number Sum Assured Premium Amount Nominee Name",
            "Health Insurance Policy Schedule Policy Number Sum Assured Premium Amount Insured Person Star Health",
            "Motor Insurance Policy Schedule Policy Certificate Policy Number Premium Amount Vehicle Number Nominee Name",
            "LIC of India Insurance Policy Schedule Life Insurance Policy Number Sum Assured Premium Amount",
            "Insurance Policy Schedule Health Insurance Policy Number Premium Amount Sum Assured Nominee Name"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("insurance", result.category)
        }
    }

    @Test
    fun testInsuranceNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Company provides Group Health Insurance benefit",
            "OFFER LETTER Health Insurance Coverage Included in CTC",
            "PATIENT DIAGNOSTIC TEST REPORT Blood Test Report Laboratory Report"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("insurance", result.category)
        }
    }

    // 15. Vehicle RC/PUC Tests
    @Test
    fun testVehicleRCPUCPositives() {
        val samples = listOf(
            "CERTIFICATE OF REGISTRATION Registration Certificate Vehicle RC Chassis No Engine No Maker Class DL01AB1234",
            "POLLUTION UNDER CONTROL CERTIFICATE PUC Certificate Transport Department Vehicle RC Regn No MH12CD5678",
            "Registration Certificate Vehicle RC Pollution Under Control Certificate Chassis No Engine No MH02EF9012",
            "PUC Certificate Transport Department Registration Certificate Chassis No Engine No Maker Class HR26GH3456",
            "Certificate of Registration Vehicle RC Regn No KA03JK7890 Chassis No Engine No Transport Department"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("vehicle RC/PUC", result.category)
        }
    }

    @Test
    fun testVehicleRCPUCNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Have own vehicle RC and driving license",
            "DRIVING LICENCE Licence to Drive Motor Vehicles DL-1420110012345",
            "TAX INVOICE Vehicle Repair Charges Total Amount Payable"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("vehicle RC/PUC", result.category)
        }
    }

    // 16. Rent Agreement Tests
    @Test
    fun testRentAgreementPositives() {
        val samples = listOf(
            "RENT AGREEMENT Tenancy Agreement Landlord Tenant Monthly Rent Security Deposit Lock-in Period Premises",
            "Leave and License Agreement Lease Agreement Lessor Lessee Monthly Rent Security Deposit Premises",
            "Residential Rental Agreement Rent Agreement Landlord Lessee Monthly Rent Security Deposit Lock-in Period",
            "Tenancy Agreement Rent Agreement Lessor Lessee Security Deposit Monthly Rent Demised Premises",
            "Rent Agreement Leave and License Agreement Landlord Tenant Monthly Rent Premises Lock-in Period"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("rent agreement", result.category)
        }
    }

    @Test
    fun testRentAgreementNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Address: Rented Apartment, New Delhi",
            "RECEIPT Payment Received for Monthly Rent Fee Voucher",
            "TAX INVOICE Invoice Number GSTIN Total Amount"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("rent agreement", result.category)
        }
    }

    // 17. Loan Tests
    @Test
    fun testLoanPositives() {
        val samples = listOf(
            "LOAN AGREEMENT Loan Sanction Letter Sanctioned Amount EMI Amount Rate of Interest Loan Account No",
            "Key Fact Statement Loan Account Statement Sanctioned Amount Rate of Interest EMI Amount Tenure",
            "Personal Loan Sanction Letter Loan Agreement Sanctioned Amount EMI Amount Rate of Interest Disbursement",
            "Loan Agreement Loan Sanction Letter Key Fact Statement Sanctioned Amount EMI Amount Rate of Interest",
            "Home Loan Sanction Letter Loan Account Statement Sanctioned Amount EMI Amount Rate of Interest Tenure"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("loan", result.category)
        }
    }

    @Test
    fun testLoanNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Financial Analyst dealing with Home Loan portfolio",
            "BANK STATEMENT Account Statement Opening Balance Closing Balance Withdrawal",
            "OFFER LETTER Salary Credit Details for Loan Eligibility"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("loan", result.category)
        }
    }

    // 18. Admit Card Tests
    @Test
    fun testAdmitCardPositives() {
        val samples = listOf(
            "ADMIT CARD Hall Ticket Examination Hall Ticket Test Center Reporting Time Gate Closure Time Roll Number Slip",
            "Provisional Admit Card Examination Hall Ticket Roll Number Slip Test Center Reporting Time Exam Date",
            "Hall Ticket Admit Card Examination Hall Ticket Reporting Time Gate Closure Time Test Center",
            "Admit Card Common Admission Test Hall Ticket Roll Number Slip Test Center Exam Date Reporting Time",
            "Provisional Admit Card Hall Ticket Test Center Gate Closure Time Reporting Time Roll Number Slip"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("admit card", result.category)
        }
    }

    @Test
    fun testAdmitCardNegatives() {
        val samples = listOf(
            "STATEMENT OF MARKS CBSE Board Class XII Roll No 12646545 Subject Marks",
            "CURRICULUM VITAE Cleared CAT Exam with 99 percentile",
            "FEE RECEIPT Payment Receipt Received with Thanks Tuition Fee"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("admit card", result.category)
        }
    }

    // 19. Fee Receipt Tests
    @Test
    fun testFeeReceiptPositives() {
        val samples = listOf(
            "FEE RECEIPT Tuition Fee Receipt Payment Receipt Received with Thanks From Fee Voucher Amount Paid Fee Head",
            "Payment Receipt Fee Receipt Received with Thanks From Fee Voucher Tuition Fee Amount Paid Transaction ID",
            "Fee Voucher Tuition Fee Receipt Fee Receipt Received with Thanks From Payment Mode Amount Paid",
            "Fee Receipt Received with Thanks From Tuition Fee Payment Receipt Fee Head Amount Paid Balance Due",
            "Tuition Fee Receipt Fee Receipt Payment Receipt Received with Thanks Fee Voucher Amount Paid"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("fee receipt", result.category)
        }
    }

    @Test
    fun testFeeReceiptNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Submitted College Fee Receipts for Reimbursement",
            "TAX INVOICE Commercial Invoice Total Amount Payable GSTIN",
            "STATEMENT OF MARKS Central Board of Secondary Education"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("fee receipt", result.category)
        }
    }

    // 20. Ticket Tests
    @Test
    fun testTicketPositives() {
        val samples = listOf(
            "ELECTRONIC RESERVATION SLIP IRCTC E-Ticket PNR Number Train No Departure Time Arrival Time Seat / Berth",
            "Flight Ticket Boarding Pass PNR Number Departure Time Arrival Time Passenger Details Booking Confirmation",
            "Bus Ticket Booking Confirmation PNR No Boarding Point Departure Time Seat / Berth Passenger Name",
            "Movie Ticket Electronic Reservation Slip Booking Confirmation Seat / Berth PNR Number",
            "IRCTC E-Ticket Electronic Reservation Slip PNR Number Departure Time Arrival Time Seat / Berth"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("ticket", result.category)
        }
    }

    @Test
    fun testTicketNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Travel Details Attached",
            "TAX INVOICE Commercial Invoice GSTIN Total Amount",
            "ADMIT CARD Examination Hall Ticket Test Center Reporting Time"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("ticket", result.category)
        }
    }

    // 21. Invoice Tests
    @Test
    fun testInvoicePositives() {
        val samples = listOf(
            "TAX INVOICE Commercial Invoice Bill of Supply Invoice Number Invoice Date GSTIN Total Amount Payable HSN/SAC",
            "Commercial Invoice Tax Invoice Invoice No Invoice Date GSTIN Subtotal CGST SGST Total Amount Payable",
            "Bill of Supply Tax Invoice Invoice Number Invoice Date GSTIN Total Payable HSN/SAC Cash Memo",
            "Tax Invoice Bill of Supply Invoice No GSTIN 07AAAAA0000A1Z5 Subtotal CGST SGST Total Amount Payable",
            "Commercial Invoice Tax Invoice Invoice Date GSTIN Total Amount Payable HSN/SAC Code"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("invoice", result.category)
        }
    }

    @Test
    fun testInvoiceNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Experience in Tax Invoice Automation",
            "ELECTRICITY BILL Consumer Number Units Consumed Due Date",
            "PURCHASE WARRANTY CARD Warranty Period Terms of Warranty"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("invoice", result.category)
        }
    }

    // 22. Warranty Tests
    @Test
    fun testWarrantyPositives() {
        val samples = listOf(
            "WARRANTY CARD Guarantee Card Limited Warranty Warranty Certificate Serial Number Model No Warranty Period Terms of Warranty",
            "Limited Warranty Product Warranty Certificate Warranty Card Serial Number Model No Warranty Period Terms of Warranty",
            "Guarantee Card Warranty Certificate Limited Warranty Serial Number Warranty Period Terms of Warranty Date of Purchase",
            "Product Warranty Certificate Warranty Card Guarantee Card Serial Number Model No Warranty Period Terms of Warranty",
            "Warranty Card Limited Warranty Guarantee Card Terms of Warranty Warranty Period Serial Number Date of Purchase"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("warranty", result.category)
        }
    }

    @Test
    fun testWarrantyNegatives() {
        val samples = listOf(
            "TAX INVOICE Commercial Invoice Total Amount Payable GSTIN",
            "CURRICULUM VITAE Electronics Hardware Quality Engineer",
            "LABORATORY REPORT Diagnostic Test Report Blood Test"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("warranty", result.category)
        }
    }

    // 23. Prescription Tests
    @Test
    fun testPrescriptionPositives() {
        val samples = listOf(
            "MEDICAL PRESCRIPTION Rx Doctor's Prescription Patient Name Age / Sex Diagnosis Dosage Once Daily Tab. Syrup",
            "Doctor's Prescription Medical Prescription Rx Diagnosis Patient Name Dosage Tab. Syrup Once Daily Clinic",
            "Rx Medical Prescription Doctor's Prescription Patient Name Diagnosis Dosage Once Daily Syrup Hospital",
            "Medical Prescription Rx Patient Name Age / Sex Diagnosis Dosage Tab. Once Daily Doctor's Signature",
            "Doctor's Prescription Medical Prescription Rx Diagnosis Dosage Syrup Once Daily Clinic Rx"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("prescription", result.category)
        }
    }

    @Test
    fun testPrescriptionNegatives() {
        val samples = listOf(
            "LABORATORY REPORT Diagnostic Test Report Pathology Report Blood Test Observed Value Reference Range",
            "CURRICULUM VITAE Medical Representative Experience",
            "HEALTH INSURANCE POLICY Policy Schedule Sum Assured Premium Amount"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("prescription", result.category)
        }
    }

    // 24. Lab Report Tests
    @Test
    fun testLabReportPositives() {
        val samples = listOf(
            "LABORATORY REPORT Diagnostic Test Report Pathology Report Blood Test Report Observed Value Reference Range Test Name",
            "Pathology Report Diagnostic Test Report Blood Test Report Laboratory Report Observed Value Reference Range",
            "Blood Test Report Laboratory Report Diagnostic Test Report Pathology Report Observed Value Reference Range Test Name",
            "Diagnostic Test Report Pathology Report Laboratory Report Observed Value Reference Range Test Name Referred By",
            "Laboratory Report Blood Test Report Diagnostic Test Report Observed Value Reference Range Pathology Report"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("lab report", result.category)
        }
    }

    @Test
    fun testLabReportNegatives() {
        val samples = listOf(
            "MEDICAL PRESCRIPTION Rx Doctor's Prescription Dosage Tab Syrup",
            "CURRICULUM VITAE Lab Technician Professional Summary",
            "HEALTH INSURANCE CLAIM Form Policy Schedule Sum Assured"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("lab report", result.category)
        }
    }

    // 25. GST Tests
    @Test
    fun testGSTPositives() {
        val samples = listOf(
            "FORM GST REG-06 Registration Certificate Under GST Goods and Services Tax Registration GSTIN Constitution of Business 07AAAAA0000A1Z5",
            "Registration Certificate Under GST Form GST REG-06 Goods and Services Tax Registration GSTIN Constitution of Business",
            "Goods and Services Tax Registration Certificate Form GST REG-06 GSTIN Legal Name Trade Name 09BBBBB1111B2Z8",
            "Form GST REG-06 Registration Certificate Under GST Goods and Services Tax GSTIN Constitution of Business Date of Liability",
            "Registration Certificate Under GST Form GST REG-06 GSTIN Legal Name Trade Name Constitution of Business"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("GST", result.category)
        }
    }

    @Test
    fun testGSTNegatives() {
        val samples = listOf(
            "TAX INVOICE Commercial Invoice Total Amount Payable Subtotal CGST SGST",
            "CURRICULUM VITAE Tax Consultant GST Filing Experience",
            "INCOME TAX RETURN ACKNOWLEDGEMENT Assessment Year ITR-V"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("GST", result.category)
        }
    }

    // 26. Spreadsheet Tests
    @Test
    fun testSpreadsheetPositives() {
        val samples = listOf(
            "Comma Separated Values Worksheet Sheet1 Data Export .csv 1,2,3,4,5,6",
            "Worksheet Excel Sheet Sheet1 .xlsx Comma Separated Values Grid Data",
            "Comma Separated Values .csv Worksheet Sheet1 Column1,Column2,Column3",
            "Sheet1 Worksheet .xlsx Comma Separated Values Data Export Table",
            "Comma Separated Values Sheet1 Worksheet .csv Data Rows Columns"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("spreadsheet", result.category)
        }
    }

    @Test
    fun testSpreadsheetNegatives() {
        val samples = listOf(
            "CURRICULUM VITAE Proficient in Microsoft Excel and Spreadsheets",
            "TAX INVOICE Invoice Number GSTIN Total Payable",
            "BANK STATEMENT Account Statement Opening Balance Closing Balance"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertNotEquals("spreadsheet", result.category)
        }
    }

    // 27. Other / Low Confidence Tests
    @Test
    fun testOtherThresholdFallback() {
        val samples = listOf(
            "Random text with no document headers or specific signals 12345",
            "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod",
            "Note: meeting scheduled for tomorrow at 3pm in office conference room"
        )
        for (sample in samples) {
            val result = DocumentClassifier.classify(sample)
            assertEquals("other", result.category)
            assertEquals(0.0f, result.confidence, 0.001f)
        }
    }
}
