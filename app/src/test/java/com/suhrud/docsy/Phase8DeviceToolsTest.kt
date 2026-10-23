package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.tools.BatterySnapshot
import com.suhrud.docsy.tools.BatteryTool
import com.suhrud.docsy.tools.ContactItem
import com.suhrud.docsy.tools.ContactsTool
import com.suhrud.docsy.tools.DateTimeSnapshot
import com.suhrud.docsy.tools.DateTimeTool
import com.suhrud.docsy.tools.DisplaySnapshot
import com.suhrud.docsy.tools.DisplayTool
import com.suhrud.docsy.tools.HardwareInfoTool
import com.suhrud.docsy.tools.HardwareSnapshot
import com.suhrud.docsy.tools.MemorySnapshot
import com.suhrud.docsy.tools.MemoryTool
import com.suhrud.docsy.tools.NetworkSnapshot
import com.suhrud.docsy.tools.NetworkTool
import com.suhrud.docsy.tools.StepCounterTool
import com.suhrud.docsy.tools.StepSnapshot
import com.suhrud.docsy.tools.StorageCategoryCount
import com.suhrud.docsy.tools.StorageStatsSnapshot
import com.suhrud.docsy.tools.StorageStatsTool
import com.suhrud.docsy.tools.TopicDomain
import com.suhrud.docsy.tools.TopicIsolationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class Phase8DeviceToolsTest {

    @Test
    fun testDateTimeToolSnapshotAndFormatting() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val locale = Locale.US
        val snapshot = DateTimeTool.getSnapshot(locale, tz)

        assertTrue(snapshot.timeFormatted12h.isNotBlank())
        assertTrue(snapshot.dateFormatted.isNotBlank())
        assertTrue(snapshot.dayOfWeek.isNotBlank())
        assertEquals("Asia/Kolkata", snapshot.timeZone)

        val formatted = DateTimeTool.formatAnswer(snapshot)
        assertTrue(formatted.startsWith("It's "))
        assertTrue(formatted.contains("Asia/Kolkata"))
    }

    @Test
    fun testDateTimeToolDobRejection() {
        // DOB and personal dates must be rejected by DateTimeTool and routed to document facts (R8)
        assertTrue(DateTimeTool.isPersonalDateQuery("What is my date of birth?"))
        assertTrue(DateTimeTool.isPersonalDateQuery("tell me my DOB"))
        assertTrue(DateTimeTool.isPersonalDateQuery("When is papa's birthday?"))
        assertTrue(DateTimeTool.isPersonalDateQuery("janamdin kab hai?"))
        assertTrue(DateTimeTool.isPersonalDateQuery("passport expiry date"))

        // Pure clock queries must NOT be marked as personal date queries
        assertFalse(DateTimeTool.isPersonalDateQuery("What time is it?"))
        assertFalse(DateTimeTool.isPersonalDateQuery("What is today's date?"))
        assertFalse(DateTimeTool.isPersonalDateQuery("Which day is today?"))
    }

    @Test
    fun testBatteryToolFormatting() {
        val discharging = BatterySnapshot(
            percentage = 78,
            isCharging = false,
            chargingStatus = "Discharging",
            plugType = "Unplugged",
            health = "Good",
            temperatureCelsius = 31.5f,
            voltageMilliVolts = 4050
        )
        assertEquals("Battery is at 78%.", BatteryTool.formatAnswer(discharging))

        val charging = BatterySnapshot(
            percentage = 45,
            isCharging = true,
            chargingStatus = "Charging",
            plugType = "USB",
            health = "Good",
            temperatureCelsius = 33.0f,
            voltageMilliVolts = 4120
        )
        assertEquals("Battery is at 45% (Charging via USB).", BatteryTool.formatAnswer(charging))
    }

    @Test
    fun testNetworkToolFormatting() {
        val wifi = NetworkSnapshot(
            isConnected = true,
            transportType = "Wi-Fi",
            isWifi = true,
            isCellular = false,
            isAirplaneModeOn = false,
            downstreamBandwidthKbps = 150000,
            upstreamBandwidthKbps = 50000,
            linkSpeedMbps = 300,
            totalRxBytes = 1000000L,
            totalTxBytes = 500000L
        )
        assertEquals("Connected to Wi-Fi at 300 Mbps.", NetworkTool.formatAnswer(wifi))

        val airplane = NetworkSnapshot(
            isConnected = false,
            transportType = "None",
            isWifi = false,
            isCellular = false,
            isAirplaneModeOn = true,
            downstreamBandwidthKbps = 0,
            upstreamBandwidthKbps = 0,
            linkSpeedMbps = 0,
            totalRxBytes = 0L,
            totalTxBytes = 0L
        )
        assertTrue(NetworkTool.formatAnswer(airplane).contains("Airplane mode is ON"))
    }

    @Test
    fun testDisplayAndHardwareFormatting() {
        val display = DisplaySnapshot(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            densityScale = 2.75f,
            refreshRateHz = 120.0f
        )
        assertEquals("Display: 1080x2400 px at 120 Hz (440 DPI).", DisplayTool.formatAnswer(display))

        val memory = MemorySnapshot(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            availableRamBytes = 4L * 1024 * 1024 * 1024,
            usedRamBytes = 4L * 1024 * 1024 * 1024,
            isLowMemory = false,
            totalInternalStorageBytes = 128L * 1024 * 1024 * 1024,
            availableInternalStorageBytes = 64L * 1024 * 1024 * 1024
        )
        val memAnswer = MemoryTool.formatAnswer(memory)
        assertTrue(memAnswer.contains("RAM: 4.0 GB used of 8.0 GB"))
        assertTrue(memAnswer.contains("Internal Storage: 64.0 GB free of 128.0 GB"))

        val hw = HardwareSnapshot(
            model = "Pixel 8",
            manufacturer = "Google",
            brand = "google",
            device = "shiba",
            board = "shiba",
            androidVersion = "14",
            apiLevel = 34,
            isBluetoothEnabled = true,
            isNfcSupported = true,
            isNfcEnabled = true
        )
        assertEquals("Google Pixel 8 running Android 14 (API 34). Bluetooth is ON.", HardwareInfoTool.formatAnswer(hw))
    }

    @Test
    fun testStepCounterToolNonZeroGuarantee() {
        val nonZero = StepSnapshot(
            todaySteps = 4250,
            totalBootSteps = 15000L,
            source = "STEP_COUNTER_SENSOR_DELTA",
            isSensorAvailable = true,
            hasPermission = true
        )
        val formatted = StepCounterTool.formatAnswer(nonZero)
        assertTrue(formatted.contains("4,250 steps today") || formatted.contains("4250 steps today"))

        val zero = StepSnapshot(
            todaySteps = 0,
            totalBootSteps = 0L,
            source = "Hardware step sensor",
            isSensorAvailable = true,
            hasPermission = true
        )
        assertTrue(StepCounterTool.formatAnswer(zero).contains("0 steps today"))
    }

    @Test
    fun testContactsToolRelationshipAliases() {
        val momTerms = ContactsTool.getExpandedSearchTerms("mom")
        assertTrue(momTerms.contains("mom"))
        assertTrue(momTerms.contains("mother"))
        assertTrue(momTerms.contains("mummy"))
        assertTrue(momTerms.contains("maa"))

        val dadTerms = ContactsTool.getExpandedSearchTerms("papa")
        assertTrue(dadTerms.contains("papa"))
        assertTrue(dadTerms.contains("dad"))
        assertTrue(dadTerms.contains("father"))

        val contacts = listOf(
            ContactItem("Mom (Sunita)", "+919876543210", matchedAlias = "mom")
        )
        val answer = ContactsTool.formatAnswer(contacts, "mom")
        assertEquals("Mom (Sunita): +919876543210", answer)
    }

    @Test
    fun testStorageStatsToolFormattingAndSuperlatives() {
        assertEquals("500 B", StorageStatsTool.formatFileSize(500L))
        assertEquals("150 KB", StorageStatsTool.formatFileSize(150 * 1024L))
        assertEquals("25.5 MB", StorageStatsTool.formatFileSize((25.5 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", StorageStatsTool.formatFileSize((1.25 * 1024 * 1024 * 1024).toLong()))

        val dummyLargest = DocumentEntity(
            id = 1,
            path = "/storage/emulated/0/DCIM/movie_recording.mp4",
            name = "movie_recording.mp4",
            ext = "mp4",
            mime = "video/mp4",
            size = 1024L * 1024 * 1024 * 2, // 2 GB
            modified = 1700000000000L,
            state = DocumentStatus.INDEXED.name
        )

        val snapshot = StorageStatsSnapshot(
            totalFiles = 100,
            totalSizeBytes = 3L * 1024 * 1024 * 1024,
            categories = listOf(
                StorageCategoryCount("Videos", 10, 2L * 1024 * 1024 * 1024),
                StorageCategoryCount("Documents", 50, 100L * 1024 * 1024)
            ),
            largestFile = dummyLargest,
            smallestFile = null,
            newestFile = null,
            oldestFile = null
        )

        val videoCountAnswer = StorageStatsTool.formatCategoryCountAnswer(snapshot, "how many videos?")
        assertTrue(videoCountAnswer.contains("10 videos on this device"))

        val largestAnswer = StorageStatsTool.formatSuperlativeAnswer(snapshot, "what is my largest file?")
        assertTrue(largestAnswer.contains("The largest file is \"movie_recording.mp4\" (2.00 GB)."))
    }

    @Test
    fun testTopicIsolationFilterEnforcement() {
        // Telemetry answering shouldn't mention document facts
        val contaminatedTelemetry = "Battery is at 85%. Also found your 12th marksheet with roll number 12345."
        assertTrue(TopicIsolationFilter.hasTopicBleed(contaminatedTelemetry, TopicDomain.BATTERY_HARDWARE))

        val cleanTelemetry = TopicIsolationFilter.enforceIsolation(contaminatedTelemetry, TopicDomain.BATTERY_HARDWARE)
        assertFalse(cleanTelemetry.contains("marksheet"))
        assertFalse(cleanTelemetry.contains("roll number"))

        // Document fact answering shouldn't mention battery or RAM
        val contaminatedDoc = "Your 12th percentage is 88.4%. Battery is at 85%."
        assertTrue(TopicIsolationFilter.hasTopicBleed(contaminatedDoc, TopicDomain.DOCUMENT_FACT))

        val cleanDoc = TopicIsolationFilter.enforceIsolation(contaminatedDoc, TopicDomain.DOCUMENT_FACT)
        assertFalse(cleanDoc.contains("Battery is at"))
    }
}
