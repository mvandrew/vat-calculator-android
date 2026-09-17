package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    @Test
    fun moneyUsesLanguageSpecificSeparators() {
        assertEquals("1\u00A0234\u00A0567,89", MoneyFormatter.formatMoney(BigDecimal("1234567.89"), AppLanguage.RUSSIAN))
        assertEquals("1,234,567.89", MoneyFormatter.formatMoney(BigDecimal("1234567.89"), AppLanguage.ENGLISH))
        assertEquals("0,00", MoneyFormatter.formatMoney(BigDecimal.ZERO, AppLanguage.RUSSIAN))
        assertEquals("0.00", MoneyFormatter.formatMoney(BigDecimal.ZERO, AppLanguage.ENGLISH))
    }

    @Test
    fun maximumResultIsFormattedWithGroups() {
        val value = BigDecimal("1999998999999.98")
        assertEquals("1\u00A0999\u00A0998\u00A0999\u00A0999,98", MoneyFormatter.formatMoney(value, AppLanguage.RUSSIAN))
        assertEquals("1,999,998,999,999.98", MoneyFormatter.formatMoney(value, AppLanguage.ENGLISH))
    }

    @Test
    fun moneyContainsNoCurrencyMarks() {
        AppLanguage.entries.forEach { language ->
            val formatted = MoneyFormatter.formatMoney(BigDecimal("1234567.89"), language)
            assertFalse(formatted.any { it in "₽$€£¥" })
            assertFalse(formatted.contains("RUB") || formatted.contains("руб"))
        }
    }

    @Test
    fun rateKeepsPrecisionWithoutTrailingZeros() {
        assertEquals("20", MoneyFormatter.formatRate(BigDecimal("20.0000"), AppLanguage.RUSSIAN))
        assertEquals("7,5", MoneyFormatter.formatRate(BigDecimal("7.50"), AppLanguage.RUSSIAN))
        assertEquals("99.9999", MoneyFormatter.formatRate(BigDecimal("99.9999"), AppLanguage.ENGLISH))
    }
}
