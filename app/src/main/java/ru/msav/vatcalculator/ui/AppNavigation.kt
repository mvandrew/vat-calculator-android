package ru.msav.vatcalculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.msav.vatcalculator.ui.screens.AboutScreen
import ru.msav.vatcalculator.ui.screens.CalculatorScreen
import ru.msav.vatcalculator.ui.screens.CalculatorViewModel
import ru.msav.vatcalculator.ui.screens.HistoryScreen
import ru.msav.vatcalculator.ui.screens.SettingsScreen
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

/**
 * Навигация приложения (interface.md §4): плоская схема «калькулятор ↔
 * журнал/настройки/о программе» с возвратом. Текущий экран переживает
 * поворот и пересоздание Activity (`rememberSaveable`); системный «Назад»
 * и кнопка возврата ведут к калькулятору, сохраняя форму (ViewModel жив).
 * Открытие записи журнала включает сеанс редактирования на калькуляторе
 * (заголовок с возвратом в журнал); системный «Назад» в сеансе возвращает
 * в журнал, «Новый расчёт» завершает сеанс обычной стартовой формой.
 */
enum class AppScreen { CALCULATOR, HISTORY, SETTINGS, ABOUT }

@Composable
fun AppRoot(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val language = rememberAppLanguage(settings.languageSetting)

    VATCalculatorTheme(themeSetting = settings.themeSetting) {
        CompositionLocalProvider(LocalAppLanguage provides language) {
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.CALCULATOR) }

            if (currentScreen != AppScreen.CALCULATOR) {
                BackHandler { currentScreen = AppScreen.CALCULATOR }
            } else if (uiState.editingFromHistory) {
                // Сеанс редактирования записи: системный «Назад» возвращает в журнал.
                BackHandler {
                    viewModel.exitEntryEditing()
                    currentScreen = AppScreen.HISTORY
                }
            }

            when (currentScreen) {
                AppScreen.CALCULATOR ->
                    CalculatorScreen(
                        viewModel = viewModel,
                        onOpenHistory = { currentScreen = AppScreen.HISTORY },
                        onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                        onOpenAbout = { currentScreen = AppScreen.ABOUT },
                        onBackToHistory = {
                            viewModel.exitEntryEditing()
                            currentScreen = AppScreen.HISTORY
                        },
                        modifier = modifier,
                    )

                AppScreen.HISTORY ->
                    HistoryScreen(
                        viewModel = viewModel,
                        onBack = {
                            // Выход из журнала завершает сеанс редактирования,
                            // не затрагивая форму калькулятора.
                            viewModel.exitEntryEditing()
                            currentScreen = AppScreen.CALCULATOR
                        },
                        onEntryOpened = { currentScreen = AppScreen.CALCULATOR },
                        modifier = modifier,
                    )

                AppScreen.SETTINGS ->
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.CALCULATOR },
                        modifier = modifier,
                    )

                AppScreen.ABOUT ->
                    AboutScreen(
                        onBack = { currentScreen = AppScreen.CALCULATOR },
                        modifier = modifier,
                    )
            }
        }
    }
}
