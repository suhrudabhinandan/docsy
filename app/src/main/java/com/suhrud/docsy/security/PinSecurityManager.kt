package com.suhrud.docsy.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinSecurityManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "docsy_secure_pin_prefs"
        private const val FALLBACK_PREFS_NAME = "docsy_pin_prefs_fallback"

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_RANDOMIZE_KEYPAD = "randomize_keypad"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_TIMESTAMP = "lockout_until_timestamp"

        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16

        fun hashPin(pin: String, salt: ByteArray): String {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = skf.generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(hash)
        }
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            // Fallback for Robolectric or Keystore-disabled environments
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isPinEnabled(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash() != null
    }

    fun getPinLength(): Int {
        val len = prefs.getInt(KEY_PIN_LENGTH, 0)
        return if (len in 4..6) len else 4
    }

    fun hasExplicitPinLength(): Boolean {
        return prefs.contains(KEY_PIN_LENGTH)
    }

    fun isFingerprintEnabled(): Boolean {
        return prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    fun isRandomizeKeypadEnabled(): Boolean {
        return prefs.getBoolean(KEY_RANDOMIZE_KEYPAD, false)
    }

    fun setRandomizeKeypadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RANDOMIZE_KEYPAD, enabled).apply()
    }

    private fun getPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    private fun getPinSalt(): ByteArray? {
        val saltStr = prefs.getString(KEY_PIN_SALT, null) ?: return null
        return try {
            Base64.getDecoder().decode(saltStr)
        } catch (_: Exception) {
            null
        }
    }

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return false
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        val hash = hashPin(pin, salt)

        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putBoolean(KEY_PIN_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
        return true
    }

    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_LENGTH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .putBoolean(KEY_FINGERPRINT_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
    }

    /**
     * Returns remaining lockout seconds if currently locked out, or 0 if unlocked.
     */
    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()
        if (lockoutUntil > currentTime) {
            return (lockoutUntil - currentTime) / 1000L + 1L
        }
        return 0L
    }

    /**
     * Verifies the entered PIN.
     * Returns:
     * - VerifyResult.Success: PIN is correct
     * - VerifyResult.Incorrect(remainingAttempts, isLockedOut, lockoutSeconds)
     * - VerifyResult.LockedOut(lockoutSeconds)
     */
    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Incorrect(val failedAttempts: Int, val isLockedOut: Boolean, val lockoutSeconds: Long) : VerifyResult()
        data class LockedOut(val lockoutSeconds: Long) : VerifyResult()
    }

    fun verifyPin(pin: String): VerifyResult {
        val remainingSeconds = getRemainingLockoutSeconds()
        if (remainingSeconds > 0) {
            return VerifyResult.LockedOut(remainingSeconds)
        }

        val storedHash = getPinHash() ?: return VerifyResult.Success
        val salt = getPinSalt() ?: return VerifyResult.Success

        val candidateHash = hashPin(pin, salt)
        if (candidateHash == storedHash) {
            // Reset failed attempts on success
            val editor = prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            if (!prefs.contains(KEY_PIN_LENGTH) && pin.length in 4..6) {
                editor.putInt(KEY_PIN_LENGTH, pin.length)
            }
            editor.apply()
            return VerifyResult.Success
        } else {
            val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            var lockoutSeconds = 0L

            if (currentFailed >= 5) {
                // Progressive backoff:
                // 5 failed -> 30s
                // 6 failed -> 60s
                // 7 failed -> 120s
                // 8+ failed -> up to 300s (5 min)
                val multiplier = (currentFailed - 5).coerceIn(0, 4)
                lockoutSeconds = when (multiplier) {
                    0 -> 30L
                    1 -> 60L
                    2 -> 120L
                    else -> 300L
                }
                val lockoutUntil = System.currentTimeMillis() + (lockoutSeconds * 1000L)
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, currentFailed)
                    .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, lockoutUntil)
                    .apply()
                return VerifyResult.Incorrect(currentFailed, isLockedOut = true, lockoutSeconds = lockoutSeconds)
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentFailed).apply()
                return VerifyResult.Incorrect(currentFailed, isLockedOut = false, lockoutSeconds = 0L)
            }
        }
    }

    /**
     * Emergency recovery: verifies the stored profile name (case-insensitive trimmed).
     * If matched, resets the PIN without destroying local databases.
     */
    fun resetPinWithProfileName(candidateName: String, actualProfileName: String): Boolean {
        val trimmedCandidate = candidateName.trim()
        val trimmedActual = actualProfileName.trim()
        if (trimmedActual.isEmpty()) {
            removePin()
            return true
        }
        if (trimmedCandidate.isNotEmpty() && trimmedCandidate.equals(trimmedActual, ignoreCase = true)) {
            removePin()
            return true
        }
        return false
    }
}
