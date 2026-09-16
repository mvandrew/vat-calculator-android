package ru.msav.vatcalculator.calculation

import java.math.BigDecimal

/**
 * Результат разбора строки ввода. Различает четыре состояния из calculation.md §5.1:
 * пустое, промежуточное, корректное и ошибочное.
 */
sealed interface ParsedInput {

    /** Пустое поле или одиночный разделитель; не трактуется как ноль. */
    data object Empty : ParsedInput

    /**
     * Промежуточный ввод: завершающий (`12,`) либо ведущий (`,5`) десятичный
     * разделитель. Значение доступно для расчёта, но ввод не завершён —
     * сохранение и отправка требуют [Valid] (см. [AmountParser.finalize]).
     */
    data class Intermediate(val value: BigDecimal) : ParsedInput

    /** Корректный завершённый ввод. */
    data class Valid(val value: BigDecimal) : ParsedInput

    /** Ошибочный ввод; актуальный результат не показывается и не отправляется. */
    data class Invalid(val error: ParseError) : ParsedInput
}

/**
 * Различимые причины некорректного ввода для подсказок рядом с полем.
 * Тексты сообщений локализуются в UI-слое (фазы 05–06), ядро возвращает только тип.
 */
sealed interface ParseError {
    /** Строка длиннее 64 символов; вставка отклоняется целиком. */
    data object InputTooLong : ParseError

    /** Недопустимые символы, повторные или смешанные десятичные разделители (`1,234.56`). */
    data object WrongFormat : ParseError

    /** Неправильные группы тысяч: подсказать пробелы между тысячами. */
    data object WrongGrouping : ParseError

    /** Больше дробных знаков, чем допускает поле. */
    data object TooManyFractionDigits : ParseError

    /** Выход за допустимый диапазон значений. */
    data object OutOfRange : ParseError
}
