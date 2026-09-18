package ru.msav.vatcalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

/**
 * Журнал (interface.md §4.4): локальные сохранённые расчёты. Нажатие на
 * карточку загружает запись в калькулятор (с подтверждением при
 * несохранённых изменениях) и открывает форму редактирования; свайп влево
 * раскрывает действия «Редактировать»/«Поделиться»/«Удалить». Поделиться
 * строит текст из самой записи, удаление требует подтверждения; отмена и
 * ошибка хранилища оставляют запись. Возврат без выбора сохраняет форму.
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
    val language = LocalAppLanguage.current
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedText = appString(R.string.history_delete_failed)

    LaunchedEffect(Unit) { viewModel.refreshHistory() }

    LaunchedEffect(historyState.deleteFailed) {
        if (historyState.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedText)
            viewModel.consumeDeleteNotice()
        }
    }

    ShareSheetEffect(
        shareText = uiState.shareText,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeShareText,
    )

    HistoryScreenContent(
        historyState = historyState,
        confirmOpenEntry = uiState.confirmOpenEntry,
        onEntryClick = { entry ->
            // Немедленное открытие уводит на форму редактирования; при
            // несохранённых изменениях остаёмся здесь до подтверждения.
            if (viewModel.onHistoryEntryClick(entry)) onEntryOpened()
        },
        onConfirmOpen = {
            viewModel.confirmOpenEntry()
            onEntryOpened()
        },
        onDismissOpen = viewModel::dismissOpenEntry,
        onShareEntry = { entry -> viewModel.shareEntry(entry, language) },
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
    onShareEntry: (HistoryEntry) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Раскрыта максимум одна строка: открытие другой закрывает предыдущую.
    var revealedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }

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
                            revealed = revealedEntryId == entry.id,
                            onRevealChange = { open ->
                                revealedEntryId = if (open) entry.id else null
                            },
                            onOpen = { onEntryClick(entry) },
                            onShare = { onShareEntry(entry) },
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

/**
 * Запись журнала: исходная сумма, ставка, режим и три итога единым ядром.
 * Нажатие открывает форму редактирования; свайп влево раскрывает действия
 * «Редактировать»/«Поделиться»/«Удалить», доступные TalkBack и как
 * семантические действия карточки без жестов.
 */
@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val result = VatCalculator.calculate(CalculationInput(entry.amount, entry.rate, entry.mode))
    val openLabel = appString(R.string.history_open_entry)
    val editLabel = appString(R.string.history_edit)
    val shareLabel = appString(R.string.button_share)
    val deleteLabel = appString(R.string.history_delete)
    val modeLabel = appString(
        when (entry.mode) {
            VatMode.EXCLUSIVE -> R.string.mode_add
            VatMode.INCLUSIVE -> R.string.mode_extract
        },
    )
    val density = LocalDensity.current
    val actionsWidth = with(density) { (RevealActionWidth * REVEAL_ACTION_COUNT).toPx() }
    val dragState = remember { AnchoredDraggableState(RevealValue.CLOSED) }

    // Статические якоря зависят только от плотности; пороги и анимация —
    // умолчания AnchoredDraggableDefaults (50% расстояния, 125 dp/s).
    SideEffect {
        dragState.updateAnchors(
            DraggableAnchors {
                RevealValue.CLOSED at 0f
                RevealValue.OPEN at -actionsWidth
            },
        )
    }

    // Внешнее состояние (открытие другой строки) синхронизируется с якорями.
    LaunchedEffect(revealed) {
        val target = if (revealed) RevealValue.OPEN else RevealValue.CLOSED
        if (dragState.settledValue != target) dragState.animateTo(target)
    }

    // Завершившийся жест сообщает новое состояние списку.
    LaunchedEffect(dragState.settledValue) {
        onRevealChange(dragState.settledValue == RevealValue.OPEN)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Фон действий: прижат к правому краю, раскрывается из-под карточки.
        Row(
            modifier = Modifier
                .matchParentSize()
                .then(if (revealed) Modifier else Modifier.clearAndSetSemantics {}),
            horizontalArrangement = Arrangement.End,
        ) {
            RevealActionButton(
                text = editLabel,
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    onRevealChange(false)
                    onOpen()
                },
            )
            RevealActionButton(
                text = shareLabel,
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    onRevealChange(false)
                    onShare()
                },
            )
            RevealActionButton(
                text = deleteLabel,
                container = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    onRevealChange(false)
                    onDelete()
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    // До первой раскладки якорей ещё нет — карточка на месте.
                    val x = dragState.offset
                    IntOffset(if (x.isNaN()) 0 else x.roundToInt(), 0)
                }
                .anchoredDraggable(dragState, Orientation.Horizontal)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .semantics {
                    // Действия свайпа доступны TalkBack без жестов.
                    customActions = listOf(
                        CustomAccessibilityAction(editLabel) {
                            onOpen()
                            true
                        },
                        CustomAccessibilityAction(shareLabel) {
                            onShare()
                            true
                        },
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
                },
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
        }
    }
}

/** Кнопка свайп-действия: полноширинная ячейка фона с локализованной подписью. */
@Composable
private fun RowScope.RevealActionButton(
    text: String,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(RevealActionWidth)
            .background(container)
            .clickable(onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Якорь свайпа карточки журнала: закрыто или раскрыты кнопки действий. */
private enum class RevealValue { CLOSED, OPEN }

/** Ширина одной кнопки действия; три кнопки раскрываются свайпом влево. */
private val RevealActionWidth = 104.dp

private const val REVEAL_ACTION_COUNT = 3

private fun entrySummary(entry: HistoryEntry, language: AppLanguage, modeLabel: String): String =
    MoneyFormatter.formatMoney(entry.amount, language) + " · " + modeLabel + " · " +
        MoneyFormatter.formatRate(entry.rate, language) + "%"
