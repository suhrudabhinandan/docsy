package com.suhrud.docsy.domain.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suhrud.docsy.data.local.DocsyDatabase
import com.suhrud.docsy.data.model.StepLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class StepTrackingService : Service(), SensorEventListener {

    private val TAG = "DocsyStepService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "docsy_step_channel"

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground health service: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        stepCounterSensor?.let { sensor ->
            val registered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI,
                60_000_000 // 1 minute batch latency
            )
            Log.d(TAG, "Step counter sensor registered: $registered")
        } ?: Log.w(TAG, "TYPE_STEP_COUNTER sensor not available on this device")

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return
        val currentReading = event.values[0].toLong()
        val todayStr = LocalDate.now().toString()

        serviceScope.launch {
            try {
                val db = DocsyDatabase.getInstance(applicationContext)
                val stepDao = db.stepDao()
                val existing = stepDao.getStepLogByDate(todayStr)

                val (newSteps, newBaseline) = if (existing == null) {
                    Pair(0L, currentReading)
                } else {
                    val lastReading = existing.lastSensorReading
                    val delta = if (currentReading >= lastReading && lastReading > 0) {
                        currentReading - lastReading
                    } else {
                        // Reboot occurred (sensor reset to 0) or initial
                        currentReading
                    }
                    Pair(existing.stepCount + delta, currentReading)
                }

                stepDao.insertOrUpdateStepLog(
                    StepLogEntity(
                        dateString = todayStr,
                        stepCount = newSteps,
                        lastSensorReading = newBaseline,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                Log.d(TAG, "[Step Log] Date: $todayStr | Today Steps: $newSteps | Reading: $currentReading")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging steps to Room: ${e.message}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Docsy Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks on-device step count for Docsy offline queries"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Docsy Step Counter")
            .setContentText("Step counting active in background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
