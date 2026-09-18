package ru.msav.vatcalculator.storage.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.SkippedReason
import java.io.File
import java.math.BigDecimal

/**
 * Чтение журнала SQLite 2.2 (storage.md §6): файл
 * `databases/ru.msav.ruvattaxcalculator.db`, таблица `calculations`.
 *
 * База открывается только для чтения, без создания и без старого
 * SQLiteOpenHelper; согласованное чтение с учётом WAL выполняет сам
 * SQLite при открытии файла. Исходный SQLite-тип проверяется до числового
 * преобразования; NULL, неизвестный режим и неверные типы не превращаются
 * в ноль и уходят в пропуск с ID и причиной. REAL переводится через
 * краткое десятичное представление double (`BigDecimal.valueOf`).
 * Ошибка чтения не помечается успешной миграцией: старые данные остаются,
 * перенос можно повторить.
 */
class LegacyJournalReader(private val context: Context) {

    sealed interface ReadResult {
        /** Файл старой БД отсутствует — журнала 2.2 на установке нет. */
        data object DatabaseMissing : ReadResult

        /** Прочитано; каждая строка либо валидна, либо помечена причиной пропуска. */
        data class Read(val rows: List<Row>) : ReadResult

        /** Ошибка чтения (повреждение файла и т.п.); миграцию можно повторить позже. */
        data class Failed(val cause: Exception) : ReadResult
    }

    data class Row(
        val legacyId: Long,
        val amount: BigDecimal?,
        val rate: BigDecimal?,
        val mode: VatMode?,
        val rounded: Boolean,
        val skipReason: SkippedReason?,
    ) {
        val isValid: Boolean
            get() = amount != null && rate != null && mode != null && skipReason == null
    }

    fun read(): ReadResult {
        val databaseFile: File = context.applicationContext.getDatabasePath(DATABASE_NAME_2_2)
        if (!databaseFile.exists()) return ReadResult.DatabaseMissing
        return try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db -> ReadResult.Read(readRows(db)) }
        } catch (cause: Exception) {
            ReadResult.Failed(cause)
        }
    }

    private fun readRows(db: SQLiteDatabase): List<Row> {
        val rows = mutableListOf<Row>()
        db.query(
            TABLE_CALCULATIONS,
            arrayOf(COLUMN_ID, COLUMN_SUM, COLUMN_RATE, COLUMN_TYPE),
            null,
            null,
            null,
            null,
            COLUMN_ID, // стабильный порядок по старому ID
        ).use { cursor ->
            while (cursor.moveToNext()) rows.add(readRow(cursor))
        }
        return rows
    }

    private fun readRow(cursor: Cursor): Row {
        val legacyId = when {
            cursor.isNull(0) || cursor.getType(0) != Cursor.FIELD_TYPE_INTEGER -> return skipped(-1L, SkippedReason.WRONG_TYPE)
            else -> cursor.getLong(0)
        }
        val amount = readAmount(cursor, index = 1)
        val rate = readRate(cursor, index = 2)
        val mode = readMode(cursor, index = 3)

        val skipReason = firstSkipReason(amount, rate, mode)
        if (skipReason != null) return skipped(legacyId, skipReason)
        val validAmount = amount as Value
        val validRate = rate as Value
        val validMode = mode as ModeValue
        return Row(
            legacyId = legacyId,
            amount = validAmount.value,
            rate = validRate.value,
            mode = validMode.mode,
            rounded = validAmount.rounded || validRate.rounded,
            skipReason = null,
        )
    }

    private fun firstSkipReason(vararg outcomes: Any): SkippedReason? {
        for (outcome in outcomes) {
            val reason = when (outcome) {
                is DecimalSkipped -> outcome.reason
                is ModeSkipped -> outcome.reason
                else -> null
            }
            if (reason != null) return reason
        }
        return null
    }

    private fun readAmount(cursor: Cursor, index: Int): DecimalOutcome = readDecimal(
        cursor,
        index,
        normalize = LegacyValueNormalizer::normalizeAmount,
    )

    private fun readRate(cursor: Cursor, index: Int): DecimalOutcome = readDecimal(
        cursor,
        index,
        normalize = LegacyValueNormalizer::normalizeRate,
    )

    private fun readDecimal(
        cursor: Cursor,
        index: Int,
        normalize: (BigDecimal) -> LegacyValueNormalizer.Result,
    ): DecimalOutcome = when (val numeric = readNumeric(cursor, index)) {
        is NumericSkipped -> DecimalSkipped(numeric.reason)

        is NumericValue -> when (val normalized = normalize(numeric.value)) {
            is LegacyValueNormalizer.Result.Valid ->
                Value(normalized.value, normalized.rounded)

            LegacyValueNormalizer.Result.Invalid ->
                DecimalSkipped(SkippedReason.OUT_OF_RANGE)
        }
    }

    /** Проверка исходного SQLite-типа до числового преобразования. */
    private fun readNumeric(cursor: Cursor, index: Int): NumericOutcome = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> NumericSkipped(SkippedReason.NULL_VALUE)
        Cursor.FIELD_TYPE_FLOAT -> NumericValue(BigDecimal.valueOf(cursor.getDouble(index)))
        Cursor.FIELD_TYPE_INTEGER -> NumericValue(BigDecimal.valueOf(cursor.getLong(index)))
        else -> NumericSkipped(SkippedReason.WRONG_TYPE)
    }

    private fun readMode(cursor: Cursor, index: Int): ModeOutcome = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> ModeSkipped(SkippedReason.NULL_VALUE)
        Cursor.FIELD_TYPE_INTEGER -> when (cursor.getInt(index)) {
            0 -> ModeValue(VatMode.INCLUSIVE)
            1 -> ModeValue(VatMode.EXCLUSIVE)
            else -> ModeSkipped(SkippedReason.UNKNOWN_MODE)
        }

        else -> ModeSkipped(SkippedReason.WRONG_TYPE)
    }

    private fun skipped(legacyId: Long, reason: SkippedReason): Row =
        Row(legacyId, null, null, null, rounded = false, skipReason = reason)

    private sealed interface DecimalOutcome

    private data class Value(val value: BigDecimal, val rounded: Boolean) : DecimalOutcome

    private data class DecimalSkipped(val reason: SkippedReason) : DecimalOutcome

    private sealed interface ModeOutcome

    private data class ModeValue(val mode: VatMode) : ModeOutcome

    private data class ModeSkipped(val reason: SkippedReason) : ModeOutcome

    private sealed interface NumericOutcome

    private data class NumericValue(val value: BigDecimal) : NumericOutcome

    private data class NumericSkipped(val reason: SkippedReason) : NumericOutcome

    companion object {
        private const val DATABASE_NAME_2_2 = "ru.msav.ruvattaxcalculator.db"
        private const val TABLE_CALCULATIONS = "calculations"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_SUM = "sum_amount"
        private const val COLUMN_RATE = "vat_rate"
        private const val COLUMN_TYPE = "type"
    }
}
