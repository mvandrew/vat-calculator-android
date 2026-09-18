package ru.msav.vatcalculator.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.ThemeSetting
import java.util.Locale

/**
 * Применение настроек темы и языка (interface.md §4.2) без пересоздания
 * Activity: язык разрешается в [AppLanguage] и предоставляется композиции,
 * строки читаются через [appString] из ресурсов с нужной локалью, тема —
 * через [ru.msav.vatcalculator.ui.theme.VATCalculatorTheme].
 *
 * Резолв — чистые функции ([resolveLanguage], [resolveTheme]), покрываемые
 * JVM-тестами; системный выбор следует изменениям ОС, явный — от них не зависит.
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/** Язык вывода по настройке: системная локаль `ru*` — русский, иначе английский. */
fun resolveLanguage(setting: LanguageSetting, systemLanguageTag: String): AppLanguage =
    when (setting) {
        LanguageSetting.SYSTEM ->
            if (systemLanguageTag.startsWith("ru", ignoreCase = true)) AppLanguage.RUSSIAN else AppLanguage.ENGLISH

        LanguageSetting.RUSSIAN -> AppLanguage.RUSSIAN
        LanguageSetting.ENGLISH -> AppLanguage.ENGLISH
    }

/** Тёмная ли тема по настройке: системный выбор следует ОС, явный — фиксирован. */
fun resolveTheme(setting: ThemeSetting, systemInDarkTheme: Boolean): Boolean =
    when (setting) {
        ThemeSetting.SYSTEM -> systemInDarkTheme
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }

private fun localeFor(language: AppLanguage): Locale =
    when (language) {
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.ENGLISH -> Locale("en")
    }

/** Язык приложения с учётом настройки: перечитывается при изменении системной локали. */
@Composable
fun rememberAppLanguage(setting: LanguageSetting): AppLanguage {
    val systemTag = LocalConfiguration.current.locales[0].language
    return remember(setting, systemTag) { resolveLanguage(setting, systemTag) }
}

/**
 * Ресурсы с локалью выбранного языка приложения: без пересоздания Activity
 * и без возврата appcompat (удалён при переходе UI на Compose в фазе 02).
 */
@Composable
fun rememberAppResources(language: AppLanguage = LocalAppLanguage.current): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(language, context, configuration) {
        val localizedConfiguration = Configuration(configuration)
        localizedConfiguration.setLocale(localeFor(language))
        context.createConfigurationContext(localizedConfiguration).resources
    }
}

/** Локализованная строка по текущему языку приложения (аналог stringResource). */
@Composable
fun appString(id: Int): String = rememberAppResources().getString(id)

/** Локализованная строка с подстановкой аргументов формата. */
@Composable
fun appString(id: Int, vararg formatArgs: Any): String = rememberAppResources().getString(id, *formatArgs)
