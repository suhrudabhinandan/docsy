package com.suhrud.docsy.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Central animation tokens and spring specifications for DOCSY.
 * Satisfies Phase 14.4 in AUDIT.md.
 */
object Motion {

    // Standard duration tokens (ms)
    const val DURATION_FAST = 120
    const val DURATION_NORMAL = 200
    const val DURATION_EMPHASIZED = 320

    // Standard tween specs
    val fastTween = tween<Float>(durationMillis = DURATION_FAST)
    val normalTween = tween<Float>(durationMillis = DURATION_NORMAL)
    val emphasizedTween = tween<Float>(durationMillis = DURATION_EMPHASIZED)

    // Spring specs
    val springStiff = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val springGentle = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Checks whether system animations are disabled via Accessibility settings.
     */
    fun areAnimationsDisabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}
