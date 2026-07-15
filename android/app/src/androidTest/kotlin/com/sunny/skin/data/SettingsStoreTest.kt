package com.sunny.skin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-lock PIN security: stored only as a salted PBKDF2 hash (never plaintext),
 * verified correctly, and rate-limited with a lockout after repeated failures.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val store = SettingsStore(ctx)

    // Never leave the app locked after a test.
    @After fun tearDown() {
        store.clearPin()
        store.improveSunny = false
        store.seenOnboarding = false
    }

    @Test
    fun pin_isStoredAsHash_notPlaintext() {
        store.setPin("2468")
        assertTrue(store.hasPin())
        val dump = ctx.getSharedPreferences("sunny_settings", Context.MODE_PRIVATE).all.toString()
        assertFalse("plaintext PIN must never be persisted: $dump", dump.contains("2468"))
    }

    @Test
    fun verifyPin_acceptsCorrect_rejectsWrong() {
        store.setPin("1357")
        assertTrue(store.verifyPin("1357"))
        assertFalse(store.verifyPin("0000"))
    }

    @Test
    fun repeatedFailures_triggerLockout_thatBlocksEvenCorrectPin() {
        store.setPin("1111")
        repeat(5) { store.verifyPin("9999") } // 5 wrong attempts → escalating lockout
        assertTrue("a lockout should be active", store.lockoutRemainingMs() > 0)
        assertFalse("correct PIN is refused during lockout", store.verifyPin("1111"))
    }

    @Test
    fun contributionOptIn_recordsAndClearsExplicitConsentTime() {
        store.improveSunny = true

        assertTrue(store.improveSunny)
        assertTrue(store.improveSunnyConsentAt > 0L)

        store.improveSunny = false
        assertFalse(store.improveSunny)
        assertEquals(0L, store.improveSunnyConsentAt)
    }

    @Test
    fun inferenceSource_roundTrips() {
        store.useServerInference = false
        assertFalse(store.useServerInference)

        store.useServerInference = true
        assertTrue(store.useServerInference)
    }

    @Test
    fun onboardingAcknowledgement_recordsConsentTime() {
        store.seenOnboarding = true

        assertTrue(store.seenOnboarding)
        assertTrue(store.onboardingConsentAt > 0L)

        store.seenOnboarding = false
        assertFalse(store.seenOnboarding)
        assertEquals(0L, store.onboardingConsentAt)
    }
}
