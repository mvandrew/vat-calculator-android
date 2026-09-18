package ru.msav.vatcalculator.storage

import ru.msav.vatcalculator.calculation.VatMode

/**
 * Сохраняемое состояние формы калькулятора (storage.md §6). Хранит текст ввода
 * как есть — включая пустой и незавершённый (`12,`) — чтобы возврат или
 * перезапуск не подменял его прежним корректным расчётом. Производные итоги
 * не сохраняются и пересчитываются единым ядром.
 */
data class AppState(
    val amountText: String,
    val rateText: String,
    val mode: VatMode,
    val themeSetting: ThemeSetting,
    val languageSetting: LanguageSetting,
    /** ID редактируемой записи журнала либо отсутствие связи. */
    val openEntryId: Long?,
) {
    companion object {
        /** Начальное состояние чистой установки из scope.md: пустая сумма, 22%, «Выделить», системные тема и язык. */
        val INITIAL = AppState(
            amountText = "",
            rateText = "22",
            mode = VatMode.INCLUSIVE,
            themeSetting = ThemeSetting.SYSTEM,
            languageSetting = LanguageSetting.SYSTEM,
            openEntryId = null,
        )
    }
}
