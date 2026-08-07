package com.sunny.skin.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

enum class SunnyLanguage(
    val persistedTag: String,
    val nativeName: String,
    internal val mlKitTag: String?,
) {
    SYSTEM("system", "Use device language", null),
    ENGLISH("en", "English", TranslateLanguage.ENGLISH),
    HINDI("hi", "हिन्दी", TranslateLanguage.HINDI),
    SPANISH("es", "Español", TranslateLanguage.SPANISH),
    ITALIAN("it", "Italiano", TranslateLanguage.ITALIAN),
    FRENCH("fr", "Français", TranslateLanguage.FRENCH),
    GERMAN("de", "Deutsch", TranslateLanguage.GERMAN),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)", TranslateLanguage.PORTUGUESE),
    JAPANESE("ja", "日本語", TranslateLanguage.JAPANESE),
    KOREAN("ko", "한국어", TranslateLanguage.KOREAN),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文", TranslateLanguage.CHINESE),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文", TranslateLanguage.CHINESE);

    companion object {
        fun fromTag(tag: String?): SunnyLanguage = entries.firstOrNull {
            it.persistedTag == tag
        } ?: SYSTEM
    }
}

data class LanguageModelState(
    val preparing: SunnyLanguage? = null,
    val error: String? = null,
)

/**
 * Owns the user's language choice and only publishes a new choice after its
 * on-device ML Kit model is available. Until then the previous language stays
 * active, preventing a partially translated screen.
 */
object SunnyLanguageController {
    private const val PREFS = "sunny_settings"
    private const val KEY = "app_language"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContext: Context
    private val mutableSelection = MutableStateFlow(SunnyLanguage.ENGLISH)
    private val mutableModelState = MutableStateFlow(LanguageModelState())

    val selection: StateFlow<SunnyLanguage> = mutableSelection.asStateFlow()
    val modelState: StateFlow<LanguageModelState> = mutableModelState.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val persisted = SunnyLanguage.fromTag(
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
        )
        activate(persisted, persist = false)
    }

    fun select(context: Context, language: SunnyLanguage) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        if (language == mutableSelection.value && mutableModelState.value.preparing == null) return
        activate(language, persist = true)
    }

    internal fun resolve(context: Context, selected: SunnyLanguage): SunnyLanguage {
        if (selected != SunnyLanguage.SYSTEM) return selected
        val locale = context.resources.configuration.locales[0]
        return languageForLocale(locale)
    }

    private fun activate(selected: SunnyLanguage, persist: Boolean) {
        val resolved = resolve(appContext, selected)
        mutableModelState.value = LanguageModelState(preparing = selected)
        scope.launch {
            runCatching { SunnyTranslationEngine.prepare(resolved) }
                .onSuccess {
                    if (persist) {
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY, selected.persistedTag)
                            .apply()
                    }
                    mutableSelection.value = selected
                    mutableModelState.value = LanguageModelState()
                }
                .onFailure { error ->
                    mutableModelState.value = LanguageModelState(
                        error = error.message ?: "The language model could not be downloaded.",
                    )
                }
        }
    }
}

private val LocalSunnyLanguage = staticCompositionLocalOf { SunnyLanguage.ENGLISH }

@Composable
fun SunnyLocalizationProvider(
    selected: SunnyLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolved = SunnyLanguageController.resolve(context, selected)
    CompositionLocalProvider(LocalSunnyLanguage provides resolved, content = content)
}

@Composable
internal fun currentSunnyLanguage(): SunnyLanguage = LocalSunnyLanguage.current

/**
 * Returns a cached translation or null while ML Kit produces it. Callers use a
 * neutral placeholder for that short interval instead of leaking English into
 * an otherwise translated screen.
 */
internal fun cachedTranslation(language: SunnyLanguage, text: String): String? =
    SunnyTranslationEngine.cached(language, text)

internal suspend fun translateWithMlKit(language: SunnyLanguage, text: String): String =
    SunnyTranslationEngine.translate(language, text)

internal fun translationPlaceholder(text: String): String = when {
    text.isBlank() -> text
    text.all { it.isDigit() || it.isWhitespace() || it in ".,%/+-–—:()" } -> text
    else -> "…"
}

internal fun languageForLocale(locale: Locale): SunnyLanguage = when (locale.language.lowercase(Locale.ROOT)) {
    "hi" -> SunnyLanguage.HINDI
    "es" -> SunnyLanguage.SPANISH
    "it" -> SunnyLanguage.ITALIAN
    "fr" -> SunnyLanguage.FRENCH
    "de" -> SunnyLanguage.GERMAN
    "pt" -> SunnyLanguage.PORTUGUESE_BRAZIL
    "ja" -> SunnyLanguage.JAPANESE
    "ko" -> SunnyLanguage.KOREAN
    "zh" -> if (
        locale.script.equals("Hant", ignoreCase = true) ||
        locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
    ) SunnyLanguage.CHINESE_TRADITIONAL else SunnyLanguage.CHINESE_SIMPLIFIED
    else -> SunnyLanguage.ENGLISH
}

private object SunnyTranslationEngine {
    private data class TranslationKey(val language: SunnyLanguage, val source: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translators = ConcurrentHashMap<SunnyLanguage, Translator>()
    private val modelPreparation = ConcurrentHashMap<SunnyLanguage, Deferred<Unit>>()
    private val translations = ConcurrentHashMap<TranslationKey, String>()
    private val inFlight = ConcurrentHashMap<TranslationKey, Deferred<String>>()

    suspend fun prepare(language: SunnyLanguage) {
        if (language == SunnyLanguage.ENGLISH) return
        modelPreparation.getOrPut(language) {
            scope.async {
                translator(language)
                    .downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .awaitResult()
                Unit
            }.also { request ->
                request.invokeOnCompletion { error ->
                    if (error != null) modelPreparation.remove(language, request)
                }
            }
        }.await()
    }

    fun cached(language: SunnyLanguage, text: String): String? {
        if (language == SunnyLanguage.ENGLISH || text.isBlank()) return text
        val key = TranslationKey(language, text)
        return translations[key]
    }

    suspend fun translate(language: SunnyLanguage, text: String): String {
        if (language == SunnyLanguage.ENGLISH || text.isBlank()) return text
        val key = TranslationKey(language, text)
        translations[key]?.let { return it }
        val request = inFlight.getOrPut(key) {
            scope.async {
                prepare(language)
                translator(language).translate(text).awaitResult().also {
                    translations[key] = it
                }
            }.also { deferred ->
                deferred.invokeOnCompletion { inFlight.remove(key, deferred) }
            }
        }
        return request.await()
    }

    private fun translator(language: SunnyLanguage): Translator = translators.getOrPut(language) {
        val target = requireNotNull(language.mlKitTag) {
            "No ML Kit language tag for ${language.persistedTag}"
        }
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target)
                .build(),
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}
