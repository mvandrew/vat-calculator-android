package ru.msav.vatcalculator.calculation

import java.math.BigDecimal

/**
 * Разбор строки ввода суммы или ставки по правилам calculation.md §5.1.
 *
 * Локально-независимая грамматика: точка и запятая равноправны как десятичный
 * разделитель, но оба разделителя в одной строке (`1,234.56`, `1.234,56`) и
 * повторные разделители отклоняются. Группы тысяч разделяются обычным (U+0020),
 * неразрывным (U+00A0) или узким неразрывным (U+202F) пробелом; окружающие
 * пробелы принимаются. Отрицательные значения, экспоненциальная запись,
 * `NaN`, бесконечность и арифметические выражения не поддерживаются.
 */
class AmountParser(private val spec: ParseSpec) {

    data class ParseSpec(
        val maxFractionDigits: Int,
        val upperBound: BigDecimal,
        val upperBoundInclusive: Boolean,
    )

    fun parse(raw: String): ParsedInput {
        if (raw.length > MAX_INPUT_LENGTH) {
            return ParsedInput.Invalid(ParseError.InputTooLong)
        }
        val text = raw.trim(GROUPING_SPACES::contains)
        if (text.isEmpty()) {
            return ParsedInput.Empty
        }
        if (text.length == 1 && text[0] in DECIMAL_SEPARATORS) {
            return ParsedInput.Empty
        }

        val intDigits = StringBuilder()
        val fractionDigits = StringBuilder()
        var separatorSeen = false
        for (char in text) {
            when {
                char in '0'..'9' -> {
                    if (separatorSeen) fractionDigits.append(char) else intDigits.append(char)
                }

                char in DECIMAL_SEPARATORS -> {
                    if (separatorSeen) return ParsedInput.Invalid(ParseError.WrongFormat)
                    separatorSeen = true
                }

                char in GROUPING_SPACES -> {
                    // Пробел допустим только как разделитель групп целой части.
                    if (separatorSeen) return ParsedInput.Invalid(ParseError.WrongGrouping)
                    intDigits.append(GROUP_MARKER)
                }

                else -> return ParsedInput.Invalid(ParseError.WrongFormat)
            }
        }

        val intPart = intDigits.toString()
        if (!validateGroups(intPart)) {
            return ParsedInput.Invalid(ParseError.WrongGrouping)
        }
        val integer = intPart.filterNot { it == GROUP_MARKER }.ifEmpty { null }
        if (fractionDigits.length > spec.maxFractionDigits) {
            return ParsedInput.Invalid(ParseError.TooManyFractionDigits)
        }

        val value = BigDecimal(
            buildString {
                append(integer ?: "0")
                if (fractionDigits.isNotEmpty()) {
                    append('.')
                    append(fractionDigits)
                }
            },
        )
        if (isAboveUpperBound(value)) {
            return ParsedInput.Invalid(ParseError.OutOfRange)
        }

        return if (separatorSeen && (integer == null || fractionDigits.isEmpty())) {
            // `12,` или `,5` — ввод не завершён, но значение уже определено.
            ParsedInput.Intermediate(value)
        } else {
            ParsedInput.Valid(value)
        }
    }

    /**
     * Завершение промежуточного ввода по правилам calculation.md §5.1:
     * `12,` трактуется как `12`. Остальные состояния возвращаются без изменений.
     */
    fun finalize(raw: String): ParsedInput = when (val parsed = parse(raw)) {
        is ParsedInput.Intermediate -> ParsedInput.Valid(parsed.value)
        else -> parsed
    }

    private fun isAboveUpperBound(value: BigDecimal): Boolean {
        val comparison = value.compareTo(spec.upperBound)
        return if (spec.upperBoundInclusive) comparison > 0 else comparison >= 0
    }

    /**
     * Проверка значения против верхней границы спецификации. Используется для
     * производных величин, не прошедших разбор поля (обратный расчёт, §5.2).
     */
    fun isWithinBound(value: BigDecimal): Boolean = !isAboveUpperBound(value)

    private fun validateGroups(intPart: String): Boolean {
        if (!intPart.contains(GROUP_MARKER)) return true
        val groups = intPart.split(GROUP_MARKER)
        groups.forEachIndexed { index, group ->
            if (group.isEmpty()) return false
            if (index == 0) {
                if (group.length > 3) return false
            } else if (group.length != 3) {
                return false
            }
        }
        return true
    }

    companion object {
        /** Максимальная длина входной строки; более длинная вставка отклоняется целиком. */
        const val MAX_INPUT_LENGTH = 64

        private val DECIMAL_SEPARATORS = charArrayOf('.', ',')
        private val GROUPING_SPACES = charArrayOf(' ', '\u00A0', '\u202F')
        private const val GROUP_MARKER = '\u0000'

        /** Поле суммы: 0..999 999 999 999,99, не более двух дробных знаков. */
        val AMOUNT = AmountParser(
            ParseSpec(
                maxFractionDigits = 2,
                upperBound = BigDecimal("999999999999.99"),
                upperBoundInclusive = true,
            ),
        )

        /** Поле ставки: 0 <= r < 100, не более четырёх дробных знаков; 100% — ошибка диапазона. */
        val RATE = AmountParser(
            ParseSpec(
                maxFractionDigits = 4,
                upperBound = BigDecimal("100"),
                upperBoundInclusive = false,
            ),
        )

        /**
         * Поле итога (без НДС / НДС / с НДС): 0..1 999 999 999 999,99, не более
         * двух дробных знаков. Результаты вправе превышать предел поля ввода
         * суммы (максимум начисления §5.2), поэтому граница выше; производная
         * сумма дополнительно проверяется пределом поля ввода.
         */
        val RESULT = AmountParser(
            ParseSpec(
                maxFractionDigits = 2,
                upperBound = BigDecimal("1999999999999.99"),
                upperBoundInclusive = true,
            ),
        )
    }
}
