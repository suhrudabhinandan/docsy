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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suhrud.docsy.data.preferences.OnboardingStage
import com.suhrud.docsy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupFlowUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testStartupWithClearedDataStoreShowsWelcomeAndNoSplash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        // Clear data store / reset to welcome
        runBlocking {
            repo.setOnboardingStage(OnboardingStage.WELCOME)
            repo.setUserName("")
        }

        // Wait up to 3 seconds for "Get Started" node
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            composeTestRule.onAllNodes(hasTestTag("welcome_get_started_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Assert "Get Started" exists and is clickable
        composeTestRule.onNodeWithText("Get Started")
            .assertIsDisplayed()
            .assertHasClickAction()

        // Assert NO node with "splash" test tag exists
        composeTestRule.onAllNodes(hasTestTag("splash_screen")).fetchSemanticsNodes().isEmpty()
        composeTestRule.onAllNodes(hasTestTag("app_opening_splash")).fetchSemanticsNodes().isEmpty()

        // Click "Get Started" and assert the next onboarding screen appears
        composeTestRule.onNodeWithText("Get Started").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            composeTestRule.onAllNodes(hasTestTag("enter_name_input"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("enter_name_input").assertIsDisplayed()
    }

    @Test
    fun testStartupWithNameAlreadySavedShowsHomeWithinThreeSeconds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        // Setup saved name and completed onboarding
        runBlocking {
            repo.setUserName("Suhrud")
            repo.setOnboardingStage(OnboardingStage.COMPLETED)
        }

        // Assert Home greeting / search bar appears within 3 seconds
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            composeTestRule.onAllNodes(hasTestTag("home_search_bar"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("home_search_bar").assertIsDisplayed()
    }
}
