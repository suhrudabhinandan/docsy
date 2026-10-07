package com.suhrud.docsy.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.suhrud.docsy.data.local.DocsyDatabase
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.data.model.ChatMessageEntity
import com.suhrud.docsy.data.model.ChatSessionEntity
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.data.repository.DocumentRepository
import com.suhrud.docsy.domain.indexing.FileIndexingWorker
import com.suhrud.docsy.domain.query.ParsedQuery
import com.suhrud.docsy.domain.query.QueryParser
import com.suhrud.docsy.domain.retrieval.StrictRelevanceEngine
import com.suhrud.docsy.domain.security.AppLockManager
import com.suhrud.docsy.domain.stats.DeviceStatsEngine
import com.suhrud.docsy.ui.components.AvatarCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID

class DocsyViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("docsy_prefs", Context.MODE_PRIVATE)

    private val database = DocsyDatabase.getInstance(application)
    val repository = DocumentRepository(database.documentDao())
    val deviceStatsEngine = DeviceStatsEngine(application, database.documentDao())
    private val relevanceEngine = StrictRelevanceEngine(application, database.documentDao(), deviceStatsEngine)
    val appLockManager = AppLockManager(application)

    private val _userName = MutableStateFlow(prefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val initialAvatarId = prefs.getString("profile_avatar_id", null) ?: AvatarCatalog.migrateIndexToId(prefs.getInt("profile_icon", 0))

    private val _profileAvatarId = MutableStateFlow(initialAvatarId)
    val profileAvatarId: StateFlow<String> = _profileAvatarId.asStateFlow()

    private val _profileIconIndex = MutableStateFlow(prefs.getInt("profile_icon", 0))
    val profileIconIndex: StateFlow<Int> = _profileIconIndex.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _onboardingStep = MutableStateFlow(prefs.getInt("saved_onboarding_step", 1))
    val onboardingStep: StateFlow<Int> = _onboardingStep.asStateFlow()

    private val _introStepIndex = MutableStateFlow(0)
    val introStepIndex: StateFlow<Int> = _introStepIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeQuery = MutableStateFlow<String?>(null)
    val activeQuery: StateFlow<String?> = _activeQuery.asStateFlow()

    private val _activeAnswer = MutableStateFlow<ChatMessage?>(null)
    val activeAnswer: StateFlow<ChatMessage?> = _activeAnswer.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var lastParsedQuery: ParsedQuery? = null

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isAppLocked = MutableStateFlow(appLockManager.isAppLockEnabled())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    val allDocuments = repository.allDocuments
    val isIndexing = repository.isIndexing
    val indexingProgress = repository.indexingProgress

    private val _chatSessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val chatSessions: StateFlow<List<ChatSessionEntity>> = _chatSessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow(
        prefs.getString("current_session_id", null) ?: UUID.randomUUID().toString()
    )
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    init {
        repository.registerMediaStoreObserver(application)

        // Observe chat sessions
        viewModelScope.launch {
            try {
                val db = DocsyDatabase.getInstance(application)
                db.chatDao().getAllSessions().collect { sessions ->
                    _chatSessions.value = sessions
                }
            } catch (_: Exception) {}
        }

        // Observe messages for active session
        viewModelScope.launch {
            _currentSessionId.collect { activeId ->
                try {
                    val db = DocsyDatabase.getInstance(application)
                    db.chatDao().getMessagesForSession(activeId).collect { entities ->
                        val messages = entities.map { entity ->
                            ChatMessage(
                                id = entity.id,
                                isUser = entity.isUser,
                                text = entity.text,
                                timestamp = entity.timestamp,
                                answerHighlight = entity.answerHighlight,
                                supportingMetadata = entity.supportingMetadata,
                                subtext = entity.subtext,
                                sourceDocument = if (entity.sourceDocumentPath != null) {
                                    DocumentEntity(
                                        pathUri = entity.sourceDocumentPath,
                                        fileName = entity.sourceDocumentName ?: "Source File",
                                        mimeType = entity.sourceDocumentMime ?: "application/octet-stream"
                                    )
                                } else null,
                                isSensitive = entity.isSensitive,
                                unmaskedValue = entity.unmaskedValue,
                                isRevealed = entity.isRevealed
                            )
                        }
                        _chatHistory.value = messages
                    }
                } catch (_: Exception) {}
            }
        }

        try {
            val workRequest = PeriodicWorkRequestBuilder<FileIndexingWorker>(
                1, java.util.concurrent.TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                "docsy_indexing_work",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (_: Exception) {}

        viewModelScope.launch {
            repository.syncDeviceFiles(application)
        }
    }

    fun getTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun setUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            _userName.value = trimmed
            prefs.edit().putString("user_name", trimmed).apply()
        }
    }

    fun setProfileAvatarId(id: String) {
        val validItem = AvatarCatalog.getById(id)
        _profileAvatarId.value = validItem.id
        prefs.edit().putString("profile_avatar_id", validItem.id).apply()
    }

    fun setProfileIcon(index: Int) {
        val migratedId = AvatarCatalog.migrateIndexToId(index)
        setProfileAvatarId(migratedId)
        _profileIconIndex.value = index
        prefs.edit().putInt("profile_icon", index).apply()
    }

    fun advanceOnboardingStep() {
        if (_onboardingStep.value == 4) {
            if (_introStepIndex.value < 4) {
                _introStepIndex.value += 1
                return
            }
        }

        if (_onboardingStep.value < 6) {
            val next = _onboardingStep.value + 1
            _onboardingStep.value = next
            prefs.edit().putInt("saved_onboarding_step", next).apply()
        } else {
            completeOnboarding()
        }
    }

    fun previousOnboardingStep() {
        if (_onboardingStep.value == 4) {
            if (_introStepIndex.value > 0) {
                _introStepIndex.value -= 1
                return
            }
        }

        if (_onboardingStep.value > 1) {
            val prev = _onboardingStep.value - 1
            _onboardingStep.value = prev
            prefs.edit().putInt("saved_onboarding_step", prev).apply()
            if (prev == 4) {
                _introStepIndex.value = 4
            }
        }
    }

    fun skipOnboardingIntro() {
        _introStepIndex.value = 0
        _onboardingStep.value = 5
        prefs.edit().putInt("saved_onboarding_step", 5).apply()
    }

    fun completeOnboarding() {
        _isOnboardingCompleted.value = true
        prefs.edit()
            .putBoolean("onboarding_completed", true)
            .putInt("saved_onboarding_step", 6)
            .apply()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openHistory() {
        _isHistoryOpen.value = true
    }

    fun closeHistory() {
        _isHistoryOpen.value = false
    }

    fun startNewChat() {
        val newSessionId = UUID.randomUUID().toString()
        _currentSessionId.value = newSessionId
        prefs.edit().putString("current_session_id", newSessionId).apply()
        _chatHistory.value = emptyList()
        clearActiveAnswer()
    }

    fun selectChatSession(sessionId: String) {
        _currentSessionId.value = sessionId
        prefs.edit().putString("current_session_id", sessionId).apply()
        _isHistoryOpen.value = false
        clearActiveAnswer()
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val db = DocsyDatabase.getInstance(getApplication())
                db.chatDao().deleteSession(sessionId)
                db.chatDao().deleteMessagesForSession(sessionId)
            } catch (_: Exception) {}

            if (sessionId == _currentSessionId.value) {
                startNewChat()
            }
        }
    }

    private val queryMutex = Mutex()

    fun submitQuery(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        _searchQuery.value = ""
        _activeQuery.value = trimmed

        val activeSessionId = _currentSessionId.value

        val userMessage = ChatMessage(isUser = true, text = trimmed)
        val currentList = _chatHistory.value.toMutableList()
        currentList.add(userMessage)
        _chatHistory.value = currentList

        viewModelScope.launch {
            queryMutex.withLock {
                _isSearching.value = true
                try {
                    val db = DocsyDatabase.getInstance(getApplication())
                    val chatDao = db.chatDao()

                    val sessionTitle = trimmed.take(32)
                    chatDao.insertSession(
                        ChatSessionEntity(
                            sessionId = activeSessionId,
                            title = sessionTitle,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    )

                    chatDao.insertMessage(
                        ChatMessageEntity(
                            id = userMessage.id,
                            sessionId = activeSessionId,
                            isUser = true,
                            text = userMessage.text,
                            timestamp = userMessage.timestamp
                        )
                    )

                    val parsed = QueryParser.parse(trimmed, context = lastParsedQuery)
                    lastParsedQuery = parsed

                    val answer = relevanceEngine.answerQuery(parsed, _userName.value)
                    _activeAnswer.value = answer

                    val docsyMessage = answer
                    val updatedList = _chatHistory.value.toMutableList()
                    updatedList.add(docsyMessage)
                    _chatHistory.value = updatedList

                    chatDao.insertMessage(
                        ChatMessageEntity(
                            id = docsyMessage.id,
                            sessionId = activeSessionId,
                            isUser = false,
                            text = docsyMessage.text,
                            timestamp = docsyMessage.timestamp,
                            answerHighlight = docsyMessage.answerHighlight,
                            supportingMetadata = docsyMessage.supportingMetadata,
                            subtext = docsyMessage.subtext,
                            sourceDocumentPath = docsyMessage.sourceDocument?.pathUri,
                            sourceDocumentName = docsyMessage.sourceDocument?.fileName,
                            sourceDocumentMime = docsyMessage.sourceDocument?.mimeType,
                            isSensitive = docsyMessage.isSensitive,
                            unmaskedValue = docsyMessage.unmaskedValue,
                            isRevealed = docsyMessage.isRevealed
                        )
                    )

                    chatDao.insertSession(
                        ChatSessionEntity(
                            sessionId = activeSessionId,
                            title = sessionTitle,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    val errorMsg = ChatMessage(
                        isUser = false,
                        text = "I had trouble processing that query, but I'm ready for your next question!"
                    )
                    _activeAnswer.value = errorMsg
                    val updatedList = _chatHistory.value.toMutableList()
                    updatedList.add(errorMsg)
                    _chatHistory.value = updatedList
                } finally {
                    _isSearching.value = false
                }
            }
        }
    }

    fun clearActiveAnswer() {
        _activeAnswer.value = null
        _activeQuery.value = null
        _searchQuery.value = ""
        lastParsedQuery = null
    }

    fun toggleRevealSensitive() {
        val current = _activeAnswer.value ?: return
        if (current.isSensitive && current.unmaskedValue != null) {
            val newRevealed = !current.isRevealed
            val displayHighlight = if (newRevealed) current.unmaskedValue else "XXXX-XXXX-${current.unmaskedValue.takeLast(4)}"
            _activeAnswer.value = current.copy(
                isRevealed = newRevealed,
                answerHighlight = displayHighlight
            )
        }
    }

    fun importFile(context: Context, uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            repository.importUserDocument(context, uri, fileName, mimeType)
        }
    }

    fun triggerDeviceSync() {
        viewModelScope.launch {
            repository.syncDeviceFiles(getApplication())
        }
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun onAppResume() {
        if (appLockManager.shouldLockOnForeground()) {
            _isAppLocked.value = true
        }
    }

    fun onAppPause() {
        appLockManager.recordAppBackgrounded()
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun authenticateWithBiometrics(activity: FragmentActivity, onError: (String) -> Unit) {
        appLockManager.showBiometricPrompt(
            activity = activity,
            onAuthenticated = {
                _isAppLocked.value = false
            },
            onError = onError
        )
    }

    fun verifyPinAndUnlock(pin: String): Boolean {
        val ok = appLockManager.verifyPin(pin)
        if (ok) {
            _isAppLocked.value = false
        }
        return ok
    }
}
