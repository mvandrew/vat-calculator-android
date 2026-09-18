package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorEvaluatorTest {

    private fun evaluate(amount: String, rate: String, mode: VatMode = VatMode.EXCLUSIVE) =
        CalculatorEvaluator.evaluate(amount, rate, mode)

    @Test
    fun emptyInputDiffersFromExplicitZero() {
        assertTrue(evaluate("", "22").outcome is CalculatorEvaluation.Outcome.Empty)
        assertTrue(evaluate("100", "").outcome is CalculatorEvaluation.Outcome.Empty)
        val zero = evaluate("0", "22")
        assertEquals("0.00", (zero.outcome as CalculatorEvaluation.Outcome.Ready).result.total.toPlainString())
        assertTrue(zero.isShareReady)
    }

    @Test
    fun validInputProducesResultsInBothModes() {
        val add = evaluate("100", "22").outcome as CalculatorEvaluation.Outcome.Ready
        val extract = evaluate("122", "22", VatMode.INCLUSIVE).outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("122.00", add.result.total.toPlainString())
        assertEquals("100.00", extract.result.base.toPlainString())
    }

    @Test
    fun intermediateInputCalculatesButCannotBeShared() {
        val evaluation = evaluate("12,", "22")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("14.64", ready.result.total.toPlainString())
        assertFalse(evaluation.isCommitted)
        assertFalse(evaluation.isShareReady)
    }

    @Test
    fun fieldErrorsBlockResultsAndAmountHasPriority() {
        val amountError = evaluate("100,005", "100").outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.AMOUNT, amountError.field)
        assertEquals(ParseError.TooManyFractionDigits, amountError.error)
        val rateError = evaluate("100", "100").outcome as CalculatorEvaluation.Outcome.Error
        assertEquals(CalculatorEvaluation.Field.RATE, rateError.field)
        assertEquals(ParseError.OutOfRange, rateError.error)
    }

    @Test
    fun maximumValuesFlowThroughParserAndCalculator() {
        val evaluation = evaluate("999 999 999 999,99", "99,9999")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("1999998999999.98", ready.result.total.toPlainString())
        assertTrue(evaluation.isShareReady)
    }

    private fun evaluateResult(
        source: ResultField,
        result: String,
        rate: String,
        mode: VatMode = VatMode.EXCLUSIVE,
        amount: String = "0",
    ) = CalculatorEvaluator.evaluateResult(source, result, rate, mode, amount)

    @Test
    fun reverseFromBaseSyncsAmountToBaseInExclusiveMode() {
        val evaluation = evaluateResult(ResultField.BASE, "100", "22")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("100.00", ready.result.base.toPlainString())
        assertEquals("22.00", ready.result.vat.toPlainString())
        assertEquals("122.00", ready.result.total.toPlainString())
        assertEquals(0, BigDecimal("100").compareTo(ready.input.amount))
        assertTrue(evaluation.isCommitted)
    }

    @Test
    fun reverseSyncsAmountByMode() {
        val exclusive = evaluateResult(ResultField.VAT, "22", "22", VatMode.EXCLUSIVE).outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals(0, BigDecimal("100").compareTo(exclusive.input.amount))
        val inclusive = evaluateResult(ResultField.VAT, "22", "22", VatMode.INCLUSIVE).outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals(0, BigDecimal("122").compareTo(inclusive.input.amount))
    }

    @Test
    fun reverseIntermediateCalculatesButIsNotCommitted() {
        val evaluation = evaluateResult(ResultField.TOTAL, "12,", "22", VatMode.INCLUSIVE)
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals(0, BigDecimal("12").compareTo(ready.input.amount))
        assertEquals("9.84", ready.result.base.toPlainString())
        assertFalse(evaluation.isCommitted)
    }

    @Test
    fun reverseEmptyResultShowsNoResult() {
        val evaluation = evaluateResult(ResultField.VAT, "", "22")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
        assertNull(evaluation.resultError)
    }

    @Test
    fun reverseInvalidResultReportsErrorWithoutResult() {
        val wrongFormat = evaluateResult(ResultField.VAT, "1,234.56", "22")
        assertTrue(wrongFormat.outcome is CalculatorEvaluation.Outcome.Empty)
        assertEquals(ParseError.WrongFormat, wrongFormat.resultError)
        val tooPrecise = evaluateResult(ResultField.BASE, "1.234", "22")
        assertEquals(ParseError.TooManyFractionDigits, tooPrecise.resultError)
    }

    @Test
    fun reverseNonZeroVatAtZeroRateIsImpossible() {
        val evaluation = evaluateResult(ResultField.VAT, "1", "0")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
        assertEquals(ParseError.VatAtZeroRate, evaluation.resultError)
    }

    @Test
    fun reverseZeroVatAtZeroRateFallsBackToAmount() {
        val evaluation = evaluateResult(ResultField.VAT, "0", "0", amount = "100")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("100.00", ready.result.base.toPlainString())
        assertEquals("0.00", ready.result.vat.toPlainString())
        assertEquals("100.00", ready.result.total.toPlainString())
        assertTrue(evaluation.isCommitted)
    }

    @Test
    fun reverseZeroVatAtZeroRateWithoutAmountIsEmpty() {
        val evaluation = evaluateResult(ResultField.VAT, "0", "0", amount = "")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
    }

    @Test
    fun reverseDerivedAmountBeyondInputRangeIsRejected() {
        // Итог 1 500 000 000 000,00 при 50% даёт базу 1 000 000 000 000,00 —
        // за пределом поля ввода суммы, сохранение такого состояния невозможно.
        val evaluation = evaluateResult(ResultField.TOTAL, "1500000000000", "50")
        assertTrue(evaluation.outcome is CalculatorEvaluation.Outcome.Empty)
        assertEquals(ParseError.OutOfRange, evaluation.resultError)
    }

    @Test
    fun reverseMaximumDisplayedTotalIsAccepted() {
        val evaluation = evaluateResult(ResultField.TOTAL, "1999998999998.98", "99.9999")
        val ready = evaluation.outcome as CalculatorEvaluation.Outcome.Ready
        assertEquals("999999999999.49", ready.result.base.toPlainString())
        assertTrue(evaluation.isCommitted)
    }
}
