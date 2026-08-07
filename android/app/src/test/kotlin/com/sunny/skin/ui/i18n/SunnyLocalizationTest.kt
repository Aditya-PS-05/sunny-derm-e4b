package com.sunny.skin.ui.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SunnyLocalizationTest {
    @Test
    fun supportedDeviceLocalesResolveToMlKitLanguages() {
        assertEquals(SunnyLanguage.HINDI, languageForLocale(Locale.forLanguageTag("hi-IN")))
        assertEquals(SunnyLanguage.SPANISH, languageForLocale(Locale.forLanguageTag("es-MX")))
        assertEquals(
            SunnyLanguage.PORTUGUESE_BRAZIL,
            languageForLocale(Locale.forLanguageTag("pt-BR")),
        )
        assertEquals(
            SunnyLanguage.CHINESE_TRADITIONAL,
            languageForLocale(Locale.forLanguageTag("zh-Hant-TW")),
        )
        assertEquals(SunnyLanguage.ENGLISH, languageForLocale(Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun placeholdersNeverLeakEnglishDuringTranslation() {
        assertEquals("…", translationPlaceholder("Scanned"))
        assertEquals("…", translationPlaceholder("This image shows a brown flat spot."))
        assertEquals("9%", translationPlaceholder("9%"))
        assertEquals("0", translationPlaceholder("0"))
    }

    @Test
    fun persistedTagsAreStable() {
        assertEquals(SunnyLanguage.SYSTEM, SunnyLanguage.fromTag(null))
        assertEquals(SunnyLanguage.HINDI, SunnyLanguage.fromTag("hi"))
        assertEquals(SunnyLanguage.SPANISH, SunnyLanguage.fromTag("es"))
        assertEquals(SunnyLanguage.ITALIAN, SunnyLanguage.fromTag("it"))
        assertEquals(SunnyLanguage.PORTUGUESE_BRAZIL, SunnyLanguage.fromTag("pt-BR"))
        assertEquals(SunnyLanguage.JAPANESE, SunnyLanguage.fromTag("ja"))
        assertEquals(SunnyLanguage.CHINESE_TRADITIONAL, SunnyLanguage.fromTag("zh-Hant"))
    }
}
