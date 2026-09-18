package ru.msav.vatcalculator.storage.legacy

import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.LegacyPreferencesSource
import ru.msav.vatcalculator.storage.PrefsMigrationReport
import java.math.BigDecimal

/**
 * Перенос старых параметров формы (storage.md §6) — чистая логика от карт
 * значений, тестируемая на JVM без Android.
 *
 * При наличии хотя бы одного ключа схемы 2.2 выбирается эта схема целиком;
 * иначе пробуется 1.5; поля двух поколений не смешиваются. Отсутствующее
 * поле неполной схемы даёт стартовое значение; неверный тип или значение
 * вне диапазона оставляют поле пустым с сообщением; ставка, нормализация
 * которой дала 100%, недопустима и требует исправления.
 */
object LegacyPreferencesMigrator {

    /** Перенесённые параметры формы: значения уже нормализованы. */
    data class Outcome(
        val report: PrefsMigrationReport,
        val amountText: String,
        val rateText: String,
        val mode: VatMode,
    )

    fun migrate(values22: Map<String, Any?>, values15: Map<String, Any?>): Outcome {
        val has22Key = KEY_22_ALL.any(values22::containsKey)
        return when {
            has22Key -> migrateVersion22(values22)
            values15.isNotEmpty() -> migrateVersion15(values15)
            else -> startOutcome(LegacyPreferencesSource.NONE)
        }
    }

    private fun migrateVersion22(values: Map<String, Any?>): Outcome {
        val amount = floatField(
            values,
            KEY_22_SUM,
            LegacyValueNormalizer::normalizeAmount,
            render = { it.toPlainString() },
        )
        val rate = floatField(
            values,
            KEY_22_RATE,
            LegacyValueNormalizer::normalizeRate,
            render = { it.stripTrailingZeros().toPlainString() },
        )
        val mode = when (val stored = values[KEY_22_TYPE]) {
            is Int -> intToMode(stored)
            is Long -> intToMode(stored.toInt())
            else -> FieldValue(VatMode.INCLUSIVE, invalid = stored != null)
        }
        return buildOutcome(LegacyPreferencesSource.VERSION_2_2, amount, rate, mode)
    }

    private fun migrateVersion15(values: Map<String, Any?>): Outcome {
        val amount = floatField(
            values,
            KEY_15_SUM,
            LegacyValueNormalizer::normalizeAmount,
            render = { it.toPlainString() },
        )
        val rate = floatField(
            values,
            KEY_15_RATE,
            LegacyValueNormalizer::normalizeRate,
            render = { it.stripTrailingZeros().toPlainString() },
        )
        val mode = when (val stored = values[KEY_15_FLAG]) {
            is Boolean -> FieldValue(if (stored) VatMode.INCLUSIVE else VatMode.EXCLUSIVE, invalid = false)
            else -> FieldValue(VatMode.INCLUSIVE, invalid = stored != null)
        }
        return buildOutcome(LegacyPreferencesSource.VERSION_1_5, amount, rate, mode)
    }

    private fun intToMode(stored: Int): FieldValue<VatMode> = when (stored) {
        0 -> FieldValue(VatMode.INCLUSIVE, invalid = false)
        1 -> FieldValue(VatMode.EXCLUSIVE, invalid = false)
        else -> FieldValue(VatMode.INCLUSIVE, invalid = true)
    }

    /** Float читается по фактическому типу; другой тип или отсутствие — поле не переносится. */
    private fun floatField(
        values: Map<String, Any?>,
        key: String,
        normalize: (BigDecimal) -> LegacyValueNormalizer.Result,
        render: (BigDecimal) -> String,
    ): FieldValue<String> {
        val stored = values[key] ?: return FieldValue(null, invalid = false)
        if (stored !is Float) return FieldValue(null, invalid = true)
        val parsed = LegacyValueNormalizer.fromFloatString(stored.toString())
            ?: return FieldValue(null, invalid = true)
        return when (val normalized = normalize(parsed)) {
            is LegacyValueNormalizer.Result.Valid ->
                FieldValue(render(normalized.value), invalid = false, rounded = normalized.rounded)

            LegacyValueNormalizer.Result.Invalid -> FieldValue(null, invalid = true)
        }
    }

    private fun buildOutcome(
        source: LegacyPreferencesSource,
        amount: FieldValue<String>,
        rate: FieldValue<String>,
        mode: FieldValue<VatMode>,
    ): Outcome {
        val startAmount = ""
        val startRate = "22"
        return Outcome(
            report = PrefsMigrationReport(
                source = source,
                amountRounded = amount.rounded,
                rateRounded = rate.rounded,
                amountInvalid = amount.invalid,
                rateInvalid = rate.invalid,
                modeInvalid = mode.invalid,
            ),
            // Неверное поле остаётся пустым (требуется ввод); отсутствующее — стартовое значение.
            amountText = when {
                amount.invalid -> ""
                amount.value != null -> amount.value
                else -> startAmount
            },
            rateText = when {
                rate.invalid -> ""
                rate.value != null -> rate.value
                else -> startRate
            },
            mode = mode.value ?: VatMode.INCLUSIVE,
        )
    }

    private fun startOutcome(source: LegacyPreferencesSource): Outcome =
        Outcome(
            report = PrefsMigrationReport(source = source),
            amountText = "",
            rateText = "22",
            mode = VatMode.INCLUSIVE,
        )

    private data class FieldValue<T>(val value: T?, val invalid: Boolean, val rounded: Boolean = false)

    private const val PREFIX_22 = "ru.msav.ruvattaxcalculator.settings."
    private const val KEY_22_SUM = "${PREFIX_22}SumAmount"
    private const val KEY_22_RATE = "${PREFIX_22}VatRate"
    private const val KEY_22_TYPE = "${PREFIX_22}Type"
    private val KEY_22_ALL = listOf(KEY_22_SUM, KEY_22_RATE, KEY_22_TYPE)

    private const val PREFIX_15 = "ru.msav.passwordgenerator."
    private const val KEY_15_SUM = "${PREFIX_15}sourceSum"
    private const val KEY_15_RATE = "${PREFIX_15}vatPercent"
    private const val KEY_15_FLAG = "${PREFIX_15}allocateSumFlag"
}
