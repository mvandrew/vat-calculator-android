package ru.msav.vatcalculator.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.msav.vatcalculator.calculation.AmountParser
import ru.msav.vatcalculator.calculation.CalculationInput
import ru.msav.vatcalculator.calculation.CalculatorEvaluation
import ru.msav.vatcalculator.calculation.CalculatorEvaluator
import ru.msav.vatcalculator.calculation.ParsedInput
import ru.msav.vatcalculator.calculation.ParseError
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.calculation.VatResult
import ru.msav.vatcalculator.storage.AppState
import ru.msav.vatcalculator.storage.AppStateStore
import ru.msav.vatcalculator.storage.HistoryStore
import ru.msav.vatcalculator.storage.JournalMigrationReport
import ru.msav.vatcalculator.storage.LanguageSetting
import ru.msav.vatcalculator.storage.PrefsMigrationReport
import ru.msav.vatcalculator.storage.ThemeSetting
import ru.msav.vatcalculator.storage.legacy.StateMigrator
import java.math.BigDecimal

/**
 * Экранное состояние калькулятора (interface.md §4.1). Владеет текстом ввода,
 * режимом, связью с записью журнала и результатами; расчёт выполняет ядро
 * фазы 03, хранение — фаза 04. Состояние формы автосохраняется с задержкой
 * (не на каждое нажатие); «Сохранить» относится только к записи журнала.
 */
class CalculatorViewModel(
    private val appStateStore: AppStateStore,
    private val historyStore: HistoryStore,
    private val migrator: StateMigrator,
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    /** Основной scope: viewModelScope в приложении; явный scope — в тестах. */
    private val scope = externalScope ?: viewModelScope

    data class UiState(
        val amountText: String = "",
        val rateText: String = "22",
        val mode: VatMode = VatMode.INCLUSIVE,
        val amountError: ParseError? = null,
        val rateError: ParseError? = null,
        val results: VatResult? = null,
        val canSave: Boolean = false,
        val openEntryId: Long? = null,
        val hasUnsavedChanges: Boolean = false,
        val saveNotice: SaveNotice? = null,
        val confirmDiscard: Boolean = false,
        val migrationNotice: MigrationNotice? = null,
        /** false до завершения первичной загрузки/миграции; тесты ждут true. */
        val loaded: Boolean = false,
    )

    enum class SaveNotice { SAVED, SAVE_FAILED }

    sealed interface MigrationNotice {
        data class Preferences(val report: PrefsMigrationReport) : MigrationNotice

        data class Journal(val report: JournalMigrationReport) : MigrationNotice
    }

    /** Поле формы без результатов — для сравнения с baseline. */
    private data class FormSnapshot(
        val amountText: String,
        val rateText: String,
        val mode: VatMode,
        val openEntryId: Long?,
    )

    private val uiStateFlow = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = uiStateFlow.asStateFlow()

    private var themeSetting: ThemeSetting = ThemeSetting.SYSTEM
    private var languageSetting: LanguageSetting = LanguageSetting.SYSTEM
    private var baseline: FormSnapshot = FormSnapshot("", "22", VatMode.INCLUSIVE, null)
    private val pendingNotices = ArrayDeque<MigrationNotice>()

    init {
        scope.launch {
            val appState: AppState = migrator.migrateAppStateOnFirstRun()
            themeSetting = appState.themeSetting
            languageSetting = appState.languageSetting
            applyForm(appState.amountText, appState.rateText, appState.mode, appState.openEntryId)
            baseline = snapshot()
            queueMigrationNotices(migrator.importLegacyJournal())
            scheduleAutosave()
            uiStateFlow.value = uiStateFlow.value.copy(loaded = true)
        }
    }

    fun onAmountChange(raw: String) {
        if (!CalculatorInputFilter.isAcceptable(raw)) return
        updateForm { it.copy(amountText = raw) }
    }

    fun onRateChange(raw: String) {
        if (!CalculatorInputFilter.isAcceptable(raw)) return
        updateForm { it.copy(rateText = raw) }
    }

    /** Переключение режима сохраняет введённое число, завершая промежуточный ввод. */
    fun onModeChange(mode: VatMode) {
        if (mode == uiStateFlow.value.mode) return
        updateForm { state ->
            state.copy(
                mode = mode,
                amountText = finalized(state.amountText, AmountParser.AMOUNT),
                rateText = finalized(state.rateText, AmountParser.RATE),
            )
        }
    }

    /** Потеря фокуса завершает редактирование поля: `12,` трактуется как `12`. */
    fun onFieldFocusChanged(field: Field, focused: Boolean) {
        if (focused) return
        updateForm { state ->
            when (field) {
                Field.AMOUNT -> state.copy(amountText = finalized(state.amountText, AmountParser.AMOUNT))
                Field.RATE -> state.copy(rateText = finalized(state.rateText, AmountParser.RATE))
            }
        }
    }

    /**
     * «Сохранить»: новая форма создаёт запись и связывает форму с её ID,
     * повторное сохранение обновляет связанную запись без дублей. При ошибке
     * записи ввод и связь сохраняются, сообщение об успехе не показывается.
     */
    fun save() {
        val state = uiStateFlow.value
        val results = state.results ?: return
        if (!state.canSave) return
        scope.launch {
            val amount = evaluationAmount(state) ?: return@launch
            val rate = evaluationRate(state) ?: return@launch
            val input = CalculationInput(amount, rate, state.mode)
            val targetId = state.openEntryId
            try {
                val savedId = if (targetId == null) {
                    historyStore.insert(input.amount, input.ratePercent, input.mode)
                } else {
                    historyStore.update(targetId, input.amount, input.ratePercent, input.mode)
                    targetId
                }
                uiStateFlow.value = uiStateFlow.value.copy(
                    openEntryId = savedId,
                    saveNotice = SaveNotice.SAVED,
                )
                baseline = snapshot()
                refreshUnsavedFlag()
            } catch (error: Exception) {
                uiStateFlow.value = uiStateFlow.value.copy(saveNotice = SaveNotice.SAVE_FAILED)
            }
        }
    }

    /** «Новый расчёт»: при несохранённых изменениях требуется подтверждение сброса. */
    fun newCalculation() {
        if (uiStateFlow.value.hasUnsavedChanges) {
            uiStateFlow.value = uiStateFlow.value.copy(confirmDiscard = true)
        } else {
            resetForm()
        }
    }

    fun confirmDiscard() {
        uiStateFlow.value = uiStateFlow.value.copy(confirmDiscard = false)
        resetForm()
    }

    fun dismissDiscard() {
        uiStateFlow.value = uiStateFlow.value.copy(confirmDiscard = false)
    }

    fun consumeSaveNotice() {
        uiStateFlow.value = uiStateFlow.value.copy(saveNotice = null)
    }

    /** Подтверждение показа предупреждения миграции; за ним может следовать следующее. */
    fun acknowledgeMigrationNotice() {
        scope.launch {
            when (val notice = uiStateFlow.value.migrationNotice) {
                is MigrationNotice.Preferences -> appStateStore.markPrefsReportShown()
                is MigrationNotice.Journal -> historyStore.markJournalReportShown()
                null -> Unit
            }
            uiStateFlow.value = uiStateFlow.value.copy(migrationNotice = pendingNotices.removeFirstOrNull())
        }
    }

    private fun resetForm() {
        applyForm("", "22", VatMode.INCLUSIVE, openEntryId = null)
        baseline = snapshot()
        uiStateFlow.value = uiStateFlow.value.copy(saveNotice = null)
        refreshUnsavedFlag()
    }

    private fun applyForm(amountText: String, rateText: String, mode: VatMode, openEntryId: Long?) {
        uiStateFlow.value = evaluated(
            uiStateFlow.value.copy(
                amountText = amountText,
                rateText = rateText,
                mode = mode,
                openEntryId = openEntryId,
            ),
        )
        refreshUnsavedFlag()
    }

    private fun updateForm(transform: (UiState) -> UiState) {
        uiStateFlow.value = evaluated(transform(uiStateFlow.value))
        refreshUnsavedFlag()
    }

    private fun evaluated(state: UiState): UiState {
        val evaluation: CalculatorEvaluation =
            CalculatorEvaluator.evaluate(state.amountText, state.rateText, state.mode)
        return state.copy(
            amountError = (evaluation.amountState as? ParsedInput.Invalid)?.error,
            rateError = (evaluation.rateState as? ParsedInput.Invalid)?.error,
            results = (evaluation.outcome as? CalculatorEvaluation.Outcome.Ready)?.result,
            canSave = evaluation.isShareReady,
        )
    }

    private fun refreshUnsavedFlag() {
        uiStateFlow.value = uiStateFlow.value.copy(hasUnsavedChanges = snapshot() != baseline)
    }

    private fun snapshot(): FormSnapshot = uiStateFlow.value.let {
        FormSnapshot(it.amountText, it.rateText, it.mode, it.openEntryId)
    }

    private fun finalized(raw: String, parser: AmountParser): String {
        val value = when (val parsed = parser.finalize(raw)) {
            is ParsedInput.Valid -> parsed.value.toPlainString()
            else -> return raw
        }
        return value.ifEmpty { raw }
    }

    private fun evaluationAmount(state: UiState): BigDecimal? =
        (AmountParser.AMOUNT.finalize(state.amountText) as? ParsedInput.Valid)?.value

    private fun evaluationRate(state: UiState): BigDecimal? =
        (AmountParser.RATE.finalize(state.rateText) as? ParsedInput.Valid)?.value

    private suspend fun queueMigrationNotices(journalReport: JournalMigrationReport) {
        val prefsReport: PrefsMigrationReport? = appStateStore.loadPrefsReport()
        if (prefsReport != null && prefsReport.hasNotices && !appStateStore.isPrefsReportShown()) {
            pendingNotices.addLast(MigrationNotice.Preferences(prefsReport))
        }
        val journalHasContent = journalReport.importedCount > 0 ||
            journalReport.roundedCount > 0 ||
            journalReport.skipped.isNotEmpty()
        if (journalReport.completed && journalHasContent && !historyStore.isJournalReportShown()) {
            pendingNotices.addLast(MigrationNotice.Journal(journalReport))
        }
        uiStateFlow.value = uiStateFlow.value.copy(migrationNotice = pendingNotices.removeFirstOrNull())
    }

    /** Автосохранение состояния формы с задержкой: без записи на диск при каждом нажатии. */
    @OptIn(FlowPreview::class)
    private fun scheduleAutosave() {
        scope.launch {
            uiStateFlow
                .map { state ->
                    AppState(
                        amountText = state.amountText,
                        rateText = state.rateText,
                        mode = state.mode,
                        themeSetting = themeSetting,
                        languageSetting = languageSetting,
                        openEntryId = state.openEntryId,
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .debounce(AUTOSAVE_DELAY_MS)
                .collect { state -> appStateStore.save(state) }
        }
    }

    enum class Field { AMOUNT, RATE }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 500L

        fun factory(application: android.app.Application) = viewModelFactory {
            initializer {
                val stateStore = AppStateStore(application)
                val historyStore = HistoryStore(application)
                CalculatorViewModel(
                    appStateStore = stateStore,
                    historyStore = historyStore,
                    migrator = StateMigrator(application, stateStore, historyStore),
                )
            }
        }
    }
}
