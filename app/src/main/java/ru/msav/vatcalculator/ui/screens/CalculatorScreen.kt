package ru.msav.vatcalculator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import ru.msav.vatcalculator.ui.rememberAppLanguage

/**
 * Экран калькулятора (interface.md §4.1): ввод суммы и ставки, переключатель
 * режима, три результата, команды «Сохранить»/«Новый расчёт»/«Поделиться»
 * и переходы в «Журнал»/«Настройки»/«О программе» (фаза 06).
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val language = rememberAppLanguage(settings.languageSetting)
    CalculatorScreenContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onRateChange = viewModel::onRateChange,
        onModeChange = viewModel::onModeChange,
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
        modifier = modifier,
    )
}

@Composable
fun CalculatorScreenContent(
    state: CalculatorViewModel.UiState,
    onAmountChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onModeChange: (VatMode) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val savedText = appString(R.string.save_saved)
    val failedText = appString(R.string.save_failed)
    val shareNoAppText = appString(R.string.share_no_app)
    val chooserTitle = appString(R.string.share_via)
    val language = LocalAppLanguage.current

    LaunchedEffect(state.saveNotice) {
        when (state.saveNotice) {
            CalculatorViewModel.SaveNotice.SAVED -> snackbarHostState.showSnackbar(savedText)
            CalculatorViewModel.SaveNotice.SAVE_FAILED -> snackbarHostState.showSnackbar(failedText)
            null -> Unit
        }
        onConsumeSaveNotice()
    }

    LaunchedEffect(state.shareText) {
        val text = state.shareText
        if (text != null) {
            // interface.md §7: text/plain через системный Sharesheet; отмена и
            // отсутствие обработчика не меняют расчёт и не вызывают падения.
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle)
            try {
                context.startActivity(chooser)
            } catch (error: ActivityNotFoundException) {
                snackbarHostState.showSnackbar(shareNoAppText)
            }
        }
        onConsumeShareText()
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountField(state, onAmountChange, onFieldFocusChanged)
            RateField(state, onRateChange, onFieldFocusChanged)
            ModeSelector(state.mode, onModeChange)
            ResultsBlock(state, language)
            CommandButtons(
                canSave = state.canSave,
                onSave = onSave,
                onNewCalculation = onNewCalculation,
                onShare = onShare,
            )
            NavigationButtons(
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
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

@Composable
private fun ResultsBlock(state: CalculatorViewModel.UiState, language: AppLanguage) {
    val results = state.results
    val formatter = MoneyFormatter
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultRow(
            label = appString(R.string.result_base),
            value = results?.let { formatter.formatMoney(it.base, language) },
        )
        ResultRow(
            label = appString(R.string.result_vat),
            value = results?.let { formatter.formatMoney(it.vat, language) },
        )
        ResultRow(
            label = appString(R.string.result_total),
            value = results?.let { formatter.formatMoney(it.total, language) },
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = value ?: appString(R.string.result_empty),
            style = resultValueStyle(value),
        )
    }
}

/** Крупные числа остаются читаемыми: длинные значения используют меньший стиль. */
private fun resultValueStyle(value: String?): TextStyle = if ((value?.length ?: 0) > 14) {
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

/** Переходы в остальные экраны (interface.md §4.1); все кнопки подписаны. */
@Composable
private fun NavigationButtons(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onOpenHistory) { Text(appString(R.string.screen_history)) }
        TextButton(onClick = onOpenSettings) { Text(appString(R.string.screen_settings)) }
        TextButton(onClick = onOpenAbout) { Text(appString(R.string.screen_about)) }
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
}

private fun rateErrorText(error: ParseError): Int = when (error) {
    ParseError.InputTooLong -> R.string.error_too_long
    ParseError.WrongFormat -> R.string.error_wrong_format
    ParseError.WrongGrouping -> R.string.error_grouping
    ParseError.TooManyFractionDigits -> R.string.error_precision_rate
    ParseError.OutOfRange -> R.string.error_range_rate
}
