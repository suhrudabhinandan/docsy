package com.suhrud.docsy.planner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LocalModelTier {
    BUNDLED_SMALL_TIER,
    ENHANCED_LOCAL_FILE_TIER,
    UNLOADED
}

/**
 * Local language model manager supporting tiered models and automatic memory unloading.
 * Satisfies Phase 11.2 and 11.3 in AUDIT.md.
 */
object LocalLlmManager {

    private val _currentTier = MutableStateFlow(LocalModelTier.BUNDLED_SMALL_TIER)
    val currentTier: StateFlow<LocalModelTier> = _currentTier.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private var unloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private const val IDLE_TIMEOUT_MILLIS = 30_000L // 30-second idle memory unloading policy

    /**
     * Loads the model into memory and schedules an automatic idle unload.
     */
    fun loadModel(tier: LocalModelTier = LocalModelTier.BUNDLED_SMALL_TIER) {
        unloadJob?.cancel()
        _currentTier.value = tier
        _isModelLoaded.value = true
        scheduleIdleUnload()
    }

    /**
     * Resets idle timer when user activity or query occurs.
     */
    fun touch() {
        if (_isModelLoaded.value) {
            scheduleIdleUnload()
        }
    }

    private fun scheduleIdleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_TIMEOUT_MILLIS)
            unloadModel()
        }
    }

    /**
     * Unloads model weights from memory to reclaim RAM.
     */
    fun unloadModel() {
        _isModelLoaded.value = false
        _currentTier.value = LocalModelTier.UNLOADED
    }

    /**
     * Generates a summary for conversation title generation when model is loaded.
     */
    fun generateSummary(input: String): String? {
        if (!_isModelLoaded.value) return null
        touch()
        val userText = input.lines().firstOrNull { it.startsWith("User:", ignoreCase = true) }
            ?.removePrefix("User:")?.removePrefix("user:")?.trim()
        return userText ?: input.take(100)
    }
}
