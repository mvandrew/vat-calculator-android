package ru.msav.vatcalculator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.calculation.MoneyFormatter
import ru.msav.vatcalculator.calculation.ParseError
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.LegacyPreferencesSource
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.appString
import ru.msav.vatcalculator.ui.components.AppTopBar
import ru.msav.vatcalculator.ui.components.TopBarOverflowAction
import ru.msav.vatcalculator.ui.rememberAppLanguage
import java.math.BigDecimal

/**
 * Экран калькулятора (interface.md §4.1): ввод суммы и ставки, переключатель
 * режима, три редактируемых результата с обратным пересчётом, команды
 * «Сохранить»/«Новый расчёт»/«Поделиться» и переходы в «Журнал»/«Настройки»/
 * «О программе» через верхнюю панель (фаза 06). При
 * [CalculatorViewModel.UiState.editingFromHistory] верхняя панель показывает
 * заголовок редактирования со стрелкой возврата в журнал; «Новый расчёт»
 * завершает сеанс редактирования и возвращает стартовую форму.
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToHistory: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val language = rememberAppLanguage(settings.languageSetting)
    CalculatorScreenContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onRateChange = viewModel::onRateChange,
        onModeChange = viewModel::onModeChange,
        onResultChange = viewModel::onResultFieldChange,
        onFieldFocusChanged = viewModel::onFieldFocusChanged,
        onSave = viewModel::save,
        onNewCalculation = viewModel::newCalculation,
        onShare = { viewModel.share(language) },
        onConsumeShareText = viewModel::consumeShareText,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onConsumeSaveNotice = viewModel::consumeSaveNotice,
        onAcknowledgeMigrationNotice = viewModel::acknowledgeMigrationNotice,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
        onBackToHistory = onBackToHistory,
        modifier = modifier,
    )
}

@Composable
fun CalculatorScreenContent(
    state: CalculatorViewModel.UiState,
    onAmountChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onModeChange: (VatMode) -> Unit,
    onResultChange: (CalculatorViewModel.Field, String) -> Unit,
    onFieldFocusChanged: (CalculatorViewModel.Field, Boolean) -> Unit,
    onSave: () -> Unit,
    onNewCalculation: () -> Unit,
    onShare: () -> Unit,
    onConsumeShareText: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConsumeSaveNotice: () -> Unit,
    onAcknowledgeMigrationNotice: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onBackToHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedText = appString(R.string.save_saved)
    val failedText = appString(R.string.save_failed)
    val language = LocalAppLanguage.current

    val saveNotice = state.saveNotice
    LaunchedEffect(saveNotice) {
        if (saveNotice == null) return@LaunchedEffect
        try {
            when (saveNotice) {
                CalculatorViewModel.SaveNotice.SAVED -> snackbarHostState.showSnackbar(savedText)
                CalculatorViewModel.SaveNotice.SAVE_FAILED -> snackbarHostState.showSnackbar(failedText)
            }
        } finally {
            // Навигация может удалить экран из композиции до завершения Snackbar.
            // Поглощаем одноразовое событие и при штатном завершении, и при отмене эффекта.
            onConsumeSaveNotice()
        }
    }

    ShareSheetEffect(
        shareText = state.shareText,
        snackbarHostState = snackbarHostState,
        onConsume = onConsumeShareText,
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            val editing = state.editingFromHistory
            AppTopBar(
                title = appString(
                    if (editing) R.string.calculator_edit_title else R.string.screen_calculator,
                ),
                onBack = if (editing) onBackToHistory else null,
                backContentDescription = if (editing) {
                    appString(R.string.nav_back_to_history)
                } else {
                    null
                },
                onOpenHistory = if (editing) null else onOpenHistory,
                overflowActions = listOf(
                    TopBarOverflowAction(appString(R.string.screen_settings), onOpenSettings),
                    TopBarOverflowAction(appString(R.string.screen_about), onOpenAbout),
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountField(state, onAmountChange, onFieldFocusChanged)
            RateField(state, onRateChange, onFieldFocusChanged)
            ModeSelector(state.mode, onModeChange)
            ResultsBlock(state, onResultChange, onFieldFocusChanged, language)
            CommandButtons(
                canSave = state.canSave,
                onSave = onSave,
                onNewCalculation = onNewCalculation,
                onShare = onShare,
            )
        }
    }

    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(appString(R.string.discard_title)) },
            text = { Text(appString(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) { Text(appString(R.string.discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) { Text(appString(R.string.discard_cancel)) }
            },
        )
    }

    state.migrationNotice?.let { notice ->
        MigrationNoticeDialog(notice = notice, onAcknowledge = onAcknowledgeMigrationNotice)
    }
}

@Composable
private fun AmountField(
    state: CalculatorViewModel.UiState,
    onAmountChange: (String) -> Unit,
    onFieldFocusChanged: (CalculatorViewModel.Field, Boolean) -> Unit,
) {
    val label = when (state.mode) {
        VatMode.EXCLUSIVE -> R.string.label_amount_exclusive
        VatMode.INCLUSIVE -> R.string.label_amount_inclusive
    }
    OutlinedTextField(
        value = state.amountText,
        onValueChange = onAmountChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused -> onFieldFocusChanged(CalculatorViewModel.Field.AMOUNT, focused.isFocused) },
        label = { Text(appString(label)) },
        isError = state.amountError != null,
        supportingText = state.amountError?.let { error ->
            { Text(appString(amountErrorText(error))) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

@Composable
private fun RateField(
    state: CalculatorViewModel.UiState,
    onRateChange: (String) -> Unit,
    onFieldFocusChanged: (CalculatorViewModel.Field, Boolean) -> Unit,
) {
    OutlinedTextField(
        value = state.rateText,
        onValueChange = onRateChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused -> onFieldFocusChanged(CalculatorViewModel.Field.RATE, focused.isFocused) },
        label = { Text(appString(R.string.label_rate)) },
        isError = state.rateError != null,
        supportingText = state.rateError?.let { error ->
            { Text(appString(rateErrorText(error))) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

@Composable
private fun ModeSelector(mode: VatMode, onModeChange: (VatMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == VatMode.EXCLUSIVE,
            onClick = { onModeChange(VatMode.EXCLUSIVE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(appString(R.string.mode_add))
        }
        SegmentedButton(
            selected = mode == VatMode.INCLUSIVE,
            onClick = { onModeChange(VatMode.INCLUSIVE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(appString(R.string.mode_extract))
        }
    }
}

/**
 * Итоги «Без НДС», «НДС», «С НДС» (interface.md §4.1). Вне фокуса показывают
 * форматированные производные значения и прочерк без готового результата;
 * фокус или правка делает поле источником обратного пересчёта: остальные
 * суммы и поле суммы пересчитываются от введённого значения по текущей ставке.
 */
@Composable
private fun ResultsBlock(
    state: CalculatorViewModel.UiState,
    onResultChange: (CalculatorViewModel.Field, String) -> Unit,
    onFieldFocusChanged: (CalculatorViewModel.Field, Boolean) -> Unit,
    language: AppLanguage,
) {
    val results = state.results
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultField(
            field = CalculatorViewModel.Field.BASE,
            labelRes = R.string.result_base,
            value = results?.base,
            state = state,
            onResultChange = onResultChange,
            onFieldFocusChanged = onFieldFocusChanged,
            language = language,
            imeAction = ImeAction.Next,
        )
        ResultField(
            field = CalculatorViewModel.Field.VAT,
            labelRes = R.string.result_vat,
            value = results?.vat,
            state = state,
            onResultChange = onResultChange,
            onFieldFocusChanged = onFieldFocusChanged,
            language = language,
            imeAction = ImeAction.Next,
        )
        ResultField(
            field = CalculatorViewModel.Field.TOTAL,
            labelRes = R.string.result_total,
            value = results?.total,
            state = state,
            onResultChange = onResultChange,
            onFieldFocusChanged = onFieldFocusChanged,
            language = language,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun ResultField(
    field: CalculatorViewModel.Field,
    labelRes: Int,
    value: BigDecimal?,
    state: CalculatorViewModel.UiState,
    onResultChange: (CalculatorViewModel.Field, String) -> Unit,
    onFieldFocusChanged: (CalculatorViewModel.Field, Boolean) -> Unit,
    language: AppLanguage,
    imeAction: ImeAction,
) {
    // Фокус нужен только для выбора режима отображения (сырой ввод или формат);
    // семантика фокуса обрабатывается ViewModel через onFieldFocusChanged.
    var focused by remember { mutableStateOf(false) }
    val formatted = value?.let { MoneyFormatter.formatMoney(it, language) }
    val isSource = state.source == field
    val shown = when {
        focused && isSource -> state.resultEditText ?: formatted.orEmpty()
        else -> formatted ?: appString(R.string.result_empty)
    }
    val error = if (isSource) state.resultError else null
    OutlinedTextField(
        value = shown,
        onValueChange = { onResultChange(field, it) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { changed ->
                focused = changed.isFocused
                onFieldFocusChanged(field, changed.isFocused)
            },
        label = { Text(appString(labelRes)) },
        isError = error != null,
        supportingText = error?.let { err ->
            { Text(appString(resultErrorText(err))) }
        },
        singleLine = true,
        textStyle = resultValueStyle(shown).copy(textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}

/** Крупные числа остаются читаемыми: длинные значения используют меньший стиль. */
private fun resultValueStyle(value: String): TextStyle = if (value.length > 14) {
    TextStyle(fontSize = 18.sp)
} else {
    TextStyle(fontSize = 24.sp)
}

@Composable
private fun CommandButtons(
    canSave: Boolean,
    onSave: () -> Unit,
    onNewCalculation: () -> Unit,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) {
                Text(appString(R.string.button_save))
            }
            OutlinedButton(onClick = onNewCalculation, modifier = Modifier.weight(1f)) {
                Text(appString(R.string.button_new_calculation))
            }
        }
        OutlinedButton(onClick = onShare, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            Text(appString(R.string.button_share))
        }
    }
}

@Composable
private fun MigrationNoticeDialog(
    notice: CalculatorViewModel.MigrationNotice,
    onAcknowledge: () -> Unit,
) {
    when (notice) {
        is CalculatorViewModel.MigrationNotice.Preferences -> {
            val report = notice.report
            AlertDialog(
                onDismissRequest = onAcknowledge,
                title = { Text(appString(R.string.migration_prefs_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val sourceText = when (report.source) {
                            LegacyPreferencesSource.VERSION_2_2 -> R.string.migration_from_22
                            LegacyPreferencesSource.VERSION_1_5 -> R.string.migration_from_15
                            LegacyPreferencesSource.NONE -> null
                        }
                        sourceText?.let { Text(appString(it)) }
                        if (report.amountRounded) Text(appString(R.string.migration_amount_rounded))
                        if (report.rateRounded) Text(appString(R.string.migration_rate_rounded))
                        if (report.amountInvalid) Text(appString(R.string.migration_amount_invalid))
                        if (report.rateInvalid) Text(appString(R.string.migration_rate_invalid))
                        if (report.modeInvalid) Text(appString(R.string.migration_mode_invalid))
                    }
                },
                confirmButton = {
                    TextButton(onClick = onAcknowledge) { Text(appString(R.string.migration_ok)) }
                },
            )
        }

        is CalculatorViewModel.MigrationNotice.Journal -> {
            AlertDialog(
                onDismissRequest = onAcknowledge,
                title = { Text(appString(R.string.migration_journal_title)) },
                text = {
                    Text(
                        appString(
                            R.string.migration_journal_summary,
                            notice.report.importedCount,
                            notice.report.roundedCount,
                            notice.report.skipped.size,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onAcknowledge) { Text(appString(R.string.migration_ok)) }
                },
            )
        }
    }
}

private fun amountErrorText(error: ParseError): Int = when (error) {
    ParseError.InputTooLong -> R.string.error_too_long
    ParseError.WrongFormat -> R.string.error_wrong_format
    ParseError.WrongGrouping -> R.string.error_grouping
    ParseError.TooManyFractionDigits -> R.string.error_precision_amount
    ParseError.OutOfRange -> R.string.error_range_amount
    ParseError.VatAtZeroRate -> R.string.error_vat_zero_rate
}

private fun rateErrorText(error: ParseError): Int = when (error) {
    ParseError.InputTooLong -> R.string.error_too_long
    ParseError.WrongFormat -> R.string.error_wrong_format
    ParseError.WrongGrouping -> R.string.error_grouping
    ParseError.TooManyFractionDigits -> R.string.error_precision_rate
    ParseError.OutOfRange -> R.string.error_range_rate
    ParseError.VatAtZeroRate -> R.string.error_vat_zero_rate
}

/** Ошибки поля итога: точность и диапазон — как у суммы; налог при 0% — своя. */
private fun resultErrorText(error: ParseError): Int = when (error) {
    ParseError.InputTooLong -> R.string.error_too_long
    ParseError.WrongFormat -> R.string.error_wrong_format
    ParseError.WrongGrouping -> R.string.error_grouping
    ParseError.TooManyFractionDigits -> R.string.error_precision_amount
    ParseError.OutOfRange -> R.string.error_range_amount
    ParseError.VatAtZeroRate -> R.string.error_vat_zero_rate
}
