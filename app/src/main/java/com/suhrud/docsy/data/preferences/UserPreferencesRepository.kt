package com.suhrud.docsy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docsy_user_preferences")

enum class OnboardingStage {
    WELCOME,
    ENTER_NAME,
    CHOOSE_AVATAR,
    INSTRUCTIONS,
    PERMISSIONS,
    COMPLETED
}

data class UserProfile(
    val name: String,
    val alias: String?,
    val avatarId: String,
    val isNameUnmatchable: Boolean,
    val onboardingStage: OnboardingStage,
    val instructionStep: Int,
    val onboardingCompleted: Boolean,
    val autoRead24h: Boolean,
    val chatVoiceTier: String
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_DOCUMENT_ALIAS = stringPreferencesKey("document_alias")
        val KEY_AVATAR_ID = stringPreferencesKey("avatar_id")
        val KEY_LEGACY_AVATAR_INDEX = intPreferencesKey("legacy_avatar_index")
        val KEY_ONBOARDING_STAGE = stringPreferencesKey("onboarding_stage")
        val KEY_INSTRUCTION_STEP = intPreferencesKey("instruction_step")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_AUTO_READ_24H = booleanPreferencesKey("auto_read_24h")
        val KEY_CHAT_VOICE_TIER = stringPreferencesKey("chat_voice_tier")

        const val DEFAULT_AVATAR_ID = "avatar_01"

        fun isUnmatchableName(name: String): Boolean {
            val trimmed = name.trim()
            if (trimmed.length < 2) return true
            // If name contains any digits (e.g. "X01"), flag UNMATCHABLE per Phase 2.2
            return trimmed.any { it.isDigit() }
        }
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
        val name = preferences[KEY_USER_NAME] ?: ""
        val alias = preferences[KEY_DOCUMENT_ALIAS]
        
        // Handle avatar id with fallback and integer migration
        var avatarId = preferences[KEY_AVATAR_ID]
        if (avatarId.isNullOrEmpty()) {
            val legacyIndex = preferences[KEY_LEGACY_AVATAR_INDEX]
            avatarId = if (legacyIndex != null && legacyIndex in 1..17) {
                String.format("avatar_%02d", legacyIndex)
            } else {
                DEFAULT_AVATAR_ID
            }
        }

        val stageStr = preferences[KEY_ONBOARDING_STAGE] ?: OnboardingStage.WELCOME.name
        val stage = try {
            OnboardingStage.valueOf(stageStr)
        } catch (_: Exception) {
            OnboardingStage.WELCOME
        }

        val step = preferences[KEY_INSTRUCTION_STEP] ?: 1
        val completed = preferences[KEY_ONBOARDING_COMPLETED] ?: false
        val autoRead = preferences[KEY_AUTO_READ_24H] ?: true
        val voiceTier = preferences[KEY_CHAT_VOICE_TIER] ?: "Basic"

        UserProfile(
            name = name,
            alias = alias,
            avatarId = avatarId,
            isNameUnmatchable = isUnmatchableName(name),
            onboardingStage = stage,
            instructionStep = step,
            onboardingCompleted = completed,
            autoRead24h = autoRead,
            chatVoiceTier = voiceTier
        )
    }

    suspend fun setUserName(name: String) {
        val trimmed = name.trim()
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_NAME] = trimmed
        }
    }

    suspend fun setDocumentAlias(alias: String?) {
        val trimmed = alias?.trim()
        context.dataStore.edit { preferences ->
            if (trimmed.isNullOrEmpty()) {
                preferences.remove(KEY_DOCUMENT_ALIAS)
            } else {
                preferences[KEY_DOCUMENT_ALIAS] = trimmed
            }
        }
    }

    suspend fun setAvatarId(avatarId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AVATAR_ID] = avatarId
            // Clear any legacy index upon explicit set
            preferences.remove(KEY_LEGACY_AVATAR_INDEX)
        }
    }

    suspend fun setOnboardingStage(stage: OnboardingStage) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_STAGE] = stage.name
            if (stage == OnboardingStage.COMPLETED) {
                preferences[KEY_ONBOARDING_COMPLETED] = true
            }
        }
    }

    suspend fun setInstructionStep(step: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_INSTRUCTION_STEP] = step.coerceIn(1, 5)
        }
    }

    suspend fun setAutoRead24h(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_READ_24H] = enabled
        }
    }

    suspend fun setChatVoiceTier(tier: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CHAT_VOICE_TIER] = tier
        }
    }
}
