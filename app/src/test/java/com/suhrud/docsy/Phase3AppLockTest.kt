package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.security.AppLockSessionTracker
import com.suhrud.docsy.security.PinSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase3AppLockTest {

    private lateinit var context: Context
    private lateinit var pinManager: PinSecurityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pinManager = PinSecurityManager(context)
        pinManager.removePin()
    }

    @Test
    fun testPbkdf2HashAndVerify() {
        assertFalse(pinManager.isPinEnabled())

        // Invalid length
        assertFalse(pinManager.setPin("123"))
        assertFalse(pinManager.setPin("1234567"))
        assertFalse(pinManager.setPin("abcd"))

        // Valid 4-digit PIN
        assertTrue(pinManager.setPin("1234"))
        assertTrue(pinManager.isPinEnabled())

        // Verify correct PIN
        val successResult = pinManager.verifyPin("1234")
        assertTrue(successResult is PinSecurityManager.VerifyResult.Success)

        // Verify incorrect PIN
        val failureResult = pinManager.verifyPin("9999")
        assertTrue(failureResult is PinSecurityManager.VerifyResult.Incorrect)
        assertEquals(1, (failureResult as PinSecurityManager.VerifyResult.Incorrect).failedAttempts)
        assertFalse(failureResult.isLockedOut)
    }

    @Test
    fun testProgressiveRateLimitLockoutAndPersistence() {
        assertTrue(pinManager.setPin("4321"))

        // 4 failed attempts
        for (i in 1..4) {
            val res = pinManager.verifyPin("0000")
            assertTrue(res is PinSecurityManager.VerifyResult.Incorrect)
            assertFalse((res as PinSecurityManager.VerifyResult.Incorrect).isLockedOut)
        }

        // 5th failed attempt triggers 30s lockout
        val fifthRes = pinManager.verifyPin("0000")
        assertTrue(fifthRes is PinSecurityManager.VerifyResult.Incorrect)
        val incorrect = fifthRes as PinSecurityManager.VerifyResult.Incorrect
        assertTrue(incorrect.isLockedOut)
        assertTrue(incorrect.lockoutSeconds >= 30L)

        // Subsequence verify attempt should return LockedOut directly
        val lockedRes = pinManager.verifyPin("4321") // Even with correct PIN, must reject while locked
        assertTrue(lockedRes is PinSecurityManager.VerifyResult.LockedOut)

        // Verify lockout persistence across fresh manager instance
        val newManagerInstance = PinSecurityManager(context)
        assertTrue(newManagerInstance.getRemainingLockoutSeconds() > 0)
    }

    @Test
    fun testForgotPinRecoveryWithProfileName() {
        assertTrue(pinManager.setPin("9876"))
        assertTrue(pinManager.isPinEnabled())

        // Wrong profile name does not reset
        assertFalse(pinManager.resetPinWithProfileName("WrongName", "Suhrud"))
        assertTrue(pinManager.isPinEnabled())

        // Blank name does not reset
        assertFalse(pinManager.resetPinWithProfileName("", "Suhrud"))
        assertTrue(pinManager.isPinEnabled())

        // Correct profile name (case-insensitive trimmed) resets PIN
        assertTrue(pinManager.resetPinWithProfileName("  suhrud  ", "Suhrud"))
        assertFalse(pinManager.isPinEnabled())
    }

    @Test
    fun testAppLockSessionTracker() {
        assertTrue(pinManager.setPin("1111"))
        val tracker = AppLockSessionTracker(pinManager)

        // Cold start locks app
        assertTrue(tracker.isLocked.value)

        // Unlocking
        tracker.onUnlockSuccess()
        assertFalse(tracker.isLocked.value)

        // Screen off immediately locks
        tracker.onScreenOff()
        assertTrue(tracker.isLocked.value)
    }

    @Test
    fun testTrackerDoesNotLockWithoutPin() {
        pinManager.removePin()
        val tracker = AppLockSessionTracker(pinManager)
        assertFalse(tracker.isLocked.value)
        tracker.onScreenOff()
        assertFalse(tracker.isLocked.value)
        assertFalse(tracker.shouldAutoPromptBiometric())
    }

    @Test
    fun testPinLengthTrackingAndLockNow() {
        assertTrue(pinManager.setPin("123456"))
        assertEquals(6, pinManager.getPinLength())
        assertTrue(pinManager.hasExplicitPinLength())

        val tracker = AppLockSessionTracker(pinManager)
        tracker.onUnlockSuccess()
        assertFalse(tracker.isLocked.value)

        tracker.lockNow()
        assertTrue(tracker.isLocked.value)

        pinManager.removePin()
        assertFalse(pinManager.isPinEnabled())
        assertFalse(pinManager.hasExplicitPinLength())
        assertEquals(4, pinManager.getPinLength())
    }
}
