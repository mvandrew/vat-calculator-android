package ru.msav.vatcalculator.calculation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Язык числового и текстового представления. Системный выбор настроек
 * (фаза 06) приводится к одному из этих значений до вызовов ядра.
 */
enum class AppLanguage(
    val decimalSeparator: Char,
    val groupingSeparator: Char,
) {
    RUSSIAN(',', '\u00A0'),
    ENGLISH('.', ','),
}

/**
 * Форматирование денежных значений и ставки по calculation.md §5.3:
 * два дробных знака без символа и кода валюты, разделители — по языку.
 * Один формат используется на калькуляторе, в журнале и в отправке.
 *
 * Разделители задаёт собственная реализация, а не системный NumberFormat:
 * представление не зависит от версии ICU/CLDR устройства и совпадает
 * на экране и в тексте отправки (решение фазы 03: RU — неразрывный пробел U+00A0).
 */
object MoneyFormatter {

    private const val MONEY_SCALE = 2
    private const val GROUP_SIZE = 3

    fun formatMoney(value: BigDecimal, language: AppLanguage): String {
        val plain = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP).abs().toPlainString()
        val separatorIndex = plain.indexOf('.')
        val integerPart = plain.substring(0, separatorIndex)
        val fractionPart = plain.substring(separatorIndex + 1)
        return buildString {
            append(groupInteger(integerPart, language))
            append(language.decimalSeparator)
            append(fractionPart)
        }
    }

    /**
     * Ставка в принятой точности без лишних конечных нулей: `20`, `7,5`, `99,9999`.
     * Значение ставки не превышает 99,9999, поэтому группировка разрядов не требуется.
     */
    fun formatRate(rate: BigDecimal, language: AppLanguage): String =
        rate.stripTrailingZeros().toPlainString().replace('.', language.decimalSeparator)

    private fun groupInteger(integerPart: String, language: AppLanguage): String {
        if (integerPart.length <= GROUP_SIZE) return integerPart
        val groups = mutableListOf<String>()
        var end = integerPart.length
        while (end > GROUP_SIZE) {
            groups += integerPart.substring(end - GROUP_SIZE, end)
            end -= GROUP_SIZE
        }
        groups += integerPart.substring(0, end)
        return groups.asReversed().joinToString(language.groupingSeparator.toString())
    }
}
