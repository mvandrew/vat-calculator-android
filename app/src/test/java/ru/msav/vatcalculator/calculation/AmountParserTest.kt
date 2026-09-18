package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmountParserTest {

    private fun amount(raw: String): ParsedInput = AmountParser.AMOUNT.parse(raw)
    private fun rate(raw: String): ParsedInput = AmountParser.RATE.parse(raw)
    private fun result(raw: String): ParsedInput = AmountParser.RESULT.parse(raw)

    private fun assertInvalid(parsed: ParsedInput, error: ParseError) {
        assertTrue("expected Invalid but was $parsed", parsed is ParsedInput.Invalid)
        assertEquals(error, (parsed as ParsedInput.Invalid).error)
    }

    @Test
    fun acceptedAmountFormats() {
        mapOf(
            "100" to "100",
            "100,25" to "100.25",
            "100.25" to "100.25",
            "1 234 567,89" to "1234567.89",
            "1\u00A0234,56" to "1234.56",
            "1\u202F234,56" to "1234.56",
            " 0,03 " to "0.03",
        ).forEach { (raw, expected) ->
            assertEquals(ParsedInput.Valid(BigDecimal(expected)), amount(raw))
        }
    }

    @Test
    fun amountBoundaryIsEnforced() {
        assertEquals(ParsedInput.Valid(BigDecimal("999999999999.99")), amount("999 999 999 999,99"))
        assertInvalid(amount("1 000 000 000 000,00"), ParseError.OutOfRange)
    }

    @Test
    fun wrongGroupingIsRejected() {
        listOf("12 34", "1 23 456", "1234 567", "1  2", "1 ,5", "12.3 4").forEach {
            assertInvalid(amount(it), ParseError.WrongGrouping)
        }
    }

    @Test
    fun unsupportedFormatsAreRejected() {
        listOf("1,234.56", "1.234,56", "1..2", "1,,2", "-5", "1e5", "abc", "NaN", "10₽").forEach {
            assertInvalid(amount(it), ParseError.WrongFormat)
        }
    }

    @Test
    fun precisionAndLengthAreEnforced() {
        assertInvalid(amount("100,005"), ParseError.TooManyFractionDigits)
        assertInvalid(amount("9".repeat(64)), ParseError.OutOfRange)
        assertInvalid(amount("9".repeat(65)), ParseError.InputTooLong)
    }

    @Test
    fun emptyInputIsNotZero() {
        listOf("", "   ", "\u00A0", ",", ".").forEach {
            assertEquals(ParsedInput.Empty, amount(it))
        }
    }

    @Test
    fun intermediateInputRetainsCompletableValue() {
        assertEquals(ParsedInput.Intermediate(BigDecimal("12")), amount("12,"))
        assertEquals(ParsedInput.Intermediate(BigDecimal("0.5")), amount(",5"))
        assertInvalid(amount(",005"), ParseError.TooManyFractionDigits)
    }

    @Test
    fun rateBoundariesAreAccepted() {
        listOf("0" to "0", "22" to "22", "7.5" to "7.5", "99,9999" to "99.9999").forEach { (raw, expected) ->
            assertEquals(ParsedInput.Valid(BigDecimal(expected)), rate(raw))
        }
    }

    @Test
    fun invalidRatesAreRejected() {
        assertInvalid(rate("100"), ParseError.OutOfRange)
        assertInvalid(rate("99,99999"), ParseError.TooManyFractionDigits)
        assertInvalid(rate("-1"), ParseError.WrongFormat)
    }

    @Test
    fun finalizeCompletesOnlyIntermediateInput() {
        assertEquals(ParsedInput.Valid(BigDecimal("12")), AmountParser.AMOUNT.finalize("12,"))
        assertEquals(ParsedInput.Valid(BigDecimal("0.5")), AmountParser.AMOUNT.finalize(",5"))
        assertEquals(ParsedInput.Empty, AmountParser.AMOUNT.finalize(""))
        assertInvalid(AmountParser.RATE.finalize("100"), ParseError.OutOfRange)
    }

    @Test
    fun resultFieldAllowsMaximumDisplayedTotal() {
        assertEquals(ParsedInput.Valid(BigDecimal("1999998999998.98")), result("1 999 998 999 998,98"))
        assertInvalid(result("2 000 000 000 000,00"), ParseError.OutOfRange)
        assertInvalid(result("1,234"), ParseError.TooManyFractionDigits)
    }

    @Test
    fun boundCheckAcceptsDerivedAmountsWithinRange() {
        assertTrue(AmountParser.AMOUNT.isWithinBound(BigDecimal("999999999999.99")))
        assertTrue(!AmountParser.AMOUNT.isWithinBound(BigDecimal("1000000000000")))
        assertTrue(AmountParser.RESULT.isWithinBound(BigDecimal("1999998999999.98")))
    }
}
