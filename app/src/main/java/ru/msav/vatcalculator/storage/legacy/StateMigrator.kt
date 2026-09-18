package ru.msav.vatcalculator.storage.legacy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.msav.vatcalculator.storage.AppState
import ru.msav.vatcalculator.storage.AppStateStore
import ru.msav.vatcalculator.storage.HistoryStore
import ru.msav.vatcalculator.storage.JournalMigrationReport
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.LegacyJournalRow
import ru.msav.vatcalculator.storage.SkippedLegacyRow
import ru.msav.vatcalculator.storage.ThemeSetting

/**
 * Однократный перенос данных старой версии (storage.md §6).
 *
 * Параметры формы импортируются при первом запуске, когда нового состояния
 * ещё нет; существующее новое состояние и пользовательские правки имеют
 * приоритет и повторной миграцией не подменяются. Импорт журнала выполняется
 * независимо от состояния формы; после транзакционно зафиксированного
 * завершения повторные вызовы ничего не меняют и не восстанавливают
 * удалённые пользователем записи. Старые файлы не удаляются и не меняются.
 */
class StateMigrator(
    context: Context,
    private val appStateStore: AppStateStore,
    private val historyStore: HistoryStore,
) {

    private val appContext = context.applicationContext

    /** Возвращает актуальное состояние формы; при первом запуске переносит старые параметры. */
    suspend fun migrateAppStateOnFirstRun(): AppState {
        appStateStore.load()?.let { existing -> return existing }
        val values22 = withContext(Dispatchers.IO) { LegacyPreferencesStore.readVersion22(appContext) }
        val values15 = withContext(Dispatchers.IO) { LegacyPreferencesStore.readVersion15(appContext) }
        val outcome = LegacyPreferencesMigrator.migrate(values22, values15)
        val initialState = AppState(
            amountText = outcome.amountText,
            rateText = outcome.rateText,
            mode = outcome.mode,
            themeSetting = ThemeSetting.SYSTEM,
            languageSetting = LanguageSetting.SYSTEM,
            openEntryId = null,
        )
        appStateStore.saveMigratedInitial(initialState, outcome.report)
        return initialState
    }

    /**
     * Импорт журнала 2.2. Отсутствие старой БД фиксируется как завершённый
     * пустой перенос; ошибка чтения оставляет миграцию незавершённой —
     * следующий вызов повторит попытку, не создавая дублей.
     */
    suspend fun importLegacyJournal(): JournalMigrationReport {
        historyStore.loadJournalMigrationMark().takeIf { it.completed }?.let { done -> return done }
        return when (val result = withContext(Dispatchers.IO) { LegacyJournalReader(appContext).read() }) {
            LegacyJournalReader.ReadResult.DatabaseMissing ->
                historyStore.importLegacyEntries(emptyList(), emptyList())

            is LegacyJournalReader.ReadResult.Read -> {
                val validRows = result.rows
                    .filter { it.isValid }
                    .map { row ->
                        LegacyJournalRow(
                            legacyId = row.legacyId,
                            amount = requireNotNull(row.amount),
                            rate = requireNotNull(row.rate),
                            mode = requireNotNull(row.mode),
                            rounded = row.rounded,
                        )
                    }
                val skippedRows = result.rows
                    .filterNot { it.isValid }
                    .map { row -> SkippedLegacyRow(row.legacyId, requireNotNull(row.skipReason)) }
                historyStore.importLegacyEntries(validRows, skippedRows)
            }

            is LegacyJournalReader.ReadResult.Failed ->
                JournalMigrationReport(0, 0, emptyList(), completed = false)
        }
    }
}
