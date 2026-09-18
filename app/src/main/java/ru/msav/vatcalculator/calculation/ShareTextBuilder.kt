package ru.msav.vatcalculator.calculation

/**
 * Формирование текста отправки по interface.md §7 — чистая функция без Android
 * Intent; Sharesheet открывает UI-слой в более поздних фазах.
 *
 * Используются текущие рассчитанные суммы и ставка без отдельного пересчёта.
 * Дробная ставка передаётся полностью без округления до целого процента;
 * символ и код валюты всегда отсутствуют; группировка соответствует экрану.
 */
object ShareTextBuilder {

    fun build(input: CalculationInput, result: VatResult, language: AppLanguage): String {
        val rate = MoneyFormatter.formatRate(input.ratePercent, language) + "%"
        val base = MoneyFormatter.formatMoney(result.base, language)
        val vat = MoneyFormatter.formatMoney(result.vat, language)
        val total = MoneyFormatter.formatMoney(result.total, language)
        return when (language) {
            AppLanguage.RUSSIAN ->
                "Без НДС: $base\nНДС ($rate): $vat\nС НДС: $total"

            AppLanguage.ENGLISH ->
                "Excluding VAT: $base\nVAT ($rate): $vat\nIncluding VAT: $total"
        }
    }
}
