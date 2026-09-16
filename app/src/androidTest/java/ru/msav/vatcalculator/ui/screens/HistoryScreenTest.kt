package ru.msav.vatcalculator.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.HistoryEntry
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.rememberAppLanguage
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme
import java.math.BigDecimal

/** Журнал (interface.md §4.4): пустое состояние, отображение записей, колбэки. */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appLanguage: AppLanguage
        get() = if (context.resources.configuration.locales[0].language.startsWith("ru")) {
            AppLanguage.RUSSIAN
        } else {
            AppLanguage.ENGLISH
        }

    private val entry = HistoryEntry(
        id = 7L,
        amount = BigDecimal("100.00"),
        rate = BigDecimal("22"),
        mode = VatMode.INCLUSIVE,
    )

    private fun string(id: Int): String = context.getString(id)

    private fun money(value: String): String =
        ru.msav.vatcalculator.calculation.MoneyFormatter.formatMoney(BigDecimal(value), appLanguage)

    private fun setContent(
        historyState: CalculatorViewModel.HistoryState,
        confirmOpenEntry: HistoryEntry? = null,
        onEntryClick: (HistoryEntry) -> Unit = {},
        onDeleteRequest: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            val language = rememberAppLanguage(LanguageSetting.SYSTEM)
            VATCalculatorTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    HistoryScreenContent(
                        historyState = historyState,
                        confirmOpenEntry = confirmOpenEntry,
                        onEntryClick = onEntryClick,
                        onConfirmOpen = {},
                        onDismissOpen = {},
                        onDeleteRequest = onDeleteRequest,
                        onConfirmDelete = {},
                        onDismissDelete = {},
                        onBack = {},
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
    }

    @Test
    fun emptyHistoryShowsMessageAndBackButton() {
        setContent(historyState = CalculatorViewModel.HistoryState())
        composeRule.onNodeWithText(string(R.string.history_empty)).assertExists()
        composeRule.onNodeWithText(string(R.string.history_back_to_calculator)).assertExists()
    }

    @Test
    fun entryRowShowsSummaryAndTotals() {
        setContent(historyState = CalculatorViewModel.HistoryState(entries = listOf(entry)))
        // Заголовок: исходная сумма, режим и ставка; итоги — единым ядром.
        composeRule.onNodeWithText("${money("100.00")} · ${string(R.string.mode_extract)} · 22%")
            .assertExists()
        composeRule.onNodeWithText("${string(R.string.result_total)}: ${money("100.00")}").assertExists()
    }

    @Test
    fun entryClickInvokesCallback() {
        var clicked: HistoryEntry? = null
        setContent(
            historyState = CalculatorViewModel.HistoryState(entries = listOf(entry)),
            onEntryClick = { clicked = it },
        )
        composeRule.onNodeWithText("${money("100.00")} · ${string(R.string.mode_extract)} · 22%")
            .performClick()
        assertEquals(entry.id, clicked?.id)
    }

    @Test
    fun deleteRequestInvokesCallbackWithId() {
        var deleteRequested: Long? = null
        setContent(
            historyState = CalculatorViewModel.HistoryState(entries = listOf(entry)),
            onDeleteRequest = { deleteRequested = it },
        )
        composeRule.onNodeWithText(string(R.string.history_delete)).performClick()
        assertEquals(entry.id, deleteRequested)
    }

    @Test
    fun confirmOpenDialogShownWhenPending() {
        setContent(
            historyState = CalculatorViewModel.HistoryState(entries = listOf(entry)),
            confirmOpenEntry = entry,
        )
        composeRule.onNodeWithText(string(R.string.discard_title)).assertExists()
    }
}
