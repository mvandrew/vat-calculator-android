package ru.msav.vatcalculator.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
