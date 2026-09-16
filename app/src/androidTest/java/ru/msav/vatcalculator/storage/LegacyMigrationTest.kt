package ru.msav.vatcalculator.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.legacy.StateMigrator
import java.io.File

/**
 * Интеграция миграции с реальным Android-хранилищем (storage.md §6):
 * искусственные legacy preferences 1.5/2.2 и SQLite-журнал 2.2, включая
 * повреждённые строки, WAL и повторы. Исходные данные не изменяются.
 */
@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appStateStore = AppStateStore(context)
    private val historyStore = HistoryStore(context)
    private val migrator = StateMigrator(context, appStateStore, historyStore)

    private val legacyDbFile: File
        get() = context.getDatabasePath("ru.msav.ruvattaxcalculator.db")

    @Before
    fun cleanEverything() {
        appStateStoreCleanup()
        context.deleteDatabase("ru.msav.vatcalculator.db")
        removeLegacyDatabase()
        legacyPrefs("ru.msav.ruvattaxcalculator.settings").edit().clear().commit()
        legacyPrefs("ru.msav.passwordgenerator.settings").edit().clear().commit()
    }

    private fun appStateStoreCleanup() {
        context.getSharedPreferences("ru.msav.vatcalculator.state", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun legacyPrefs(name: String) =
        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)

    private fun removeLegacyDatabase() {
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(legacyDbFile.parentFile, legacyDbFile.name + suffix).delete()
        }
    }

    private fun writeLegacy22Prefs(sum: Float?, rate: Float?, type: Int?) {
        val editor = legacyPrefs("ru.msav.ruvattaxcalculator.settings").edit()
        sum?.let { editor.putFloat("ru.msav.ruvattaxcalculator.settings.SumAmount", it) }
        rate?.let { editor.putFloat("ru.msav.ruvattaxcalculator.settings.VatRate", it) }
        type?.let { editor.putInt("ru.msav.ruvattaxcalculator.settings.Type", it) }
        editor.commit()
    }

    private fun writeLegacy15Prefs(sum: Float?, rate: Float?, allocate: Boolean?) {
        val editor = legacyPrefs("ru.msav.passwordgenerator.settings").edit()
        sum?.let { editor.putFloat("ru.msav.passwordgenerator.sourceSum", it) }
        rate?.let { editor.putFloat("ru.msav.passwordgenerator.vatPercent", it) }
        allocate?.let { editor.putBoolean("ru.msav.passwordgenerator.allocateSumFlag", it) }
        editor.commit()
    }

    private fun createLegacyDatabase(inserts: List<String>) {
        legacyDbFile.parentFile?.mkdirs()
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null)
        db.execSQL(
            "CREATE TABLE calculations (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sum_amount REAL, vat_rate REAL, type INTEGER)",
        )
        inserts.forEach(db::execSQL)
        db.close()
    }

    @Test
    fun noLegacyDataGivesStartStateAndEmptyCompletedJournal() = runBlocking {
        val state = migrator.migrateAppStateOnFirstRun()
        assertEquals("", state.amountText)
        assertEquals("22", state.rateText)
        assertEquals(VatMode.INCLUSIVE, state.mode)
        assertEquals(ThemeSetting.SYSTEM, state.themeSetting)
        assertEquals(LanguageSetting.SYSTEM, state.languageSetting)

        val journal = migrator.importLegacyJournal()
        assertTrue(journal.completed)
        assertEquals(0, journal.importedCount)
        assertEquals(0, journal.skipped.size)
        assertTrue(appStateStore.isPrefsMigrated())
    }

    @Test
    fun fullLegacy22DataIsMigrated() = runBlocking {
        writeLegacy22Prefs(sum = 122.0f, rate = 20.0f, type = 1)
        createLegacyDatabase(
            listOf(
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (1, 100.5, 20.0, 1)",
                // Одинаковые значения с разными ID остаются отдельными записями.
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (2, 100.5, 20.0, 1)",
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (3, NULL, 20.0, 1)",
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (4, 'abc', 20.0, 1)",
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (5, 100.0, 20.0, 9)",
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (6, 100.0, 100.0, 1)",
            ),
        )

        val state = migrator.migrateAppStateOnFirstRun()
        assertEquals("122.00", state.amountText)
        assertEquals("20", state.rateText)
        assertEquals(VatMode.EXCLUSIVE, state.mode)
        val prefsReport = appStateStore.loadPrefsReport()
        assertNotNull(prefsReport)
        assertEquals(LegacyPreferencesSource.VERSION_2_2, prefsReport!!.source)
        assertFalse(prefsReport.hasNotices)

        val journal = migrator.importLegacyJournal()
        assertTrue(journal.completed)
        assertEquals(2, journal.importedCount)
        assertEquals(
            setOf(
                SkippedLegacyRow(3, SkippedReason.NULL_VALUE),
                SkippedLegacyRow(4, SkippedReason.WRONG_TYPE),
                SkippedLegacyRow(5, SkippedReason.UNKNOWN_MODE),
                SkippedLegacyRow(6, SkippedReason.OUT_OF_RANGE),
            ),
            journal.skipped.toSet(),
        )

        val entries = historyStore.list()
        assertEquals(2, entries.size)
        assertEquals(setOf(1L, 2L), entries.map { it.legacyId }.toSet())

        // Исходные данные не удаляются и не изменяются миграцией.
        assertTrue(legacyDbFile.exists())
        assertEquals(122.0f, legacyPrefs("ru.msav.ruvattaxcalculator.settings")
            .getFloat("ru.msav.ruvattaxcalculator.settings.SumAmount", -1f))
    }

    @Test
    fun legacy15PreferencesAreMigrated() = runBlocking {
        writeLegacy15Prefs(sum = 100.5f, rate = 18.0f, allocate = true)
        val state = migrator.migrateAppStateOnFirstRun()
        assertEquals("100.50", state.amountText)
        assertEquals("18", state.rateText)
        assertEquals(VatMode.INCLUSIVE, state.mode)
        assertEquals(LegacyPreferencesSource.VERSION_1_5, appStateStore.loadPrefsReport()!!.source)
    }

    @Test
    fun newUserStateHasPriorityOverLegacy() = runBlocking {
        writeLegacy22Prefs(sum = 122.0f, rate = 20.0f, type = 0)
        migrator.migrateAppStateOnFirstRun()

        val edited = appStateStore.load()!!.copy(rateText = "7,5")
        appStateStore.save(edited)

        val afterRestart = migrator.migrateAppStateOnFirstRun()
        assertEquals("7,5", afterRestart.rateText)
        assertEquals(122.0f, legacyPrefs("ru.msav.ruvattaxcalculator.settings")
            .getFloat("ru.msav.ruvattaxcalculator.settings.SumAmount", -1f))
    }

    @Test
    fun damagedDatabaseLeavesMigrationIncompleteAndRetrySucceeds() = runBlocking {
        legacyDbFile.parentFile?.mkdirs()
        legacyDbFile.writeBytes("this is not a sqlite database".toByteArray())

        val failed = migrator.importLegacyJournal()
        assertFalse(failed.completed)

        // Повтор после замены повреждённого файла валидной базой переносит записи.
        removeLegacyDatabase()
        createLegacyDatabase(
            listOf("INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (1, 50.0, 20.0, 0)"),
        )
        val retried = migrator.importLegacyJournal()
        assertTrue(retried.completed)
        assertEquals(1, retried.importedCount)
        assertEquals(1, historyStore.list().size)
    }

    @Test
    fun walDatabaseIsReadConsistently() = runBlocking {
        createLegacyDatabase(
            listOf("INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (1, 10.0, 20.0, 0)"),
        )
        // Второе подключение пишет в WAL и не закрывается: читатель обязан
        // увидеть строку из журнала WAL, а не только основной файл.
        val writer = android.database.sqlite.SQLiteDatabase.openDatabase(
            legacyDbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        writer.enableWriteAheadLogging()
        writer.execSQL("INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (10, 30.0, 20.0, 1)")

        val journal = migrator.importLegacyJournal()
        writer.close()

        assertTrue(journal.completed)
        assertEquals(2, journal.importedCount)
        assertNotNull(historyStore.findByLegacyId(10L))
    }

    @Test
    fun deletedEntriesAreNotResurrectedOnLaterLaunches() = runBlocking {
        createLegacyDatabase(
            listOf(
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (1, 100.0, 20.0, 0)",
                "INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (2, 200.0, 20.0, 0)",
            ),
        )
        migrator.importLegacyJournal()
        val first = historyStore.findByLegacyId(1)
        assertTrue(historyStore.delete(first!!.id))

        val afterRestart = migrator.importLegacyJournal()
        assertTrue(afterRestart.completed)
        assertEquals(1, historyStore.list().size)
    }
}
