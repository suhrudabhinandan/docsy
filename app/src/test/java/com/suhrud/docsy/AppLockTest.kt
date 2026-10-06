package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.domain.security.AppLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockTest {

    private lateinit var context: Context
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("docsy_security_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        appLockManager = AppLockManager(context)
    }

    @Test
    fun testPinHashingAndVerification() {
        assertFalse(appLockManager.hasPin())
        appLockManager.setPin("4921")
        assertTrue(appLockManager.hasPin())
        assertEquals(4, appLockManager.getPinLength())

        assertTrue("Correct PIN must verify", appLockManager.verifyPin("4921"))
        assertFalse("Incorrect PIN must fail", appLockManager.verifyPin("0000"))
    }

    @Test
    fun testGrowingLockoutDelayAfterFailedAttempts() {
        appLockManager.setPin("123456")
        assertEquals(6, appLockManager.getPinLength())

        assertFalse(appLockManager.verifyPin("111111"))
        assertFalse(appLockManager.isLockedOut())
        assertFalse(appLockManager.verifyPin("222222"))
        assertFalse(appLockManager.isLockedOut())

        assertFalse(appLockManager.verifyPin("333333"))
        assertTrue("Should be locked out after 3 failed attempts", appLockManager.isLockedOut())
        assertTrue("Remaining seconds should be > 0", appLockManager.getRemainingLockoutSeconds() > 0)
    }

    @Test
    fun testBackgroundTimeoutTiming() {
        appLockManager.setPin("4444")
        appLockManager.setAppLockEnabled(true)

        appLockManager.setTimeoutMinutes(0)
        val t0 = 1000000L
        appLockManager.recordAppBackgrounded(t0)
        assertTrue("Immediately timeout should require lock", appLockManager.shouldLockOnForeground(t0 + 1000L))

        appLockManager.setTimeoutMinutes(5)
        appLockManager.recordAppBackgrounded(t0)
        assertFalse("2 minutes later should not lock", appLockManager.shouldLockOnForeground(t0 + 2 * 60 * 1000L))
        assertTrue("5 minutes later should lock", appLockManager.shouldLockOnForeground(t0 + 5 * 60 * 1000L + 1000L))
    }
}
