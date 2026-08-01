package com.sunny.skin.ui.i18n

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SunnyLocalizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharedTextRendererUpdatesForHindiAndSpanish() {
        compose.setContent {
            SunnyLocalizationProvider(SunnyLanguage.HINDI) {
                Text("Settings")
            }
        }
        compose.onNodeWithText("सेटिंग्स").assertIsDisplayed()

        compose.setContent {
            SunnyLocalizationProvider(SunnyLanguage.SPANISH) {
                Text("Settings")
            }
        }
        compose.onNodeWithText("Ajustes").assertIsDisplayed()

        compose.setContent {
            SunnyLocalizationProvider(SunnyLanguage.JAPANESE) {
                Text("Settings")
            }
        }
        compose.onNodeWithText("設定").assertIsDisplayed()

        compose.setContent {
            SunnyLocalizationProvider(SunnyLanguage.CHINESE_SIMPLIFIED) {
                Text("Settings")
            }
        }
        compose.onNodeWithText("设置").assertIsDisplayed()
    }
}
