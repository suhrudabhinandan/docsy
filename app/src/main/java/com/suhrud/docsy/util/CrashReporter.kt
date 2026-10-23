package com.suhrud.docsy.util

import android.content.Context
import android.util.Log
import com.suhrud.docsy.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val FILE_NAME = "crash_last.txt"
    private const val DEBUG_FILE_NAME = "debug_log.txt"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashToFile(
                    context = context,
                    tag = "UNCAUGHT_CRASH",
                    threadName = thread.name,
                    throwable = throwable
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error writing uncaught crash to file", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun appendLog(context: Context, text: String) {
        appendDebugLog(context, text)
    }

    fun appendDebugLog(context: Context, text: String) {
        try {
            val file = File(context.filesDir, DEBUG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("[$timestamp] $text\n")
        } catch (_: Throwable) {
        }
    }

    fun getDebugLog(context: Context): String? {
        val file = File(context.filesDir, DEBUG_FILE_NAME)
        return if (file.exists() && file.length() > 0L) {
            try {
                file.readText()
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    fun writeLoggedThrowable(context: Context, tag: String, throwable: Throwable) {
        try {
            saveCrashToFile(
                context = context,
                tag = tag,
                threadName = Thread.currentThread().name,
                throwable = throwable,
                append = true
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error writing logged throwable to file", e)
        }
    }

    fun getCrashLog(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val content = file.readText()
            // Only show crash card if an actual uncaught crash or throwable was recorded
            if (content.contains("UNCAUGHT_CRASH") || content.contains("CRASH REPORT")) {
                content
            } else {
                // Stale or diagnostic content in crash_last.txt -> clear it
                clearCrashLog(context)
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun clearCrashLog(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting crash log file", e)
        }
    }

    private fun saveCrashToFile(
        context: Context,
        tag: String,
        threadName: String,
        throwable: Throwable,
        append: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = BuildConfig.VERSION_NAME
        val stackTraceStr = Log.getStackTraceString(throwable)

        val logContent = """
            ========================================
            CRASH REPORT [$tag]
            Timestamp: $timestamp
            App Version: $version
            Thread: $threadName
            Exception: ${throwable.javaClass.name}: ${throwable.message}
            ========================================
            $stackTraceStr
            
        """.trimIndent()

        val file = File(context.filesDir, FILE_NAME)
        if (append && file.exists()) {
            file.appendText("\n$logContent")
        } else {
            file.writeText(logContent)
        }
    }
}

