package com.suhrud.docsy

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupFlowRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testStartupWithClearedDataStoreShowsWelcomeAndNoSplash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        runBlocking {
            repo.setOnboardingStage(OnboardingStage.WELCOME)
            repo.setUserName("")
        }

        composeTestRule.waitForIdle()

        // Assert "Get Started" exists and is clickable
        composeTestRule.onNodeWithText("Get Started")
            .assertIsDisplayed()
            .assertHasClickAction()

        // Assert NO node with "splash" test tag exists
        val splashNodes = composeTestRule.onAllNodes(hasTestTag("splash_screen")).fetchSemanticsNodes()
        assertTrue("No splash screen test tag should exist", splashNodes.isEmpty())

        // Click "Get Started" and assert the next onboarding screen appears
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("enter_name_input").assertIsDisplayed()
    }

    @Test
    fun testStartupViewModelCreationViaActivityViewModelProviderDoesNotThrow() {
        val activity = composeTestRule.activity
        // Verify creation via StartupViewModel.Factory used in MainActivity
        val vmWithFactory = androidx.lifecycle.ViewModelProvider(
            activity,
            com.suhrud.docsy.ui.startup.StartupViewModel.Factory
        )[com.suhrud.docsy.ui.startup.StartupViewModel::class.java]
        org.junit.Assert.assertNotNull(vmWithFactory)

        // Verify creation via default AndroidViewModelFactory to ensure (Application) constructor is valid
        val defaultFactory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(activity.application)
        val vmWithDefaultFactory = androidx.lifecycle.ViewModelProvider(
            activity.viewModelStore,
            defaultFactory
        )[com.suhrud.docsy.ui.startup.StartupViewModel::class.java]
        org.junit.Assert.assertNotNull(vmWithDefaultFactory)
    }
}

