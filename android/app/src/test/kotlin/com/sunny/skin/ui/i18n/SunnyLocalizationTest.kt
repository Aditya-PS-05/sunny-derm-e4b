package com.sunny.skin.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class SunnyLocalizationTest {
    @Test
    fun primaryNavigationTranslates() {
        assertEquals("अवलोकन", translateFor(SunnyLanguage.HINDI, "Overview"))
        assertEquals("Ajustes", translateFor(SunnyLanguage.SPANISH, "Settings"))
    }

    @Test
    fun dynamicCountsTranslate() {
        assertEquals("3 चयनित", translateFor(SunnyLanguage.HINDI, "3 selected"))
        assertEquals("2 fotos", translateFor(SunnyLanguage.SPANISH, "2 photos"))
    }

    @Test
    fun unknownUserContentIsNeverModified() {
        assertEquals(
            "Left shoulder birthmark",
            translateFor(SunnyLanguage.HINDI, "Left shoulder birthmark"),
        )
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
