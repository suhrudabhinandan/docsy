package com.suhrud.docsy.domain.stats

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.suhrud.docsy.data.model.ChatMessage
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TypedFact(
    val topic: String,
    val subtopic: String,
    val key: String,
    val value: String,
    val asOf: String = ""
)

sealed class SubtopicResult {
    data class Success(val subtopic: String, val facts: List<TypedFact>) : SubtopicResult()
    data class Unavailable(val subtopic: String, val reason: String, val alternativeInfo: String? = null) : SubtopicResult()
}

object DeviceInfoEngine {

    private fun getCurrentAsOf(): String {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    fun answerSubtopic(context: Context, subtopic: String): SubtopicResult {
        val asOf = getCurrentAsOf()
        return when (subtopic.lowercase(Locale.ROOT)) {
            "bluetooth" -> {
                val btAdapter = try { BluetoothAdapter.getDefaultAdapter() } catch (_: Exception) { null }
                if (btAdapter == null) {
                    SubtopicResult.Unavailable("bluetooth", "Bluetooth hardware is not supported on this device.")
                } else {
                    val stateStr = if (btAdapter.isEnabled) "ON" else "OFF"
                    val facts = listOf(
                        TypedFact("device", "bluetooth", "Bluetooth", "Bluetooth is $stateStr", asOf),
                        TypedFact("device", "bluetooth", "LE Support", if (btAdapter.isMultipleAdvertisementSupported) "Yes" else "No", asOf)
                    )
                    SubtopicResult.Success("bluetooth", facts)
                }
            }

            "wifi", "network" -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

                val isWifiEnabled = wifiManager?.isWifiEnabled == true
                val net = connManager?.activeNetwork
                val caps = connManager?.getNetworkCapabilities(net)
                val isConnectedWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                val stateStr = if (isWifiEnabled) {
                    if (isConnectedWifi) "ON and Connected" else "ON (Not connected)"
                } else {
                    "OFF"
                }

                val linkSpeedStr = if (isConnectedWifi && wifiManager != null) {
                    "${wifiManager.connectionInfo.linkSpeed} Mbps"
                } else "N/A"

                val facts = listOf(
                    TypedFact("device", "wifi", "Wi-Fi", "Wi-Fi is $stateStr", asOf),
                    TypedFact("device", "wifi", "Link Speed", linkSpeedStr, asOf),
                    TypedFact("device", "wifi", "SSID", "Hidden by Android for privacy", asOf)
                )
                SubtopicResult.Success("wifi", facts)
            }

            "nfc" -> {
                val nfcAdapter = try { NfcAdapter.getDefaultAdapter(context) } catch (_: Exception) { null }
                if (nfcAdapter == null) {
                    SubtopicResult.Unavailable("nfc", "NFC hardware is not supported on this device.")
                } else {
                    val stateStr = if (nfcAdapter.isEnabled) "ON" else "OFF"
                    val facts = listOf(
                        TypedFact("device", "nfc", "NFC", "NFC is $stateStr", asOf)
                    )
                    SubtopicResult.Success("nfc", facts)
                }
            }

            "gps", "location" -> {
                val mode = try {
                    Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE)
                } catch (_: Exception) { Settings.Secure.LOCATION_MODE_OFF }

                val stateStr = if (mode != Settings.Secure.LOCATION_MODE_OFF) "ON" else "OFF"
                val facts = listOf(
                    TypedFact("device", "location", "Location Services", "GPS/Location is $stateStr", asOf)
                )
                SubtopicResult.Success("location", facts)
            }

            "hotspot" -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                val isWifiOn = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

                SubtopicResult.Unavailable(
                    subtopic = "hotspot",
                    reason = "Android restricts direct hotspot status reading for non-system apps.",
                    alternativeInfo = "Wi-Fi is ${if (isWifiOn) "ON" else "OFF"}. Cellular network active: ${caps != null}."
                )
            }

            "vpn" -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

                val facts = listOf(
                    TypedFact("device", "vpn", "VPN", if (isVpn) "Active" else "Inactive", asOf)
                )
                SubtopicResult.Success("vpn", facts)
            }

            "battery" -> {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryIntent = context.registerReceiver(null, filter)

                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val statusText = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Plugged in, not charging"
                    else -> "Normal"
                }

                val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f
                val volt = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0f

                val facts = listOf(
                    TypedFact("device", "battery", "Battery Level", "$pct%", asOf),
                    TypedFact("device", "battery", "Status", statusText, asOf),
                    TypedFact("device", "battery", "Temperature", "${temp}°C", asOf),
                    TypedFact("device", "battery", "Voltage", "${volt}V", asOf)
                )
                SubtopicResult.Success("battery", facts)
            }

            "ram", "memory" -> {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)

                val freeStr = formatBytes(memInfo.availMem)
                val totalStr = formatBytes(memInfo.totalMem)
                val usedStr = formatBytes(memInfo.totalMem - memInfo.availMem)

                val facts = listOf(
                    TypedFact("device", "memory", "Free RAM", freeStr, asOf),
                    TypedFact("device", "memory", "Total RAM", totalStr, asOf),
                    TypedFact("device", "memory", "Used RAM", usedStr, asOf)
                )
                SubtopicResult.Success("memory", facts)
            }

            "storage", "rom" -> {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.blockCountLong * stat.blockSizeLong
                val free = stat.availableBlocksLong * stat.blockSizeLong
                val used = total - free

                val facts = listOf(
                    TypedFact("device", "storage", "Free Storage", formatBytes(free), asOf),
                    TypedFact("device", "storage", "Total Storage", formatBytes(total), asOf),
                    TypedFact("device", "storage", "Used Storage", formatBytes(used), asOf)
                )
                SubtopicResult.Success("storage", facts)
            }

            "display" -> {
                val dm = context.resources.displayMetrics
                val facts = listOf(
                    TypedFact("device", "display", "Resolution", "${dm.widthPixels}x${dm.heightPixels}", asOf),
                    TypedFact("device", "display", "Density", "${dm.densityDpi} dpi", asOf)
                )
                SubtopicResult.Success("display", facts)
            }

            "system" -> {
                val facts = listOf(
                    TypedFact("device", "system", "Android Version", Build.VERSION.RELEASE, asOf),
                    TypedFact("device", "system", "API Level", "${Build.VERSION.SDK_INT}", asOf),
                    TypedFact("device", "system", "Kernel", System.getProperty("os.version") ?: "Linux", asOf),
                    TypedFact("device", "system", "Uptime", formatUptime(SystemClock.elapsedRealtime()), asOf)
                )
                SubtopicResult.Success("system", facts)
            }

            else -> {
                // Overview
                val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = Build.MODEL
                val androidVer = Build.VERSION.RELEASE
                val uptimeStr = formatUptime(SystemClock.elapsedRealtime())

                val facts = listOf(
                    TypedFact("device", "overview", "Model", "$manufacturer $model", asOf),
                    TypedFact("device", "overview", "OS", "Android $androidVer", asOf),
                    TypedFact("device", "overview", "Uptime", uptimeStr, asOf)
                )
                SubtopicResult.Success("overview", facts)
            }
        }
    }

    fun answerDeviceInfo(context: Context, rawQuery: String): ChatMessage {
        val q = rawQuery.lowercase(Locale.ROOT)
        val subtopic = when {
            q.contains("bluetooth") -> "bluetooth"
            q.contains("wifi") || q.contains("internet") || q.contains("network") -> "wifi"
            q.contains("nfc") -> "nfc"
            q.contains("gps") || q.contains("location") -> "gps"
            q.contains("hotspot") -> "hotspot"
            q.contains("vpn") -> "vpn"
            q.contains("battery") || q.contains("charge") -> "battery"
            q.contains("ram") -> "ram"
            q.contains("storage") || q.contains("rom") || q.contains("space") -> "storage"
            q.contains("display") || q.contains("screen") || q.contains("resolution") -> "display"
            q.contains("android") || q.contains("kernel") || q.contains("uptime") -> "system"
            else -> "overview"
        }

        return when (val result = answerSubtopic(context, subtopic)) {
            is SubtopicResult.Success -> {
                val firstFact = result.facts.first()
                val details = result.facts.joinToString(" · ") { "${it.key}: ${it.value}" }
                ChatMessage(
                    isUser = false,
                    text = "${firstFact.key}: ${firstFact.value} (as of ${firstFact.asOf}).",
                    answerHighlight = firstFact.value,
                    supportingMetadata = "${subtopic.uppercase(Locale.ROOT)} · AS OF ${firstFact.asOf}",
                    subtext = details
                )
            }
            is SubtopicResult.Unavailable -> {
                val altText = result.alternativeInfo?.let { " $it" } ?: ""
                ChatMessage(
                    isUser = false,
                    text = "Docsy cannot read ${result.subtopic} status on this device: ${result.reason}$altText",
                    supportingMetadata = "${result.subtopic.uppercase(Locale.ROOT)} · UNAVAILABLE"
                )
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            gb >= 1.0 -> String.format(Locale.ROOT, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.ROOT, "%.0f MB", mb)
            else -> "$bytes B"
        }
    }

    fun formatUptime(millis: Long): String {
        val sec = millis / 1000
        val hours = sec / 3600
        val mins = (sec % 3600) / 60
        return when {
            hours > 24 -> "${hours / 24}d ${hours % 24}h ${mins}m"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }
}
