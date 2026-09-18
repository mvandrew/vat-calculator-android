package ru.msav.vatcalculator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.ThemeSetting
import ru.msav.vatcalculator.ui.appString
import ru.msav.vatcalculator.ui.components.AppTopBar

/**
 * Настройки (interface.md §4.2): тема и язык. Выбор применяется немедленно
 * ко всем экранам (ViewModel публикует состояние, MainActivity применяет)
 * и сохраняется между запусками.
 */
@Composable
fun SettingsScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    SettingsScreenContent(
        themeSetting = settings.themeSetting,
        languageSetting = settings.languageSetting,
        onThemeChange = viewModel::onThemeChange,
        onLanguageChange = viewModel::onLanguageChange,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreenContent(
    themeSetting: ThemeSetting,
    languageSetting: LanguageSetting,
    onThemeChange: (ThemeSetting) -> Unit,
    onLanguageChange: (LanguageSetting) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppTopBar(
                title = appString(R.string.screen_settings),
                onBack = onBack,
                backContentDescription = appString(R.string.nav_back_to_calculator),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(appString(R.string.settings_theme_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ThemeSetting.SYSTEM to R.string.settings_theme_system,
                    ThemeSetting.LIGHT to R.string.settings_theme_light,
                    ThemeSetting.DARK to R.string.settings_theme_dark,
                )
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = themeSetting == option,
                        onClick = { onThemeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(appString(label))
                    }
                }
            }

            Text(appString(R.string.settings_language_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    LanguageSetting.SYSTEM to R.string.settings_language_system,
                    LanguageSetting.RUSSIAN to R.string.settings_language_russian,
                    LanguageSetting.ENGLISH to R.string.settings_language_english,
                )
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = languageSetting == option,
                        onClick = { onLanguageChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(appString(label))
                    }
                }
            }
        }
    }
}
