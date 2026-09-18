package ru.msav.vatcalculator.storage

import ru.msav.vatcalculator.calculation.VatMode
import java.math.BigDecimal

/**
 * Запись журнала расчётов (storage.md §6). Устойчивый ID, исходная сумма и
 * ставка в точном десятичном виде и режим; итоги вычисляются единым ядром и
 * не сохраняются. [legacyId] хранит соответствие старому `_id` SQLite 2.2
 * с уникальностью по источнику и ID; у новых записей — null.
 */
data class HistoryEntry(
    val id: Long,
    val amount: BigDecimal,
    val rate: BigDecimal,
    val mode: VatMode,
    val legacyId: Long? = null,
) {
    init {
        require(amount.signum() >= 0 && amount.scale() <= 2 && amount <= AMOUNT_MAX) { "invalid amount $amount" }
        require(rate.signum() >= 0 && rate.scale() <= 4 && rate < HUNDRED) { "invalid rate $rate" }
    }

    companion object {
        private val AMOUNT_MAX = BigDecimal("999999999999.99")
        private val HUNDRED = BigDecimal(100)
    }
}
