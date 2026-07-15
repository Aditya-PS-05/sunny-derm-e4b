package com.sunny.skin.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sunny.skin.AppMode
import com.sunny.skin.ui.screens.OnboardingScreen
import com.sunny.skin.ui.theme.SunnyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingConsentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun getStarted_requiresExplicitAcknowledgement() {
        var finished = false
        compose.setContent {
            SunnyTheme {
                OnboardingScreen { finished = true }
            }
        }

        compose.onNodeWithText("Get started").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter Sunny").assertIsNotEnabled()
        compose.onNodeWithText(AppMode.onboardingAcknowledgement).performClick()
        compose.onNodeWithText("Enter Sunny").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(finished) }
    }
}
