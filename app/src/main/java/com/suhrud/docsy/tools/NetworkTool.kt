package com.suhrud.docsy.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.provider.Settings

data class NetworkSnapshot(
    val isConnected: Boolean,
    val transportType: String,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isAirplaneModeOn: Boolean,
    val downstreamBandwidthKbps: Int,
    val upstreamBandwidthKbps: Int,
    val linkSpeedMbps: Int,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val wifiSsid: String? = null
)

/**
 * Network state telemetry tool.
 * Satisfies Phase 8.3 in AUDIT.md.
 *
 * CRITICAL CONSTRAINT (STRICT OFFLINE):
 * Does NOT perform any network queries, DNS lookups, or internet ping checks.
 * Reads solely from local system managers (ConnectivityManager, TrafficStats).
 */
object NetworkTool {

    fun getSnapshot(context: Context): NetworkSnapshot {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val isAirplaneModeOn = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }

        var isConnected = false
        var isWifi = false
        var isCellular = false
        var transport = "None"
        var downBandwidth = 0
        var upBandwidth = 0

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        if (capabilities != null) {
            isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                isWifi = true
                transport = "Wi-Fi"
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                isCellular = true
                transport = "Mobile Data"
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                transport = "Ethernet"
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                transport = "Bluetooth Tethering"
            }

            downBandwidth = capabilities.linkDownstreamBandwidthKbps
            upBandwidth = capabilities.linkUpstreamBandwidthKbps
        }

        val linkSpeed = wifiManager?.connectionInfo?.linkSpeed ?: 0
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()

        return NetworkSnapshot(
            isConnected = isConnected,
            transportType = transport,
            isWifi = isWifi,
            isCellular = isCellular,
            isAirplaneModeOn = isAirplaneModeOn,
            downstreamBandwidthKbps = downBandwidth,
            upstreamBandwidthKbps = upBandwidth,
            linkSpeedMbps = linkSpeed,
            totalRxBytes = if (rxBytes >= 0) rxBytes else 0L,
            totalTxBytes = if (txBytes >= 0) txBytes else 0L
        )
    }

    fun formatAnswer(snapshot: NetworkSnapshot): String {
        if (snapshot.isAirplaneModeOn) {
            return "Airplane mode is ON. Device network radios are disabled."
        }
        if (!snapshot.isConnected) {
            return "Device is not currently connected to any network."
        }
        return if (snapshot.isWifi) {
            val speedInfo = if (snapshot.linkSpeedMbps > 0) " at ${snapshot.linkSpeedMbps} Mbps" else ""
            "Connected to Wi-Fi$speedInfo."
        } else if (snapshot.isCellular) {
            "Connected via Mobile Data."
        } else {
            "Connected via ${snapshot.transportType}."
        }
    }
}
