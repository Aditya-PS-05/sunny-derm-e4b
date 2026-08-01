package com.sunny.skin

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.ui.nav.SunnyNavHost
import com.sunny.skin.ui.screens.LockScreen
import com.sunny.skin.ui.screens.OnboardingScreen
import com.sunny.skin.ui.screens.PinScreen
import com.sunny.skin.ui.i18n.SunnyLanguageController
import com.sunny.skin.ui.i18n.SunnyLocalizationProvider
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.SunnyTheme
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled

/**
 * Single-activity host. If an app-lock PIN has been set, the app content is
 * gated behind [PinScreen] until the user enters it — health data is protected
 * locally. Server beta builds separately disclose remote scan processing.
 */
class MainActivity : ComponentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots and screen recording are intentionally allowed so users can
        // capture their own Sunny screens when they choose.
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        if (settings.hasPin()) CryptoManager.lock()
        launchRequest = intent.toLaunchRequest()

        setContent {
            val language by SunnyLanguageController.selection.collectAsStateWithLifecycle()
            SunnyLocalizationProvider(language) {
              SunnyTheme {
                var onboarded by remember { mutableStateOf(settings.seenOnboarding) }
                var unlocked by remember { mutableStateOf(!settings.hasPin()) }
                var showKeypad by remember { mutableStateOf(false) }
                val motionEnabled = rememberSunnyMotionEnabled()
                // The controller outlives the lock gate so a successful unlock
                // returns to the screen the user was viewing, not Overview.
                val navController = rememberNavController()
                val lockTransitionDistance = with(LocalDensity.current) { 8.dp.roundToPx() }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && settings.hasPin()) {
                            CryptoManager.lock()
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
                        nav = navController,
                        launchRequest = launchRequest,
                        onLaunchRequestHandled = { launchRequest = null },
                    )
                    else -> AnimatedContent(
                        targetState = showKeypad,
                        transitionSpec = {
                            lockGateTransform(
                                motionEnabled = motionEnabled,
                                openingKeypad = targetState,
                                maxOffsetPx = lockTransitionDistance,
                            )
                        },
                        label = "Lock to PIN",
                    ) { keypadVisible ->
                        if (keypadVisible) {
                            PinScreen(
                                verify = { settings.verifyPin(it) },
                                lockoutRemainingMs = { settings.lockoutRemainingMs() },
                                onSuccess = { unlocked = true },
                                onCancel = { showKeypad = false },
                            )
                        } else {
                            LockScreen(onUnlock = { showKeypad = true })
                        }
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    override fun onResume() {
        super.onResume()
        SunnyApp.instance.billing.refresh()
    }

    companion object {
        const val EXTRA_SCAN_ID = "com.sunny.skin.extra.SCAN_ID"
        const val EXTRA_OPEN_REMINDERS = "com.sunny.skin.extra.OPEN_REMINDERS"
    }
}

private fun lockGateTransform(
    motionEnabled: Boolean,
    openingKeypad: Boolean,
    maxOffsetPx: Int,
): ContentTransform {
    if (!motionEnabled) {
        return fadeIn(
            tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut),
        ) togetherWith fadeOut(
            tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
        )
    }
    val enter = fadeIn(
        tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut),
    ) + slideInVertically(
        animationSpec = tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.EaseOut),
        initialOffsetY = { height ->
            if (openingKeypad) minOf(height, maxOffsetPx) else -minOf(height, maxOffsetPx)
        },
    )
    val exit = fadeOut(
        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
    ) + slideOutVertically(
        animationSpec = tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
        targetOffsetY = { height ->
            if (openingKeypad) -minOf(height, maxOffsetPx) else minOf(height, maxOffsetPx)
        },
    )
    return enter togetherWith exit
}

data class LaunchRequest(val scanId: String? = null, val openReminders: Boolean = false)

private fun Intent.toLaunchRequest(): LaunchRequest? {
    val scanId = getStringExtra(MainActivity.EXTRA_SCAN_ID)?.takeIf { it.isNotBlank() }
    val openReminders = getBooleanExtra(MainActivity.EXTRA_OPEN_REMINDERS, false)
    return if (scanId != null || openReminders) LaunchRequest(scanId, openReminders) else null
}
