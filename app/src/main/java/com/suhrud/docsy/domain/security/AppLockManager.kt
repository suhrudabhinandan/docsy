package com.suhrud.docsy.domain.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class BiometricStatus {
    READY,
    NONE_ENROLLED,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    UNAVAILABLE
}

class AppLockManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("docsy_security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_LOCK_ENABLED = "pref_lock_enabled"
        private const val PREF_PIN_SALT = "pref_pin_salt"
        private const val PREF_PIN_HASH = "pref_pin_hash"
        private const val PREF_PIN_LENGTH = "pref_pin_length"
        private const val PREF_TIMEOUT_MIN = "pref_timeout_min"
        private const val PREF_LAST_BACKGROUND_TIME = "pref_last_background_time"
        private const val PREF_FAILED_ATTEMPTS = "pref_failed_attempts"
        private const val PREF_LOCKOUT_UNTIL = "pref_lockout_until"

        private const val PBKDF2_ITERATIONS = 10_000
        private const val PBKDF2_KEY_LENGTH = 256
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(PREF_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_LOCK_ENABLED, enabled).apply()
    }

    fun hasPin(): Boolean = prefs.getString(PREF_PIN_HASH, null) != null

    fun getPinLength(): Int = prefs.getInt(PREF_PIN_LENGTH, 4).coerceIn(4, 6)

    fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN must be 4 to 6 digits" }
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPinWithPbkdf2(pin, salt)

        val saltB64 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(salt)
        } else {
            android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        }

        prefs.edit()
            .putString(PREF_PIN_SALT, saltB64)
            .putString(PREF_PIN_HASH, hash)
            .putInt(PREF_PIN_LENGTH, pin.length)
            .putInt(PREF_FAILED_ATTEMPTS, 0)
            .putLong(PREF_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false

        val storedSaltB64 = prefs.getString(PREF_PIN_SALT, null) ?: return false
        val storedHash = prefs.getString(PREF_PIN_HASH, null) ?: return false

        val salt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getDecoder().decode(storedSaltB64)
        } else {
            android.util.Base64.decode(storedSaltB64, android.util.Base64.NO_WRAP)
        }

        val testHash = hashPinWithPbkdf2(pin, salt)
        val matches = (testHash == storedHash)

        if (matches) {
            prefs.edit()
                .putInt(PREF_FAILED_ATTEMPTS, 0)
                .putLong(PREF_LOCKOUT_UNTIL, 0L)
                .apply()
        } else {
            val failed = prefs.getInt(PREF_FAILED_ATTEMPTS, 0) + 1
            val delayMs = computeLockoutDelayMillis(failed)
            val editor = prefs.edit().putInt(PREF_FAILED_ATTEMPTS, failed)
            if (delayMs > 0L) {
                editor.putLong(PREF_LOCKOUT_UNTIL, System.currentTimeMillis() + delayMs)
            }
            editor.apply()
        }

        return matches
    }

    fun computeLockoutDelayMillis(failedAttempts: Int): Long {
        return when {
            failedAttempts < 3 -> 0L
            failedAttempts == 3 -> 5_000L
            failedAttempts == 4 -> 15_000L
            failedAttempts == 5 -> 30_000L
            else -> (30_000L + (failedAttempts - 5) * 15_000L).coerceAtMost(300_000L)
        }
    }

    fun isLockedOut(): Boolean {
        val until = prefs.getLong(PREF_LOCKOUT_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun getRemainingLockoutSeconds(): Int {
        val until = prefs.getLong(PREF_LOCKOUT_UNTIL, 0L)
        val remaining = ((until - System.currentTimeMillis()) / 1000).toInt()
        return remaining.coerceAtLeast(0)
    }

    fun getTimeoutMinutes(): Int = prefs.getInt(PREF_TIMEOUT_MIN, 0)

    fun setTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(PREF_TIMEOUT_MIN, minutes).apply()
    }

    fun recordAppBackgrounded(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(PREF_LAST_BACKGROUND_TIME, timestamp).apply()
    }

    fun shouldLockOnForeground(now: Long = System.currentTimeMillis()): Boolean {
        if (!isAppLockEnabled()) return false
        val timeoutMin = getTimeoutMinutes()
        if (timeoutMin == 0) return true

        val lastBg = prefs.getLong(PREF_LAST_BACKGROUND_TIME, 0L)
        if (lastBg == 0L) return true
        val elapsed = now - lastBg
        return elapsed >= (timeoutMin * 60 * 1000L)
    }

    fun checkBiometricStatus(): BiometricStatus {
        val bm = BiometricManager.from(context)
        return when (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onAuthenticated: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Docsy")
            .setSubtitle("Unlock to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Use PIN")
            .build()

        prompt.authenticate(promptInfo)
    }

    fun openSecurityEnrollment(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun hashPinWithPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(hash)
        } else {
            android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
        }
    }
}
