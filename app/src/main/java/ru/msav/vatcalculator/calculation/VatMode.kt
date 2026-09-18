package ru.msav.vatcalculator.calculation

/**
 * Режим расчёта НДС.
 *
 * EXCLUSIVE — начислить: введённая сумма трактуется как база без НДС.
 * INCLUSIVE — выделить: введённая сумма трактуется как сумма с НДС.
 *
 * Именование соответствует терминам старой версии (Type 0 = INCLUSIVE, 1 = EXCLUSIVE)
 * и используется контрактом хранения фазы 04; экранные подписи находятся вне ядра.
 */
enum class VatMode {
    EXCLUSIVE,
    INCLUSIVE,
}
