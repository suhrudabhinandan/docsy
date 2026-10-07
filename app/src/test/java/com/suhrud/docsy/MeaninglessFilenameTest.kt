package com.suhrud.docsy

import com.suhrud.docsy.data.model.DocumentCategories
import com.suhrud.docsy.domain.ai.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class MeaninglessFilenameTest {

    @Test
    fun testContentOnlyClassificationForMeaninglessFilename() {
        val ocrText = """
            BANGALORE ELECTRICITY SUPPLY COMPANY LIMITED
            Billing Month: April 2026
            Net Amount Payable: ₹1,842.00
            Due Date: 18/05/2026
        """.trimIndent()

        val structured = DocumentClassifier.classifyAndExtract("IMG12092025.jpg", ocrText)
        assertEquals(DocumentCategories.ELECTRICITY_BILL, structured.category)
        assertEquals("April", structured.billingMonth)
        assertEquals(2026, structured.billingYear)
        assertEquals(1842.0, structured.amountPayable ?: 0.0, 0.01)
    }
}
