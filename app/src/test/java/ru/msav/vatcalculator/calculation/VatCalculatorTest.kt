package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class VatCalculatorTest {

    private data class Case(
        val amount: String,
        val rate: String,
        val mode: VatMode,
        val base: String,
        val vat: String,
        val total: String,
    )

    private fun assertCalculation(case: Case) {
        val result = VatCalculator.calculate(
            CalculationInput(BigDecimal(case.amount), BigDecimal(case.rate), case.mode),
        )
        assertEquals(case.base, result.base.toPlainString())
        assertEquals(case.vat, result.vat.toPlainString())
        assertEquals(case.total, result.total.toPlainString())
        assertEquals(2, result.base.scale())
        assertEquals(2, result.vat.scale())
        assertEquals(2, result.total.scale())
        assertEquals(0, result.base.add(result.vat).compareTo(result.total))
    }

    @Test
    fun controlExamples() {
        listOf(
            Case("100", "22", VatMode.EXCLUSIVE, "100.00", "22.00", "122.00"),
            Case("122", "22", VatMode.INCLUSIVE, "100.00", "22.00", "122.00"),
            Case("100", "20", VatMode.INCLUSIVE, "83.33", "16.67", "100.00"),
            Case("100", "0", VatMode.EXCLUSIVE, "100.00", "0.00", "100.00"),
            Case("100", "7.5", VatMode.EXCLUSIVE, "100.00", "7.50", "107.50"),
            Case("100", "99.5", VatMode.EXCLUSIVE, "100.00", "99.50", "199.50"),
            Case("0.03", "20", VatMode.EXCLUSIVE, "0.03", "0.01", "0.04"),
            Case("0.05", "10", VatMode.EXCLUSIVE, "0.05", "0.01", "0.06"),
            Case("0.03", "20", VatMode.INCLUSIVE, "0.03", "0.00", "0.03"),
            Case("1234567.89", "20", VatMode.EXCLUSIVE, "1234567.89", "246913.58", "1481481.47"),
            Case("0", "22", VatMode.EXCLUSIVE, "0.00", "0.00", "0.00"),
            Case("0", "22", VatMode.INCLUSIVE, "0.00", "0.00", "0.00"),
        ).forEach(::assertCalculation)
    }

    @Test
    fun maximumInputProducesUnboundedResult() =
        assertCalculation(
            Case(
                "999999999999.99",
                "99.9999",
                VatMode.EXCLUSIVE,
                "999999999999.99",
                "999998999999.99",
                "1999998999999.98",
            ),
        )

    @Test
    fun halfUpRoundingIsUsedInBothModes() {
        assertCalculation(Case("0.15", "50", VatMode.EXCLUSIVE, "0.15", "0.08", "0.23"))
        assertCalculation(Case("0.27", "20", VatMode.INCLUSIVE, "0.23", "0.04", "0.27"))
    }

    @Test
    fun divisionIsRoundedWithoutIntermediateCoefficient() =
        assertCalculation(Case("100", "20", VatMode.INCLUSIVE, "83.33", "16.67", "100.00"))

    @Test
    fun zeroRateAndExactInversePreserveValues() {
        assertCalculation(Case("122", "0", VatMode.EXCLUSIVE, "122.00", "0.00", "122.00"))
        assertCalculation(Case("122", "0", VatMode.INCLUSIVE, "122.00", "0.00", "122.00"))
        val add = VatCalculator.calculate(CalculationInput(BigDecimal("100"), BigDecimal("22"), VatMode.EXCLUSIVE))
        val extract = VatCalculator.calculate(CalculationInput(BigDecimal("122"), BigDecimal("22"), VatMode.INCLUSIVE))
        assertEquals(add, extract)
    }

    @Test
    fun fourDigitRateIsSupported() =
        assertCalculation(Case("100", "12.3456", VatMode.EXCLUSIVE, "100.00", "12.35", "112.35"))

    @Test
    fun negativeInputIsRejected() {
        var thrown = false
        try {
            VatCalculator.calculate(CalculationInput(BigDecimal("-1"), BigDecimal("20"), VatMode.EXCLUSIVE))
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
