package com.suhrud.docsy

import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.tools.AudioThermalSnapshot
import com.suhrud.docsy.tools.BatterySpecsSnapshot
import com.suhrud.docsy.tools.CameraSpecsSnapshot
import com.suhrud.docsy.tools.ConnectivitySnapshot
import com.suhrud.docsy.tools.CpuSnapshot
import com.suhrud.docsy.tools.DeviceHardwareSnapshot
import com.suhrud.docsy.tools.DisplaySpecsSnapshot
import com.suhrud.docsy.tools.GpuSnapshot
import com.suhrud.docsy.tools.PhoneSpecsReader
import com.suhrud.docsy.tools.PhoneSystemSnapshot
import com.suhrud.docsy.tools.RamSnapshot
import com.suhrud.docsy.tools.SensorsSnapshot
import com.suhrud.docsy.tools.SoftwareSnapshot
import com.suhrud.docsy.tools.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSpecsUnitTest {

    @Test
    fun testFormatters() {
        assertEquals("0 B", PhoneSpecsReader.formatBytes(0L))
        assertEquals("1.0 KB", PhoneSpecsReader.formatBytes(1024L))
        assertEquals("1.0 MB", PhoneSpecsReader.formatBytes(1024L * 1024L))
        assertEquals("4.0 GB", PhoneSpecsReader.formatBytes(4L * 1024L * 1024L * 1024L))

        assertEquals("1h 1m 1s", PhoneSpecsReader.formatUptime(3661000L))
        assertEquals("1d 1h 0m", PhoneSpecsReader.formatUptime(90000000L))

        assertEquals("2.40 GHz", PhoneSpecsReader.formatFrequencyKhz(2400000L))
        assertEquals("800 MHz", PhoneSpecsReader.formatFrequencyKhz(800000L))

        assertEquals("37.5 °C", PhoneSpecsReader.formatTemperature(37.5f))
    }

    private fun createFakeSnapshot(): PhoneSystemSnapshot {
        val sw = SoftwareSnapshot(
            androidVersion = "14",
            apiLevel = 34,
            codename = "UpsideDownCake",
            securityPatch = "2024-05-01",
            buildDisplay = "UP1A.231005.007",
            buildId = "UP1A.231005.007",
            fingerprint = "google/shiba/shiba:14/UP1A.231005.007:user/release-keys",
            buildType = "user",
            buildTags = "release-keys",
            buildTimeFormatted = "2024-05-01 10:00",
            incremental = "10987654",
            bootloader = "shiba-1.0",
            radioVersion = "g5300g-231005",
            baseOs = "not exposed",
            previewSdk = "0",
            vendorSkin = "not exposed",
            kernelShort = "5.15.110-android14",
            kernelFull = "Linux version 5.15.110-android14-9-g8765432 (build-user@build-host)",
            playServicesVersion = "24.18.14",
            webViewVersion = "124.0.6367.82",
            playStoreVersion = "40.8.21",
            language = "English",
            locale = "en-US",
            timeZone = "UTC",
            timeFormat = "24-Hour",
            uptimeFormatted = "5h 23m 10s",
            bootTimeFormatted = "2024-05-01 04:36",
            trebleSupport = "true",
            abPartitionSupport = "true",
            dynamicPartitions = "true",
            selinuxMode = "Enforcing",
            screenLockSet = "Enabled",
            devOptionsEnabled = "Disabled",
            usbDebuggingEnabled = "Disabled",
            installUnknownApps = "Denied",
            storageEncryption = "Encrypted (Active)"
        )

        val hw = DeviceHardwareSnapshot(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8",
            product = "shiba",
            device = "shiba",
            board = "shiba",
            hardware = "zuma",
            socManufacturer = "Google",
            socModel = "Tensor G3",
            supportedAbis = "arm64-v8a",
            cpu = CpuSnapshot(
                coreCount = 9,
                supportedAbis = "arm64-v8a",
                perCoreFrequencies = "Core 0: 1800 MHz / max 2150 MHz",
                cpuModelAndFeatures = "Cortex-X3; Features: fp asimd",
                governor = "schedutil"
            ),
            gpu = GpuSnapshot(
                renderer = "Mali-G715",
                vendor = "ARM",
                version = "OpenGL ES 3.2",
                vulkanSupport = "Supported"
            ),
            ram = RamSnapshot(
                totalRamBytes = 8L * 1024L * 1024L * 1024L,
                availableRamBytes = 3L * 1024L * 1024L * 1024L,
                usedRamBytes = 5L * 1024L * 1024L * 1024L,
                isLowMemory = false,
                thresholdBytes = 512L * 1024L * 1024L,
                zRamSwapInfo = "SwapTotal: 4096000 kB, SwapFree: 2048000 kB"
            ),
            storage = StorageSnapshot(
                internalTotalBytes = 128L * 1024L * 1024L * 1024L,
                internalUsedBytes = 64L * 1024L * 1024L * 1024L,
                internalFreeBytes = 64L * 1024L * 1024L * 1024L,
                sdCardPresent = false,
                sdCardDetails = "Not Present",
                filesystemType = "this phone doesn't expose it",
                encryptionStatus = "Encrypted"
            )
        )

        val disp = DisplaySpecsSnapshot(
            resolution = "1080x2400 px",
            densityDpi = 420,
            physicalSizeEstimateInches = "6.2\"",
            refreshRateHz = 120.0f,
            supportedRefreshModes = "1080x2400 @ 60Hz, 1080x2400 @ 120Hz",
            hdrSupport = "Supported (HDR10, HLG)",
            wideColorGamut = "Supported",
            brightness = "180 / 255",
            screenTimeout = "30s",
            fontScale = 1.0f,
            darkMode = "Enabled",
            orientation = "Portrait (0°)",
            notchCutout = "Present"
        )

        val bat = BatterySpecsSnapshot(
            levelPercent = 85,
            status = "Charging",
            plugType = "USB",
            health = "Good",
            temperatureCelsius = 31.2f,
            voltageMv = 4120,
            technology = "Li-ion",
            capacityMah = "4575 mAh",
            cycleCount = "142",
            powerSaverMode = "Inactive"
        )

        val cams = CameraSpecsSnapshot(
            totalCameras = 2,
            cameraDetails = listOf("Camera 0 (Rear): 50.0 MP, Flash: Yes", "Camera 1 (Front): 10.5 MP, Flash: No")
        )

        val sensors = SensorsSnapshot(
            totalSensors = 32,
            sensorSummary = listOf("Accelerometer (Bosch)", "Gyroscope (Bosch)", "Proximity (AMS)")
        )

        val conn = ConnectivitySnapshot(
            wifiState = "Connected",
            wifiLinkSpeed = "866 Mbps",
            wifiSignal = "-55 dBm",
            bluetoothState = "Enabled",
            bluetoothLeSupport = "Supported",
            bluetoothLeAudio = "Supported",
            nfcStatus = "Supported & Enabled",
            gpsLocationServices = "Enabled",
            usbFeatures = "Host/OTG Supported",
            uwbSupport = "Not supported",
            irBlasterSupport = "Not supported",
            biometricsHardware = "Fingerprint Sensor, Face Authentication",
            simCount = "2",
            simOperator = "Jio 5G",
            networkType = "Wi-Fi (Connected)",
            esimSupport = "Supported",
            airplaneMode = "Disabled",
            vpnState = "Disconnected"
        )

        val audio = AudioThermalSnapshot(
            ringerMode = "Normal",
            volumeLevels = "Media: 10/15, Ring: 8/15",
            vibratorHaptics = "Supported (with amplitude control)",
            speakersHeadsetState = "Internal Speakers Active",
            thermalStatus = "Nominal (None)",
            availableSystemFeaturesCount = 120
        )

        return PhoneSystemSnapshot(
            timestampMillis = 1714560000000L,
            formattedTimestamp = "as of 12:00",
            software = sw,
            hardware = hw,
            display = disp,
            battery = bat,
            cameras = cams,
            sensors = sensors,
            connectivity = conn,
            audioThermal = audio
        )
    }

    @Test
    fun testTopicScopingStrictIsolation() {
        val snapshot = createFakeSnapshot()

        // Kernel query returns facts ONLY about kernel, no battery or RAM facts
        val (kernelReply, kernelCard) = PhoneSpecsReader.answerKernel(snapshot)
        assertTrue(kernelReply.contains("5.15.110"))
        assertTrue(kernelCard.items.any { it.first.contains("Kernel") || it.first.contains("Version") })
        assertFalse(kernelReply.contains("Battery"))
        assertFalse(kernelReply.contains("RAM"))
        assertFalse(kernelReply.contains("mAh"))

        // Battery query returns facts ONLY about battery, no kernel or cpu facts
        val (batteryReply, batteryCard) = PhoneSpecsReader.answerBattery(snapshot)
        assertTrue(batteryReply.contains("85%"))
        assertTrue(batteryCard.items.any { it.first.contains("Battery Level") })
        assertFalse(batteryReply.contains("Kernel"))
        assertFalse(batteryReply.contains("Tensor"))
        assertFalse(batteryReply.contains("Linux version"))

        // Refresh rate query returns facts ONLY about display/refresh rate
        val (refreshReply, refreshCard) = PhoneSpecsReader.answerRefreshRate(snapshot)
        assertTrue(refreshReply.contains("120 Hz"))
        assertFalse(refreshReply.contains("Battery"))
        assertFalse(refreshReply.contains("SIM"))
    }

    @Test
    fun testMissingPropertyHonesty() {
        val snapshot = createFakeSnapshot()
        // If a property is not exposed, it must state "not exposed" or "this phone doesn't expose it"
        assertEquals("not exposed", snapshot.software.baseOs)
        assertEquals("this phone doesn't expose it", snapshot.hardware.storage.filesystemType)
    }

    @Test
    fun testSynonymsTyposAndHinglishRouting() {
        assertEquals(DocsyIntent.PHONE_KERNEL, IntentRouter.route("kernal").intent)
        assertEquals(DocsyIntent.PHONE_KERNEL, IntentRouter.route("os version").intent)
        assertEquals(DocsyIntent.PHONE_KERNEL, IntentRouter.route("what is my linux version").intent)

        assertEquals(DocsyIntent.PHONE_BUILD_NUMBER, IntentRouter.route("build no").intent)
        assertEquals(DocsyIntent.PHONE_BUILD_NUMBER, IntentRouter.route("fingerprint").intent)

        assertEquals(DocsyIntent.PHONE_ANDROID_VERSION, IntentRouter.route("firmware").intent)
        assertEquals(DocsyIntent.PHONE_ANDROID_VERSION, IntentRouter.route("android version").intent)

        assertEquals(DocsyIntent.PHONE_SECURITY_PATCH, IntentRouter.route("security patch").intent)
        assertEquals(DocsyIntent.PHONE_CHIPSET_CPU, IntentRouter.route("chipset").intent)
        assertEquals(DocsyIntent.PHONE_CHIPSET_CPU, IntentRouter.route("cpu frequency").intent)

        assertEquals(DocsyIntent.PHONE_REFRESH_RATE, IntentRouter.route("refresh rate").intent)
        assertEquals(DocsyIntent.PHONE_REFRESH_RATE, IntentRouter.route("how many hz screen").intent)

        assertEquals(DocsyIntent.MEMORY_RAM_STATUS, IntentRouter.route("ram kitni hai").intent)
        assertEquals(DocsyIntent.MEMORY_RAM_STATUS, IntentRouter.route("ram").intent)

        assertEquals(DocsyIntent.PHONE_OVERVIEW_SUMMARY, IntentRouter.route("tell me about my phone").intent)
        assertEquals(DocsyIntent.PHONE_FULL_SPECS, IntentRouter.route("all details").intent)
        assertEquals(DocsyIntent.PHONE_FULL_SPECS, IntentRouter.route("full specs").intent)
        assertEquals(DocsyIntent.PHONE_FULL_SPECS, IntentRouter.route("everything about my phone").intent)
    }
}
