package ru.msav.vatcalculator.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

/** «О программе» (interface.md §4.3): версия, автор и работающие внешние действия. */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

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

    private fun setContent(
        onSite: () -> Unit = {},
        onFeedback: () -> Unit = {},
        onPrivacy: () -> Unit = {},
    ) {
        composeRule.setContent {
            VATCalculatorTheme {
                CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                    AboutScreenContent(
                        versionName = "3.0",
                        onSite = onSite,
                        onFeedback = onFeedback,
                        onPrivacy = onPrivacy,
                        onBack = {},
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
    }

    @Test
    fun aboutShowsVersionAuthorAndActions() {
        setContent()
        composeRule.onNodeWithText(string(R.string.screen_calculator)).assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.about_version, "3.0"))
            .assertExists()
        composeRule.onNodeWithText(string(R.string.about_author)).assertExists()
        composeRule.onNodeWithText(string(R.string.about_site)).assertExists()
        composeRule.onNodeWithText(string(R.string.about_feedback)).assertExists()
        composeRule.onNodeWithText(string(R.string.about_privacy)).assertExists()
    }

    @Test
    fun actionCallbacksAreWired() {
        var site = 0
        var feedback = 0
        var privacy = 0
        setContent(
            onSite = { site++ },
            onFeedback = { feedback++ },
            onPrivacy = { privacy++ },
        )
        composeRule.onNodeWithText(string(R.string.about_site)).performClick()
        composeRule.onNodeWithText(string(R.string.about_feedback)).performClick()
        composeRule.onNodeWithText(string(R.string.about_privacy)).performClick()
        assertEquals(1, site)
        assertEquals(1, feedback)
        assertEquals(1, privacy)
    }

    @Test
    fun agreedUrlsAreHttpsAndStable() {
        // Адреса согласованы (раздел приложений msav.ru); без подмены тестовыми страницами.
        assertTrue(AboutLinks.SITE_URL.startsWith("https://msav.ru/apps/vat-calculator/"))
        assertTrue(AboutLinks.PRIVACY_URL.startsWith("https://msav.ru/apps/vat-calculator/"))
        assertEquals("info@msav.ru", AboutLinks.FEEDBACK_EMAIL)
    }
}
