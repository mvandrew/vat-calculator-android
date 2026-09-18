package ru.msav.vatcalculator.calculation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Неизменяемые исходные данные расчёта, общие для формы, журнала и отправки.
 * Содержит точные числа, а не пользовательский текст, и не зависит от хранилища.
 */
data class CalculationInput(
    val amount: BigDecimal,
    val ratePercent: BigDecimal,
    val mode: VatMode,
)

/**
 * Итоги расчёта в показанной разрядности: всегда два дробных знака и
 * точное равенство [base] + [vat] = [total].
 */
data class VatResult(
    val base: BigDecimal,
    val vat: BigDecimal,
    val total: BigDecimal,
)

/**
 * Десятичные формулы начисления и выделения НДС по calculation.md §5.2.
 *
 * Начислить: `B = S`; `T = R2(S × (1 + r / 100))`; `V = T − B`.
 * Выделить: `T = S`; `B = R2(S / (1 + r / 100))`; `V = T − B`.
 *
 * Деление выполняется сразу до двух знаков HALF_UP без предварительного
 * округления коэффициента; налог получается разностью итогов. Входные значения
 * считаются уже проверенными [AmountParser] (сумма — два знака, ставка —
 * четыре, диапазоны соблюдены).
 */
object VatCalculator {

    private val HUNDRED = BigDecimal(100)
    private val SCALE = 2

    fun calculate(input: CalculationInput): VatResult {
        require(input.amount.signum() >= 0) { "amount must be non-negative" }
        require(input.ratePercent.signum() >= 0) { "rate must be non-negative" }

        // 1 + r/100 = (100 + r)/100 — точная величина без деления.
        val hundredPlusRate = HUNDRED.add(input.ratePercent)
        if (input.mode == VatMode.EXCLUSIVE) {
            val base = input.amount.setScale(SCALE)
            val total = input.amount
                .multiply(hundredPlusRate)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
            return VatResult(base = base, vat = total.subtract(base), total = total)
        }
        val total = input.amount.setScale(SCALE)
        val base = input.amount
            .multiply(HUNDRED)
            .divide(hundredPlusRate, SCALE, RoundingMode.HALF_UP)
        return VatResult(base = base, vat = total.subtract(base), total = total)
    }

    /**
     * Обратный расчёт от базы: `T = R2(B × (1 + r / 100))`; `V = T − B`.
     * Совпадает с начислением от суммы, равной базе (calculation.md §5.2).
     */
    fun fromBase(base: BigDecimal, ratePercent: BigDecimal): VatResult =
        calculate(CalculationInput(base, ratePercent, VatMode.EXCLUSIVE))

    /**
     * Обратный расчёт от итога: `B = R2(T × 100 / (100 + r))`; `V = T − B`.
     * Совпадает с выделением от суммы, равной итогу (calculation.md §5.2).
     */
    fun fromTotal(total: BigDecimal, ratePercent: BigDecimal): VatResult =
        calculate(CalculationInput(total, ratePercent, VatMode.INCLUSIVE))

    /**
     * Обратный расчёт от налога: `B = R2(V × 100 / r)`; `T = B + V`.
     * Отредактированное значение не округляется повторно; итог получается
     * суммой, инвариант `B + V = T` сохраняется. Требует `r > 0`: при нулевой
     * ставке ненулевой налог невозможен, эта проверка лежит на вызывающем.
     */
    fun fromVat(vat: BigDecimal, ratePercent: BigDecimal): VatResult {
        require(ratePercent.signum() > 0) { "rate must be positive" }
        val base = vat
            .multiply(HUNDRED)
            .divide(ratePercent, SCALE, RoundingMode.HALF_UP)
        val tax = vat.setScale(SCALE)
        return VatResult(base = base, vat = tax, total = base.add(tax))
    }
}
