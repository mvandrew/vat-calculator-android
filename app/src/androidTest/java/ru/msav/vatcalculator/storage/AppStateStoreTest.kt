package ru.msav.vatcalculator.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.calculation.VatMode

@RunWith(AndroidJUnit4::class)
class AppStateStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AppStateStore(context)

    @Before
    fun cleanState() {
        context.getSharedPreferences("ru.msav.vatcalculator.state", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private val state = AppState(
        amountText = "1234567,89",
        rateText = "22",
        mode = VatMode.EXCLUSIVE,
        themeSetting = ThemeSetting.LIGHT,
        languageSetting = LanguageSetting.RUSSIAN,
        openEntryId = 5L,
    )

    @Test
    fun missingStateReadsAsNull() = runBlocking {
        assertNull(store.load())
        assertFalse(store.isPrefsMigrated())
        assertNull(store.loadPrefsReport())
    }

    @Test
    fun largeAmountSurvivesRoundtripWithoutPrecisionLoss() = runBlocking {
        store.save(state)
        assertEquals(state, store.load())
    }

    @Test
    fun emptyAndUnfinishedInputSurviveRoundtrip() = runBlocking {
        val unfinished = state.copy(amountText = "12,", rateText = "", openEntryId = null)
        store.save(unfinished)
        assertEquals(unfinished, store.load())
    }

    @Test
    fun noOpenEntrySurvivesRoundtrip() = runBlocking {
        store.save(state.copy(openEntryId = null))
        assertNull(store.load()?.openEntryId)
    }

    @Test
    fun migratedInitialPersistsStateAndReportTogether() = runBlocking {
        val report = PrefsMigrationReport(
            source = LegacyPreferencesSource.VERSION_2_2,
            amountRounded = true,
            rateInvalid = true,
        )
        store.saveMigratedInitial(state, report)

        assertEquals(state, store.load())
        assertTrue(store.isPrefsMigrated())
        val loaded = store.loadPrefsReport()
        assertNotNull(loaded)
        assertEquals(report, loaded)
        assertFalse(store.isPrefsReportShown())

        store.markPrefsReportShown()
        assertTrue(store.isPrefsReportShown())
        // Повторное чтение согласовано: состояние и отметки переживают перечитывание.
        assertEquals(state, store.load())
        assertEquals(report, store.loadPrefsReport())
    }

    @Test
    fun plainSaveDoesNotTouchMigrationMarks() = runBlocking {
        val report = PrefsMigrationReport(source = LegacyPreferencesSource.VERSION_1_5)
        store.saveMigratedInitial(state, report)
        store.markPrefsReportShown()

        store.save(state.copy(rateText = "7,5"))

        assertTrue(store.isPrefsMigrated())
        assertTrue(store.isPrefsReportShown())
        assertEquals("7,5", store.load()?.rateText)
    }
}
