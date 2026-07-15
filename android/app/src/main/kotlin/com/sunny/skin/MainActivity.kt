package com.sunny.skin

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.ui.nav.SunnyNavHost
import com.sunny.skin.ui.screens.LockScreen
import com.sunny.skin.ui.screens.OnboardingScreen
import com.sunny.skin.ui.screens.PinScreen
import com.sunny.skin.ui.theme.SunnyTheme

/**
 * Single-activity host. If an app-lock PIN has been set, the app content is
 * gated behind [PinScreen] until the user enters it — health data is protected
 * locally. Server beta builds separately disclose remote scan processing.
 */
class MainActivity : FragmentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep skin photos, descriptions and reports out of the recents/task-switcher
        // thumbnail and block screenshots/screen-recording of sensitive health data.
        // Relaxed on debug builds so testers can capture screenshots; release keeps it on.
        if (!com.sunny.skin.BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        launchRequest = intent.toLaunchRequest()

        setContent {
            SunnyTheme {
                var onboarded by remember { mutableStateOf(settings.seenOnboarding) }
                var unlocked by remember { mutableStateOf(!settings.hasPin()) }
                var showKeypad by remember { mutableStateOf(false) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && settings.hasPin()) {
                            unlocked = false
                            showKeypad = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                when {
                    !onboarded -> OnboardingScreen(onFinish = {
                        settings.seenOnboarding = true; onboarded = true
                    })
                    unlocked -> SunnyNavHost(
                        launchRequest = launchRequest,
                        onLaunchRequestHandled = { launchRequest = null },
                    )
                    // "Sunny is Locked" landing → tap to reveal the PIN keypad.
                    !showKeypad -> LockScreen(onUnlock = { showKeypad = true })
                    else -> PinScreen(
                        verify = { settings.verifyPin(it) },
                        lockoutRemainingMs = { settings.lockoutRemainingMs() },
                        onSuccess = { unlocked = true },
                        onCancel = { showKeypad = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    companion object {
        const val EXTRA_SCAN_ID = "com.sunny.skin.extra.SCAN_ID"
        const val EXTRA_OPEN_REMINDERS = "com.sunny.skin.extra.OPEN_REMINDERS"
    }
}

data class LaunchRequest(val scanId: String? = null, val openReminders: Boolean = false)

private fun Intent.toLaunchRequest(): LaunchRequest? {
    val scanId = getStringExtra(MainActivity.EXTRA_SCAN_ID)?.takeIf { it.isNotBlank() }
    val openReminders = getBooleanExtra(MainActivity.EXTRA_OPEN_REMINDERS, false)
    return if (scanId != null || openReminders) LaunchRequest(scanId, openReminders) else null
}
