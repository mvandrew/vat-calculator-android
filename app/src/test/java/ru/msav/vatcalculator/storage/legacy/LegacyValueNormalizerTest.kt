package ru.msav.vatcalculator.storage.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.msav.vatcalculator.storage.legacy.LegacyValueNormalizer.Result
import java.math.BigDecimal

class LegacyValueNormalizerTest {

    private fun amount(raw: String) = LegacyValueNormalizer.normalizeAmount(BigDecimal(raw))
    private fun rate(raw: String) = LegacyValueNormalizer.normalizeRate(BigDecimal(raw))

    @Test
    fun amountNormalizesToTwoDigitsWithoutValueChange() {
        val result = amount("122.0") as Result.Valid
        assertEquals(BigDecimal("122.00"), result.value)
        assertFalse(result.rounded)
    }

    @Test
    fun amountRoundingIsReported() {
        val result = amount("100.005") as Result.Valid
        assertEquals(BigDecimal("100.01"), result.value)
        assertTrue(result.rounded)
    }

    @Test
    fun amountAboveUpperBoundIsInvalid() {
        assertEquals(Result.Invalid, amount("1000000000000"))
        assertEquals(Result.Invalid, amount("999999999999.995"))
    }

    @Test
    fun amountAtUpperBoundIsValid() {
        val result = amount("999999999999.99") as Result.Valid
        assertFalse(result.rounded)
    }

    @Test
    fun negativeAmountIsInvalid() {
        assertEquals(Result.Invalid, amount("-0.01"))
    }

    @Test
    fun floatExponentialNotationIsParsed() {
        // Float.toString больших значений даёт научную запись; BigDecimal её принимает.
        // Значение уже кратно 1000: нормализация не меняет его (утраченная во float
        // точность невосстановима, но дополнительного округления не возникает).
        val result = LegacyValueNormalizer.normalizeAmount(
            LegacyValueNormalizer.fromFloatString("9.99999992E11")!!,
        ) as Result.Valid
        assertEquals(BigDecimal("999999992000.00"), result.value)
        assertFalse(result.rounded)
    }

    @Test
    fun garbageFloatStringIsNull() {
        assertNull(LegacyValueNormalizer.fromFloatString("abc"))
        assertNull(LegacyValueNormalizer.fromFloatString("1,5"))
    }

    @Test
    fun rateNormalizesToFourDigits() {
        val result = rate("7.525") as Result.Valid
        assertEquals(BigDecimal("7.5250"), result.value)
        assertFalse(result.rounded)
    }

    @Test
    fun rateRoundingIsReported() {
        val result = rate("20.00005") as Result.Valid
        assertEquals(BigDecimal("20.0001"), result.value)
        assertTrue(result.rounded)
    }

    @Test
    fun rateNormalizedTo100PercentIsInvalid() {
        // Нормализация ставки в 100% недопустима: требуется исправление.
        assertEquals(Result.Invalid, rate("99.99999"))
        assertEquals(Result.Invalid, rate("100"))
        assertEquals(Result.Invalid, rate("100.0000"))
    }

    @Test
    fun rateJustBelow100IsValidWithRounding() {
        val result = rate("99.99994") as Result.Valid
        assertEquals(BigDecimal("99.9999"), result.value)
        assertTrue(result.rounded)
    }

    @Test
    fun zeroRateIsValid() {
        val result = rate("0") as Result.Valid
        assertEquals(BigDecimal("0.0000"), result.value)
        assertFalse(result.rounded)
    }

    @Test
    fun negativeRateIsInvalid() {
        assertEquals(Result.Invalid, rate("-1"))
    }

    @Test
    fun sqliteDoubleUsesValueOf() {
        // SQLite REAL: краткое десятичное представление double.
        val fromDouble = LegacyValueNormalizer.fromDouble(1234567.89)
        val result = LegacyValueNormalizer.normalizeAmount(fromDouble) as Result.Valid
        assertEquals(BigDecimal("1234567.89"), result.value)
        assertFalse(result.rounded)
    }
}
