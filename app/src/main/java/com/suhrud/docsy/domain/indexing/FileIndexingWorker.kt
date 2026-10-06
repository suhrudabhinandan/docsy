package com.suhrud.docsy.domain.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.suhrud.docsy.data.local.DocsyDatabase
import com.suhrud.docsy.data.repository.DocumentRepository

class FileIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = DocsyDatabase.getInstance(applicationContext)
            val repository = DocumentRepository(database.documentDao())
            repository.syncDeviceFiles(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
