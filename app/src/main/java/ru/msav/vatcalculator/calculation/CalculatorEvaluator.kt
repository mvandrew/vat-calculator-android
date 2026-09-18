package ru.msav.vatcalculator.calculation

import java.math.BigDecimal

/**
 * Результат оценки формы калькулятора: состояния обоих полей и агрегированное
 * экранное состояние. Передаёт валидный результат либо ошибку поля; ошибочный
 * или незаполненный ввод не даёт актуального результата.
 */
data class CalculatorEvaluation(
    val amountState: ParsedInput,
    val rateState: ParsedInput,
    val outcome: Outcome,
    /**
     * true, когда оба поля завершены (нет промежуточных форм `12,`/`,5`).
     * «Сохранить» и «Поделиться» доступны только при готовом результате
     * и завершённом вводе; до этого ввод не навязывает ошибку.
     */
    val isCommitted: Boolean,
) {
    /** Доступность «Сохранить»/«Поделиться»: корректный расчёт после завершения ввода. */
    val isShareReady: Boolean
        get() = outcome is Outcome.Ready && isCommitted

    sealed interface Outcome {
        /** Хотя бы одно поле не заполнено: вместо зависимых результатов показываются прочерки. */
        data object Empty : Outcome

        /** Актуальный расчёт; промежуточный ввод пересчитывается по завершаемому значению. */
        data class Ready(val input: CalculationInput, val result: VatResult) : Outcome

        /** Ошибка конкретного поля; прежние результаты не остаются актуальными. */
        data class Error(val field: Field, val error: ParseError) : Outcome
    }

    enum class Field { AMOUNT, RATE }
}

/**
 * Оценка формы: разбор текста полей и расчёт без кнопки «Рассчитать».
 * Один и тот же результат используется экраном, сохранением и отправкой.
 */
object CalculatorEvaluator {

    fun evaluate(amountText: String, rateText: String, mode: VatMode): CalculatorEvaluation {
        val amountState = AmountParser.AMOUNT.parse(amountText)
        val rateState = AmountParser.RATE.parse(rateText)
        val isCommitted = amountState is ParsedInput.Valid && rateState is ParsedInput.Valid

        val outcome = when {
            amountState is ParsedInput.Invalid ->
                CalculatorEvaluation.Outcome.Error(CalculatorEvaluation.Field.AMOUNT, amountState.error)

            rateState is ParsedInput.Invalid ->
                CalculatorEvaluation.Outcome.Error(CalculatorEvaluation.Field.RATE, rateState.error)

            amountState is ParsedInput.Empty || rateState is ParsedInput.Empty ->
                CalculatorEvaluation.Outcome.Empty

            else -> {
                val input = CalculationInput(
                    amount = amountState.valueOrThrow(),
                    ratePercent = rateState.valueOrThrow(),
                    mode = mode,
                )
                CalculatorEvaluation.Outcome.Ready(input, VatCalculator.calculate(input))
            }
        }
        return CalculatorEvaluation(amountState, rateState, outcome, isCommitted)
    }

    private fun ParsedInput.valueOrThrow(): BigDecimal = when (this) {
        is ParsedInput.Valid -> value
        is ParsedInput.Intermediate -> value
        else -> error("value is only available for Valid and Intermediate inputs")
    }
}
