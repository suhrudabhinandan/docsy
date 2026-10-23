package com.suhrud.docsy.scanner

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One place that decides "is a scan running right now" and "does a scan still need to finish".
 *
 * - Only one scan may run at a time (foreground service, WorkManager slice, or app start can all ask).
 * - A persisted "pending" flag survives process death. Scans are incremental (already-read files are
 *   skipped by content key), so resuming after a kill continues where it stopped.
 */
object ScanCoordinator {
    private const val PREFS = "docsy_scan_state"
    private const val KEY_PENDING = "scan_pending"
    private const val KEY_FULL_REREAD_PENDING = "full_reread_pending"

    private val running = AtomicBoolean(false)

    /** Returns true if the caller now owns the scan. Call [release] in a finally block. */
    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    fun release() { running.set(false) }

    fun isRunning(): Boolean = running.get()

    fun markPending(context: Context, fullReRead: Boolean) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean(KEY_PENDING, true)
            .putBoolean(KEY_FULL_REREAD_PENDING, fullReRead || p.getBoolean(KEY_FULL_REREAD_PENDING, false))
            .apply()
    }

    fun isPending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PENDING, false)

    /** True once only: the full re-read wipe must happen a single time, never again on a resume. */
    fun consumeFullReRead(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = p.getBoolean(KEY_FULL_REREAD_PENDING, false)
        if (v) p.edit().putBoolean(KEY_FULL_REREAD_PENDING, false).apply()
        return v
    }

    fun markFinished(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PENDING, false).putBoolean(KEY_FULL_REREAD_PENDING, false).apply()
    }

    /**
     * How many files may be OCR'd at the same time. More parallelism is faster until the phone gets hot,
     * then the system throttles the CPU and everything slows down, so back off when it heats up.
     */
    fun ocrParallelism(context: Context): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        var n = (cores / 2).coerceIn(2, 4)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_MODERATE -> n = minOf(n, 2)
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> n = 1
                else -> {}
            }
        }
        return n
    }

    /**
     * Call before each file. A long background scan heats the phone; once Android starts throttling the CPU,
     * running flat out is slower overall. This pauses briefly (cancellable) until the phone cools.
     */
    suspend fun throttleIfHot(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        var waited = 0
        while (waited < 12) { // at most ~60 s per file, then carry on
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> { delay(5000); waited++ }
                PowerManager.THERMAL_STATUS_MODERATE -> { delay(250); return }
                else -> return
            }
        }
    }
}
