package ru.msav.vatcalculator.storage

/**
 * Настройка языка приложения (interface.md §4.2). Системный выбор приводится
 * к [ru.msav.vatcalculator.calculation.AppLanguage] RUSSIAN/ENGLISH до вызовов
 * ядра; при отсутствии поддерживаемого языка используется английский.
 */
enum class LanguageSetting {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
}
