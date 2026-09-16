package ru.msav.vatcalculator.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.calculation.VatMode
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class HistoryStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = HistoryStore(context)

    @Before
    fun removeDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun cleanup() {
        store.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun rows(vararg legacyIds: Long): List<LegacyJournalRow> = legacyIds.map { id ->
        LegacyJournalRow(
            legacyId = id,
            amount = BigDecimal("100.5"),
            rate = BigDecimal("20"),
            mode = VatMode.EXCLUSIVE,
            rounded = false,
        )
    }

    @Test
    fun crudLifecycle() = runBlocking {
        val id = store.insert(BigDecimal("1234567,89".replace(',', '.')), BigDecimal("22"), VatMode.INCLUSIVE)
        assertTrue(id > 0)

        val stored = store.get(id)
        assertNotNull(stored)
        assertEquals(0, BigDecimal("1234567.89").compareTo(stored!!.amount))
        assertEquals(0, BigDecimal("22").compareTo(stored.rate))
        assertEquals(VatMode.INCLUSIVE, stored.mode)
        assertNull(stored.legacyId)

        assertTrue(store.update(id, BigDecimal("200"), BigDecimal("7,5".replace(',', '.')), VatMode.EXCLUSIVE))
        val updated = store.get(id)!!
        assertEquals(0, BigDecimal("200.00").compareTo(updated.amount))
        assertEquals(0, BigDecimal("7.5").compareTo(updated.rate))
        assertEquals(VatMode.EXCLUSIVE, updated.mode)

        assertTrue(store.delete(id))
        assertNull(store.get(id))
        assertFalse(store.delete(id))
    }

    @Test
    fun listShowsNewestFirst() = runBlocking {
        val first = store.insert(BigDecimal("1"), BigDecimal("22"), VatMode.INCLUSIVE)
        val second = store.insert(BigDecimal("2"), BigDecimal("22"), VatMode.INCLUSIVE)
        val third = store.insert(BigDecimal("3"), BigDecimal("22"), VatMode.INCLUSIVE)
        assertEquals(listOf(third, second, first), store.list().map { it.id })
    }

    @Test
    fun importInsertsOnceAndKeepsMark() = runBlocking {
        val skipped = listOf(SkippedLegacyRow(3, SkippedReason.NULL_VALUE))
        val report = store.importLegacyEntries(rows(1, 2), skipped)

        assertEquals(2, report.importedCount)
        assertEquals(1, report.skipped.size)
        assertTrue(report.completed)

        // Повтор той же поставки: дубликатов нет, отчёт устойчив.
        val repeat = store.importLegacyEntries(rows(1, 2), skipped)
        assertEquals(2, repeat.importedCount)
        assertEquals(2, store.list().size)
        assertEquals(listOf(1L, 2L), store.list().map { it.legacyId })

        val mark = store.loadJournalMigrationMark()
        assertTrue(mark.completed)
        assertEquals(2, mark.importedCount)
        assertEquals(skipped, mark.skipped)
    }

    @Test
    fun identicalLegacyRowsWithDifferentIdsStaySeparate() = runBlocking {
        val all = rows(1, 2) + rows(1, 2) // одинаковые значения, разные ID
        val unique = all.distinctBy { it.legacyId }
        store.importLegacyEntries(unique, emptyList())
        assertEquals(2, store.list().size)
        assertEquals(setOf(1L, 2L), store.list().map { it.legacyId }.toSet())
    }

    @Test
    fun interruptedImportResumesWithoutDuplicates() = runBlocking {
        // Имитация прерывания до фиксации отметки: часть записей уже вставлена,
        // отметки завершения нет (одна транзакция атомарна; защита — уникальный legacy_id).
        store.writableDatabase.execSQL(
            "INSERT INTO calculations (sum_amount, vat_rate, type, legacy_id) VALUES ('100.5', '20', 1, 1)",
        )
        assertFalse(store.loadJournalMigrationMark().completed)

        // Полный повтор: уже перенесённый ID не дублируется, новый добавляется.
        val report = store.importLegacyEntries(rows(1, 2), emptyList())
        assertEquals(2, report.importedCount)
        assertEquals(2, store.list().size)
        assertTrue(store.loadJournalMigrationMark().completed)
    }

    @Test
    fun completedImportDoesNotResurrectDeletedEntries() = runBlocking {
        store.importLegacyEntries(rows(1, 2), emptyList())
        val imported = store.findByLegacyId(1)
        assertTrue(store.delete(imported!!.id))

        // Повторный импорт после завершения не восстанавливает удалённую запись.
        val report = store.importLegacyEntries(rows(1, 2), emptyList())
        assertEquals(1, store.list().size)
        assertEquals(2, report.importedCount) // отчёт считает перенесённое в сумме за попытки
    }

    @Test
    fun updateKeepsLegacyIdOfImportedEntry() = runBlocking {
        store.importLegacyEntries(rows(1), emptyList())
        val imported = store.findByLegacyId(1)!!
        store.update(imported.id, BigDecimal("999"), BigDecimal("30"), VatMode.INCLUSIVE)
        val updated = store.get(imported.id)!!
        assertEquals(1L, updated.legacyId)
        assertEquals(0, BigDecimal("999").compareTo(updated.amount))
    }

    @Test
    fun reportShownMarkIsPersisted() = runBlocking {
        store.importLegacyEntries(rows(1), emptyList())
        assertFalse(store.isJournalReportShown())
        store.markJournalReportShown()
        assertTrue(store.isJournalReportShown())
    }

    companion object {
        private const val DATABASE_NAME = "ru.msav.vatcalculator.db"
    }
}
