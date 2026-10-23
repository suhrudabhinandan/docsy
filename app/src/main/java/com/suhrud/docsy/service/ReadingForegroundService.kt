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
import android.os.PowerManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.suhrud.docsy.MainActivity
import com.suhrud.docsy.R
import com.suhrud.docsy.scanner.ReadingStateRepository
import com.suhrud.docsy.scanner.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReadingForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var scanJob: Job? = null
    private var hasActiveReadingStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "ReadingFgService"
        const val CHANNEL_ID = "docsy_reading_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_SCAN = "com.suhrud.docsy.ACTION_START_SCAN"
        const val ACTION_FULL_REREAD = "com.suhrud.docsy.ACTION_FULL_REREAD"
        const val EXTRA_FULL_REREAD = "extra_full_reread"

        fun startScan(context: Context, isFullReRead: Boolean = false) {
            try {
                val intent = Intent(context, ReadingForegroundService::class.java).apply {
                    action = if (isFullReRead) ACTION_FULL_REREAD else ACTION_START_SCAN
                    putExtra(EXTRA_FULL_REREAD, isFullReRead)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ReadingForegroundService scan", e)
            }
        }

        fun start(context: Context) {
            startScan(context, isFullReRead = false)
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
        try {
            createNotificationChannel()
            promoteToForeground("Reading files on device...", 0, 100)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in ReadingForegroundService onCreate", e)
        }

        progressJob = serviceScope.launch {
            ReadingStateRepository.readingState.collect { progress ->
                try {
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
                            buildNotification(
                                contentText = text,
                                current = current,
                                max = max,
                                docs = progress.documentsCount,
                                images = progress.photosCount,
                                sms = progress.messagesCount,
                                code = progress.otherCount
                            )
                        )
                    } else if (hasActiveReadingStarted) {
                        stopSelf()
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error updating foreground service notification", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            promoteToForeground("Reading files on device...", 0, 100)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in ReadingForegroundService onStartCommand", e)
        }

        val isStartScan = intent?.action == ACTION_START_SCAN || intent?.action == ACTION_FULL_REREAD || intent?.action == null
        if (isStartScan && (scanJob == null || scanJob?.isActive == false)) {
            val isFullReRead = intent?.getBooleanExtra(EXTRA_FULL_REREAD, false) ?: false
            val isRestart = intent == null

            if (!isRestart || ScanCoordinator.isPending(applicationContext)) {
                ScanCoordinator.markPending(applicationContext, isFullReRead)
            } else {
                stopSelf()
                return START_NOT_STICKY
            }

            if (!ScanCoordinator.tryAcquire()) {
                stopSelf()
                return START_NOT_STICKY
            }

            acquireWakeLock()
            ScheduledSyncWorker.scheduleWatchdog(applicationContext)

            scanJob = serviceScope.launch(Dispatchers.IO) {
                var completed = false
                try {
                    ReadingStateRepository.startReading(totalFiles = 0)
                    val db = com.suhrud.docsy.data.db.DocsyDatabase.getInstance(applicationContext)
                    if (ScanCoordinator.consumeFullReRead(applicationContext)) {
                        db.documentDao().deleteAllDocuments()
                    }
                    val scanner = com.suhrud.docsy.scanner.FileScanner(applicationContext, db.documentDao())
                    val result = scanner.scanStorage()
                    ReadingStateRepository.finishReading(result.documentsDiscovered, errors = 0)
                    completed = true
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception running scanStorage in ReadingForegroundService", t)
                    ReadingStateRepository.finishReading(0, errors = 1)
                } finally {
                    if (completed) ScanCoordinator.markFinished(applicationContext)
                    ScanCoordinator.release()
                    releaseWakeLock()
                    if (completed) {
                        kotlinx.coroutines.delay(500)
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service time limit reached; handing over to WorkManager")
        ScheduledSyncWorker.scheduleResumeSoon(applicationContext)
        scanJob?.cancel()
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "docsy:reading").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Throwable) {}
        wakeLock = null
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
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to call startForeground", e)
            com.suhrud.docsy.util.CrashReporter.writeLoggedThrowable(this, "ReadingForegroundService", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fallbackEx: Throwable) {
                Log.e(TAG, "Fallback startForeground failed", fallbackEx)
            }
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        scanJob?.cancel()
        releaseWakeLock()
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        contentText: String,
        current: Int,
        max: Int,
        docs: Int = 0,
        images: Int = 0,
        sms: Int = 0,
        code: Int = 0
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val collapsedView = RemoteViews(packageName, R.layout.notification_reading_custom).apply {
            setTextViewText(R.id.notification_title, "Docsy Reading")
            setTextViewText(R.id.notification_text, contentText)
            setProgressBar(R.id.notification_progress, max, current, max == 0)
        }

        val expandedView = RemoteViews(packageName, R.layout.notification_reading_expanded).apply {
            setTextViewText(R.id.notification_title, "Docsy Reading")
            setTextViewText(R.id.notification_text, contentText)
            setProgressBar(R.id.notification_progress, max, current, max == 0)
            setTextViewText(R.id.notification_stat_docs, "Docs: $docs")
            setTextViewText(R.id.notification_stat_images, "Images: $images")
            setTextViewText(R.id.notification_stat_sms, "SMS: $sms")
            setTextViewText(R.id.notification_stat_code, "Code: $code")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent)
            .build()
    }
}
