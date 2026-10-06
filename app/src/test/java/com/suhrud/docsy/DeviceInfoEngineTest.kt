package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.domain.stats.DeviceInfoEngine
import com.suhrud.docsy.domain.stats.SubtopicResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceInfoEngineTest {

    @Test
    fun testSubtopicIsolation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Test bluetooth subtopic
        val btResult = DeviceInfoEngine.answerSubtopic(context, "bluetooth")
        if (btResult is SubtopicResult.Success) {
            for (fact in btResult.facts) {
                assertEquals("bluetooth", fact.subtopic)
                assertFalse("Bluetooth facts must not contain battery text", fact.key.contains("Battery"))
                assertFalse("Bluetooth facts must not contain model text", fact.key.contains("Model"))
            }
        }

        // Test wifi subtopic
        val wifiResult = DeviceInfoEngine.answerSubtopic(context, "wifi")
        if (wifiResult is SubtopicResult.Success) {
            for (fact in wifiResult.facts) {
                assertEquals("wifi", fact.subtopic)
                assertFalse("Wi-Fi facts must not contain battery text", fact.key.contains("Battery"))
            }
        }

        // Test battery subtopic
        val batteryResult = DeviceInfoEngine.answerSubtopic(context, "battery")
        if (batteryResult is SubtopicResult.Success) {
            for (fact in batteryResult.facts) {
                assertEquals("battery", fact.subtopic)
                assertFalse("Battery facts must not contain Wi-Fi text", fact.key.contains("Wi-Fi"))
            }
        }
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", DeviceInfoEngine.formatBytes(0L))
        assertEquals("500 B", DeviceInfoEngine.formatBytes(500L))
        assertEquals("512 MB", DeviceInfoEngine.formatBytes(512L * 1024 * 1024))
        assertEquals("5.8 GB", DeviceInfoEngine.formatBytes((5.8 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatUptime() {
        assertEquals("15m", DeviceInfoEngine.formatUptime(15 * 60 * 1000L))
        assertEquals("2h 30m", DeviceInfoEngine.formatUptime((2 * 3600 + 30 * 60) * 1000L))
        assertEquals("1d 4h 10m", DeviceInfoEngine.formatUptime((28 * 3600 + 10 * 60) * 1000L))
    }
}
