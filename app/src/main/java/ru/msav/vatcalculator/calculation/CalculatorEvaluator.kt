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
 * Источник обратного расчёта: поле итога, отредактированное пользователем.
 * Ставка всегда берётся из поля ставки и не пересчитывается.
 */
enum class ResultField {
    BASE,
    VAT,
    TOTAL,
}

/**
 * Результат обратной оценки формы калькулятора: пересчёт от отредактированного
 * итога по выбранной ставке. Ошибки редактируемого поля передаются через
 * [resultError] (разбор, диапазон производной суммы или невозможный налог при
 * нулевой ставке); [outcome] при этом не содержит актуального результата.
 */
data class ReverseEvaluation(
    val rateState: ParsedInput,
    val resultState: ParsedInput,
    val resultError: ParseError?,
    val outcome: CalculatorEvaluation.Outcome,
    /** true, когда ставка и итог завершены (нет промежуточных форм `12,`/`,5`). */
    val isCommitted: Boolean,
)

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

    /**
     * Обратная оценка: все суммы пересчитываются от отредактированного итога
     * [source] по ставке из поля ставки (calculation.md §5.2). Режим задаёт
     * только поле, с которым синхронизируется сумма: при начислении это база,
     * при выделении — итог; сам пересчёт от режима не зависит.
     *
     * Особый случай — налог при нулевой ставке: ненулевой `V` невозможен
     * (ошибка [ParseError.VatAtZeroRate]), нулевой не определяет базу, и расчёт
     * выполняется прямо от текущей суммы. Производная сумма не может превышать
     * предел поля ввода — иначе запись и отправка состояния были бы невозможны.
     */
    fun evaluateResult(
        source: ResultField,
        resultText: String,
        rateText: String,
        mode: VatMode,
        amountText: String,
    ): ReverseEvaluation {
        val rateState = AmountParser.RATE.parse(rateText)
        val resultState = AmountParser.RESULT.parse(resultText)
        val isCommitted = rateState is ParsedInput.Valid && resultState is ParsedInput.Valid

        var resultError: ParseError? = null
        val outcome: CalculatorEvaluation.Outcome = when {
            rateState is ParsedInput.Invalid ->
                CalculatorEvaluation.Outcome.Error(CalculatorEvaluation.Field.RATE, rateState.error)

            resultState is ParsedInput.Invalid -> {
                resultError = resultState.error
                CalculatorEvaluation.Outcome.Empty
            }

            rateState is ParsedInput.Empty || resultState is ParsedInput.Empty ->
                CalculatorEvaluation.Outcome.Empty

            else -> {
                val rate = rateState.valueOrThrow()
                val value = resultState.valueOrThrow()
                if (source == ResultField.VAT && rate.signum() == 0) {
                    if (value.signum() != 0) {
                        resultError = ParseError.VatAtZeroRate
                        CalculatorEvaluation.Outcome.Empty
                    } else {
                        amountForwardOutcome(rate, mode, amountText)
                    }
                } else {
                    val result = when (source) {
                        ResultField.BASE -> VatCalculator.fromBase(value, rate)
                        ResultField.VAT -> VatCalculator.fromVat(value, rate)
                        ResultField.TOTAL -> VatCalculator.fromTotal(value, rate)
                    }
                    val amount = if (mode == VatMode.EXCLUSIVE) result.base else result.total
                    if (!AmountParser.AMOUNT.isWithinBound(amount)) {
                        resultError = ParseError.OutOfRange
                        CalculatorEvaluation.Outcome.Empty
                    } else {
                        CalculatorEvaluation.Outcome.Ready(
                            CalculationInput(amount, rate, mode),
                            result,
                        )
                    }
                }
            }
        }
        return ReverseEvaluation(rateState, resultState, resultError, outcome, isCommitted)
    }

    /** Нулевой налог при нулевой ставке не определяет базу: прямой расчёт от суммы. */
    private fun amountForwardOutcome(
        rate: BigDecimal,
        mode: VatMode,
        amountText: String,
    ): CalculatorEvaluation.Outcome {
        val amountState = AmountParser.AMOUNT.finalize(amountText)
        if (amountState !is ParsedInput.Valid) {
            return CalculatorEvaluation.Outcome.Empty
        }
        val input = CalculationInput(amountState.value, rate, mode)
        return CalculatorEvaluation.Outcome.Ready(input, VatCalculator.calculate(input))
    }

    private fun ParsedInput.valueOrThrow(): BigDecimal = when (this) {
        is ParsedInput.Valid -> value
        is ParsedInput.Intermediate -> value
        else -> error("value is only available for Valid and Intermediate inputs")
    }
}
