package com.suhrud.docsy.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLockSessionTracker(
    private val pinSecurityManager: PinSecurityManager
) {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundTimestamp: Long = 0L
    private var lastAuthenticatedTimestamp: Long = 0L

    companion object {
        const val BACKGROUND_TIMEOUT_MS = 30_000L // 30 seconds
    }

    init {
        // Cold start lock only if a PIN is actually configured
        try {
            if (pinSecurityManager.isPinEnabled()) {
                _isLocked.value = true
            }
        } catch (_: Throwable) {
            _isLocked.value = false
        }
    }

    fun onAppStart() {
        if (!pinSecurityManager.isPinEnabled()) {
            _isLocked.value = false
            return
        }

        if (backgroundTimestamp != 0L) {
            val elapsed = SystemClock.elapsedRealtime() - backgroundTimestamp
            if (elapsed > BACKGROUND_TIMEOUT_MS) {
                _isLocked.value = true
            }
        }
    }

    fun onAppStop() {
        backgroundTimestamp = SystemClock.elapsedRealtime()
    }

    fun onScreenOff() {
        if (pinSecurityManager.isPinEnabled()) {
            _isLocked.value = true
        }
    }

    fun onUnlockSuccess() {
        lastAuthenticatedTimestamp = SystemClock.elapsedRealtime()
        _isLocked.value = false
    }

    fun shouldAutoPromptBiometric(): Boolean {
        // Never auto-prompt in headless or streaming emulator environments
        // Users can tap the biometric icon on the LockScreen directly.
        return false
    }

    fun lockNow() {
        if (pinSecurityManager.isPinEnabled()) {
            _isLocked.value = true
        }
    }

    fun registerScreenOffReceiver(context: Context): BroadcastReceiver? {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    onScreenOff()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiver
        } catch (_: Throwable) {
            null
        }
    }
}
