package com.suhrud.docsy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import com.suhrud.docsy.ui.avatar.AvatarCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Phase2OnboardingTest {

    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserPreferencesRepository(context)
    }

    @Test
    fun testCatalogSizeEqualsResourceCount() {
        // Must derive dynamically and match the 17 artwork pages
        assertEquals(17, AvatarCatalog.avatars.size)
        AvatarCatalog.avatars.forEach { avatar ->
            assertNotNull(avatar.id)
            assertTrue(avatar.drawableRes != 0)
            assertTrue(avatar.contentDescription.isNotBlank())
        }

        // Verify resource files exist on disk
        val resDir = File("src/main/res/drawable-nodpi")
        if (resDir.exists()) {
            val webpFiles = resDir.listFiles { _, name -> name.startsWith("avatar_") && name.endsWith(".webp") }
            assertNotNull(webpFiles)
            assertEquals(17, webpFiles!!.size)
        }
    }

    @Test
    fun testAvatarUnknownIdFallback() {
        assertEquals("avatar_01", AvatarCatalog.getAvatar("unknown_invalid_id").id)
        assertEquals("avatar_01", AvatarCatalog.getAvatar(null).id)
        assertEquals("avatar_01", AvatarCatalog.getAvatar("").id)
        assertEquals("avatar_05", AvatarCatalog.getAvatar("avatar_05").id)
    }

    @Test
    fun testUnmatchableNameLogic() {
        // Names shorter than 2 letters or containing digits must be flagged UNMATCHABLE per Phase 2.2
        assertTrue(UserPreferencesRepository.isUnmatchableName("X01"))
        assertTrue(UserPreferencesRepository.isUnmatchableName("A"))
        assertTrue(UserPreferencesRepository.isUnmatchableName("   "))
        assertTrue(UserPreferencesRepository.isUnmatchableName("User2026"))

        assertFalse(UserPreferencesRepository.isUnmatchableName("Suhrud"))
        assertFalse(UserPreferencesRepository.isUnmatchableName("Abhinandan"))
        assertFalse(UserPreferencesRepository.isUnmatchableName("Alex"))
    }

    @Test
    fun testOnboardingStageProgressionAndPersistence() = runBlocking {
        // Initial state should be WELCOME
        val initialProfile = repository.userProfileFlow.first()
        assertEquals(OnboardingStage.WELCOME, initialProfile.onboardingStage)

        // Set name
        repository.setUserName("Suhrud")
        repository.setOnboardingStage(OnboardingStage.CHOOSE_AVATAR)

        var profile = repository.userProfileFlow.first()
        assertEquals("Suhrud", profile.name)
        assertEquals(OnboardingStage.CHOOSE_AVATAR, profile.onboardingStage)
        assertFalse(profile.isNameUnmatchable)

        // Set avatar
        repository.setAvatarId("avatar_07")
        repository.setOnboardingStage(OnboardingStage.INSTRUCTIONS)
        repository.setInstructionStep(3)

        profile = repository.userProfileFlow.first()
        assertEquals("avatar_07", profile.avatarId)
        assertEquals(OnboardingStage.INSTRUCTIONS, profile.onboardingStage)
        assertEquals(3, profile.instructionStep)

        // Complete onboarding
        repository.setOnboardingStage(OnboardingStage.COMPLETED)
        profile = repository.userProfileFlow.first()
        assertEquals(OnboardingStage.COMPLETED, profile.onboardingStage)
        assertTrue(profile.onboardingCompleted)
    }
}
