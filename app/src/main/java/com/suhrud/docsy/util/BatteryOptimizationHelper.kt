package com.suhrud.docsy.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Manages background running & battery optimization exemptions.
 * Safe against crashes, avoids concurrent activity launches, and ensures
 * the prompt is asked at most once without exiting the app.
 */
object BatteryOptimizationHelper {

    private const val PREFS = "docsy_battery_prompt"
    private const val KEY_PROMPTED = "has_prompted_background_run"

    fun hasBeenPrompted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PROMPTED, false)
    }

    fun setPrompted(context: Context, prompted: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PROMPTED, prompted).apply()
    }

    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        setPrompted(context, true)
        if (isExempt(context)) return

        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(appSettingsIntent)
            } catch (t: Throwable) {
                Log.w("BatteryOptimization", "Could not open settings", t)
            }
        }
    }
}
