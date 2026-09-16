package ru.msav.vatcalculator.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorInputFilterTest {

    @Test
    fun digitsAndSingleSeparatorAreAccepted() {
        assertTrue(CalculatorInputFilter.isAcceptable("100"))
        assertTrue(CalculatorInputFilter.isAcceptable("100,25"))
        assertTrue(CalculatorInputFilter.isAcceptable("100.25"))
        assertTrue(CalculatorInputFilter.isAcceptable("12,"))
        assertTrue(CalculatorInputFilter.isAcceptable(",5"))
        assertTrue(CalculatorInputFilter.isAcceptable(""))
    }

    @Test
    fun groupingSpacesAreAccepted() {
        assertTrue(CalculatorInputFilter.isAcceptable("1 234,56"))
        assertTrue(CalculatorInputFilter.isAcceptable("1\u00A0234,56"))
        assertTrue(CalculatorInputFilter.isAcceptable("1\u202F234,56"))
    }

    @Test
    fun repeatedOrMixedSeparatorsAreRejectedEntirely() {
        assertFalse(CalculatorInputFilter.isAcceptable("1,234.56"))
        assertFalse(CalculatorInputFilter.isAcceptable("1.234,56"))
        assertFalse(CalculatorInputFilter.isAcceptable("1..2"))
        assertFalse(CalculatorInputFilter.isAcceptable("1,,2"))
        assertFalse(CalculatorInputFilter.isAcceptable("1.2,3"))
    }

    @Test
    fun invalidCharactersAreRejected() {
        assertFalse(CalculatorInputFilter.isAcceptable("-5"))
        assertFalse(CalculatorInputFilter.isAcceptable("1e5"))
        assertFalse(CalculatorInputFilter.isAcceptable("abc"))
        assertFalse(CalculatorInputFilter.isAcceptable("1+1"))
        assertFalse(CalculatorInputFilter.isAcceptable("100₽"))
        assertFalse(CalculatorInputFilter.isAcceptable("1\t2"))
    }

    @Test
    fun lengthLimitRejectsWholeChange() {
        assertTrue(CalculatorInputFilter.isAcceptable("9".repeat(64)))
        assertFalse(CalculatorInputFilter.isAcceptable("9".repeat(65)))
    }
}
