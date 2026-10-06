package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.domain.ai.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Docsy", appName)
    }

    @Test
    fun `test document classification and structured extraction`() {
        val sampleText = """
            BANGALORE ELECTRICITY SUPPLY COMPANY LIMITED
            Billing Period: 01/04/2026 to 30/04/2026
            Net Amount Payable: ₹1,842.00
            Due Date: 18/05/2026
        """.trimIndent()

        val structured = DocumentClassifier.classifyAndExtract("Electricity_Bill_April_2026.pdf", sampleText)
        assertEquals("Electricity Bill", structured.category)
        assertEquals("April", structured.billingMonth)
        assertEquals(1842.0, structured.amountPayable ?: 0.0, 0.01)
    }
}
