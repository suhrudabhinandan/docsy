package com.suhrud.docsy.tools

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager

data class DisplaySnapshot(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val refreshRateHz: Float
)

data class MemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val isLowMemory: Boolean,
    val totalInternalStorageBytes: Long,
    val availableInternalStorageBytes: Long
)

data class HardwareSnapshot(
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val board: String,
    val androidVersion: String,
    val apiLevel: Int,
    val isBluetoothEnabled: Boolean?,
    val isNfcSupported: Boolean,
    val isNfcEnabled: Boolean
)

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean
)

/**
 * System and hardware information tools.
 * Satisfies Phase 8.4 in AUDIT.md.
 */
object DisplayTool {

    fun getSnapshot(context: Context): DisplaySnapshot {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = windowManager?.defaultDisplay
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)

        @Suppress("DEPRECATION")
        val refreshRate = display?.refreshRate ?: 60.0f

        return DisplaySnapshot(
            widthPixels = metrics.widthPixels,
            heightPixels = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            densityScale = metrics.density,
            refreshRateHz = refreshRate
        )
    }

    fun formatAnswer(snapshot: DisplaySnapshot): String {
        return "Display: ${snapshot.widthPixels}x${snapshot.heightPixels} px at ${snapshot.refreshRateHz.toInt()} Hz (${snapshot.densityDpi} DPI)."
    }
}

object MemoryTool {

    fun getSnapshot(context: Context): MemorySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalInternal = stat.totalBytes
        val availableInternal = stat.availableBytes

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = totalRam - availRam

        return MemorySnapshot(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            usedRamBytes = usedRam,
            isLowMemory = memInfo.lowMemory,
            totalInternalStorageBytes = totalInternal,
            availableInternalStorageBytes = availableInternal
        )
    }

    fun formatAnswer(snapshot: MemorySnapshot): String {
        val usedRamGb = snapshot.usedRamBytes / (1024.0 * 1024.0 * 1024.0)
        val totalRamGb = snapshot.totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        val freeStorageGb = snapshot.availableInternalStorageBytes / (1024.0 * 1024.0 * 1024.0)
        val totalStorageGb = snapshot.totalInternalStorageBytes / (1024.0 * 1024.0 * 1024.0)

        return "RAM: %.1f GB used of %.1f GB. Internal Storage: %.1f GB free of %.1f GB.".format(
            usedRamGb, totalRamGb, freeStorageGb, totalStorageGb
        )
    }
}

object HardwareInfoTool {

    fun getSnapshot(context: Context): HardwareSnapshot {
        val bluetoothAdapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            null
        }

        val isBtEnabled = try {
            bluetoothAdapter?.isEnabled
        } catch (_: SecurityException) {
            null
        }

        val nfcAdapter = try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (_: Exception) {
            null
        }

        val isNfcSupported = nfcAdapter != null
        val isNfcEnabled = try {
            nfcAdapter?.isEnabled ?: false
        } catch (_: Exception) {
            false
        }

        return HardwareSnapshot(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            device = Build.DEVICE,
            board = Build.BOARD,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            isBluetoothEnabled = isBtEnabled,
            isNfcSupported = isNfcSupported,
            isNfcEnabled = isNfcEnabled
        )
    }

    fun formatAnswer(snapshot: HardwareSnapshot): String {
        val btStatus = when (snapshot.isBluetoothEnabled) {
            true -> "ON"
            false -> "OFF"
            null -> "Unavailable"
        }
        return "${snapshot.manufacturer} ${snapshot.model} running Android ${snapshot.androidVersion} (API ${snapshot.apiLevel}). Bluetooth is $btStatus."
    }
}

object AppManagerTool {

    fun getInstalledApps(context: Context, userAppsOnly: Boolean = true): List<AppInfoItem> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return packages.mapNotNull { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (userAppsOnly && isSystem) return@mapNotNull null

            val name = pm.getApplicationLabel(appInfo).toString()
            val version = try {
                pm.getPackageInfo(appInfo.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) {
                "1.0"
            }

            AppInfoItem(
                appName = name,
                packageName = appInfo.packageName,
                versionName = version,
                isSystemApp = isSystem
            )
        }.sortedBy { it.appName.lowercase() }
    }

    fun searchApp(context: Context, query: String): List<AppInfoItem> {
        val clean = query.trim().lowercase()
        return getInstalledApps(context, userAppsOnly = false).filter {
            it.appName.lowercase().contains(clean) || it.packageName.lowercase().contains(clean)
        }
    }
}
