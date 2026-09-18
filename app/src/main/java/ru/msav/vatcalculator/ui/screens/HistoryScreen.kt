package ru.msav.vatcalculator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.calculation.CalculationInput
import ru.msav.vatcalculator.calculation.MoneyFormatter
import ru.msav.vatcalculator.calculation.VatCalculator
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.HistoryEntry
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.appString

/**
 * Журнал (interface.md §4.4): локальные сохранённые расчёты. Открытие записи
 * загружает её в калькулятор (с подтверждением при несохранённых изменениях),
 * удаление требует подтверждения; отмена и ошибка хранилища оставляют запись.
 * Возврат без выбора сохраняет форму калькулятора.
 */
@Composable
fun HistoryScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onEntryOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedText = appString(R.string.history_delete_failed)

    LaunchedEffect(Unit) { viewModel.refreshHistory() }

    LaunchedEffect(historyState.deleteFailed) {
        if (historyState.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedText)
            viewModel.consumeDeleteNotice()
        }
    }

    HistoryScreenContent(
        historyState = historyState,
        confirmOpenEntry = uiState.confirmOpenEntry,
        onEntryClick = viewModel::onHistoryEntryClick,
        onConfirmOpen = {
            viewModel.confirmOpenEntry()
            onEntryOpened()
        },
        onDismissOpen = viewModel::dismissOpenEntry,
        onDeleteRequest = viewModel::requestDeleteEntry,
        onConfirmDelete = viewModel::confirmDeleteEntry,
        onDismissDelete = viewModel::dismissDeleteEntry,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun HistoryScreenContent(
    historyState: CalculatorViewModel.HistoryState,
    confirmOpenEntry: HistoryEntry?,
    onEntryClick: (HistoryEntry) -> Unit,
    onConfirmOpen: () -> Unit,
    onDismissOpen: () -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = appString(R.string.screen_history), onBack = onBack)
            if (historyState.entries.isEmpty()) {
                // Пустой журнал: понятное сообщение и возврат к калькулятору.
                Text(appString(R.string.history_empty))
                Button(onClick = onBack) { Text(appString(R.string.history_back_to_calculator)) }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(historyState.entries, key = { it.id }) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            onOpen = { onEntryClick(entry) },
                            onDelete = { onDeleteRequest(entry.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmOpenEntry != null) {
        AlertDialog(
            onDismissRequest = onDismissOpen,
            title = { Text(appString(R.string.discard_title)) },
            text = { Text(appString(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = onConfirmOpen) { Text(appString(R.string.discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissOpen) { Text(appString(R.string.discard_cancel)) }
            },
        )
    }

    historyState.confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(appString(R.string.history_delete_title)) },
            text = { Text(appString(R.string.history_delete_text)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(appString(R.string.history_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text(appString(R.string.discard_cancel)) }
            },
        )
    }
}

/** Запись журнала: исходная сумма, ставка, режим и три итога единым ядром. */
@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val result = VatCalculator.calculate(CalculationInput(entry.amount, entry.rate, entry.mode))
    val openLabel = appString(R.string.history_open_entry)
    val modeLabel = appString(
        when (entry.mode) {
            VatMode.EXCLUSIVE -> R.string.mode_add
            VatMode.INCLUSIVE -> R.string.mode_extract
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entrySummary(entry, language, modeLabel),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(appString(R.string.result_base) + ": " + MoneyFormatter.formatMoney(result.base, language))
            Text(appString(R.string.result_vat) + ": " + MoneyFormatter.formatMoney(result.vat, language))
            Text(appString(R.string.result_total) + ": " + MoneyFormatter.formatMoney(result.total, language))
        }
        TextButton(onClick = onDelete) { Text(appString(R.string.history_delete)) }
    }
}

private fun entrySummary(entry: HistoryEntry, language: AppLanguage, modeLabel: String): String =
    MoneyFormatter.formatMoney(entry.amount, language) + " · " + modeLabel + " · " +
        MoneyFormatter.formatRate(entry.rate, language) + "%"
