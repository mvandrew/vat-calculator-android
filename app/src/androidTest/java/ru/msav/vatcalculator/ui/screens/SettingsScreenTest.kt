package ru.msav.vatcalculator.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.ThemeSetting
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

/** Настройки (interface.md §4.2): выбор темы/языка немедленно вызывает применение. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appLanguage: AppLanguage
        get() = if (context.resources.configuration.locales[0].language.startsWith("ru")) {
            AppLanguage.RUSSIAN
        } else {
            AppLanguage.ENGLISH
        }

    private fun string(id: Int): String = context.getString(id)

    private fun setContent(themeSetting: ThemeSetting, languageSetting: LanguageSetting, onTheme: (ThemeSetting) -> Unit, onLanguage: (LanguageSetting) -> Unit) {
        composeRule.setContent {
            VATCalculatorTheme {
                CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                    SettingsScreenContent(
                        themeSetting = themeSetting,
                        languageSetting = languageSetting,
                        onThemeChange = onTheme,
                        onLanguageChange = onLanguage,
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun settingsCallbacksReceiveSelection() {
        var selectedTheme: ThemeSetting? = null
        var selectedLanguage: LanguageSetting? = null
        setContent(
            themeSetting = ThemeSetting.SYSTEM,
            languageSetting = LanguageSetting.SYSTEM,
            onTheme = { selectedTheme = it },
            onLanguage = { selectedLanguage = it },
        )

        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_language_english)).performClick()

        assertEquals(ThemeSetting.DARK, selectedTheme)
        assertEquals(LanguageSetting.ENGLISH, selectedLanguage)
    }

    @Test
    fun selectedOptionsReflectCurrentSettings() {
        setContent(
            themeSetting = ThemeSetting.DARK,
            languageSetting = LanguageSetting.RUSSIAN,
            onTheme = {},
            onLanguage = {},
        )
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.settings_language_russian)).assertIsSelected()
    }
}
