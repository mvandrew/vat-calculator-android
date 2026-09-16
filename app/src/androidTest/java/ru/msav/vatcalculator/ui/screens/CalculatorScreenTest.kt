package ru.msav.vatcalculator.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.calculation.CalculationInput
import ru.msav.vatcalculator.calculation.MoneyFormatter
import ru.msav.vatcalculator.calculation.VatCalculator
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.AppStateStore
import ru.msav.vatcalculator.storage.HistoryStore
import ru.msav.vatcalculator.storage.legacy.StateMigrator
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var historyStore: HistoryStore
    private lateinit var viewModel: CalculatorViewModel
    private val language: AppLanguage
        get() = if (context.resources.configuration.locales[0].language.startsWith("ru")) {
            AppLanguage.RUSSIAN
        } else {
            AppLanguage.ENGLISH
        }

    private fun string(id: Int): String = context.getString(id)

    @Before
    fun setUp() {
        if (::historyStore.isInitialized) historyStore.close()
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

        val stateStore = AppStateStore(context)
        historyStore = HistoryStore(context)
        viewModel = CalculatorViewModel(
            appStateStore = stateStore,
            historyStore = historyStore,
            migrator = StateMigrator(context, stateStore, historyStore),
            externalScope = CoroutineScope(Dispatchers.Unconfined),
        )
        composeRule.setContent {
            VATCalculatorTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    CalculatorScreen(
                        viewModel = viewModel,
                        onOpenHistory = {},
                        onOpenSettings = {},
                        onOpenAbout = {},
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.loaded }
    }

    @After
    fun tearDown() {
        historyStore.close()
    }

    private fun enterAmount(raw: String) {
        val label = when (viewModel.uiState.value.mode) {
            VatMode.EXCLUSIVE -> R.string.label_amount_exclusive
            VatMode.INCLUSIVE -> R.string.label_amount_inclusive
        }
        composeRule.onNodeWithText(string(label))
            .performClick()
            .performTextInput(raw)
    }

    private fun selectAddVat() {
        composeRule.onNodeWithText(string(R.string.mode_add)).performClick()
    }

    private fun expectedTotal(amount: String, rate: String, mode: VatMode): String {
        val input = CalculationInput(BigDecimal(amount), BigDecimal(rate), mode)
        return MoneyFormatter.formatMoney(VatCalculator.calculate(input).total, language)
    }

    @Test
    fun initialScreenShowsStartStateWithDashesAndDisabledSave() {
        composeRule.onNodeWithText(string(R.string.label_rate))
            .assertTextContains("22", substring = true)
        assertEquals(
            3,
            composeRule.onAllNodesWithText(string(R.string.result_empty)).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText(string(R.string.button_save)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.button_share)).assertIsNotEnabled()
    }

    @Test
    fun navigationButtonsAreShown() {
        composeRule.onNodeWithText(string(R.string.screen_history)).assertExists()
        composeRule.onNodeWithText(string(R.string.screen_settings)).assertExists()
        composeRule.onNodeWithText(string(R.string.screen_about)).assertExists()
    }

    @Test
    fun liveCalculationShowsFormattedResults() {
        selectAddVat()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.mode == VatMode.EXCLUSIVE }
        enterAmount("100")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.results != null }

        val expectedTotal = expectedTotal("100", "22", VatMode.EXCLUSIVE)
        composeRule.onNodeWithText(expectedTotal).assertExists()
        assertEquals("122.00", viewModel.uiState.value.results!!.total.toPlainString())
    }

    @Test
    fun intermediateInputRecalculatesByCompletableValue() {
        selectAddVat()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.mode == VatMode.EXCLUSIVE }
        enterAmount("12,")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.results != null }
        assertEquals("14.64", viewModel.uiState.value.results!!.total.toPlainString())
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    @Test
    fun invalidAmountShowsErrorAndKeepsSaveDisabled() {
        enterAmount("100,005")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.amountError != null }
        composeRule.onNodeWithText(string(R.string.error_precision_amount)).assertExists()
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    @Test
    fun clearedAmountShowsDashesAgain() {
        selectAddVat()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.mode == VatMode.EXCLUSIVE }
        enterAmount("100")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.results != null }
        composeRule.onNodeWithText(string(R.string.label_amount_exclusive))
            .performTextClearance()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.results == null }
    }

    @Test
    fun savePersistsEntryAndShowsNotice() = runBlocking {
        selectAddVat()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.mode == VatMode.EXCLUSIVE }
        enterAmount("100")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.canSave }

        composeRule.onNodeWithText(string(R.string.button_save)).performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.saveNotice != null }
        assertEquals(1, historyStore.list().size)
        composeRule.waitUntil(5_000) { viewModel.uiState.value.openEntryId != null }
        assertTrue(historyStore.list().first().id == viewModel.uiState.value.openEntryId)
    }

    @Test
    fun shareButtonBuildsShareTextForValidInput() = runBlocking {
        enterAmount("100")
        composeRule.waitUntil(5_000) { viewModel.uiState.value.canSave }

        composeRule.onNodeWithText(string(R.string.button_share)).performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.shareText != null }
        // Текст построен по шаблону фазы 03 без пересчёта и без валютных обозначений.
        val shareText = viewModel.uiState.value.shareText!!
        val total = MoneyFormatter.formatMoney(
            VatCalculator.calculate(CalculationInput(BigDecimal("100"), BigDecimal("22"), VatMode.INCLUSIVE)).total,
            language,
        )
        assertTrue(shareText.contains(total))
        assertTrue(!shareText.contains("₽"))
        assertTrue(!shareText.contains("RUB"))
        assertTrue(!shareText.contains("$"))
    }
}
