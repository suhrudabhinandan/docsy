package com.suhrud.docsy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserProfile
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import com.suhrud.docsy.ui.startup.StartupDestination
import com.suhrud.docsy.ui.startup.StartupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.security.KeyStoreException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartupStateUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testEmptyDataStoreResolvesToWelcome() = runTest(testDispatcher) {
        val emptyProfile = UserProfile(
            name = "",
            alias = null,
            avatarId = UserPreferencesRepository.DEFAULT_AVATAR_ID,
            isNameUnmatchable = false,
            onboardingStage = OnboardingStage.WELCOME,
            instructionStep = 1,
            onboardingCompleted = false,
            autoRead24h = true,
            chatVoiceTier = "Basic"
        )

        val viewModel = StartupViewModel(
            application = application,
            profileFlowProvider = { flowOf(emptyProfile) },
            pinEnabledChecker = { false },
            isLockedChecker = { false }
        )

        advanceUntilIdle()

        val state = viewModel.startupState.value
        assertTrue("StartupState must be resolved", state.isResolved)
        assertEquals(StartupDestination.Welcome, state.destination)
        assertEquals("", state.profile.name)
    }

    @Test
    fun testDataStoreThrowingResolvesSafelyToWelcome() = runTest(testDispatcher) {
        val viewModel = StartupViewModel(
            application = application,
            profileFlowProvider = {
                flow {
                    throw IOException("Simulated DataStore read disk error")
                }
            },
            pinEnabledChecker = { false },
            isLockedChecker = { false }
        )

        advanceUntilIdle()

        val state = viewModel.startupState.value
        assertTrue("StartupState must resolve even when DataStore throws", state.isResolved)
        assertEquals(StartupDestination.Welcome, state.destination)
    }

    @Test
    fun testKeystoreThrowingResolvesSafely() = runTest(testDispatcher) {
        val completedProfile = UserProfile(
            name = "Suhrud",
            alias = null,
            avatarId = "avatar_01",
            isNameUnmatchable = false,
            onboardingStage = OnboardingStage.COMPLETED,
            instructionStep = 5,
            onboardingCompleted = true,
            autoRead24h = true,
            chatVoiceTier = "Basic"
        )

        val viewModel = StartupViewModel(
            application = application,
            profileFlowProvider = { flowOf(completedProfile) },
            pinEnabledChecker = { throw KeyStoreException("Simulated Keystore unwrap failure") },
            isLockedChecker = { false }
        )

        advanceUntilIdle()

        val state = viewModel.startupState.value
        assertTrue("StartupState must resolve even when Keystore throws", state.isResolved)
        // With pin check failing gracefully, it falls back to Home
        assertEquals(StartupDestination.Home, state.destination)
    }

    @Test
    fun testAppLockEnabledResolvesToLock() = runTest(testDispatcher) {
        val completedProfile = UserProfile(
            name = "Suhrud",
            alias = null,
            avatarId = "avatar_01",
            isNameUnmatchable = false,
            onboardingStage = OnboardingStage.COMPLETED,
            instructionStep = 5,
            onboardingCompleted = true,
            autoRead24h = true,
            chatVoiceTier = "Basic"
        )

        val viewModel = StartupViewModel(
            application = application,
            profileFlowProvider = { flowOf(completedProfile) },
            pinEnabledChecker = { true },
            isLockedChecker = { true }
        )

        advanceUntilIdle()

        val state = viewModel.startupState.value
        assertTrue("StartupState must be resolved", state.isResolved)
        assertEquals(StartupDestination.Lock, state.destination)
    }
}
