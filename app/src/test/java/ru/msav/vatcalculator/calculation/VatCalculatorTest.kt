package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Контрольные примеры calculation.md §5.4, предельное начисление §5.2
 * и округление HALF_UP на половине копейки.
 */
class VatCalculatorTest {

    private fun assertCalculation(
        amount: String,
        rate: String,
        mode: VatMode,
        base: String,
        vat: String,
        total: String,
    ) {
        val result = VatCalculator.calculate(CalculationInput(BigDecimal(amount), BigDecimal(rate), mode))
        assertEquals(base, result.base.toPlainString())
        assertEquals(vat, result.vat.toPlainString())
        assertEquals(total, result.total.toPlainString())
        assertEquals(2, result.base.scale())
        assertEquals(2, result.vat.scale())
        assertEquals(2, result.total.scale())
        // Инвариант: B + V = T в показанных значениях.
        assertEquals(0, result.base.add(result.vat).compareTo(result.total))
    }

    // --- 11 контрольных примеров calculation.md §5.4 ---

    @Test
    fun controlExample01Add22() = assertCalculation("100", "22", VatMode.EXCLUSIVE, "100.00", "22.00", "122.00")

    @Test
    fun controlExample02Extract22() = assertCalculation("122", "22", VatMode.INCLUSIVE, "100.00", "22.00", "122.00")

    @Test
    fun controlExample03Extract20() = assertCalculation("100", "20", VatMode.INCLUSIVE, "83.33", "16.67", "100.00")

    @Test
    fun controlExample04AddZeroRate() = assertCalculation("100", "0", VatMode.EXCLUSIVE, "100.00", "0.00", "100.00")

    @Test
    fun controlExample05AddFractionalRate() = assertCalculation("100", "7.5", VatMode.EXCLUSIVE, "100.00", "7.50", "107.50")

    @Test
    fun controlExample06AddHighRate() = assertCalculation("100", "99.5", VatMode.EXCLUSIVE, "100.00", "99.50", "199.50")

    @Test
    fun controlExample07AddSmallAmount() = assertCalculation("0.03", "20", VatMode.EXCLUSIVE, "0.03", "0.01", "0.04")

    @Test
    fun controlExample08AddHalfKopeck() = assertCalculation("0.05", "10", VatMode.EXCLUSIVE, "0.05", "0.01", "0.06")

    @Test
    fun controlExample09ExtractSmallAmount() = assertCalculation("0.03", "20", VatMode.INCLUSIVE, "0.03", "0.00", "0.03")

    @Test
    fun controlExample10AddLargeAmount() =
        assertCalculation("1234567.89", "20", VatMode.EXCLUSIVE, "1234567.89", "246913.58", "1481481.47")

    @Test
    fun controlExample11ZeroAmountInBothModes() {
        assertCalculation("0", "22", VatMode.EXCLUSIVE, "0.00", "0.00", "0.00")
        assertCalculation("0", "22", VatMode.INCLUSIVE, "0.00", "0.00", "0.00")
    }

    // --- Предельное начисление §5.2 ---

    @Test
    fun maximumResultIsComputedAndNotLimitedByInputBound() =
        assertCalculation(
            "999999999999.99",
            "99.9999",
            VatMode.EXCLUSIVE,
            "999999999999.99",
            "999998999999.99",
            "1999998999999.98",
        )

    // --- HALF_UP на половине копейки ---

    @Test
    fun halfUpRoundsHalfAwayFromZeroInAddition() {
        // 0,05 × 1,1 = 0,055 → 0,06 (вверх).
        assertCalculation("0.05", "10", VatMode.EXCLUSIVE, "0.05", "0.01", "0.06")
        // 0,15 × 1,5 = 0,225 → 0,23 (вверх).
        assertCalculation("0.15", "50", VatMode.EXCLUSIVE, "0.15", "0.08", "0.23")
    }

    @Test
    fun halfUpRoundsHalfAwayFromZeroInExtraction() {
        // 0,27 / 1,2 = 0,225 → 0,23 (вверх).
        assertCalculation("0.27", "20", VatMode.INCLUSIVE, "0.23", "0.04", "0.27")
        // 0,03 / 1,2 = 0,025 → 0,03 (вверх).
        assertCalculation("0.03", "20", VatMode.INCLUSIVE, "0.03", "0.00", "0.03")
    }

    @Test
    fun divisionRoundsResultDirectlyWithoutCoefficientRounding() {
        // 100 / 1,2 = 83,333… → 83,33; налог — разность итогов.
        assertCalculation("100", "20", VatMode.INCLUSIVE, "83.33", "16.67", "100.00")
    }

    // --- Симметрия режимов и нулевая ставка ---

    @Test
    fun zeroRateKeepsAmountInBothModes() {
        assertCalculation("122", "0", VatMode.EXCLUSIVE, "122.00", "0.00", "122.00")
        assertCalculation("122", "0", VatMode.INCLUSIVE, "122.00", "0.00", "122.00")
    }

    @Test
    fun addAndExtractAreInverseForExactCase() {
        // Начисление 22% к 100 и выделение 22% из 122 дают одинаковые итоги.
        val add = VatCalculator.calculate(CalculationInput(BigDecimal("100"), BigDecimal("22"), VatMode.EXCLUSIVE))
        val extract = VatCalculator.calculate(CalculationInput(BigDecimal("122"), BigDecimal("22"), VatMode.INCLUSIVE))
        assertEquals(add, extract)
    }

    @Test
    fun fourDigitRateIsSupported() =
        assertCalculation("100", "12.3456", VatMode.EXCLUSIVE, "100.00", "12.35", "112.35")

    @Test
    fun negativeInputIsRejectedByContract() {
        val input = CalculationInput(BigDecimal("-1"), BigDecimal("20"), VatMode.EXCLUSIVE)
        var thrown = false
        try {
            VatCalculator.calculate(input)
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
