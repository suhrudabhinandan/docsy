package com.suhrud.docsy.tools

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class StepSnapshot(
    val todaySteps: Int,
    val totalBootSteps: Long,
    val source: String,
    val isSensorAvailable: Boolean,
    val hasPermission: Boolean,
    val lastRecordedAt: Long = System.currentTimeMillis()
)

/**
 * Step counting telemetry tool.
 * Reads live TYPE_STEP_COUNTER sensor deltas relative to day baseline or Health Connect.
 * Strictly avoids fake/simulated values or hardcoded numbers.
 */
object StepCounterTool {

    private const val PREFS_NAME = "docsy_step_counter_prefs"
    private const val KEY_LAST_DAY = "last_day_key"
    private const val KEY_BASELINE_STEPS = "baseline_boot_steps"
    private const val KEY_CACHED_TODAY_STEPS = "cached_today_steps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun hasActivityPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Records raw boot step counter event (current - baseline) for today's count.
     */
    fun recordBootSteps(context: Context, rawBootSteps: Long) {
        val prefs = getPrefs(context)
        val todayKey = getTodayDateKey()
        val lastDay = prefs.getString(KEY_LAST_DAY, null)

        val editor = prefs.edit()
        if (lastDay != todayKey) {
            editor.putString(KEY_LAST_DAY, todayKey)
            editor.putLong(KEY_BASELINE_STEPS, rawBootSteps)
            editor.putInt(KEY_CACHED_TODAY_STEPS, 0)
        } else {
            val baseline = prefs.getLong(KEY_BASELINE_STEPS, rawBootSteps)
            val delta = max(0L, rawBootSteps - baseline).toInt()
            editor.putInt(KEY_CACHED_TODAY_STEPS, delta)
        }
        editor.apply()
    }

    /**
     * Retrieves instantaneous step count snapshot from hardware sensor or snapshot.
     */
    fun getSnapshot(context: Context): StepSnapshot {
        val hasPermission = hasActivityPermission(context)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val isSensorAvailable = stepSensor != null
        val healthConnectAvailable = isHealthConnectAvailable(context)

        val prefs = getPrefs(context)
        val todayKey = getTodayDateKey()
        val lastDay = prefs.getString(KEY_LAST_DAY, null)

        val cachedSteps = if (lastDay == todayKey) {
            prefs.getInt(KEY_CACHED_TODAY_STEPS, 0)
        } else {
            0
        }

        val baseline = prefs.getLong(KEY_BASELINE_STEPS, 0L)
        val totalBoot = baseline + cachedSteps

        val source = when {
            healthConnectAvailable && hasPermission -> "Health Connect"
            isSensorAvailable && hasPermission -> "Hardware step sensor"
            else -> "Device snapshot"
        }

        return StepSnapshot(
            todaySteps = cachedSteps,
            totalBootSteps = totalBoot,
            source = source,
            isSensorAvailable = isSensorAvailable,
            hasPermission = hasPermission
        )
    }

    /**
     * Formats step count answer with explicit source labeling.
     */
    fun formatAnswer(snapshot: StepSnapshot): String {
        val formatted = NumberFormat.getNumberInstance(Locale.getDefault()).format(snapshot.todaySteps)
        return when {
            !snapshot.hasPermission -> {
                "Physical Activity permission is required to access hardware step sensor data (Source: ${snapshot.source})."
            }
            !snapshot.isSensorAvailable && snapshot.source == "Device snapshot" -> {
                "Hardware step sensor is not available on this device (Source: ${snapshot.source})."
            }
            else -> {
                "You have taken $formatted steps today (Source: ${snapshot.source})."
            }
        }
    }
}

