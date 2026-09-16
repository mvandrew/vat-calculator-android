package ru.msav.vatcalculator.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.calculation.ParseError
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.AppStateStore
import ru.msav.vatcalculator.storage.HistoryStore
import ru.msav.vatcalculator.storage.legacy.StateMigrator
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalculatorViewModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var appStateStore: AppStateStore
    private lateinit var historyStore: HistoryStore
    private lateinit var migrator: StateMigrator
    private lateinit var testScope: CoroutineScope
    private var viewModel: CalculatorViewModel? = null

    @Before
    fun cleanStorage() {
        if (::historyStore.isInitialized) historyStore.close()
        testScope = CoroutineScope(Dispatchers.Unconfined + Job())
        context.getSharedPreferences("ru.msav.vatcalculator.state", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("ru.msav.ruvattaxcalculator.settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("ru.msav.passwordgenerator.settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.deleteDatabase("ru.msav.vatcalculator.db")
        val legacy = context.getDatabasePath("ru.msav.ruvattaxcalculator.db")
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(legacy.parentFile, legacy.name + suffix).delete()
        }

        appStateStore = AppStateStore(context)
        historyStore = HistoryStore(context)
        migrator = StateMigrator(context, appStateStore, historyStore)
    }

    @After
    fun closeDatabase() {
        testScope.cancel()
        historyStore.close()
        viewModel = null
    }

    private fun createViewModel(): CalculatorViewModel =
        CalculatorViewModel(
            appStateStore = appStateStore,
            historyStore = historyStore,
            migrator = migrator,
            externalScope = testScope,
        ).also { viewModel = it }

    private suspend fun awaitState(predicate: (CalculatorViewModel.UiState) -> Boolean) {
        withTimeout(5_000) {
            while (!predicate(viewModel!!.uiState.value)) {
                kotlinx.coroutines.delay(50)
            }
        }
    }

    @Test
    fun startStateWithoutLegacy() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        val state = model.uiState.value
        assertEquals("", state.amountText)
        assertEquals(VatMode.INCLUSIVE, state.mode)
        assertNull(state.results)
        assertFalse(state.canSave)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.migrationNotice)
    }

    @Test
    fun liveRecalculationAndCanSave() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("100")
        model.onModeChange(VatMode.EXCLUSIVE)
        val state = model.uiState.value
        assertNotNull(state.results)
        assertEquals("122.00", state.results!!.total.toPlainString())
        assertTrue(state.canSave)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun intermediateInputRecalculatesAndFinalizesOnFocusLoss() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("12,")
        // Режим по умолчанию «Выделить»: «12,» — сумма с НДС, итог 12,00.
        val intermediate = model.uiState.value
        assertNotNull(intermediate.results)
        assertEquals("12.00", intermediate.results!!.total.toPlainString())
        assertFalse(intermediate.canSave)

        model.onFieldFocusChanged(CalculatorViewModel.Field.AMOUNT, focused = false)
        val finalized = model.uiState.value
        assertEquals("12", finalized.amountText)
        assertTrue(finalized.canSave)
    }

    @Test
    fun invalidInputBlocksResultsAndSave() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("100")
        model.onAmountChange("100,005")
        val state = model.uiState.value
        assertEquals(ParseError.TooManyFractionDigits, state.amountError)
        assertNull(state.results)
        assertFalse(state.canSave)
    }

    @Test
    fun rejectedChangeKeepsPreviousText() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("100")
        model.onAmountChange("1,234.56")
        model.onAmountChange("-5")
        model.onAmountChange("abc")
        assertEquals("100", model.uiState.value.amountText)
    }

    @Test
    fun modeChangeFinalizesInputAndRepeatIsNoOp() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("12,")
        model.onModeChange(VatMode.EXCLUSIVE)
        val switched = model.uiState.value
        assertEquals("12", switched.amountText)
        assertEquals(VatMode.EXCLUSIVE, switched.mode)

        val beforeRepeat = model.uiState.value
        model.onModeChange(VatMode.EXCLUSIVE)
        assertEquals(beforeRepeat, model.uiState.value)
    }

    @Test
    fun saveCreatesThenUpdatesSameEntry() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("100")
        model.onModeChange(VatMode.EXCLUSIVE)
        model.save()
        awaitState { it.openEntryId != null && it.saveNotice == CalculatorViewModel.SaveNotice.SAVED }
        val firstId = model.uiState.value.openEntryId

        model.onAmountChange("200")
        model.save()
        awaitState { it.saveNotice == CalculatorViewModel.SaveNotice.SAVED }
        assertEquals(firstId, model.uiState.value.openEntryId)
        assertEquals(1, historyStore.list().size)
        assertEquals(0, historyStore.get(firstId!!)!!.amount.compareTo(java.math.BigDecimal("200")))

        model.consumeSaveNotice()
        assertFalse(model.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun newCalculationRequiresConfirmationOnlyWhenDirty() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }

        model.newCalculation()
        assertFalse(model.uiState.value.confirmDiscard)
        assertEquals("", model.uiState.value.amountText)

        model.onAmountChange("100")
        model.newCalculation()
        assertTrue(model.uiState.value.confirmDiscard)

        model.dismissDiscard()
        assertEquals("100", model.uiState.value.amountText)

        model.newCalculation()
        model.confirmDiscard()
        val reset = model.uiState.value
        assertEquals("", reset.amountText)
        assertEquals("22", reset.rateText)
        assertEquals(VatMode.INCLUSIVE, reset.mode)
        assertNull(reset.openEntryId)
        assertFalse(reset.hasUnsavedChanges)
    }

    @Test
    fun legacyStateIsMigratedWithNotices() = runBlocking {
        context.getSharedPreferences("ru.msav.ruvattaxcalculator.settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putFloat("ru.msav.ruvattaxcalculator.settings.SumAmount", 122.0f)
            .putFloat("ru.msav.ruvattaxcalculator.settings.VatRate", 100.0f)
            .putInt("ru.msav.ruvattaxcalculator.settings.Type", 1)
            .commit()
        val legacyDb = context.getDatabasePath("ru.msav.ruvattaxcalculator.db")
        legacyDb.parentFile?.mkdirs()
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacyDb, null)
        db.execSQL(
            "CREATE TABLE calculations (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sum_amount REAL, vat_rate REAL, type INTEGER)",
        )
        db.execSQL("INSERT INTO calculations (_id, sum_amount, vat_rate, type) VALUES (1, 100.5, 20.0, 1)")
        db.close()

        val model = createViewModel()
        awaitState { it.amountText == "122.00" }
        val state = model.uiState.value
        assertEquals("122.00", state.amountText)
        assertEquals("", state.rateText)
        assertEquals(VatMode.EXCLUSIVE, state.mode)

        val notice = state.migrationNotice
        assertNotNull(notice)
        assertTrue(notice is CalculatorViewModel.MigrationNotice.Preferences)

        model.acknowledgeMigrationNotice()
        awaitState { it.migrationNotice is CalculatorViewModel.MigrationNotice.Journal }
        model.acknowledgeMigrationNotice()
        awaitState { it.migrationNotice == null }

        assertTrue(appStateStore.isPrefsReportShown())
        assertTrue(historyStore.isJournalReportShown())
    }

    @Test
    fun autosavePersistsUnfinishedInput() = runBlocking {
        val model = createViewModel()
        awaitState { it.loaded }
        model.onAmountChange("1234567,89")
        model.onRateChange("12,")
        kotlinx.coroutines.delay(700)

        val stored = appStateStore.load()
        assertNotNull(stored)
        assertEquals("1234567,89", stored!!.amountText)
        assertEquals("12,", stored.rateText)
    }

    @Test
    fun stateIsRestoredInNewViewModel() = runBlocking {
        val first = createViewModel()
        awaitState { it.loaded }
        first.onAmountChange("999 999 999 999,99")
        // Переключение режима завершает ввод: группировка пробелов снимается, значение то же.
        first.onModeChange(VatMode.EXCLUSIVE)
        kotlinx.coroutines.delay(700)

        val second = createViewModel()
        awaitState { it.amountText == "999999999999.99" }
        val state = second.uiState.value
        assertEquals(VatMode.EXCLUSIVE, state.mode)
        assertNotNull(state.results)
        // 999 999 999 999,99 × 1,22 — восстановленное значение и режим дают тот же расчёт.
        assertEquals("1219999999999.99", state.results!!.total.toPlainString())
    }
}
