package com.suhrud.docsy.ui.startup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserProfile
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import com.suhrud.docsy.security.AppLockSessionTracker
import com.suhrud.docsy.security.PinSecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class StartupDestination {
    data object Lock : StartupDestination()
    data object Welcome : StartupDestination()
    data object Home : StartupDestination()
}

data class StartupState(
    val destination: StartupDestination,
    val profile: UserProfile,
    val isResolved: Boolean
)

open class StartupViewModel @JvmOverloads constructor(
    application: Application,
    private val profileFlowProvider: (() -> Flow<UserProfile>)? = null,
    private val pinEnabledChecker: (() -> Boolean)? = null,
    private val isLockedChecker: (() -> Boolean)? = null,
    private val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application),
    val pinSecurityManager: PinSecurityManager = PinSecurityManager(application),
    val appLockSessionTracker: AppLockSessionTracker = AppLockSessionTracker(pinSecurityManager)
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application) {
                    "Application not found in CreationExtras"
                }
                StartupViewModel(app)
            }
        }

        val defaultProfile = UserProfile(
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
    }

    private val _startupState = MutableStateFlow(
        StartupState(
            destination = StartupDestination.Welcome,
            profile = defaultProfile,
            isResolved = false
        )
    )
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    init {
        resolveStartupState()
        viewModelScope.launch {
            try {
                val flow = profileFlowProvider?.invoke() ?: userPreferencesRepository.userProfileFlow
                flow.collect { profile ->
                    val isPinEnabled = try {
                        pinEnabledChecker?.invoke() ?: pinSecurityManager.isPinEnabled()
                    } catch (_: Throwable) {
                        false
                    }
                    val isLocked = isPinEnabled && try {
                        isLockedChecker?.invoke() ?: appLockSessionTracker.isLocked.value
                    } catch (_: Throwable) {
                        false
                    }
                    val dest = calculateDestination(profile, isLocked)
                    _startupState.value = _startupState.value.copy(
                        destination = dest,
                        profile = profile
                    )
                }
            } catch (_: Throwable) {}
        }

        viewModelScope.launch {
            try {
                appLockSessionTracker.isLocked.collect { locked ->
                    val isPinEnabled = try {
                        pinEnabledChecker?.invoke() ?: pinSecurityManager.isPinEnabled()
                    } catch (_: Throwable) {
                        false
                    }
                    val effectiveLocked = isPinEnabled && (isLockedChecker?.invoke() ?: locked)
                    val currentProfile = _startupState.value.profile
                    val dest = calculateDestination(currentProfile, effectiveLocked)
                    _startupState.value = _startupState.value.copy(destination = dest)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun calculateDestination(profile: UserProfile, isLocked: Boolean): StartupDestination {
        return when {
            isLocked && profile.onboardingStage == OnboardingStage.COMPLETED -> {
                StartupDestination.Lock
            }
            profile.name.isBlank() || profile.onboardingStage != OnboardingStage.COMPLETED -> {
                StartupDestination.Welcome
            }
            else -> {
                StartupDestination.Home
            }
        }
    }

    fun resolveStartupState(timeoutMs: Long = 1500L) {
        viewModelScope.launch {
            try {
                val resolvedProfile = try {
                    withTimeoutOrNull(timeoutMs) {
                        val flow = profileFlowProvider?.invoke() ?: userPreferencesRepository.userProfileFlow
                        flow.first()
                    } ?: defaultProfile
                } catch (_: Throwable) {
                    defaultProfile
                }

                val isPinEnabled = try {
                    pinEnabledChecker?.invoke() ?: pinSecurityManager.isPinEnabled()
                } catch (_: Throwable) {
                    false
                }

                val isLocked = isPinEnabled && try {
                    isLockedChecker?.invoke() ?: appLockSessionTracker.isLocked.value
                } catch (_: Throwable) {
                    false
                }

                val destination = calculateDestination(resolvedProfile, isLocked)

                _startupState.value = StartupState(
                    destination = destination,
                    profile = resolvedProfile,
                    isResolved = true
                )
            } catch (_: Throwable) {
                _startupState.value = StartupState(
                    destination = StartupDestination.Welcome,
                    profile = defaultProfile,
                    isResolved = true
                )
            }
        }
    }
}
