package ru.msav.vatcalculator.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.ThemeSetting

/**
 * Резолв настроек языка и темы (interface.md §4.2): системный выбор следует
 * ОС (ru* → русский, иной поддерживаемый — английский), явный выбор от
 * системных значений не зависит.
 */
class AppEnvironmentTest {

    @Test
    fun systemLanguageResolvesRussianOnlyForRu() {
        assertEquals(AppLanguage.RUSSIAN, resolveLanguage(LanguageSetting.SYSTEM, "ru"))
        assertEquals(AppLanguage.RUSSIAN, resolveLanguage(LanguageSetting.SYSTEM, "RU"))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.SYSTEM, "en"))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.SYSTEM, "en-US"))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.SYSTEM, "fr"))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.SYSTEM, "de"))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.SYSTEM, ""))
    }

    @Test
    fun explicitLanguageIgnoresSystem() {
        assertEquals(AppLanguage.RUSSIAN, resolveLanguage(LanguageSetting.RUSSIAN, "en"))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(LanguageSetting.ENGLISH, "ru"))
    }

    @Test
    fun systemThemeFollowsOsAndExplicitIsFixed() {
        assertEquals(true, resolveTheme(ThemeSetting.SYSTEM, systemInDarkTheme = true))
        assertEquals(false, resolveTheme(ThemeSetting.SYSTEM, systemInDarkTheme = false))
        assertEquals(false, resolveTheme(ThemeSetting.LIGHT, systemInDarkTheme = true))
        assertEquals(true, resolveTheme(ThemeSetting.DARK, systemInDarkTheme = false))
    }
}
