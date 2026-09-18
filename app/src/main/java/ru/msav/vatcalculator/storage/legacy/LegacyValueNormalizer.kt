package ru.msav.vatcalculator.storage.legacy

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Нормализация старых значений (storage.md §6): сумма до двух знаков, ставка
 * до четырёх по HALF_UP, с проверкой диапазонов и фиксацией округления.
 * Вход — BigDecimal, построенный из краткого десятичного представления
 * `Float.toString` (preferences) или `BigDecimal.valueOf(double)` (SQLite REAL);
 * двоичный хвост не разворачивается через лишние преобразования.
 */
object LegacyValueNormalizer {

    private val AMOUNT_MAX = BigDecimal("999999999999.99")
    private val HUNDRED = BigDecimal(100)

    sealed interface Result {
        /** Значение перенесено; [rounded] = true, если нормализация изменила число. */
        data class Valid(val value: BigDecimal, val rounded: Boolean) : Result

        /** Поле не переносится: вне диапазона, не число или ставка нормализовалась в 100%. */
        data object Invalid : Result
    }

    fun normalizeAmount(source: BigDecimal): Result = normalize(
        source = source,
        scale = 2,
        upperBound = AMOUNT_MAX,
        upperBoundInclusive = true,
    )

    fun normalizeRate(source: BigDecimal): Result = normalize(
        source = source,
        scale = 4,
        upperBound = HUNDRED,
        upperBoundInclusive = false,
    )

    private fun normalize(
        source: BigDecimal,
        scale: Int,
        upperBound: BigDecimal,
        upperBoundInclusive: Boolean,
    ): Result {
        if (source.signum() < 0) return Result.Invalid
        val normalized = source.setScale(scale, RoundingMode.HALF_UP)
        val rounded = normalized.compareTo(source) != 0
        val withinRange = if (upperBoundInclusive) {
            normalized <= upperBound
        } else {
            normalized < upperBound
        }
        if (!withinRange) return Result.Invalid
        return Result.Valid(normalized, rounded)
    }

    /** Краткое десятичное представление float без преобразования через double. */
    fun fromFloatString(floatString: String): BigDecimal? = runCatching { BigDecimal(floatString) }
        .getOrNull()

    /** Представление double для SQLite REAL: `BigDecimal.valueOf`. */
    fun fromDouble(value: Double): BigDecimal = BigDecimal.valueOf(value)
}
