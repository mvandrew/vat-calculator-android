package ru.msav.vatcalculator.ui.screens

import ru.msav.vatcalculator.calculation.AmountParser

/**
 * Фильтр изменений текста в полях суммы и ставки (calculation.md §5.1).
 * Недопустимая вставка отклоняется целиком: обработчик изменения просто
 * не применяет новую строку, и поле сохраняет прежнее содержимое.
 *
 * Допускаются цифры, один десятичный разделитель (точка или запятая —
 * повторный или смешанный отклоняется) и три вида пробелов-разделителей
 * групп; длина не превышает 64 символа. Семантика групп и точности
 * проверяется парсером и отображается подсказкой у поля.
 */
object CalculatorInputFilter {

    fun isAcceptable(raw: String): Boolean {
        if (raw.length > AmountParser.MAX_INPUT_LENGTH) return false
        var separators = 0
        for (char in raw) {
            separators += when (char) {
                in '0'..'9' -> 0
                '.', ',' -> 1
                ' ', NBSP, NNBSP -> 0
                else -> return false
            }
        }
        return separators <= 1
    }

    private const val NBSP = '\u00A0'
    private const val NNBSP = '\u202F'
}
