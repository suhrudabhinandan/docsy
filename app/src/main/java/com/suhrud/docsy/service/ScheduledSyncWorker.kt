package com.suhrud.docsy.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.scanner.FileScanner
import com.suhrud.docsy.scanner.ReadingStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ScheduledSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsy_24h_scheduled_sync"

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

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = DocsyDatabase.getInstance(applicationContext)
            val scanner = FileScanner(
                context = applicationContext,
                documentDao = database.documentDao(),
                dispatcher = Dispatchers.IO
            )

            ReadingStateRepository.startReading(totalFiles = 0)
            val result = scanner.scanStorage()
            ReadingStateRepository.finishReading(result.documentsDiscovered, errors = 0)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
