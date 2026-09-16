package ru.msav.vatcalculator.storage

/**
 * Результаты однократного импорта старых данных (storage.md §6). Служат
 * для однократного сообщения пользователю в UI-фазах; персональные значения
 * в логи не записываются.
 */

/** Источник импортированных параметров формы. */
enum class LegacyPreferencesSource {
    /** Схема 2.2: `ru.msav.ruvattaxcalculator.settings` (float/float/int). */
    VERSION_2_2,

    /** Схема 1.5: `ru.msav.passwordgenerator.settings` (float/float/boolean). */
    VERSION_1_5,

    /** Старые preferences отсутствуют — начальное состояние калькулятора. */
    NONE,
}

/**
 * Отчёт переноса параметров формы. Округление фиксируется при изменении
 * числового значения нормализацией; недопустимое поле (вне диапазона,
 * неверный тип, ставка нормализовалась в 100%) остаётся пустым и требует
 * ввода; некорректный режим требует явного выбора.
 */
data class PrefsMigrationReport(
    val source: LegacyPreferencesSource,
    val amountRounded: Boolean = false,
    val rateRounded: Boolean = false,
    val amountInvalid: Boolean = false,
    val rateInvalid: Boolean = false,
    val modeInvalid: Boolean = false,
) {
    val hasNotices: Boolean
        get() = amountRounded || rateRounded || amountInvalid || rateInvalid || modeInvalid
}

/** Причина, по которой старая запись журнала не перенесена. */
enum class SkippedReason {
    /** NULL в sum_amount или vat_rate. */
    NULL_VALUE,

    /** Неожиданный тип колонки (текст вместо REAL и т.п.). */
    WRONG_TYPE,

    /** Значение вне допустимого диапазона. */
    OUT_OF_RANGE,

    /** Неизвестный код режима (type не 0 и не 1). */
    UNKNOWN_MODE,
}

/** Неперенесённая старая запись: исходный `_id` и причина. */
data class SkippedLegacyRow(val legacyId: Long, val reason: SkippedReason)

/**
 * Отчёт импорта журнала 2.2. [completed] = транзакционно зафиксированное
 * завершение; при `false` перенос можно повторить — уже импортированные
 * `legacyId` повтор не дублирует.
 */
data class JournalMigrationReport(
    val importedCount: Int,
    val roundedCount: Int,
    val skipped: List<SkippedLegacyRow>,
    val completed: Boolean,
) {
    companion object {
        val NOT_STARTED = JournalMigrationReport(0, 0, emptyList(), completed = false)
    }
}
