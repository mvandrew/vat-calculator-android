package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    // --- Деньги: два знака, группировка по языку, без валюты ---

    @Test
    fun russianMoneyUsesCommaAndNbsp() {
        assertEquals("100,00", MoneyFormatter.formatMoney(BigDecimal("100"), AppLanguage.RUSSIAN))
        assertEquals("1\u00A0234,56", MoneyFormatter.formatMoney(BigDecimal("1234.56"), AppLanguage.RUSSIAN))
        assertEquals("1\u00A0234\u00A0567,89", MoneyFormatter.formatMoney(BigDecimal("1234567.89"), AppLanguage.RUSSIAN))
    }

    @Test
    fun englishMoneyUsesDotAndComma() {
        assertEquals("100.00", MoneyFormatter.formatMoney(BigDecimal("100"), AppLanguage.ENGLISH))
        assertEquals("1,234.56", MoneyFormatter.formatMoney(BigDecimal("1234.56"), AppLanguage.ENGLISH))
        assertEquals("1,234,567.89", MoneyFormatter.formatMoney(BigDecimal("1234567.89"), AppLanguage.ENGLISH))
    }

    @Test
    fun boundaryGroupLengths() {
        assertEquals("999,99", MoneyFormatter.formatMoney(BigDecimal("999.99"), AppLanguage.RUSSIAN))
        assertEquals("1\u00A0000,00", MoneyFormatter.formatMoney(BigDecimal("1000"), AppLanguage.RUSSIAN))
        assertEquals("1,000.00", MoneyFormatter.formatMoney(BigDecimal("1000"), AppLanguage.ENGLISH))
        assertEquals("0,00", MoneyFormatter.formatMoney(BigDecimal("0"), AppLanguage.RUSSIAN))
        assertEquals("0.00", MoneyFormatter.formatMoney(BigDecimal("0"), AppLanguage.ENGLISH))
    }

    @Test
    fun maximumResultIsFormattedWithGroups() {
        val max = BigDecimal("1999998999999.98")
        assertEquals("1\u00A0999\u00A0998\u00A0999\u00A0999,98", MoneyFormatter.formatMoney(max, AppLanguage.RUSSIAN))
        assertEquals("1,999,998,999,999.98", MoneyFormatter.formatMoney(max, AppLanguage.ENGLISH))
    }

    @Test
    fun moneyNeverContainsCurrencyMarks() {
        val samples = listOf("0", "100", "1234567.89", "1999998999999.98")
        for (sample in samples) {
            for (language in AppLanguage.entries) {
                val formatted = MoneyFormatter.formatMoney(BigDecimal(sample), language)
                assert(!formatted.any { it in "₽$€£¥" }) { formatted }
                assert(!formatted.contains("RUB") && !formatted.contains("руб")) { formatted }
            }
        }
    }

    // --- Ставка: точность без лишних конечных нулей ---

    @Test
    fun integerRateHasNoDecimalPart() {
        assertEquals("20", MoneyFormatter.formatRate(BigDecimal("20"), AppLanguage.RUSSIAN))
        assertEquals("20", MoneyFormatter.formatRate(BigDecimal("20.00"), AppLanguage.ENGLISH))
        assertEquals("0", MoneyFormatter.formatRate(BigDecimal("0"), AppLanguage.RUSSIAN))
        assertEquals("0", MoneyFormatter.formatRate(BigDecimal("0.0000"), AppLanguage.ENGLISH))
    }

    @Test
    fun fractionalRateKeepsPrecisionWithoutTrailingZeros() {
        assertEquals("7,5", MoneyFormatter.formatRate(BigDecimal("7.5"), AppLanguage.RUSSIAN))
        assertEquals("7.5", MoneyFormatter.formatRate(BigDecimal("7.50"), AppLanguage.ENGLISH))
        assertEquals("99,9999", MoneyFormatter.formatRate(BigDecimal("99.9999"), AppLanguage.RUSSIAN))
        assertEquals("99.9999", MoneyFormatter.formatRate(BigDecimal("99.9999"), AppLanguage.ENGLISH))
        assertEquals("7,25", MoneyFormatter.formatRate(BigDecimal("7.25"), AppLanguage.RUSSIAN))
    }
}
