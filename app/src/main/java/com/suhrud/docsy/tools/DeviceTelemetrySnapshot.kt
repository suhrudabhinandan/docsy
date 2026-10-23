package com.suhrud.docsy.tools

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.Vibrator
import android.provider.Settings
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

data class PhoneSpecCardData(
    val title: String,
    val timestampText: String,
    val items: List<Pair<String, String>>,
    val expandableItems: List<Pair<String, String>> = emptyList()
)

data class SoftwareSnapshot(
    val androidVersion: String,
    val apiLevel: Int,
    val codename: String,
    val securityPatch: String,
    val buildDisplay: String,
    val buildId: String,
    val fingerprint: String,
    val buildType: String,
    val buildTags: String,
    val buildTimeFormatted: String,
    val incremental: String,
    val bootloader: String,
    val radioVersion: String,
    val baseOs: String,
    val previewSdk: String,
    val vendorSkin: String,
    val kernelShort: String,
    val kernelFull: String,
    val playServicesVersion: String,
    val webViewVersion: String,
    val playStoreVersion: String,
    val language: String,
    val locale: String,
    val timeZone: String,
    val timeFormat: String,
    val uptimeFormatted: String,
    val bootTimeFormatted: String,
    val trebleSupport: String,
    val abPartitionSupport: String,
    val dynamicPartitions: String,
    val selinuxMode: String,
    val screenLockSet: String,
    val devOptionsEnabled: String,
    val usbDebuggingEnabled: String,
    val installUnknownApps: String,
    val storageEncryption: String
)

data class CpuSnapshot(
    val coreCount: Int,
    val supportedAbis: String,
    val perCoreFrequencies: String,
    val cpuModelAndFeatures: String,
    val governor: String
)

data class GpuSnapshot(
    val renderer: String,
    val vendor: String,
    val version: String,
    val vulkanSupport: String
)

data class RamSnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val isLowMemory: Boolean,
    val thresholdBytes: Long,
    val zRamSwapInfo: String
)

data class StorageSnapshot(
    val internalTotalBytes: Long,
    val internalUsedBytes: Long,
    val internalFreeBytes: Long,
    val sdCardPresent: Boolean,
    val sdCardDetails: String,
    val filesystemType: String,
    val encryptionStatus: String
)

data class DisplaySpecsSnapshot(
    val resolution: String,
    val densityDpi: Int,
    val physicalSizeEstimateInches: String,
    val refreshRateHz: Float,
    val supportedRefreshModes: String,
    val hdrSupport: String,
    val wideColorGamut: String,
    val brightness: String,
    val screenTimeout: String,
    val fontScale: Float,
    val darkMode: String,
    val orientation: String,
    val notchCutout: String
)

data class BatterySpecsSnapshot(
    val levelPercent: Int,
    val status: String,
    val plugType: String,
    val health: String,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val technology: String,
    val capacityMah: String,
    val cycleCount: String,
    val powerSaverMode: String
)

data class CameraSpecsSnapshot(
    val totalCameras: Int,
    val cameraDetails: List<String>
)

data class SensorsSnapshot(
    val totalSensors: Int,
    val sensorSummary: List<String>
)

data class ConnectivitySnapshot(
    val wifiState: String,
    val wifiLinkSpeed: String,
    val wifiSignal: String,
    val bluetoothState: String,
    val bluetoothLeSupport: String,
    val bluetoothLeAudio: String,
    val nfcStatus: String,
    val gpsLocationServices: String,
    val usbFeatures: String,
    val uwbSupport: String,
    val irBlasterSupport: String,
    val biometricsHardware: String,
    val simCount: String,
    val simOperator: String,
    val networkType: String,
    val esimSupport: String,
    val airplaneMode: String,
    val vpnState: String
)

data class AudioThermalSnapshot(
    val ringerMode: String,
    val volumeLevels: String,
    val vibratorHaptics: String,
    val speakersHeadsetState: String,
    val thermalStatus: String,
    val availableSystemFeaturesCount: Int
)

data class DeviceHardwareSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val product: String,
    val device: String,
    val board: String,
    val hardware: String,
    val socManufacturer: String,
    val socModel: String,
    val supportedAbis: String,
    val cpu: CpuSnapshot,
    val gpu: GpuSnapshot,
    val ram: RamSnapshot,
    val storage: StorageSnapshot
)

data class PhoneSystemSnapshot(
    val timestampMillis: Long,
    val formattedTimestamp: String,
    val software: SoftwareSnapshot,
    val hardware: DeviceHardwareSnapshot,
    val display: DisplaySpecsSnapshot,
    val battery: BatterySpecsSnapshot,
    val cameras: CameraSpecsSnapshot,
    val sensors: SensorsSnapshot,
    val connectivity: ConnectivitySnapshot,
    val audioThermal: AudioThermalSnapshot
)

object PhoneSpecsReader {

    @Volatile
    private var cachedSnapshot: PhoneSystemSnapshot? = null
    @Volatile
    private var lastSnapshotReadTime: Long = 0L

    fun getLiveSnapshot(context: Context): PhoneSystemSnapshot {
        val nowElapsed = SystemClock.elapsedRealtime()
        val cached = cachedSnapshot
        if (cached != null && (nowElapsed - lastSnapshotReadTime) <= 2000L) {
            return cached
        }

        val snapshot = readLiveSnapshot(context.applicationContext ?: context)
        cachedSnapshot = snapshot
        lastSnapshotReadTime = nowElapsed
        return snapshot
    }

    private fun readLiveSnapshot(ctx: Context): PhoneSystemSnapshot {
        val now = System.currentTimeMillis()
        val timeZone = TimeZone.getDefault()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val asOfText = "as of " + timeFormat.format(Date(now))

        val software = readSoftware(ctx)
        val hardware = readHardware(ctx)
        val display = readDisplay(ctx)
        val battery = readBattery(ctx)
        val cameras = readCameras(ctx)
        val sensors = readSensors(ctx)
        val connectivity = readConnectivity(ctx)
        val audioThermal = readAudioThermal(ctx)

        return PhoneSystemSnapshot(
            timestampMillis = now,
            formattedTimestamp = asOfText,
            software = software,
            hardware = hardware,
            display = display,
            battery = battery,
            cameras = cameras,
            sensors = sensors,
            connectivity = connectivity,
            audioThermal = audioThermal
        )
    }

    // ==========================================================
    // Software Reader
    // ==========================================================
    private fun readSoftware(ctx: Context): SoftwareSnapshot {
        val pm = ctx.packageManager

        // Reflection on SystemProperties
        fun getSystemProperty(key: String): String? {
            return try {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                val value = method.invoke(null, key, "") as? String
                if (!value.isNullOrBlank()) value else null
            } catch (_: Throwable) {
                null
            }
        }

        val vendorSkin = sequenceOf(
            "ro.miui.ui.version.name",
            "ro.build.version.opporom",
            "ro.oplus.version",
            "ro.vivo.os.build.display.id",
            "ro.build.version.emui",
            "ro.build.version.oneui",
            "ro.lineage.version",
            "ro.build.version.incremental"
        ).mapNotNull { key ->
            val v = getSystemProperty(key)
            if (v != null) "$key: $v" else null
        }.firstOrNull() ?: "not exposed"

        val kernelShort = System.getProperty("os.version") ?: "not exposed"
        val kernelFull = try {
            val procVer = File("/proc/version")
            if (procVer.exists() && procVer.canRead()) {
                procVer.readText().trim()
            } else {
                kernelShort
            }
        } catch (_: Throwable) {
            kernelShort
        }

        fun getPkgVersion(pkg: String): String {
            return try {
                pm.getPackageInfo(pkg, 0).versionName ?: "not exposed"
            } catch (_: Throwable) {
                "not exposed"
            }
        }

        val gmsVer = getPkgVersion("com.google.android.gms")
        val webViewVer = getPkgVersion("com.google.android.webview").let {
            if (it == "not exposed") getPkgVersion("com.android.chrome") else it
        }
        val playStoreVer = getPkgVersion("com.android.vending")

        val uptimeMs = SystemClock.elapsedRealtime()
        val bootTimeMs = System.currentTimeMillis() - uptimeMs
        val bootTimeFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(bootTimeMs))

        val treble = getSystemProperty("ro.treble.enabled") ?: "not exposed"
        val abPartition = getSystemProperty("ro.build.ab_update") ?: "not exposed"
        val dynamicPartitions = getSystemProperty("ro.boot.dynamic_partitions") ?: "not exposed"

        val selinux = try {
            val selinuxFile = File("/sys/fs/selinux/enforce")
            if (selinuxFile.exists() && selinuxFile.canRead()) {
                val enforce = selinuxFile.readText().trim()
                if (enforce == "1") "Enforcing" else "Permissive"
            } else {
                getSystemProperty("ro.boot.selinux") ?: "not exposed"
            }
        } catch (_: Throwable) {
            "not exposed"
        }

        val keyguard = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isScreenLockSet = keyguard?.isDeviceSecure ?: false

        val cr = ctx.contentResolver
        val devOptionsOn = try {
            Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }

        val usbDebugOn = try {
            Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }

        val unknownSources = try {
            pm.canRequestPackageInstalls()
        } catch (_: Throwable) {
            false
        }

        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val encStatus = when (dpm?.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "Encrypted (Active)"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "Encrypted (Per-User)"
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "Not Encrypted"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING -> "Activating Encryption"
            else -> "Encrypted"
        }

        val baseOs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val b = Build.VERSION.BASE_OS
            if (!b.isNullOrBlank()) b else "not exposed"
        } else "not exposed"

        val previewSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.PREVIEW_SDK_INT.toString()
        } else "0"

        val buildTimeFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(Build.TIME))

        return SoftwareSnapshot(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            codename = Build.VERSION.CODENAME,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH ?: "not exposed"
            } else "not exposed",
            buildDisplay = Build.DISPLAY,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
            buildType = Build.TYPE,
            buildTags = Build.TAGS ?: "not exposed",
            buildTimeFormatted = buildTimeFormatted,
            incremental = Build.VERSION.INCREMENTAL,
            bootloader = Build.BOOTLOADER,
            radioVersion = try { Build.getRadioVersion() ?: "not exposed" } catch (_: Throwable) { "not exposed" },
            baseOs = baseOs,
            previewSdk = previewSdk,
            vendorSkin = vendorSkin,
            kernelShort = kernelShort,
            kernelFull = kernelFull,
            playServicesVersion = gmsVer,
            webViewVersion = webViewVer,
            playStoreVersion = playStoreVer,
            language = Locale.getDefault().displayLanguage,
            locale = Locale.getDefault().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
            timeFormat = if (DateFormat.is24HourFormat(ctx)) "24-Hour" else "12-Hour",
            uptimeFormatted = formatUptime(uptimeMs),
            bootTimeFormatted = bootTimeFormatted,
            trebleSupport = treble,
            abPartitionSupport = abPartition,
            dynamicPartitions = dynamicPartitions,
            selinuxMode = selinux,
            screenLockSet = if (isScreenLockSet) "Enabled" else "Disabled",
            devOptionsEnabled = if (devOptionsOn) "Enabled" else "Disabled",
            usbDebuggingEnabled = if (usbDebugOn) "Enabled" else "Disabled",
            installUnknownApps = if (unknownSources) "Granted" else "Denied",
            storageEncryption = encStatus
        )
    }

    // ==========================================================
    // Hardware Reader
    // ==========================================================
    private fun readHardware(ctx: Context): DeviceHardwareSnapshot {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = totalRam - availRam

        // Read /proc/meminfo for zRAM / swap
        var zramSwap = "this phone doesn't expose it"
        try {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists() && memInfoFile.canRead()) {
                val lines = memInfoFile.readLines()
                val swapTotal = lines.firstOrNull { it.startsWith("SwapTotal:") }?.substringAfter(":")?.trim()
                val swapFree = lines.firstOrNull { it.startsWith("SwapFree:") }?.substringAfter(":")?.trim()
                if (swapTotal != null && swapFree != null) {
                    zramSwap = "SwapTotal: $swapTotal, SwapFree: $swapFree"
                }
            }
        } catch (_: Throwable) {}

        val ramSnapshot = RamSnapshot(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            usedRamBytes = usedRam,
            isLowMemory = memInfo.lowMemory,
            thresholdBytes = memInfo.threshold,
            zRamSwapInfo = zramSwap
        )

        // Storage
        val dataStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = dataStat.totalBytes
        val internalFree = dataStat.availableBytes
        val internalUsed = internalTotal - internalFree
        val sdCardPresent = try {
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        } catch (_: Throwable) {
            false
        }

        val storageSnapshot = StorageSnapshot(
            internalTotalBytes = internalTotal,
            internalUsedBytes = internalUsed,
            internalFreeBytes = internalFree,
            sdCardPresent = sdCardPresent,
            sdCardDetails = if (sdCardPresent) "Mounted" else "Not Present",
            filesystemType = "this phone doesn't expose it",
            encryptionStatus = "Encrypted"
        )

        // CPU
        val cores = Runtime.getRuntime().availableProcessors()
        val perCoreFrequencies = try {
            val freqs = mutableListOf<String>()
            for (i in 0 until cores) {
                val curFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val cur = if (curFreqFile.exists() && curFreqFile.canRead()) curFreqFile.readText().trim() else null
                val max = if (maxFreqFile.exists() && maxFreqFile.canRead()) maxFreqFile.readText().trim() else null
                if (cur != null || max != null) {
                    val curStr = cur?.let { "${it.toLongOrNull()?.div(1000) ?: it} MHz" } ?: "?"
                    val maxStr = max?.let { "${it.toLongOrNull()?.div(1000) ?: it} MHz" } ?: "?"
                    freqs.add("Core $i: $curStr / max $maxStr")
                }
            }
            if (freqs.isNotEmpty()) freqs.joinToString(", ") else "this phone doesn't expose it"
        } catch (_: Throwable) {
            "this phone doesn't expose it"
        }

        var cpuModelFeatures = "this phone doesn't expose it"
        var cpuGovernor = "this phone doesn't expose it"
        try {
            val cpuinfo = File("/proc/cpuinfo")
            if (cpuinfo.exists() && cpuinfo.canRead()) {
                val lines = cpuinfo.readLines()
                val hardwareLine = lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }?.substringAfter(":")?.trim()
                val modelName = lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }?.substringAfter(":")?.trim()
                val features = lines.firstOrNull { it.startsWith("Features", ignoreCase = true) }?.substringAfter(":")?.trim()
                val parts = listOfNotNull(modelName, hardwareLine, features?.let { "Features: $it" })
                if (parts.isNotEmpty()) cpuModelFeatures = parts.joinToString("; ")
            }
            val govFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (govFile.exists() && govFile.canRead()) {
                cpuGovernor = govFile.readText().trim()
            }
        } catch (_: Throwable) {}

        val cpuSnapshot = CpuSnapshot(
            coreCount = cores,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            perCoreFrequencies = perCoreFrequencies,
            cpuModelAndFeatures = cpuModelFeatures,
            governor = cpuGovernor
        )

        // GPU via offscreen EGL query
        val gpuSnapshot = queryGpuOffscreen(ctx)

        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = Build.SOC_MANUFACTURER
            if (!m.isNullOrBlank()) m else "this phone doesn't expose it"
        } else "this phone doesn't expose it"

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = Build.SOC_MODEL
            if (!m.isNullOrBlank()) m else "this phone doesn't expose it"
        } else "this phone doesn't expose it"

        return DeviceHardwareSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            socManufacturer = socManufacturer,
            socModel = socModel,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            cpu = cpuSnapshot,
            gpu = gpuSnapshot,
            ram = ramSnapshot,
            storage = storageSnapshot
        )
    }

    private fun queryGpuOffscreen(ctx: Context): GpuSnapshot {
        var renderer = "not exposed"
        var vendor = "not exposed"
        var version = "not exposed"

        try {
            val egl = EGLContext.getEGL() as? EGL10
            if (egl != null) {
                val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
                if (display != EGL10.EGL_NO_DISPLAY) {
                    val versionArr = IntArray(2)
                    if (egl.eglInitialize(display, versionArr)) {
                        val configs = arrayOfNulls<EGLConfig>(1)
                        val numConfigs = IntArray(1)
                        val configSpec = intArrayOf(
                            EGL10.EGL_RENDERABLE_TYPE, 4,
                            EGL10.EGL_NONE
                        )
                        egl.eglChooseConfig(display, configSpec, configs, 1, numConfigs)
                        if (numConfigs[0] > 0 && configs[0] != null) {
                            val context = egl.eglCreateContext(
                                display, configs[0], EGL10.EGL_NO_CONTEXT,
                                intArrayOf(0x3098, 2, EGL10.EGL_NONE)
                            )
                            val surface = egl.eglCreatePbufferSurface(
                                display, configs[0],
                                intArrayOf(EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE)
                            )
                            if (context != EGL10.EGL_NO_CONTEXT && surface != EGL10.EGL_NO_SURFACE) {
                                egl.eglMakeCurrent(display, surface, surface, context)
                                renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: "not exposed"
                                vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR) ?: "not exposed"
                                version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION) ?: "not exposed"
                                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                                egl.eglDestroySurface(display, surface)
                            }
                            if (context != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, context)
                        }
                        egl.eglTerminate(display)
                    }
                }
            }
        } catch (_: Throwable) {}

        val pm = ctx.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val vulkanVersion = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        val vulkanText = if (hasVulkan || vulkanVersion) "Supported (Level/Version feature detected)" else "Not supported / not exposed"

        return GpuSnapshot(
            renderer = renderer,
            vendor = vendor,
            version = version,
            vulkanSupport = vulkanText
        )
    }

    // ==========================================================
    // Display Reader
    // ==========================================================
    private fun readDisplay(ctx: Context): DisplaySpecsSnapshot {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = wm?.defaultDisplay
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)

        val wPx = metrics.widthPixels
        val hPx = metrics.heightPixels
        val dpi = metrics.densityDpi

        @Suppress("DEPRECATION")
        val refreshRate = display?.refreshRate ?: 60.0f
        val supportedModes = display?.supportedModes?.joinToString(", ") {
            "${it.physicalWidth}x${it.physicalHeight} @ ${it.refreshRate.toInt()}Hz"
        } ?: "${wPx}x${hPx} @ ${refreshRate.toInt()}Hz"

        val xInches = if (metrics.xdpi > 0) wPx / metrics.xdpi else 0f
        val yInches = if (metrics.ydpi > 0) hPx / metrics.ydpi else 0f
        val diagonal = if (xInches > 0 && yInches > 0) {
            String.format(Locale.US, "%.1f\"", Math.sqrt((xInches * xInches + yInches * yInches).toDouble()))
        } else "not exposed"

        val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val types = display?.hdrCapabilities?.supportedHdrTypes
            if (types != null && types.isNotEmpty()) "Supported (${types.joinToString()})" else "None"
        } else "not exposed"

        val wideColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (display?.isWideColorGamut == true) "Supported" else "Not supported"
        } else "not exposed"

        val cr = ctx.contentResolver
        val brightness = try {
            val b = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            "$b / 255"
        } catch (_: Throwable) {
            "not exposed"
        }

        val timeout = try {
            val t = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
            "${t / 1000}s"
        } catch (_: Throwable) {
            "not exposed"
        }

        val fontScale = ctx.resources.configuration.fontScale
        val isDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        @Suppress("DEPRECATION")
        val orientation = when (display?.rotation) {
            Surface.ROTATION_0 -> "Portrait (0°)"
            Surface.ROTATION_90 -> "Landscape (90°)"
            Surface.ROTATION_180 -> "Reverse Portrait (180°)"
            Surface.ROTATION_270 -> "Reverse Landscape (270°)"
            else -> "Portrait"
        }

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            display?.cutout?.let { "Present" } ?: "None"
        } else "None"

        return DisplaySpecsSnapshot(
            resolution = "${wPx}x${hPx} px",
            densityDpi = dpi,
            physicalSizeEstimateInches = diagonal,
            refreshRateHz = refreshRate,
            supportedRefreshModes = supportedModes,
            hdrSupport = hdr,
            wideColorGamut = wideColor,
            brightness = brightness,
            screenTimeout = timeout,
            fontScale = fontScale,
            darkMode = if (isDark) "Enabled" else "Disabled",
            orientation = orientation,
            notchCutout = cutout
        )
    }

    // ==========================================================
    // Battery Reader
    // ==========================================================
    private fun readBattery(ctx: Context): BatterySpecsSnapshot {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = ctx.registerReceiver(null, filter)

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusStr = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugStr = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Power"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Unplugged"
            else -> "Unplugged"
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (tempTenths > 0) tempTenths / 10.0f else 0.0f

        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacityMah = try {
            val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
            if (chargeCounter > 0) "${chargeCounter / 1000} mAh" else "this phone doesn't expose it"
        } catch (_: Throwable) {
            "this phone doesn't expose it"
        }

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // BatteryManager.BATTERY_PROPERTY_CYCLE_COUNT is 8 on API 34+
                val cycles = bm?.getIntProperty(8) ?: -1
                if (cycles >= 0) cycles.toString() else "this phone doesn't expose it"
            } catch (_: Throwable) {
                "this phone doesn't expose it"
            }
        } else {
            "this phone doesn't expose it"
        }

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = if (pm?.isPowerSaveMode == true) "Active" else "Inactive"

        return BatterySpecsSnapshot(
            levelPercent = pct,
            status = statusStr,
            plugType = plugStr,
            health = healthStr,
            temperatureCelsius = tempCelsius,
            voltageMv = voltage,
            technology = tech,
            capacityMah = capacityMah,
            cycleCount = cycleCount,
            powerSaverMode = powerSave
        )
    }

    // ==========================================================
    // Camera Reader
    // ==========================================================
    private fun readCameras(ctx: Context): CameraSpecsSnapshot {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cm == null) return CameraSpecsSnapshot(0, emptyList())

        val list = mutableListOf<String>()
        try {
            val ids = cm.cameraIdList
            for (id in ids) {
                val chars = cm.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                val pixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val mp = if (pixelSize != null) {
                    val totalPixels = pixelSize.width.toLong() * pixelSize.height.toLong()
                    String.format(Locale.US, "%.1f MP (%dx%d)", totalPixels / 1_000_000.0, pixelSize.width, pixelSize.height)
                } else "not exposed"

                val flash = if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) "Yes" else "No"
                val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                    else -> "External"
                }
                list.add("Camera $id ($facing): $mp, Flash: $flash, HW Level: $hwLevel")
            }
        } catch (_: Throwable) {}

        return CameraSpecsSnapshot(list.size, list)
    }

    // ==========================================================
    // Sensors Reader
    // ==========================================================
    private fun readSensors(ctx: Context): SensorsSnapshot {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val list = sm?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
        val formatted = list.map {
            "${it.name} (${it.vendor}) - Type: ${it.stringType ?: it.type.toString()}, Range: ${it.maximumRange}, Res: ${it.resolution}, Power: ${it.power}mA"
        }
        return SensorsSnapshot(list.size, formatted)
    }

    // ==========================================================
    // Connectivity Reader
    // ==========================================================
    private fun readConnectivity(ctx: Context): ConnectivitySnapshot {
        val pm = ctx.packageManager
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        @Suppress("DEPRECATION")
        val wifiInfo = try { wm?.connectionInfo } catch (_: Throwable) { null }
        val linkSpeed = if (isWifi && wifiInfo != null) "${wifiInfo.linkSpeed} Mbps" else "not exposed"
        val wifiSignal = if (isWifi && wifiInfo != null) "${wifiInfo.rssi} dBm" else "not exposed"

        // Bluetooth
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val btEnabled = try { btAdapter?.isEnabled } catch (_: Throwable) { null }
        val btState = when (btEnabled) {
            true -> "Enabled"
            false -> "Disabled"
            null -> "not exposed"
        }

        val btLe = if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) "Supported" else "Not supported"
        val btLeAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) "Supported" else "this phone doesn't expose it"

        // NFC
        val nfcAdapter = try { NfcAdapter.getDefaultAdapter(ctx) } catch (_: Throwable) { null }
        val nfcStatus = when {
            nfcAdapter == null -> "Not supported"
            nfcAdapter.isEnabled -> "Supported & Enabled"
            else -> "Supported (Disabled)"
        }

        // GPS
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsEnabled = try { lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false } catch (_: Throwable) { false }
        val gpsStatus = if (gpsEnabled) "Enabled" else "Disabled / Unavailable"

        val usbOtg = if (pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) "Host/OTG Supported" else "Standard Device"
        val uwb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pm.hasSystemFeature(PackageManager.FEATURE_UWB)) "Supported" else "Not supported"
        val ir = try {
            val cir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            if (cir?.hasIrEmitter() == true) "Supported" else "Not supported"
        } catch (_: Throwable) {
            "Not supported"
        }

        val fingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val face = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        val biometrics = listOfNotNull(
            if (fingerprint) "Fingerprint Sensor" else null,
            if (face) "Face Authentication" else null
        ).joinToString(", ").ifEmpty { "None / not exposed" }

        // Telephony
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCount = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm?.activeModemCount?.toString() ?: "1"
            else tm?.phoneCount?.toString() ?: "1"
        } catch (_: Throwable) {
            "1"
        }

        val operator = try {
            val op = tm?.networkOperatorName ?: tm?.simOperatorName
            if (!op.isNullOrBlank()) op else "No SIM / not exposed"
        } catch (_: Throwable) {
            "No SIM / not exposed"
        }

        val networkType = when {
            isWifi -> "Wi-Fi (${caps?.linkDownstreamBandwidthKbps?.let { "${it / 1000} Mbps cap" } ?: "Connected"})"
            isCellular -> "Cellular Mobile Data"
            else -> "Disconnected"
        }

        val esim = try {
            val euicc = ctx.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            if (euicc?.isEnabled == true) "Supported" else "Not supported / not exposed"
        } catch (_: Throwable) {
            "this phone doesn't expose it"
        }

        val cr = ctx.contentResolver
        val airplane = try {
            Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Throwable) {
            false
        }

        return ConnectivitySnapshot(
            wifiState = if (isWifi) "Connected" else "Disconnected",
            wifiLinkSpeed = linkSpeed,
            wifiSignal = wifiSignal,
            bluetoothState = btState,
            bluetoothLeSupport = btLe,
            bluetoothLeAudio = btLeAudio,
            nfcStatus = nfcStatus,
            gpsLocationServices = gpsStatus,
            usbFeatures = usbOtg,
            uwbSupport = uwb,
            irBlasterSupport = ir,
            biometricsHardware = biometrics,
            simCount = simCount,
            simOperator = operator,
            networkType = networkType,
            esimSupport = esim,
            airplaneMode = if (airplane) "Enabled" else "Disabled",
            vpnState = if (isVpn) "Connected" else "Disconnected"
        )
    }

    // ==========================================================
    // Audio & Thermal Reader
    // ==========================================================
    private fun readAudioThermal(ctx: Context): AudioThermalSnapshot {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringer = when (am?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            else -> "Normal"
        }

        val mediaVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxMediaVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val ringVol = am?.getStreamVolume(AudioManager.STREAM_RING) ?: 0
        val maxRingVol = am?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 15
        val volLevels = "Media: $mediaVol/$maxMediaVol, Ring: $ringVol/$maxRingVol"

        @Suppress("DEPRECATION")
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val vibSupport = if (vib?.hasVibrator() == true) "Supported (with amplitude control)" else "Not supported"

        @Suppress("DEPRECATION")
        val headsetOn = if (am?.isWiredHeadsetOn == true) "Wired Headset Connected" else "Internal Speakers Active"

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal (None)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Throttling"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Thermal Shutdown"
                else -> "Nominal"
            }
        } else "not exposed"

        val featuresCount = ctx.packageManager.systemAvailableFeatures?.size ?: 0

        return AudioThermalSnapshot(
            ringerMode = ringer,
            volumeLevels = volLevels,
            vibratorHaptics = vibSupport,
            speakersHeadsetState = headsetOn,
            thermalStatus = thermalStatus,
            availableSystemFeaturesCount = featuresCount
        )
    }

    // ==========================================================
    // Formatters
    // ==========================================================
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatUptime(uptimeMillis: Long): String {
        val totalSecs = uptimeMillis / 1000
        val days = totalSecs / (24 * 3600)
        val hours = (totalSecs % (24 * 3600)) / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (days > 0) {
            String.format(Locale.US, "%dd %dh %dm", days, hours, minutes)
        } else {
            String.format(Locale.US, "%dh %dm %ds", hours, minutes, seconds)
        }
    }

    fun formatFrequencyKhz(khz: Long): String {
        val mhz = khz / 1000.0
        val ghz = mhz / 1000.0
        return if (ghz >= 1.0) {
            String.format(Locale.US, "%.2f GHz", ghz)
        } else {
            String.format(Locale.US, "%.0f MHz", mhz)
        }
    }

    fun formatTemperature(celsius: Float): String {
        return String.format(Locale.US, "%.1f °C", celsius)
    }

    // ==========================================================
    // Query Handlers (Strictly Scoped: One topic per query)
    // ==========================================================

    /**
     * "tell me about my phone" -> Overview summary. ONLY case combining topics.
     */
    fun answerOverview(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val hw = snapshot.hardware
        val disp = snapshot.display
        val bat = snapshot.battery

        val text = "Here is an overview of your device (${snapshot.formattedTimestamp}):\n" +
            "• **Device:** ${hw.manufacturer} ${hw.model} (${hw.brand})\n" +
            "• **Software:** Android ${sw.androidVersion} (API ${sw.apiLevel}, Security Patch: ${sw.securityPatch})\n" +
            "• **CPU / RAM / Storage:** ${hw.cpu.coreCount} Cores, ${formatBytes(hw.ram.usedRamBytes)} used of ${formatBytes(hw.ram.totalRamBytes)} RAM, ${formatBytes(hw.storage.internalFreeBytes)} free internal storage\n" +
            "• **Display:** ${disp.resolution} @ ${disp.refreshRateHz.toInt()}Hz (${disp.densityDpi} DPI)\n" +
            "• **Battery:** ${bat.levelPercent}% (${bat.status}, ${bat.plugType})"

        val card = PhoneSpecCardData(
            title = "Device Overview",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Model" to "${hw.manufacturer} ${hw.model}",
                "OS" to "Android ${sw.androidVersion} (API ${sw.apiLevel})",
                "Processor" to "${hw.cpu.coreCount} Cores (${hw.cpu.supportedAbis.substringBefore(",")})",
                "RAM" to "${formatBytes(hw.ram.usedRamBytes)} / ${formatBytes(hw.ram.totalRamBytes)}",
                "Storage Free" to formatBytes(hw.storage.internalFreeBytes),
                "Display" to "${disp.resolution} @ ${disp.refreshRateHz.toInt()}Hz",
                "Battery" to "${bat.levelPercent}% (${bat.status})"
            )
        )
        return text to card
    }

    /**
     * "all details", "full specs", "everything about my phone" -> Grouped sections with expandable items.
     */
    fun answerFullSpecs(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val hw = snapshot.hardware
        val disp = snapshot.display
        val bat = snapshot.battery
        val conn = snapshot.connectivity

        val text = "Full specifications for ${hw.manufacturer} ${hw.model} (${snapshot.formattedTimestamp}):\n" +
            "• **Software:** Android ${sw.androidVersion} (API ${sw.apiLevel}), Build ${sw.buildDisplay}, Kernel ${sw.kernelShort}\n" +
            "• **Hardware:** ${hw.cpu.coreCount}-core CPU, ${formatBytes(hw.ram.totalRamBytes)} RAM, ${formatBytes(hw.storage.internalTotalBytes)} Storage\n" +
            "• **Display:** ${disp.resolution}, ${disp.densityDpi} DPI, ${disp.refreshRateHz.toInt()}Hz\n" +
            "• **Battery:** ${bat.levelPercent}%, ${bat.health} health, ${formatTemperature(bat.temperatureCelsius)}\n" +
            "• **Connectivity:** Wi-Fi ${conn.wifiState}, BT ${conn.bluetoothState}, SIM: ${conn.simOperator}\n" +
            "• **Cameras:** ${snapshot.cameras.totalCameras} cameras detected\n" +
            "• **Sensors:** ${snapshot.sensors.totalSensors} hardware sensors detected"

        val mainItems = listOf(
            "Manufacturer" to hw.manufacturer,
            "Model" to hw.model,
            "Android Version" to sw.androidVersion,
            "API Level" to sw.apiLevel.toString(),
            "Security Patch" to sw.securityPatch,
            "Kernel (Short)" to sw.kernelShort,
            "CPU Cores" to hw.cpu.coreCount.toString(),
            "GPU Renderer" to hw.gpu.renderer,
            "Total RAM" to formatBytes(hw.ram.totalRamBytes),
            "Internal Storage" to formatBytes(hw.storage.internalTotalBytes),
            "Display" to "${disp.resolution} @ ${disp.refreshRateHz.toInt()}Hz",
            "Battery" to "${bat.levelPercent}% (${bat.status})",
            "Network Type" to conn.networkType
        )

        val expandableItems = mutableListOf<Pair<String, String>>()
        expandableItems.add("Build ID" to sw.buildId)
        expandableItems.add("Fingerprint" to sw.fingerprint)
        expandableItems.add("Vendor Skin" to sw.vendorSkin)
        expandableItems.add("Kernel (Full)" to sw.kernelFull)
        expandableItems.add("Google Play Services" to sw.playServicesVersion)
        expandableItems.add("WebView Version" to sw.webViewVersion)
        expandableItems.add("Play Store Version" to sw.playStoreVersion)
        expandableItems.add("Uptime" to sw.uptimeFormatted)
        expandableItems.add("Bootloader" to sw.bootloader)
        expandableItems.add("SELinux" to sw.selinuxMode)
        expandableItems.add("Screen Lock" to sw.screenLockSet)
        expandableItems.add("Dev Options" to sw.devOptionsEnabled)
        expandableItems.add("USB Debugging" to sw.usbDebuggingEnabled)
        expandableItems.add("GPU Vendor" to hw.gpu.vendor)
        expandableItems.add("Vulkan" to hw.gpu.vulkanSupport)
        expandableItems.add("Battery Voltage" to "${bat.voltageMv} mV")
        expandableItems.add("Battery Temp" to formatTemperature(bat.temperatureCelsius))
        expandableItems.add("Battery Tech" to bat.technology)
        expandableItems.add("Battery Cycles" to bat.cycleCount)
        expandableItems.add("Wi-Fi Speed" to conn.wifiLinkSpeed)
        expandableItems.add("NFC Status" to conn.nfcStatus)
        expandableItems.add("Biometrics" to conn.biometricsHardware)
        expandableItems.add("eSIM Support" to conn.esimSupport)
        expandableItems.add("Cameras Count" to snapshot.cameras.totalCameras.toString())
        snapshot.cameras.cameraDetails.forEachIndexed { idx, cam ->
            expandableItems.add("Camera $idx" to cam)
        }
        expandableItems.add("Total Sensors" to snapshot.sensors.totalSensors.toString())
        snapshot.sensors.sensorSummary.take(15).forEachIndexed { idx, s ->
            expandableItems.add("Sensor $idx" to s)
        }

        val card = PhoneSpecCardData(
            title = "All Specifications",
            timestampText = snapshot.formattedTimestamp,
            items = mainItems,
            expandableItems = expandableItems
        )
        return text to card
    }

    /**
     * Single Fact / Topic Readers
     */

    fun answerKernel(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Your phone's kernel version is **${sw.kernelShort}** (${snapshot.formattedTimestamp}).\n" +
            "Full kernel string: `${sw.kernelFull}`."
        val card = PhoneSpecCardData(
            title = "Kernel Version",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Short Version" to sw.kernelShort,
                "Full Version" to sw.kernelFull
            )
        )
        return text to card
    }

    fun answerBuildNumber(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Your phone's build number is **${sw.buildDisplay}** (Build ID: ${sw.buildId}, ${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Build Information",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Build Number / Display ID" to sw.buildDisplay,
                "Build ID" to sw.buildId,
                "Fingerprint" to sw.fingerprint,
                "Build Type" to sw.buildType,
                "Build Date" to sw.buildTimeFormatted
            )
        )
        return text to card
    }

    fun answerAndroidVersion(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Your phone is running **Android ${sw.androidVersion}** (API level ${sw.apiLevel}, Codename: ${sw.codename}) as of ${snapshot.formattedTimestamp}."
        val card = PhoneSpecCardData(
            title = "Android Version",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Android Release" to sw.androidVersion,
                "API Level" to sw.apiLevel.toString(),
                "Codename" to sw.codename,
                "Base OS" to sw.baseOs,
                "Incremental" to sw.incremental
            )
        )
        return text to card
    }

    fun answerSecurityPatch(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Your phone's Android security patch level is **${sw.securityPatch}** (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Security Patch",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Security Patch" to sw.securityPatch,
                "Screen Lock" to sw.screenLockSet,
                "Storage Encryption" to sw.storageEncryption
            )
        )
        return text to card
    }

    fun answerChipsetAndCpu(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val hw = snapshot.hardware
        val cpu = hw.cpu
        val gpu = hw.gpu
        val socInfo = if (hw.socManufacturer != "this phone doesn't expose it" || hw.socModel != "this phone doesn't expose it") {
            "${hw.socManufacturer} ${hw.socModel}".trim()
        } else {
            hw.hardware
        }
        val text = "Your phone is powered by **$socInfo** with a **${cpu.coreCount}-core CPU** and **${gpu.renderer}** GPU (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Chipset & Processor",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Chipset / Hardware" to socInfo,
                "CPU Cores" to "${cpu.coreCount} Cores",
                "Supported ABIs" to cpu.supportedAbis,
                "GPU Renderer" to gpu.renderer,
                "GPU Vendor" to gpu.vendor,
                "Governor" to cpu.governor,
                "Per-Core Frequencies" to cpu.perCoreFrequencies
            )
        )
        return text to card
    }

    fun answerRefreshRate(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val disp = snapshot.display
        val text = "Your display refresh rate is **${disp.refreshRateHz.toInt()} Hz** (${snapshot.formattedTimestamp}). Supported modes: ${disp.supportedRefreshModes}."
        val card = PhoneSpecCardData(
            title = "Screen Refresh Rate",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Current Refresh Rate" to "${disp.refreshRateHz.toInt()} Hz",
                "Supported Modes" to disp.supportedRefreshModes
            )
        )
        return text to card
    }

    fun answerRam(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val ram = snapshot.hardware.ram
        val text = "Your device has **${formatBytes(ram.totalRamBytes)}** total RAM. Currently, **${formatBytes(ram.usedRamBytes)}** is in use and **${formatBytes(ram.availableRamBytes)}** is available (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "RAM / Memory",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Total RAM" to formatBytes(ram.totalRamBytes),
                "Used RAM" to formatBytes(ram.usedRamBytes),
                "Available RAM" to formatBytes(ram.availableRamBytes),
                "Low Memory Alert" to if (ram.isLowMemory) "Yes" else "No",
                "Swap / zRAM" to ram.zRamSwapInfo
            )
        )
        return text to card
    }

    fun answerStorage(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val storage = snapshot.hardware.storage
        val text = "Your internal storage has **${formatBytes(storage.internalFreeBytes)}** free out of **${formatBytes(storage.internalTotalBytes)}** total (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Storage",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Total Internal" to formatBytes(storage.internalTotalBytes),
                "Used Internal" to formatBytes(storage.internalUsedBytes),
                "Free Internal" to formatBytes(storage.internalFreeBytes),
                "SD Card" to storage.sdCardDetails,
                "Filesystem" to storage.filesystemType,
                "Encryption" to storage.encryptionStatus
            )
        )
        return text to card
    }

    fun answerDisplay(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val disp = snapshot.display
        val text = "Your screen is **${disp.resolution}** at **${disp.refreshRateHz.toInt()} Hz** (${disp.densityDpi} DPI, size estimate: ${disp.physicalSizeEstimateInches}, ${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Display Details",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Resolution" to disp.resolution,
                "Refresh Rate" to "${disp.refreshRateHz.toInt()} Hz",
                "Density (DPI)" to "${disp.densityDpi} DPI",
                "Estimated Size" to disp.physicalSizeEstimateInches,
                "Brightness" to disp.brightness,
                "HDR Support" to disp.hdrSupport,
                "Wide Color Gamut" to disp.wideColorGamut,
                "Dark Mode" to disp.darkMode,
                "Notch / Cutout" to disp.notchCutout
            )
        )
        return text to card
    }

    fun answerBattery(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val bat = snapshot.battery
        val text = "Battery is at **${bat.levelPercent}%** (${bat.status}, plugged into ${bat.plugType}, temp ${formatTemperature(bat.temperatureCelsius)}, health: ${bat.health}) as of ${snapshot.formattedTimestamp}."
        val card = PhoneSpecCardData(
            title = "Battery Status",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Battery Level" to "${bat.levelPercent}%",
                "Status" to bat.status,
                "Power Source" to bat.plugType,
                "Health" to bat.health,
                "Temperature" to formatTemperature(bat.temperatureCelsius),
                "Voltage" to "${bat.voltageMv} mV",
                "Technology" to bat.technology,
                "Capacity" to bat.capacityMah,
                "Cycle Count" to bat.cycleCount,
                "Power Saver" to bat.powerSaverMode
            )
        )
        return text to card
    }

    fun answerCameras(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val cams = snapshot.cameras
        val text = "Your phone has **${cams.totalCameras} cameras** detected (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Cameras",
            timestampText = snapshot.formattedTimestamp,
            items = cams.cameraDetails.mapIndexed { idx, d -> "Camera $idx" to d }
        )
        return text to card
    }

    fun answerSensors(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val s = snapshot.sensors
        val text = "Your phone has **${s.totalSensors} hardware sensors** detected (${snapshot.formattedTimestamp})."
        val card = PhoneSpecCardData(
            title = "Sensors (${s.totalSensors})",
            timestampText = snapshot.formattedTimestamp,
            items = s.sensorSummary.take(8).mapIndexed { idx, item -> "Sensor ${idx + 1}" to item },
            expandableItems = s.sensorSummary.drop(8).mapIndexed { idx, item -> "Sensor ${idx + 9}" to item }
        )
        return text to card
    }

    fun answerConnectivity(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val conn = snapshot.connectivity
        val text = "Connectivity (${snapshot.formattedTimestamp}): Wi-Fi is **${conn.wifiState}** (${conn.networkType}), Bluetooth is **${conn.bluetoothState}**, NFC is **${conn.nfcStatus}**, SIM: **${conn.simOperator}**."
        val card = PhoneSpecCardData(
            title = "Connectivity",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Network Type" to conn.networkType,
                "Wi-Fi State" to conn.wifiState,
                "Wi-Fi Link Speed" to conn.wifiLinkSpeed,
                "Bluetooth State" to conn.bluetoothState,
                "Bluetooth LE" to conn.bluetoothLeSupport,
                "NFC" to conn.nfcStatus,
                "GPS" to conn.gpsLocationServices,
                "SIM Operator" to conn.simOperator,
                "SIM Count" to conn.simCount,
                "eSIM" to conn.esimSupport,
                "Airplane Mode" to conn.airplaneMode,
                "VPN" to conn.vpnState,
                "Biometrics Hardware" to conn.biometricsHardware
            )
        )
        return text to card
    }

    fun answerAudioAndThermal(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val at = snapshot.audioThermal
        val text = "Audio & Thermal (${snapshot.formattedTimestamp}): Ringer mode is **${at.ringerMode}** (${at.volumeLevels}), thermal status is **${at.thermalStatus}**, speakers: **${at.speakersHeadsetState}**."
        val card = PhoneSpecCardData(
            title = "Audio & Thermal",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Ringer Mode" to at.ringerMode,
                "Volumes" to at.volumeLevels,
                "Vibration & Haptics" to at.vibratorHaptics,
                "Audio Output" to at.speakersHeadsetState,
                "Thermal Status" to at.thermalStatus,
                "Available Features" to at.availableSystemFeaturesCount.toString()
            )
        )
        return text to card
    }

    fun answerSecurityStatus(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Security Status (${snapshot.formattedTimestamp}): Screen lock is **${sw.screenLockSet}**, storage is **${sw.storageEncryption}**, developer options are **${sw.devOptionsEnabled}**, USB debugging is **${sw.usbDebuggingEnabled}**."
        val card = PhoneSpecCardData(
            title = "Security & Integrity",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Screen Lock" to sw.screenLockSet,
                "Storage Encryption" to sw.storageEncryption,
                "Developer Options" to sw.devOptionsEnabled,
                "USB Debugging" to sw.usbDebuggingEnabled,
                "Install Unknown Apps" to sw.installUnknownApps,
                "SELinux Mode" to sw.selinuxMode,
                "Security Patch" to sw.securityPatch
            )
        )
        return text to card
    }

    fun answerSoftwareOverview(snapshot: PhoneSystemSnapshot): Pair<String, PhoneSpecCardData> {
        val sw = snapshot.software
        val text = "Software Details (${snapshot.formattedTimestamp}): Android **${sw.androidVersion}** (API ${sw.apiLevel}, Security Patch: ${sw.securityPatch}), Build: **${sw.buildDisplay}**, Kernel: **${sw.kernelShort}**."
        val card = PhoneSpecCardData(
            title = "Software Specifications",
            timestampText = snapshot.formattedTimestamp,
            items = listOf(
                "Android Version" to sw.androidVersion,
                "API Level" to sw.apiLevel.toString(),
                "Codename" to sw.codename,
                "Security Patch" to sw.securityPatch,
                "Build Number" to sw.buildDisplay,
                "Kernel" to sw.kernelShort,
                "Vendor Skin" to sw.vendorSkin,
                "Play Services" to sw.playServicesVersion,
                "WebView" to sw.webViewVersion,
                "Play Store" to sw.playStoreVersion,
                "Language / Locale" to "${sw.language} (${sw.locale})",
                "Time Zone" to sw.timeZone,
                "Uptime" to sw.uptimeFormatted
            )
        )
        return text to card
    }
}
