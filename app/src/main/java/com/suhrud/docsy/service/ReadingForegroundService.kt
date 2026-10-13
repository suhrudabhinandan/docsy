package com.suhrud.docsy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.suhrud.docsy.MainActivity
import com.suhrud.docsy.R
import com.suhrud.docsy.scanner.ReadingStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReadingForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var hasActiveReadingStarted = false

    companion object {
        private const val TAG = "ReadingFgService"
        const val CHANNEL_ID = "docsy_reading_channel"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            try {
                val intent = Intent(context, ReadingForegroundService::class.java)
                // Prefer context.startService() which is valid while app is active and does NOT
                // impose the fatal 5-second ForegroundServiceDidNotStartInTimeException deadline.
                // Service immediately promotes itself to foreground via promoteToForeground().
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ReadingForegroundService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ReadingForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ReadingForegroundService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground("Reading your phone...", 0, 100)

        progressJob = serviceScope.launch {
            ReadingStateRepository.readingState.collect { progress ->
                if (progress.isReading) {
                    hasActiveReadingStarted = true
                    val max = if (progress.totalFiles > 0) progress.totalFiles else 100
                    val current = progress.currentFileIndex
                    val text = if (progress.totalFiles > 0) {
                        "Processed $current of ${progress.totalFiles} files"
                    } else {
                        if (progress.stageDescription.isNotBlank()) progress.stageDescription else "Reading files..."
                    }
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(
                        NOTIFICATION_ID,
                        buildNotification(text, current, max)
                    )
                } else if (hasActiveReadingStarted) {
                    // Only stop once reading has actually completed after starting
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure startForeground is called promptly in onStartCommand to satisfy Android 14+
        promoteToForeground("Reading your phone...", 0, 100)
        return START_NOT_STICKY
    }

    private fun promoteToForeground(contentText: String, current: Int, max: Int) {
        val notification = buildNotification(contentText, current, max)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call startForeground", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback startForeground failed", fallbackEx)
            }
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Docsy File Reading",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress while Docsy reads local documents"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String, current: Int, max: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Docsy Reading")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(max, current, max == 0)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }
}
