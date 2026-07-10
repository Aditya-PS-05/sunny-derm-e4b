package com.sunny.skin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.ui.nav.SunnyNavHost
import com.sunny.skin.ui.screens.LockScreen
import com.sunny.skin.ui.screens.OnboardingScreen
import com.sunny.skin.ui.screens.PinScreen
import com.sunny.skin.ui.theme.SunnyTheme

/**
 * Single-activity host. If an app-lock PIN has been set, the app content is
 * gated behind [PinScreen] until the user enters it — health data is protected
 * locally (it never leaves the device regardless).
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)

        setContent {
            SunnyTheme {
                var onboarded by remember { mutableStateOf(settings.seenOnboarding) }
                var unlocked by remember { mutableStateOf(!settings.pinLockEnabled) }
                var showKeypad by remember { mutableStateOf(false) }
                when {
                    !onboarded -> OnboardingScreen(onFinish = {
                        settings.seenOnboarding = true; onboarded = true
                    })
                    unlocked -> SunnyNavHost()
                    // "Sunny is Locked" landing → tap to reveal the PIN keypad.
                    !showKeypad -> LockScreen(onUnlock = { showKeypad = true })
                    else -> PinScreen(
                        existingPin = settings.pin,
                        onSuccess = { unlocked = true },
                        onCancel = { showKeypad = false },
                    )
                }
            }
        }
    }
}
