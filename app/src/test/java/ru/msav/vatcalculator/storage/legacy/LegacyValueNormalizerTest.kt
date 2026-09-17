package ru.msav.vatcalculator.storage.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.msav.vatcalculator.storage.legacy.LegacyValueNormalizer.Result
import java.math.BigDecimal

class LegacyValueNormalizerTest {

    @Test
    fun shortFloatAndDoubleRepresentationsAreNormalized() {
        val fromFloat = LegacyValueNormalizer.normalizeAmount(
            LegacyValueNormalizer.fromFloatString("9.99999992E11")!!,
        ) as Result.Valid
        assertEquals(BigDecimal("999999992000.00"), fromFloat.value)
        assertFalse(fromFloat.rounded)

        val fromDouble = LegacyValueNormalizer.normalizeAmount(
            LegacyValueNormalizer.fromDouble(1234567.89),
        ) as Result.Valid
        assertEquals(BigDecimal("1234567.89"), fromDouble.value)
        assertFalse(fromDouble.rounded)
    }

    @Test
    fun roundingAndInvalidBoundariesAreReported() {
        val roundedAmount = LegacyValueNormalizer.normalizeAmount(BigDecimal("100.005")) as Result.Valid
        assertEquals(BigDecimal("100.01"), roundedAmount.value)
        assertTrue(roundedAmount.rounded)

        assertEquals(Result.Invalid, LegacyValueNormalizer.normalizeAmount(BigDecimal("1000000000000")))
        assertEquals(Result.Invalid, LegacyValueNormalizer.normalizeRate(BigDecimal("99.99999")))
        assertEquals(Result.Invalid, LegacyValueNormalizer.normalizeRate(BigDecimal("-1")))
    }
}
