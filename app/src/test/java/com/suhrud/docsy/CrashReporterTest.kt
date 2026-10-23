package com.suhrud.docsy

import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.util.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrashReporterTest {

    @Test
    fun testCrashLogWriteReadClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Clear existing log
        CrashReporter.clearCrashLog(context)
        assertNull(CrashReporter.getCrashLog(context))

        // Log a test exception
        val testException = java.lang.RuntimeException("Simulated crash for testing")
        CrashReporter.writeLoggedThrowable(context, "TEST_TAG", testException)

        val log = CrashReporter.getCrashLog(context)
        assertNotNull(log)
        assertTrue(log!!.contains("CRASH REPORT [TEST_TAG]"))
        assertTrue(log.contains("Simulated crash for testing"))
        assertTrue(log.contains("java.lang.RuntimeException"))

        // Clear log
        CrashReporter.clearCrashLog(context)
        assertNull(CrashReporter.getCrashLog(context))
    }
}
