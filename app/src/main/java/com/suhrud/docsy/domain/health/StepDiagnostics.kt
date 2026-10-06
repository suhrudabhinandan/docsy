package com.suhrud.docsy.domain.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient

object StepDiagnostics {

    data class DiagnosticsResult(
        val isActivityRecognitionGranted: Boolean,
        val isStepCounterPresent: Boolean,
        val rawCounterValue: Long?,
        val healthConnectStatus: String,
        val isBatteryOptimized: Boolean
    )

    fun runDiagnostics(context: Context): DiagnosticsResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true

        val result = DiagnosticsResult(
            isActivityRecognitionGranted = hasPermission,
            isStepCounterPresent = sensor != null,
            rawCounterValue = null,
            healthConnectStatus = HealthConnectClient.getSdkStatus(context).toString(),
            isBatteryOptimized = !isIgnoringBattery
        )
        Log.d("DocsyStepDiag", "[Step Diagnostics] $result")
        return result
    }
}
