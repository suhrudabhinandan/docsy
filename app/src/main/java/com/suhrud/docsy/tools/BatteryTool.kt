package com.suhrud.docsy.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

data class BatterySnapshot(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingStatus: String,
    val plugType: String,
    val health: String,
    val temperatureCelsius: Float,
    val voltageMilliVolts: Int
)

/**
 * Battery status tool providing instantaneous hardware battery telemetry.
 * Satisfies Phase 8.2 in AUDIT.md.
 */
object BatteryTool {

    fun getSnapshot(context: Context): BatterySnapshot {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter)

        if (batteryIntent == null) {
            return BatterySnapshot(
                percentage = 0,
                isCharging = false,
                chargingStatus = "UNKNOWN",
                plugType = "UNKNOWN",
                health = "UNKNOWN",
                temperatureCelsius = 0.0f,
                voltageMilliVolts = 0
            )
        }

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).roundToInt()
        } else {
            0
        }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Plugged in" else "Unplugged"
        }

        val healthInt = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureCelsius = tempTenths / 10.0f
        val voltageMilliVolts = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return BatterySnapshot(
            percentage = percentage,
            isCharging = isCharging,
            chargingStatus = statusString,
            plugType = plugType,
            health = health,
            temperatureCelsius = temperatureCelsius,
            voltageMilliVolts = voltageMilliVolts
        )
    }

    fun formatAnswer(snapshot: BatterySnapshot): String {
        return if (snapshot.isCharging) {
            "Battery is at ${snapshot.percentage}% (${snapshot.chargingStatus} via ${snapshot.plugType})."
        } else {
            "Battery is at ${snapshot.percentage}%."
        }
    }
}
