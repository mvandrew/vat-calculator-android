package ru.msav.vatcalculator.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.msav.vatcalculator.calculation.VatMode
import java.math.BigDecimal

/** Валидная строка старого журнала, подготовленная к переносу. */
data class LegacyJournalRow(
    val legacyId: Long,
    val amount: BigDecimal,
    val rate: BigDecimal,
    val mode: VatMode,
    val rounded: Boolean,
)

/**
 * Журнал расчётов новой реализации (storage.md §6): собственная БД
 * `ru.msav.vatcalculator.db` версии 1 — имя не пересекается с базой 2.2.
 *
 * Сумма и ставка хранятся точными десятичными строками (TEXT), режим — int,
 * как в старой схеме: 0 — выделить, 1 — начислить. `legacy_id` уникален и
 * хранит соответствие старому `_id`. Отметка завершения импорта журнала
 * живёт в таблице `meta` и фиксируется в одной транзакции со вставками.
 */
class HistoryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CALCULATIONS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                sum_amount TEXT NOT NULL,
                vat_rate TEXT NOT NULL,
                type INTEGER NOT NULL,
                legacy_id INTEGER UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE $TABLE_META (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Схема версии 1; будущие изменения — согласованные миграции с обновлением версии.
        throw UnsupportedOperationException("upgrade from $oldVersion to $newVersion is not defined")
    }

    /** Создаёт новую запись и возвращает её ID. */
    suspend fun insert(amount: BigDecimal, rate: BigDecimal, mode: VatMode): Long =
        withContext(Dispatchers.IO) {
            writableDatabase.insertWithOnConflict(
                TABLE_CALCULATIONS,
                null,
                values(amount, rate, mode, legacyId = null),
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }

    /** Обновляет запись по ID, не меняя её `legacy_id`; false — записи нет. */
    suspend fun update(id: Long, amount: BigDecimal, rate: BigDecimal, mode: VatMode): Boolean =
        withContext(Dispatchers.IO) {
            writableDatabase.update(
                TABLE_CALCULATIONS,
                values(amount, rate, mode, legacyId = null),
                "${COLUMN_ID} = ?",
                arrayOf(id.toString()),
            ) > 0
        }

    /** Удаляет только выбранную запись; false — записи нет. */
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_CALCULATIONS, "${COLUMN_ID} = ?", arrayOf(id.toString())) > 0
    }

    suspend fun get(id: Long): HistoryEntry? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_CALCULATIONS,
            COLUMNS,
            "${COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) entry(cursor) else null }
    }

    suspend fun findByLegacyId(legacyId: Long): HistoryEntry? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_CALCULATIONS,
            COLUMNS,
            "$COLUMN_LEGACY_ID = ?",
            arrayOf(legacyId.toString()),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) entry(cursor) else null }
    }

    /** Все записи: новые первыми по монотонному ID. */
    suspend fun list(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_CALCULATIONS,
            COLUMNS,
            null,
            null,
            null,
            null,
            "$COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(entry(cursor))
            }
        }
    }

    /**
     * Транзакционный импорт старого журнала: вставка ещё не перенесённых
     * строк и отметка завершения фиксируются одной транзакцией, поэтому
     * повтор после прерывания не создаёт дублей (уникальный `legacy_id`,
     * конфликт игнорируется) и не перезаписывает существующие записи.
     * Возвращает отчёт: перенесено (включая ранее импортированные из этого
     * набора), округлено и пропущено с ID и причинами.
     */
    suspend fun importLegacyEntries(
        validRows: List<LegacyJournalRow>,
        skippedRows: List<SkippedLegacyRow>,
    ): JournalMigrationReport = withContext(Dispatchers.IO) {
        // Завершённый импорт не повторяется: удалённые пользователем записи
        // не восстанавливаются из старой БД.
        loadJournalMigrationMark().takeIf { it.completed }?.let { done -> return@withContext done }
        require(validRows.all { row -> row.amount.signum() >= 0 && row.rate.signum() >= 0 })
        // Вставка по убыванию старого ID: при выдаче «новые первыми» (ORDER BY _id DESC)
        // импортированные записи упорядочиваются по возрастанию старого ID (interface.md §4.4).
        val sortedByLegacyId = validRows.sortedByDescending { it.legacyId }
        val db = writableDatabase
        db.beginTransaction()
        val insertedNow: Int
        val alreadyPresent: Int
        try {
            alreadyPresent = sortedByLegacyId.count { row -> legacyIdExists(db, row.legacyId) }
            insertedNow = sortedByLegacyId.count { row ->
                db.insertWithOnConflict(
                    TABLE_CALCULATIONS,
                    null,
                    values(row.amount, row.rate, row.mode, row.legacyId),
                    SQLiteDatabase.CONFLICT_IGNORE,
                ) != -1L
            }
            for ((key, value) in listOf(
                META_IMPORT_STATE to STATE_DONE,
                META_IMPORTED_COUNT to (alreadyPresent + insertedNow).toString(),
                META_ROUNDED_COUNT to sortedByLegacyId.count { it.rounded }.toString(),
                META_SKIPPED to serializeSkipped(skippedRows),
                META_REPORT_SHOWN to "0",
            )) {
                db.execSQL(
                    "INSERT OR REPLACE INTO $TABLE_META (key, value) VALUES (?, ?)",
                    arrayOf(key, value),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        JournalMigrationReport(
            importedCount = alreadyPresent + insertedNow,
            roundedCount = sortedByLegacyId.count { it.rounded },
            skipped = skippedRows,
            completed = true,
        )
    }

    /**
     * Текущая отметка импорта журнала. [JournalMigrationReport.completed] = true
     * означает: импорт закончен, повторные запуски и удалённые пользователем
     * записи из старой БД не восстанавливаются.
     */
    suspend fun loadJournalMigrationMark(): JournalMigrationReport = withContext(Dispatchers.IO) {
        val state = readMeta(readableDatabase, META_IMPORT_STATE)
        if (state != STATE_DONE) {
            return@withContext JournalMigrationReport.NOT_STARTED
        }
        JournalMigrationReport(
            importedCount = readMeta(readableDatabase, META_IMPORTED_COUNT)?.toIntOrNull() ?: 0,
            roundedCount = readMeta(readableDatabase, META_ROUNDED_COUNT)?.toIntOrNull() ?: 0,
            skipped = deserializeSkipped(readMeta(readableDatabase, META_SKIPPED)),
            completed = true,
        )
    }

    /** Отметка «отчёт импорта журнала показан». */
    suspend fun markJournalReportShown() = withContext(Dispatchers.IO) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO $TABLE_META (key, value) VALUES (?, ?)",
            arrayOf(META_REPORT_SHOWN, "1"),
        )
    }

    suspend fun isJournalReportShown(): Boolean = withContext(Dispatchers.IO) {
        readMeta(readableDatabase, META_REPORT_SHOWN) == "1"
    }

    private fun legacyIdExists(db: SQLiteDatabase, legacyId: Long): Boolean =
        db.query(
            TABLE_CALCULATIONS,
            arrayOf(COLUMN_ID),
            "$COLUMN_LEGACY_ID = ?",
            arrayOf(legacyId.toString()),
            null,
            null,
            null,
            "1",
        ).use { it.moveToFirst() }

    private fun values(amount: BigDecimal, rate: BigDecimal, mode: VatMode, legacyId: Long?): ContentValues =
        ContentValues().apply {
            put(COLUMN_SUM, amount.toPlainString())
            put(COLUMN_RATE, rate.toPlainString())
            put(COLUMN_TYPE, modeToInt(mode))
            legacyId?.let { put(COLUMN_LEGACY_ID, it) }
        }

    private fun entry(cursor: android.database.Cursor): HistoryEntry = HistoryEntry(
        id = cursor.getLong(0),
        amount = BigDecimal(cursor.getString(1)),
        rate = BigDecimal(cursor.getString(2)),
        mode = intToMode(cursor.getInt(3)),
        legacyId = if (cursor.isNull(4)) null else cursor.getLong(4),
    )

    private fun readMeta(db: SQLiteDatabase, key: String): String? =
        db.query(TABLE_META, arrayOf("value"), "key = ?", arrayOf(key), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun serializeSkipped(rows: List<SkippedLegacyRow>): String =
        rows.joinToString(",") { "${it.legacyId}:${it.reason.name}" }

    private fun deserializeSkipped(raw: String?): List<SkippedLegacyRow> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(',')
            .mapNotNull { item ->
                val parts = item.split(':')
                val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val reason = parts.getOrNull(1)
                    ?.let { name -> SkippedReason.entries.firstOrNull { it.name == name } }
                    ?: return@mapNotNull null
                SkippedLegacyRow(id, reason)
            }
    }

    companion object {
        private const val DATABASE_NAME = "ru.msav.vatcalculator.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_CALCULATIONS = "calculations"
        private const val TABLE_META = "meta"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_SUM = "sum_amount"
        private const val COLUMN_RATE = "vat_rate"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_LEGACY_ID = "legacy_id"
        private val COLUMNS = arrayOf(COLUMN_ID, COLUMN_SUM, COLUMN_RATE, COLUMN_TYPE, COLUMN_LEGACY_ID)

        private const val META_IMPORT_STATE = "journal_import_state"
        private const val META_IMPORTED_COUNT = "journal_imported_count"
        private const val META_ROUNDED_COUNT = "journal_rounded_count"
        private const val META_SKIPPED = "journal_skipped"
        private const val META_REPORT_SHOWN = "journal_report_shown"
        private const val STATE_DONE = "done"

        /** Как в старой схеме: 0 — выделить (INCLUSIVE), 1 — начислить (EXCLUSIVE). */
        fun modeToInt(mode: VatMode): Int = if (mode == VatMode.EXCLUSIVE) 1 else 0

        fun intToMode(value: Int): VatMode = if (value == 1) VatMode.EXCLUSIVE else VatMode.INCLUSIVE
    }
}
