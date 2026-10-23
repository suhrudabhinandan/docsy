package com.suhrud.docsy.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.scanner.FileScanner
import com.suhrud.docsy.scanner.ReadingStateRepository
import com.suhrud.docsy.scanner.ScanCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Safety net for reading. If Android (or a phone maker's battery manager) kills the foreground service,
 * this worker continues the scan in slices. Scans skip files that are already read, so each slice
 * picks up where the last one stopped. It also does the normal 24-hour refresh.
 */
class ScheduledSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsy_24h_scheduled_sync"
        const val WATCHDOG_NAME = "docsy_scan_watchdog"
        const val RESUME_NAME = "docsy_scan_resume"
        private const val WATCHDOG_TAG = "docsy_watchdog"
        private const val SLICE_MS = 8 * 60 * 1000L // WorkManager gives ~10 minutes per run

        fun schedule24hSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduledSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Every 15 minutes: if a scan is still pending and nothing is running it, continue it. */
        fun scheduleWatchdog(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduledSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .addTag(WATCHDOG_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Continue soon (used when the foreground service hits Android's time limit). */
        fun scheduleResumeSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduledSyncWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RESUME_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // The 15-minute watchdog only acts when a scan is unfinished; the 24h job always refreshes.
        if (tags.contains(WATCHDOG_TAG) && !ScanCoordinator.isPending(applicationContext)) {
            return@withContext Result.success()
        }
        if (ScanCoordinator.isRunning()) return@withContext Result.success() // service is already on it

        if (!ScanCoordinator.tryAcquire()) return@withContext Result.success()
        try {
            val database = DocsyDatabase.getInstance(applicationContext)
            val scanner = FileScanner(
                context = applicationContext,
                documentDao = database.documentDao(),
                dispatcher = Dispatchers.IO
            )

            ScanCoordinator.markPending(applicationContext, fullReRead = false)
            ReadingStateRepository.startReading(totalFiles = 0)
            val result = withTimeoutOrNull(SLICE_MS) { scanner.scanStorage() }

            if (result != null) {
                ScanCoordinator.markFinished(applicationContext)
                ReadingStateRepository.finishReading(result.documentsDiscovered, errors = 0)
                Result.success()
            } else {
                // Time slice used up: files read so far are saved. Ask for another slice.
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        } finally {
            ScanCoordinator.release()
        }
    }
}
