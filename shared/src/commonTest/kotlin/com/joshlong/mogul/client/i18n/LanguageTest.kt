package com.joshlong.mogul.client.i18n

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageTest {

    @AfterTest
    fun backToEnglish() {
        currentLanguage = Language.English
    }

    @Test
    fun everyLanguageHasEveryKey() {
        val english = messageKeys(Language.English)
        Language.entries.forEach { language ->
            assertEquals(emptySet(), english - messageKeys(language), "keys missing in ${language.englishName}")
            assertEquals(emptySet(), messageKeys(language) - english, "keys only in ${language.englishName}")
        }
    }

    @Test
    fun looksUpInTheCurrentLanguage() {
        assertEquals("Settings", t("settings.title"))
        currentLanguage = Language.Spanish
        assertEquals("Ajustes", t("settings.title"))
        assertEquals("Cuenta: Josh", t("account.label", "name" to "Josh"))
        assertEquals("1 adjunto", tc("publications.plugins.mogulAyrshare.composer.attachments", 1))
        assertEquals("3 adjuntos", tc("publications.plugins.mogulAyrshare.composer.attachments", 3))
        currentLanguage = Language.French
        assertEquals("Réglages", t("settings.title"))
        assertEquals("Se déconnecter", t("app.sign-out"))
        assertEquals("no.such.key", t("no.such.key"))
    }

    @Test
    fun findsTheLanguageForATag() {
        assertEquals(Language.Spanish, Language.of("es-MX"))
        assertEquals(Language.French, Language.of("fr_CA"))
        assertEquals(Language.English, Language.of("EN"))
        assertNull(Language.of("zh-Hans"))
        assertNull(Language.of(""))
        assertNull(Language.of(null))
    }
}
