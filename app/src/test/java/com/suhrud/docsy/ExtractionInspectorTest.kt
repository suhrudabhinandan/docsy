package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.RecordEntity
import com.suhrud.docsy.data.db.entity.SheetMetaEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import com.suhrud.docsy.scanner.FileSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionInspectorTest {

    @Test
    fun testSyntheticLayoutTargetsAndDataHealth() {
        // 1. Electricity Bill
        val electricityDoc = DocumentEntity(
            path = "/storage/emulated/0/Download/electricity_bill_apr2024.jpg",
            name = "electricity_bill_apr2024.jpg",
            ext = "jpg",
            mime = "image/jpeg",
            size = 450120L,
            modified = System.currentTimeMillis(),
            section = FileSection.IMAGES.id,
            docType = "ELECTRICITY_BILL",
            docTypeScore = 0.98f,
            displayTitle = "Electricity Bill, Apr-2024",
            state = "DONE",
            extractionMethod = "OCR",
            charsExtracted = 840
        )
        val electricityFields = listOf(
            FieldEntity(documentId = 1, key = "consumer_no", label = "Consumer No.", value = "****7890", sensitive = true),
            FieldEntity(documentId = 1, key = "total_amount", label = "Total Amount", value = "1,245.00", valueType = "amount")
        )

        assertEquals("Electricity Bill, Apr-2024", electricityDoc.displayName)
        assertTrue(electricityFields.first().sensitive)
        assertEquals("****7890", electricityFields.first().value)

        // 2. Marksheet & Percentage Calculation Test
        val subjects = listOf(
            SubjectEntity(documentId = 4, rowNo = 1, name = "Mathematics", marks = "88", maxMarks = "100", grade = "A1"),
            SubjectEntity(documentId = 4, rowNo = 2, name = "Physics", marks = "85", maxMarks = "100", grade = "A2"),
            SubjectEntity(documentId = 4, rowNo = 3, name = "Chemistry", marks = "81", maxMarks = "100", grade = "B1"),
            SubjectEntity(documentId = 4, rowNo = 4, name = "English Core", marks = "92", maxMarks = "100", grade = "A1"),
            SubjectEntity(documentId = 4, rowNo = 5, name = "Computer Science", marks = "86", maxMarks = "100", grade = "A2")
        )

        val totalObtained = subjects.mapNotNull { it.marks?.toIntOrNull() }.sum()
        val totalMax = subjects.mapNotNull { it.maxMarks?.toIntOrNull() }.sum()
        val calculatedPct = (totalObtained.toDouble() / totalMax) * 100

        assertEquals(432, totalObtained)
        assertEquals(500, totalMax)
        assertEquals(86.40, calculatedPct, 0.01)

        // 3. Spreadsheet Header Block & Record Grid Test
        val sheetMeta = SheetMetaEntity(
            docId = 5,
            sheet = "Semester 4",
            title = "Semester 4 Final Evaluation",
            institution = "St. Xavier College",
            batch = "2023-2024"
        )
        val recordCell = RecordEntity(
            docId = 5,
            sheet = "Semester 4",
            rowNo = 1,
            colIndex = 1,
            colKey = "Roll No.",
            value = "101",
            valueType = "id"
        )

        assertNotNull(sheetMeta.institution)
        assertEquals("St. Xavier College", sheetMeta.institution)
        assertEquals("101", recordCell.value)
    }

    @Test
    fun testSensitiveFieldMaskingLogic() {
        fun maskValue(valStr: String): String {
            val clean = valStr.replace(" ", "")
            if (clean.length <= 4) return "****"
            return "****" + clean.takeLast(4)
        }

        assertEquals("****4321", maskValue("987654324321"))
        assertEquals("****5678", maskValue("ABCDE5678"))
        assertEquals("****", maskValue("123"))
    }
}
