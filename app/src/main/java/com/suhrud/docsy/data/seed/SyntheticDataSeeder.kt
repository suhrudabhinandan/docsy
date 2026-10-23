package com.suhrud.docsy.data.seed

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.RecordEntity
import com.suhrud.docsy.data.db.entity.SheetMetaEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import com.suhrud.docsy.scanner.FileSection

object SyntheticDataSeeder {

    suspend fun seedSyntheticDocuments(db: DocsyDatabase) {
        val existingCount = db.documentDao().getAllDocumentsSnapshot().size
        if (existingCount > 0) return

        db.runInTransaction {
            // 1. Electricity Bill
            val doc1 = DocumentEntity(
                path = "/storage/emulated/0/Download/electricity_bill_apr2024.jpg",
                name = "electricity_bill_apr2024.jpg",
                ext = "jpg",
                mime = "image/jpeg",
                size = 450120L,
                modified = System.currentTimeMillis() - 86400000L * 5,
                section = FileSection.IMAGES.id,
                docType = "ELECTRICITY_BILL",
                docTypeScore = 0.98f,
                displayTitle = "Electricity Bill, Apr-2024",
                state = "DONE",
                extractionMethod = "OCR",
                charsExtracted = 840,
                pageCount = 1,
                updatedAt = System.currentTimeMillis()
            )
            val doc1Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc1) }
            val fields1 = listOf(
                FieldEntity(documentId = doc1Id, key = "consumer_no", label = "Consumer No.", value = "****7890", sensitive = true, confidence = 0.99f),
                FieldEntity(documentId = doc1Id, key = "bill_date", label = "Bill Date", value = "15-Apr-2024", valueType = "date", confidence = 0.95f),
                FieldEntity(documentId = doc1Id, key = "due_date", label = "Due Date", value = "30-Apr-2024", valueType = "date", confidence = 0.96f),
                FieldEntity(documentId = doc1Id, key = "total_amount", label = "Total Amount", value = "1,245.00", valueType = "amount", confidence = 0.98f),
                FieldEntity(documentId = doc1Id, key = "tariff", label = "Tariff", value = "LT-1 Domestic", confidence = 0.91f),
                FieldEntity(documentId = doc1Id, key = "units_consumed", label = "Units Consumed", value = "184 kWh", valueType = "number", confidence = 0.94f),
                FieldEntity(documentId = doc1Id, key = "meter_reading", label = "Meter Reading", value = "14,520", confidence = 0.92f),
                FieldEntity(documentId = doc1Id, key = "billing_unit", label = "Billing Unit", value = "BU-4011", confidence = 0.90f)
            )
            kotlinx.coroutines.runBlocking { db.fieldDao().insertFields(fields1) }

            // 2. Aadhaar and PAN Cards
            val doc2 = DocumentEntity(
                path = "/storage/emulated/0/Pictures/aadhaar_pan_cards.png",
                name = "aadhaar_pan_cards.png",
                ext = "png",
                mime = "image/png",
                size = 890200L,
                modified = System.currentTimeMillis() - 86400000L * 12,
                section = FileSection.IMAGES.id,
                docType = "IDENTITY_CARD",
                docTypeScore = 0.99f,
                displayTitle = "Aadhaar & PAN Card",
                state = "DONE",
                extractionMethod = "OCR",
                charsExtracted = 620,
                pageCount = 2,
                updatedAt = System.currentTimeMillis()
            )
            val doc2Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc2) }
            val fields2 = listOf(
                FieldEntity(documentId = doc2Id, key = "name", label = "Name", value = "Rahul Sharma", page = 1, confidence = 0.99f),
                FieldEntity(documentId = doc2Id, key = "aadhaar_no", label = "Aadhaar No.", value = "****4321", sensitive = true, page = 1, confidence = 0.98f),
                FieldEntity(documentId = doc2Id, key = "pan_no", label = "PAN No.", value = "****5678", sensitive = true, page = 1, confidence = 0.97f),
                FieldEntity(documentId = doc2Id, key = "dob", label = "DOB", value = "12/08/1992", valueType = "date", page = 1, confidence = 0.96f),
                FieldEntity(documentId = doc2Id, key = "father_name", label = "Father's Name", value = "Suresh Sharma", page = 2, confidence = 0.92f),
                FieldEntity(documentId = doc2Id, key = "address", label = "Address", value = "123 MG Road, Bengaluru, Karnataka - 560001", page = 2, confidence = 0.94f)
            )
            kotlinx.coroutines.runBlocking { db.fieldDao().insertFields(fields2) }

            // 3. CAT Admit Card
            val doc3 = DocumentEntity(
                path = "/storage/emulated/0/Documents/cat_2024_admit_card.docx",
                name = "cat_2024_admit_card.docx",
                ext = "docx",
                mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                size = 310400L,
                modified = System.currentTimeMillis() - 86400000L * 2,
                section = FileSection.WORD.id,
                docType = "ADMIT_CARD",
                docTypeScore = 0.95f,
                displayTitle = "CAT 2024 Admit Card",
                state = "DONE",
                extractionMethod = "DOCX_PARSER",
                charsExtracted = 1120,
                pageCount = 1,
                updatedAt = System.currentTimeMillis()
            )
            val doc3Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc3) }
            val fields3 = listOf(
                FieldEntity(documentId = doc3Id, key = "reg_no", label = "Registration No.", value = "CAT20240981", valueType = "id", confidence = 0.98f),
                FieldEntity(documentId = doc3Id, key = "candidate_name", label = "Candidate Name", value = "Ananya Verma", confidence = 0.99f),
                FieldEntity(documentId = doc3Id, key = "dob", label = "DOB", value = "04-11-2001", valueType = "date", confidence = 0.97f),
                FieldEntity(documentId = doc3Id, key = "exam_date", label = "Exam Date", value = "24-Nov-2024", valueType = "date", confidence = 0.98f),
                FieldEntity(documentId = doc3Id, key = "exam_time", label = "Exam Time", value = "08:30 AM - 10:30 AM", confidence = 0.96f),
                FieldEntity(documentId = doc3Id, key = "test_city", label = "Test City", value = "Mumbai", confidence = 0.95f),
                FieldEntity(documentId = doc3Id, key = "test_center", label = "Test Center", value = "Zone 4 iON Digital Zone, Powai", confidence = 0.93f)
            )
            kotlinx.coroutines.runBlocking { db.fieldDao().insertFields(fields3) }

            // 4. 10th and 12th Marksheets
            val doc4 = DocumentEntity(
                path = "/storage/emulated/0/Documents/class_12_marksheet.pdf",
                name = "class_12_marksheet.pdf",
                ext = "pdf",
                mime = "application/pdf",
                size = 520000L,
                modified = System.currentTimeMillis() - 86400000L * 20,
                section = FileSection.PDF.id,
                docType = "MARKSHEET",
                docTypeScore = 0.97f,
                displayTitle = "Class 12th Marksheet - CBSE",
                state = "DONE",
                extractionMethod = "TEXT_LAYER",
                charsExtracted = 1450,
                pageCount = 1,
                updatedAt = System.currentTimeMillis()
            )
            val doc4Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc4) }
            val fields4 = listOf(
                FieldEntity(documentId = doc4Id, key = "name", label = "Candidate Name", value = "Rohan Patel", confidence = 0.99f),
                FieldEntity(documentId = doc4Id, key = "roll_no", label = "Roll No.", value = "1208942", valueType = "id", confidence = 0.98f),
                FieldEntity(documentId = doc4Id, key = "board", label = "Board", value = "CBSE", confidence = 0.97f),
                FieldEntity(documentId = doc4Id, key = "school", label = "School", value = "Delhi Public School", confidence = 0.95f),
                FieldEntity(documentId = doc4Id, key = "guardian_name", label = "Guardian Name", value = "Anita Patel", confidence = 0.94f),
                FieldEntity(documentId = doc4Id, key = "printed_percentage", label = "Printed Percentage", value = "86.40%", confidence = 0.96f)
            )
            val subjects4 = listOf(
                SubjectEntity(documentId = doc4Id, rowNo = 1, name = "Mathematics", marks = "88", maxMarks = "100", grade = "A1"),
                SubjectEntity(documentId = doc4Id, rowNo = 2, name = "Physics", marks = "85", maxMarks = "100", grade = "A2"),
                SubjectEntity(documentId = doc4Id, rowNo = 3, name = "Chemistry", marks = "81", maxMarks = "100", grade = "B1"),
                SubjectEntity(documentId = doc4Id, rowNo = 4, name = "English Core", marks = "92", maxMarks = "100", grade = "A1"),
                SubjectEntity(documentId = doc4Id, rowNo = 5, name = "Computer Science", marks = "86", maxMarks = "100", grade = "A2")
            )
            kotlinx.coroutines.runBlocking {
                db.fieldDao().insertFields(fields4)
                db.subjectDao().insertSubjects(subjects4)
            }

            // 5. Classroom.xlsx
            val doc5 = DocumentEntity(
                path = "/storage/emulated/0/Documents/classroom_semester4_results.xlsx",
                name = "classroom_semester4_results.xlsx",
                ext = "xlsx",
                mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                size = 210500L,
                modified = System.currentTimeMillis() - 86400000L * 3,
                section = FileSection.EXCEL.id,
                docType = "SPREADSHEET",
                docTypeScore = 0.96f,
                displayTitle = "Classroom Performance Report",
                state = "DONE",
                extractionMethod = "XLSX_PARSER",
                charsExtracted = 2300,
                pageCount = 1,
                updatedAt = System.currentTimeMillis()
            )
            val doc5Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc5) }
            val meta5 = SheetMetaEntity(
                docId = doc5Id,
                sheet = "Semester 4",
                headerRow = 1,
                title = "Semester 4 Final Evaluation",
                institution = "St. Xavier College",
                batch = "2023-2024"
            )
            val records5 = mutableListOf<RecordEntity>()
            val sampleStudents = listOf(
                listOf("101", "Aarav Gupta", "94.5%", "A+"),
                listOf("102", "Bhavna Nair", "88.0%", "A"),
                listOf("103", "Chetan Kumar", "76.2%", "B+"),
                listOf("104", "Divya Joshi", "91.0%", "A+"),
                listOf("105", "Eshan Mehta", "82.4%", "A")
            )
            for ((rIdx, student) in sampleStudents.withIndex()) {
                records5.add(RecordEntity(docId = doc5Id, sheet = "Semester 4", rowNo = rIdx + 1, colIndex = 1, colKey = "Roll No.", value = student[0], valueType = "id"))
                records5.add(RecordEntity(docId = doc5Id, sheet = "Semester 4", rowNo = rIdx + 1, colIndex = 2, colKey = "Student Name", value = student[1]))
                records5.add(RecordEntity(docId = doc5Id, sheet = "Semester 4", rowNo = rIdx + 1, colIndex = 3, colKey = "Score", value = student[2], valueType = "number"))
                records5.add(RecordEntity(docId = doc5Id, sheet = "Semester 4", rowNo = rIdx + 1, colIndex = 4, colKey = "Grade", value = student[3]))
            }
            kotlinx.coroutines.runBlocking {
                db.sheetMetaDao().insertSheetMeta(meta5)
                db.recordDao().insertRecords(records5)
            }

            // 6. Unknown Document Type
            val doc6 = DocumentEntity(
                path = "/storage/emulated/0/Download/scanned_note_2024.jpg",
                name = "scanned_note_2024.jpg",
                ext = "jpg",
                mime = "image/jpeg",
                size = 320100L,
                modified = System.currentTimeMillis() - 86400000L * 1,
                section = FileSection.OTHER.id,
                docType = "UNKNOWN",
                docTypeScore = 0.42f,
                runnerUpType = "NOTE",
                runnerUpScore = 0.35f,
                displayTitle = "Scanned Note Record",
                state = "NEEDS_REVIEW",
                extractionMethod = "OCR",
                charsExtracted = 380,
                pageCount = 1,
                updatedAt = System.currentTimeMillis()
            )
            val doc6Id = kotlinx.coroutines.runBlocking { db.documentDao().insertDocument(doc6) }
            val fields6 = listOf(
                FieldEntity(documentId = doc6Id, key = "header_text", label = "Header Text", value = "MEMORANDUM DRAFT", confidence = 0.65f),
                FieldEntity(documentId = doc6Id, key = "date_found", label = "Date Found", value = "2024-01-10", valueType = "date", confidence = 0.58f, validationWarning = "Low confidence date extraction")
            )
            kotlinx.coroutines.runBlocking { db.fieldDao().insertFields(fields6) }
        }
    }
}
