package ru.msav.vatcalculator.storage

/**
 * Настройка темы приложения (interface.md §4.2). Системный выбор следует
 * изменениям ОС; явный — от них не зависит. Применяется в UI-слое (фазы 05–06).
 */
enum class ThemeSetting {
    SYSTEM,
    LIGHT,
    DARK,
}
