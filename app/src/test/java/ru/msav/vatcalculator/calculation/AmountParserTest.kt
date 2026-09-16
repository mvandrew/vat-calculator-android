package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmountParserTest {

    private fun amount(raw: String): ParsedInput = AmountParser.AMOUNT.parse(raw)
    private fun rate(raw: String): ParsedInput = AmountParser.RATE.parse(raw)

    private fun assertInvalidError(parsed: ParsedInput, expected: ParseError) {
        assertTrue("expected Invalid but was $parsed", parsed is ParsedInput.Invalid)
        assertEquals(expected, (parsed as ParsedInput.Invalid).error)
    }

    // --- Корректный ввод: разделители и группы ---

    @Test
    fun plainInteger() {
        assertEquals(ParsedInput.Valid(BigDecimal("100")), amount("100"))
    }

    @Test
    fun commaAndDotAreEquivalentDecimalSeparators() {
        assertEquals(ParsedInput.Valid(BigDecimal("100.25")), amount("100,25"))
        assertEquals(ParsedInput.Valid(BigDecimal("100.25")), amount("100.25"))
    }

    @Test
    fun thousandsGroupsWithAllThreeSpaceKinds() {
        assertEquals(ParsedInput.Valid(BigDecimal("1234.56")), amount("1 234,56"))
        assertEquals(ParsedInput.Valid(BigDecimal("1234.56")), amount("1\u00A0234,56"))
        assertEquals(ParsedInput.Valid(BigDecimal("1234.56")), amount("1\u202F234,56"))
    }

    @Test
    fun multipleGroups() {
        assertEquals(ParsedInput.Valid(BigDecimal("1234567.89")), amount("1 234 567,89"))
    }

    @Test
    fun groupsOfExactlyThreeWithoutLeadingGroup() {
        assertEquals(ParsedInput.Valid(BigDecimal("123456")), amount("123 456"))
    }

    @Test
    fun surroundingSpacesAreAccepted() {
        assertEquals(ParsedInput.Valid(BigDecimal("100")), amount(" 100 "))
        assertEquals(ParsedInput.Valid(BigDecimal("100")), amount("\u00A0100\u202F"))
    }

    @Test
    fun zeroIsAValidValue() {
        assertEquals(ParsedInput.Valid(BigDecimal("0")), amount("0"))
        assertEquals(ParsedInput.Valid(BigDecimal("0.03")), amount("0,03"))
    }

    @Test
    fun amountUpperBoundary() {
        assertEquals(ParsedInput.Valid(BigDecimal("999999999999.99")), amount("999 999 999 999,99"))
        assertInvalidError(amount("1 000 000 000 000,00"), ParseError.OutOfRange)
    }

    // --- Некорректные группы ---

    @Test
    fun wrongGroupingIsRejected() {
        assertInvalidError(amount("12 34"), ParseError.WrongGrouping)
        assertInvalidError(amount("1 23 456"), ParseError.WrongGrouping)
        assertInvalidError(amount("1234 567"), ParseError.WrongGrouping)
        assertInvalidError(amount("1  2"), ParseError.WrongGrouping)
        assertInvalidError(amount("1 ,5"), ParseError.WrongGrouping)
        assertInvalidError(amount("12.3 4"), ParseError.WrongGrouping)
    }

    // --- Некорректный формат ---

    @Test
    fun internationalFormatsAreRejected() {
        assertInvalidError(amount("1,234.56"), ParseError.WrongFormat)
        assertInvalidError(amount("1.234,56"), ParseError.WrongFormat)
    }

    @Test
    fun repeatedSeparatorsAreRejected() {
        assertInvalidError(amount("1..2"), ParseError.WrongFormat)
        assertInvalidError(amount("1,,2"), ParseError.WrongFormat)
        assertInvalidError(amount("1.2.3"), ParseError.WrongFormat)
        assertInvalidError(amount("1,2,3"), ParseError.WrongFormat)
    }

    @Test
    fun negativeNumbersAreRejected() {
        assertInvalidError(amount("-5"), ParseError.WrongFormat)
        assertInvalidError(amount("−5"), ParseError.WrongFormat)
        assertInvalidError(amount("-0,01"), ParseError.WrongFormat)
    }

    @Test
    fun nonNumericInputIsRejected() {
        assertInvalidError(amount("1e5"), ParseError.WrongFormat)
        assertInvalidError(amount("abc"), ParseError.WrongFormat)
        assertInvalidError(amount("1+1"), ParseError.WrongFormat)
        assertInvalidError(amount("NaN"), ParseError.WrongFormat)
        assertInvalidError(amount("Infinity"), ParseError.WrongFormat)
        assertInvalidError(amount("10₽"), ParseError.WrongFormat)
        assertInvalidError(amount("1\t2"), ParseError.WrongFormat)
    }

    // --- Точность и длина ---

    @Test
    fun thirdMoneyDigitIsRejectedNotRounded() {
        assertInvalidError(amount("100,005"), ParseError.TooManyFractionDigits)
        assertInvalidError(amount("0,999"), ParseError.TooManyFractionDigits)
    }

    @Test
    fun inputLongerThan64CharactersIsRejectedEntirely() {
        val exactly64 = "9".repeat(64)
        assertInvalidError(amount(exactly64), ParseError.OutOfRange)
        assertInvalidError(amount("9".repeat(65)), ParseError.InputTooLong)
    }

    // --- Пустота и промежуточный ввод ---

    @Test
    fun emptyIsNotZero() {
        assertEquals(ParsedInput.Empty, amount(""))
        assertEquals(ParsedInput.Empty, amount("   "))
        assertEquals(ParsedInput.Empty, amount("\u00A0"))
    }

    @Test
    fun loneSeparatorIsEmpty() {
        assertEquals(ParsedInput.Empty, amount(","))
        assertEquals(ParsedInput.Empty, amount("."))
    }

    @Test
    fun trailingSeparatorIsIntermediate() {
        assertEquals(ParsedInput.Intermediate(BigDecimal("12")), amount("12,"))
        assertEquals(ParsedInput.Intermediate(BigDecimal("12")), amount("12."))
        assertEquals(ParsedInput.Intermediate(BigDecimal("1234")), amount("1 234,"))
    }

    @Test
    fun leadingSeparatorIsIntermediate() {
        assertEquals(ParsedInput.Intermediate(BigDecimal("0.5")), amount(",5"))
        assertEquals(ParsedInput.Intermediate(BigDecimal("0.05")), amount(".05"))
    }

    @Test
    fun intermediateInputStillCheckedForPrecisionAndRange() {
        assertInvalidError(amount(",005"), ParseError.TooManyFractionDigits)
        assertInvalidError(amount(",999999999999999"), ParseError.TooManyFractionDigits)
    }

    // --- Ставка ---

    @Test
    fun rateBoundaries() {
        assertEquals(ParsedInput.Valid(BigDecimal("22")), rate("22"))
        assertEquals(ParsedInput.Valid(BigDecimal("0")), rate("0"))
        assertEquals(ParsedInput.Valid(BigDecimal("7.5")), rate("7.5"))
        assertEquals(ParsedInput.Valid(BigDecimal("99.9999")), rate("99,9999"))
    }

    @Test
    fun rateOf100IsRangeErrorNotCorrected() {
        assertInvalidError(rate("100"), ParseError.OutOfRange)
        assertInvalidError(rate("100,0"), ParseError.OutOfRange)
        assertInvalidError(rate("100,0000"), ParseError.OutOfRange)
    }

    @Test
    fun ratePrecisionIsFourDigits() {
        assertInvalidError(rate("99,99999"), ParseError.TooManyFractionDigits)
        assertInvalidError(rate("7,50000"), ParseError.TooManyFractionDigits)
    }

    @Test
    fun negativeRateIsRejected() {
        assertInvalidError(rate("-1"), ParseError.WrongFormat)
    }

    // --- Завершение промежуточного ввода ---

    @Test
    fun finalizeCompletesIntermediateInput() {
        assertEquals(ParsedInput.Valid(BigDecimal("12")), AmountParser.AMOUNT.finalize("12,"))
        assertEquals(ParsedInput.Valid(BigDecimal("0.5")), AmountParser.AMOUNT.finalize(",5"))
    }

    @Test
    fun finalizeKeepsOtherStates() {
        assertEquals(ParsedInput.Valid(BigDecimal("100")), AmountParser.AMOUNT.finalize("100"))
        assertEquals(ParsedInput.Empty, AmountParser.AMOUNT.finalize(""))
        assertInvalidError(AmountParser.AMOUNT.finalize("abc"), ParseError.WrongFormat)
        assertInvalidError(AmountParser.RATE.finalize("100"), ParseError.OutOfRange)
    }
}
