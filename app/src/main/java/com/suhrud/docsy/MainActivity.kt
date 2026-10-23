package com.suhrud.docsy

import android.content.BroadcastReceiver
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.suhrud.docsy.ui.startup.StartupViewModel
import com.suhrud.docsy.ui.startup.StartupDestination
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import com.suhrud.docsy.security.AppLockSessionTracker
import com.suhrud.docsy.security.BiometricHelper
import com.suhrud.docsy.security.PinSecurityManager
import com.suhrud.docsy.ui.home.HomeScreen
import com.suhrud.docsy.ui.lock.LockScreen
import com.suhrud.docsy.ui.onboarding.ChooseAvatarScreen
import com.suhrud.docsy.ui.onboarding.EnterNameScreen
import com.suhrud.docsy.ui.onboarding.InstructionsScreen
import com.suhrud.docsy.ui.onboarding.PermissionFlowScreen
import com.suhrud.docsy.ui.onboarding.WelcomeScreen
import com.suhrud.docsy.ui.profile.ProfileScreen
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.ui.reading.ReadingProgressScreen
import com.suhrud.docsy.ui.settings.AccessPermissionsScreen
import com.suhrud.docsy.ui.settings.AppLockSettingsScreen
import com.suhrud.docsy.ui.settings.ExtractionInspectorScreen
import com.suhrud.docsy.ui.settings.SettingsScreen
import com.suhrud.docsy.ui.theme.DocsyTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    PROFILE,
    SETTINGS,
    APP_LOCK_SETTINGS,
    ACCESS_INFO,
    READING,
    INSPECTOR,
    STEP_DIAGNOSTICS,
    CHAT,
    CHAT_HISTORY
}

class MainActivity : FragmentActivity() {

    private val startupViewModel: StartupViewModel by viewModels { StartupViewModel.Factory }
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var pinSecurityManager: PinSecurityManager
    private lateinit var appLockSessionTracker: AppLockSessionTracker
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val splashStartTime = SystemClock.elapsedRealtime()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            userPreferencesRepository = UserPreferencesRepository(applicationContext)
            pinSecurityManager = startupViewModel.pinSecurityManager
            appLockSessionTracker = startupViewModel.appLockSessionTracker

            // Lifecycle observer for 30-second background timeout (item 3.4)
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> appLockSessionTracker.onAppStart()
                    Lifecycle.Event.ON_STOP -> appLockSessionTracker.onAppStop()
                    else -> {}
                }
            })

            // Broadcast receiver for screen off lock (item 3.4)
            screenOffReceiver = appLockSessionTracker.registerScreenOffReceiver(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error during MainActivity initialization", e)
        }

        // Keep system splash screen with a hard timeout of 800 ms so it can NEVER hold the screen
        splashScreen.setKeepOnScreenCondition {
            val elapsed = SystemClock.elapsedRealtime() - splashStartTime
            !startupViewModel.startupState.value.isResolved && elapsed < 800L
        }

        setContent {
            DocsyTheme {
                val startupState by startupViewModel.startupState
                    .collectAsStateWithLifecycle()
                val isLocked by appLockSessionTracker.isLocked
                    .collectAsStateWithLifecycle()

                val scope = rememberCoroutineScope()
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
                var initialChatQuery by rememberSaveable { mutableStateOf("") }
                var activeChatSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
                val profile = startupState.profile

                // Ensure FLAG_SECURE is cleared so the streaming emulator displays the screen
                DisposableEffect(Unit) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose {}
                }

                val readingState by com.suhrud.docsy.scanner.ReadingStateRepository.readingState
                    .collectAsStateWithLifecycle()

                val isAppLockActive = pinSecurityManager.isPinEnabled() &&
                        profile.onboardingStage == OnboardingStage.COMPLETED &&
                        (isLocked || startupState.destination == StartupDestination.Lock)

                if (isAppLockActive) {
                    LockScreen(
                        pinSecurityManager = pinSecurityManager,
                        actualProfileName = profile.name,
                        onUnlocked = {
                            appLockSessionTracker.onUnlockSuccess()
                        },
                        onRequestBiometric = {
                            promptBiometric()
                        }
                    )
                } else when (startupState.destination) {
                    StartupDestination.Lock -> {
                        LockScreen(
                            pinSecurityManager = pinSecurityManager,
                            actualProfileName = profile.name,
                            onUnlocked = {
                                appLockSessionTracker.onUnlockSuccess()
                            },
                            onRequestBiometric = {
                                promptBiometric()
                            }
                        )
                    }

                    StartupDestination.Welcome -> {
                        when (profile.onboardingStage) {
                            OnboardingStage.WELCOME -> {
                                WelcomeScreen(
                                    onGetStarted = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.ENTER_NAME)
                                        }
                                    }
                                )
                            }

                            OnboardingStage.ENTER_NAME -> {
                                EnterNameScreen(
                                    initialName = profile.name,
                                    onNameSubmitted = { name ->
                                        scope.launch {
                                            userPreferencesRepository.setUserName(name)
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.CHOOSE_AVATAR)
                                        }
                                    },
                                    onBack = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.WELCOME)
                                        }
                                    }
                                )
                            }

                            OnboardingStage.CHOOSE_AVATAR -> {
                                ChooseAvatarScreen(
                                    initialAvatarId = profile.avatarId,
                                    onAvatarChosen = { avatarId ->
                                        scope.launch {
                                            userPreferencesRepository.setAvatarId(avatarId)
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.INSTRUCTIONS)
                                        }
                                    },
                                    onBack = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.ENTER_NAME)
                                        }
                                    }
                                )
                            }

                            OnboardingStage.INSTRUCTIONS -> {
                                InstructionsScreen(
                                    initialStep = profile.instructionStep,
                                    onStepChanged = { step ->
                                        scope.launch {
                                            userPreferencesRepository.setInstructionStep(step)
                                        }
                                    },
                                    onFinish = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.PERMISSIONS)
                                        }
                                    },
                                    onBack = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.CHOOSE_AVATAR)
                                        }
                                    }
                                )
                            }

                            OnboardingStage.PERMISSIONS -> {
                                PermissionFlowScreen(
                                    onContinue = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.COMPLETED)
                                        }
                                        com.suhrud.docsy.service.ReadingForegroundService.startScan(applicationContext)
                                        currentScreen = AppScreen.READING
                                    }
                                )
                            }

                            OnboardingStage.COMPLETED -> {
                                WelcomeScreen(
                                    onGetStarted = {
                                        scope.launch {
                                            userPreferencesRepository.setOnboardingStage(OnboardingStage.ENTER_NAME)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    StartupDestination.Home -> {
                        when (currentScreen) {
                                AppScreen.HOME -> {
                                    HomeScreen(
                                        userProfile = profile,
                                        readingProgress = if (readingState.isReading) readingState.progressFraction else null,
                                        onOpenProfile = { currentScreen = AppScreen.PROFILE },
                                        onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                                        onOpenReading = { currentScreen = AppScreen.READING },
                                        onOpenChat = { query ->
                                            initialChatQuery = query
                                            currentScreen = AppScreen.CHAT
                                        }
                                    )
                                }

                                AppScreen.PROFILE -> {
                                    ProfileScreen(
                                        userProfile = profile,
                                        onSaveProfile = { name, alias, avatarId ->
                                            scope.launch {
                                                userPreferencesRepository.setUserName(name)
                                                userPreferencesRepository.setDocumentAlias(alias)
                                                userPreferencesRepository.setAvatarId(avatarId)
                                            }
                                        },
                                        onBack = { currentScreen = AppScreen.HOME }
                                    )
                                }

                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        userProfile = profile,
                                        onToggleAutoRead = { enabled ->
                                            scope.launch {
                                                userPreferencesRepository.setAutoRead24h(enabled)
                                            }
                                        },
                                        onNavigateToAppLock = { currentScreen = AppScreen.APP_LOCK_SETTINGS },
                                        onNavigateToChatHistory = { currentScreen = AppScreen.CHAT_HISTORY },
                                        onNavigateToReadingFiles = { currentScreen = AppScreen.READING },
                                        onNavigateToInspection = { currentScreen = AppScreen.INSPECTOR },
                                        onNavigateToAccessInfo = { currentScreen = AppScreen.ACCESS_INFO },
                                        onNavigateToStepDiagnostics = { currentScreen = AppScreen.STEP_DIAGNOSTICS },
                                        onTriggerQuickRefresh = {
                                            com.suhrud.docsy.service.ReadingForegroundService.startScan(applicationContext, isFullReRead = false)
                                            currentScreen = AppScreen.READING
                                        },
                                        onTriggerFullReRead = {
                                            com.suhrud.docsy.service.ReadingForegroundService.startScan(applicationContext, isFullReRead = true)
                                            currentScreen = AppScreen.READING
                                        },
                                        onBack = { currentScreen = AppScreen.HOME }
                                    )
                                }

                                AppScreen.APP_LOCK_SETTINGS -> {
                                    AppLockSettingsScreen(
                                        pinSecurityManager = pinSecurityManager,
                                        onLockNow = {
                                            appLockSessionTracker.lockNow()
                                        },
                                        onBack = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }

                                AppScreen.ACCESS_INFO -> {
                                    AccessPermissionsScreen(
                                        onBack = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }

                                AppScreen.READING -> {
                                    BackHandler { currentScreen = AppScreen.HOME }
                                    ReadingProgressScreen(
                                        onContinueToHome = { currentScreen = AppScreen.HOME }
                                    )
                                }

                                AppScreen.INSPECTOR -> {
                                    BackHandler { currentScreen = AppScreen.SETTINGS }
                                    ExtractionInspectorScreen(
                                        onBack = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }

                                AppScreen.STEP_DIAGNOSTICS -> {
                                    BackHandler { currentScreen = AppScreen.SETTINGS }
                                    com.suhrud.docsy.ui.settings.StepDiagnosticsScreen(
                                        onNavigateBack = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }

                                AppScreen.CHAT -> {
                                    BackHandler {
                                        currentScreen = AppScreen.HOME
                                        activeChatSessionId = null
                                    }
                                    com.suhrud.docsy.ui.chat.ChatScreen(
                                        initialSessionId = activeChatSessionId,
                                        initialQuery = initialChatQuery,
                                        userName = profile.name,
                                        onSessionCreated = { sessionId ->
                                            activeChatSessionId = sessionId
                                        },
                                        onBack = {
                                            currentScreen = AppScreen.HOME
                                            activeChatSessionId = null
                                        }
                                    )
                                }

                                AppScreen.CHAT_HISTORY -> {
                                    BackHandler { currentScreen = AppScreen.SETTINGS }
                                    val db = DocsyDatabase.getInstance(applicationContext)
                                    com.suhrud.docsy.ui.chat.ChatHistoryScreen(
                                        chatDao = db.chatDao(),
                                        onSelectSession = { sessionId ->
                                            activeChatSessionId = sessionId
                                            currentScreen = AppScreen.CHAT
                                        },
                                        onBack = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun promptBiometric() {
        if (!BiometricHelper.isBiometricAvailable(this)) return
        BiometricHelper.promptBiometric(
            activity = this,
            title = "Unlock Docsy",
            subtitle = "Use fingerprint or screen lock",
            onSuccess = {
                appLockSessionTracker.onUnlockSuccess()
            },
            onError = { _ ->
                // Falls back gracefully to PIN keypad
            },
            onCancel = {
                // User canceled biometric prompt -> remains on PIN screen
            }
        )
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }
}
