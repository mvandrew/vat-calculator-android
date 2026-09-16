package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Шаблоны interface.md §7: логическая часть A01, A04, A06 без Android Intent.
 */
class ShareTextBuilderTest {

    private fun share(amount: String, rate: String, mode: VatMode, language: AppLanguage): String {
        val input = CalculationInput(BigDecimal(amount), BigDecimal(rate), mode)
        return ShareTextBuilder.build(input, VatCalculator.calculate(input), language)
    }

    @Test
    fun russianTemplateMatchesSpecification() {
        assertEquals(
            "Без НДС: 100,00\nНДС (7,5%): 7,50\nС НДС: 107,50",
            share("100", "7.5", VatMode.EXCLUSIVE, AppLanguage.RUSSIAN),
        )
    }

    @Test
    fun englishTemplateMatchesSpecification() {
        assertEquals(
            "Excluding VAT: 100.00\nVAT (7.5%): 7.50\nIncluding VAT: 107.50",
            share("100", "7.5", VatMode.EXCLUSIVE, AppLanguage.ENGLISH),
        )
    }

    @Test
    fun groupingFollowsLanguageAndScreen() {
        assertEquals(
            "Без НДС: 1\u00A0234\u00A0567,89\nНДС (20%): 246\u00A0913,58\nС НДС: 1\u00A0481\u00A0481,47",
            share("1234567.89", "20", VatMode.EXCLUSIVE, AppLanguage.RUSSIAN),
        )
        assertEquals(
            "Excluding VAT: 1,234,567.89\nVAT (20%): 246,913.58\nIncluding VAT: 1,481,481.47",
            share("1234567.89", "20", VatMode.EXCLUSIVE, AppLanguage.ENGLISH),
        )
    }

    @Test
    fun fractionalRateIsPassedFully() {
        val russian = share("999999999999.99", "99.9999", VatMode.EXCLUSIVE, AppLanguage.RUSSIAN)
        assertTrue(russian.contains("НДС (99,9999%)"))
        assertEquals(
            "Excluding VAT: 999,999,999,999.99\nVAT (99.9999%): 999,998,999,999.99\nIncluding VAT: 1,999,998,999,999.98",
            share("999999999999.99", "99.9999", VatMode.EXCLUSIVE, AppLanguage.ENGLISH),
        )
    }

    @Test
    fun inclusiveModeUsesCalculatedBase() {
        assertEquals(
            "Без НДС: 83,33\nНДС (20%): 16,67\nС НДС: 100,00",
            share("100", "20", VatMode.INCLUSIVE, AppLanguage.RUSSIAN),
        )
    }

    @Test
    fun zeroRateAndAmountAreValid() {
        assertEquals(
            "Excluding VAT: 0.00\nVAT (0%): 0.00\nIncluding VAT: 0.00",
            share("0", "0", VatMode.EXCLUSIVE, AppLanguage.ENGLISH),
        )
    }

    @Test
    fun textContainsNoCurrencyMarksOrExtraData() {
        val texts = listOf(
            share("100", "7.5", VatMode.EXCLUSIVE, AppLanguage.RUSSIAN),
            share("100", "7.5", VatMode.EXCLUSIVE, AppLanguage.ENGLISH),
            share("1234567.89", "20", VatMode.INCLUSIVE, AppLanguage.RUSSIAN),
        )
        for (text in texts) {
            assertFalse(text.contains("₽"))
            assertFalse(text.contains("руб"))
            assertFalse(text.contains("RUB"))
            assertFalse(text.contains('$'))
            assertFalse(text.contains("http"))
            assertFalse(text.contains("msav.ru"))
        }
    }
}
