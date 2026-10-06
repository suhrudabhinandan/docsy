package com.suhrud.docsy.domain.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.suhrud.docsy.data.local.DocsyDatabase
import com.suhrud.docsy.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

enum class StepStatus {
    ON,
    NEEDS_PERMISSION,
    UNAVAILABLE
}

class StepManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("docsy_steps_prefs", Context.MODE_PRIVATE)

    companion object {
        val HEALTH_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }

    suspend fun getStepStatusSummary(): Pair<StepStatus, String> = withContext(Dispatchers.IO) {
        val hasActivityPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        var hasHealthPermission = false
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                hasHealthPermission = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
            } catch (_: Exception) {}
        }

        if (hasHealthPermission || hasActivityPermission) {
            Pair(StepStatus.ON, "Step counting active")
        } else if (sdkStatus == HealthConnectClient.SDK_AVAILABLE || hasHardwareSensor()) {
            Pair(StepStatus.NEEDS_PERMISSION, "Needs permission")
        } else {
            Pair(StepStatus.UNAVAILABLE, "Unavailable")
        }
    }

    private fun hasHardwareSensor(): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    suspend fun getStepStats(filterType: String = "today"): ChatMessage = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfWeek = today.minusDays(6).atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val grantedPermissions = client.permissionController.getGrantedPermissions()
                val hasHealthPermission = grantedPermissions.contains(HealthPermission.getReadPermission(StepsRecord::class))

                if (hasHealthPermission) {
                    val request = ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfWeek, now)
                    )
                    val response = client.readRecords(request)
                    val records = response.records

                    val dailySteps = mutableMapOf<LocalDate, Long>()
                    for (record in records) {
                        val recordDate = record.startTime.atZone(zone).toLocalDate()
                        dailySteps[recordDate] = (dailySteps[recordDate] ?: 0L) + record.count
                    }

                    val todayCount = dailySteps[today] ?: 0L
                    val weekTotal = dailySteps.values.sum()
                    val daysTracked = dailySteps.size.coerceAtLeast(1)
                    val average = weekTotal / daysTracked

                    return@withContext when (filterType.lowercase(Locale.ROOT)) {
                        "week", "total" -> {
                            ChatMessage(
                                isUser = false,
                                text = "You have taken $weekTotal steps over the past 7 days (average $average steps/day). Source: Health Connect.",
                                answerHighlight = "$weekTotal steps",
                                supportingMetadata = "HEALTH CONNECT · PAST 7 DAYS",
                                subtext = "Health Connect sync"
                            )
                        }

                        "average", "avg" -> {
                            ChatMessage(
                                isUser = false,
                                text = "Your daily average is $average steps per day based on Health Connect records.",
                                answerHighlight = "$average steps/day",
                                supportingMetadata = "HEALTH CONNECT · DAILY AVERAGE",
                                subtext = "Calculated over $daysTracked days"
                            )
                        }

                        else -> {
                            ChatMessage(
                                isUser = false,
                                text = "You have taken $todayCount steps today. Source: Health Connect.",
                                answerHighlight = "$todayCount steps",
                                supportingMetadata = "HEALTH CONNECT · TODAY",
                                subtext = "Recorded via Health Connect"
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val hasActivityPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasActivityPermission) {
            return@withContext ChatMessage(
                isUser = false,
                text = "Step counting isn't set up yet. Activity Recognition permission is needed to access your device's step sensor.",
                answerHighlight = "Permission Needed",
                supportingMetadata = "STEP TRACKER",
                missingPermissionRequired = Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

        val todayStr = today.toString()
        try {
            val db = DocsyDatabase.getInstance(context)
            val roomLog = db.stepDao().getStepLogByDate(todayStr)
            if (roomLog != null && roomLog.stepCount > 0) {
                return@withContext ChatMessage(
                    isUser = false,
                    text = "You have taken ${roomLog.stepCount} steps today (counted since ${roomLog.dateString} via on-device sensor log).",
                    answerHighlight = "${roomLog.stepCount} steps",
                    supportingMetadata = "ON-DEVICE SENSOR · TODAY",
                    subtext = "Recorded since ${roomLog.dateString}"
                )
            }
        } catch (_: Exception) {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCounterSensor != null) {
            val cumulativeReading = readSensorOnce(sensorManager, stepCounterSensor)
            if (cumulativeReading != null && cumulativeReading > 0) {
                var baseline = prefs.getLong("baseline_$todayStr", -1L)
                if (baseline == -1L) {
                    baseline = cumulativeReading
                    prefs.edit().putLong("baseline_$todayStr", baseline).apply()
                }

                val todaySteps = (cumulativeReading - baseline).coerceAtLeast(0)

                return@withContext ChatMessage(
                    isUser = false,
                    text = "You have taken $todaySteps steps today (counted since $todayStr via hardware step sensor).",
                    answerHighlight = "$todaySteps steps",
                    supportingMetadata = "HARDWARE SENSOR · TODAY",
                    subtext = "Device Step Counter"
                )
            }
        }

        ChatMessage(
            isUser = false,
            text = "Step counting isn't set up yet. Tap below to enable permissions and step tracking.",
            answerHighlight = "Not Configured",
            supportingMetadata = "STEP TRACKER",
            missingPermissionRequired = Manifest.permission.ACTIVITY_RECOGNITION
        )
    }

    private suspend fun readSensorOnce(sensorManager: SensorManager, sensor: Sensor): Long? =
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && event.values.isNotEmpty()) {
                        val reading = event.values[0].toLong()
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(reading)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )

            if (!registered) {
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
        }
}
