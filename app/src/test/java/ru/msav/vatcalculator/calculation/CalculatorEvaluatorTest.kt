package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEvaluatorTest {

    private fun evaluate(amount: String, rate: String, mode: VatMode = VatMode.EXCLUSIVE) =
        CalculatorEvaluator.evaluate(amount, rate, mode)

    // --- Пустота отличается от нуля ---

    @Test
    fun emptyAmountShowsNoResultInsteadOfZero() {
        val evaluation = evaluate("", "22")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun emptyRateShowsNoResult() {
        val evaluation = evaluate("100", "")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun everythingEmptyIsEmpty() {
        val evaluation = evaluate("", "")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
    }

    @Test
    fun explicitZeroGivesValidZeroResult() {
        val evaluation = evaluate("0", "22")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("0.00", ready.result.total.toPlainString())
        assertTrue(evaluation.isShareReady)
    }

    // --- Корректный ввод ---

    @Test
    fun validInputProducesReadyResult() {
        val evaluation = evaluate("100", "22")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("122.00", ready.result.total.toPlainString())
        assertEquals(VatMode.EXCLUSIVE, ready.input.mode)
        assertTrue(evaluation.isCommitted)
        assertTrue(evaluation.isShareReady)
    }

    @Test
    fun inclusiveModeIsPassedThrough() {
        val ready = evaluate("122", "22", VatMode.INCLUSIVE).outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("100.00", ready.result.base.toPlainString())
    }

    // --- Промежуточный ввод завершается по контракту ---

    @Test
    fun trailingSeparatorIsRecalculatedByCompletableValue() {
        val evaluation = evaluate("12,", "22")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("12", ready.input.amount.toPlainString())
        assertEquals("14.64", ready.result.total.toPlainString())
        assertFalse(evaluation.isCommitted)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun leadingSeparatorInRateIsRecalculatedButNotCommitted() {
        val evaluation = evaluate("100", ",5")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("0.5", ready.input.ratePercent.toPlainString())
        assertEquals("100.50", ready.result.total.toPlainString())
        assertFalse(evaluation.isShareReady)
    }

    // --- Ошибочный ввод не даёт актуального результата ---

    @Test
    fun invalidAmountYieldsFieldError() {
        val evaluation = evaluate("100,005", "22")
        val error = evaluation.outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.AMOUNT, error.field)
        assertEquals(ParseError.TooManyFractionDigits, error.error)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun outOfRangeAmountYieldsFieldError() {
        val evaluation = evaluate("1 000 000 000 000,00", "22")
        val error = evaluation.outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.AMOUNT, error.field)
        assertEquals(ParseError.OutOfRange, error.error)
    }

    @Test
    fun rateOf100YieldsRateError() {
        val evaluation = evaluate("100", "100")
        val error = evaluation.outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.RATE, error.field)
        assertEquals(ParseError.OutOfRange, error.error)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun amountErrorHasPriorityOverRateError() {
        val evaluation = evaluate("abc", "100")
        val error = evaluation.outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.AMOUNT, error.field)
    }

    @Test
    fun fieldStatesAreExposedForPerFieldMessages() {
        val evaluation = evaluate("12,", "100")
        assertTrue(evaluation.amountState is ParsedInput.Intermediate)
        assertTrue(evaluation.rateState is ParsedInput.Invalid)
    }

    // --- Максимальный расчёт ---

    @Test
    fun maximumAmountWithMaximumRateIsReady() {
        val evaluation = evaluate("999 999 999 999,99", "99,9999")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("1999998999999.98", ready.result.total.toPlainString())
        assertTrue(evaluation.isShareReady)
    }
}
