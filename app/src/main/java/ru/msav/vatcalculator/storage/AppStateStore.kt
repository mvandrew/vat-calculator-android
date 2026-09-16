package ru.msav.vatcalculator.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.msav.vatcalculator.calculation.VatMode

/**
 * Хранение состояния формы и настроек в SharedPreferences (storage.md §6).
 *
 * Файл и ключи новые; со старыми хранилищами 1.5/2.2 не пересекаются.
 * Отсутствие [KEY_SCHEMA_VERSION] означает, что новое состояние ещё не
 * создавалось — это разрешает однократный импорт старых параметров.
 * Начальное состояние и отметка его миграции записываются одним editor,
 * поэтому миграция не может зафиксироваться без самого состояния.
 *
 * Все операции выполняются вне главного потока; обработчик нажатий UI
 * не выполняет синхронного дискового ввода-вывода.
 */
class AppStateStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Состояние формы; null — новое приложение запускается впервые (состояния ещё нет). */
    suspend fun load(): AppState? = withContext(Dispatchers.IO) {
        if (!preferences.contains(KEY_SCHEMA_VERSION)) {
            return@withContext null
        }
        AppState(
            amountText = preferences.getString(KEY_AMOUNT_TEXT, null).orEmpty(),
            rateText = preferences.getString(KEY_RATE_TEXT, null).orEmpty(),
            mode = preferences.getString(KEY_MODE, null)
                ?.let { stored -> VatMode.entries.firstOrNull { it.name == stored } }
                ?: VatMode.INCLUSIVE,
            themeSetting = preferences.getString(KEY_THEME, null)
                ?.let { stored -> ThemeSetting.entries.firstOrNull { it.name == stored } }
                ?: ThemeSetting.SYSTEM,
            languageSetting = preferences.getString(KEY_LANGUAGE, null)
                ?.let { stored -> LanguageSetting.entries.firstOrNull { it.name == stored } }
                ?: LanguageSetting.SYSTEM,
            openEntryId = preferences.getLong(KEY_OPEN_ENTRY_ID, NO_ENTRY_ID)
                .takeIf { it != NO_ENTRY_ID },
        )
    }

    /** Автосохранение состояния; отметки миграции не затрагиваются. */
    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_AMOUNT_TEXT, state.amountText)
            .putString(KEY_RATE_TEXT, state.rateText)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_THEME, state.themeSetting.name)
            .putString(KEY_LANGUAGE, state.languageSetting.name)
            .putLong(KEY_OPEN_ENTRY_ID, state.openEntryId ?: NO_ENTRY_ID)
            .apply()
    }

    /**
     * Первая запись состояния вместе с результатом импорта старых параметров:
     * состояние, отметка миграции и отчёт фиксируются одним атомарным editor.
     */
    suspend fun saveMigratedInitial(state: AppState, report: PrefsMigrationReport) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_AMOUNT_TEXT, state.amountText)
            .putString(KEY_RATE_TEXT, state.rateText)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_THEME, state.themeSetting.name)
            .putString(KEY_LANGUAGE, state.languageSetting.name)
            .putLong(KEY_OPEN_ENTRY_ID, state.openEntryId ?: NO_ENTRY_ID)
            .putBoolean(KEY_PREFS_MIGRATED, true)
            .putString(KEY_PREFS_SOURCE, report.source.name)
            .putBoolean(KEY_PREFS_AMOUNT_ROUNDED, report.amountRounded)
            .putBoolean(KEY_PREFS_RATE_ROUNDED, report.rateRounded)
            .putBoolean(KEY_PREFS_AMOUNT_INVALID, report.amountInvalid)
            .putBoolean(KEY_PREFS_RATE_INVALID, report.rateInvalid)
            .putBoolean(KEY_PREFS_MODE_INVALID, report.modeInvalid)
            .putBoolean(KEY_PREFS_REPORT_SHOWN, false)
            .apply()
    }

    /** true, если импорт старых параметров уже выполнялся (успешно или с замечаниями). */
    suspend fun isPrefsMigrated(): Boolean = withContext(Dispatchers.IO) {
        preferences.getBoolean(KEY_PREFS_MIGRATED, false)
    }

    /** Отчёт переноса параметров для однократного показа; null — миграции не было. */
    suspend fun loadPrefsReport(): PrefsMigrationReport? = withContext(Dispatchers.IO) {
        if (!preferences.getBoolean(KEY_PREFS_MIGRATED, false)) {
            return@withContext null
        }
        PrefsMigrationReport(
            source = preferences.getString(KEY_PREFS_SOURCE, null)
                ?.let { stored -> LegacyPreferencesSource.entries.firstOrNull { it.name == stored } }
                ?: LegacyPreferencesSource.NONE,
            amountRounded = preferences.getBoolean(KEY_PREFS_AMOUNT_ROUNDED, false),
            rateRounded = preferences.getBoolean(KEY_PREFS_RATE_ROUNDED, false),
            amountInvalid = preferences.getBoolean(KEY_PREFS_AMOUNT_INVALID, false),
            rateInvalid = preferences.getBoolean(KEY_PREFS_RATE_INVALID, false),
            modeInvalid = preferences.getBoolean(KEY_PREFS_MODE_INVALID, false),
        )
    }

    /** Отметка «отчёт переноса параметров показан»: второй раз не повторяется. */
    suspend fun markPrefsReportShown() = withContext(Dispatchers.IO) {
        preferences.edit().putBoolean(KEY_PREFS_REPORT_SHOWN, true).apply()
    }

    suspend fun isPrefsReportShown(): Boolean = withContext(Dispatchers.IO) {
        preferences.getBoolean(KEY_PREFS_REPORT_SHOWN, true)
    }

    companion object {
        private const val PREFERENCES_NAME = "ru.msav.vatcalculator.state"
        private const val SCHEMA_VERSION = 1
        private const val NO_ENTRY_ID = Long.MIN_VALUE

        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_AMOUNT_TEXT = "amount_text"
        private const val KEY_RATE_TEXT = "rate_text"
        private const val KEY_MODE = "mode"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_OPEN_ENTRY_ID = "open_entry_id"
        private const val KEY_PREFS_MIGRATED = "prefs_migrated"
        private const val KEY_PREFS_SOURCE = "prefs_source"
        private const val KEY_PREFS_AMOUNT_ROUNDED = "prefs_amount_rounded"
        private const val KEY_PREFS_RATE_ROUNDED = "prefs_rate_rounded"
        private const val KEY_PREFS_AMOUNT_INVALID = "prefs_amount_invalid"
        private const val KEY_PREFS_RATE_INVALID = "prefs_rate_invalid"
        private const val KEY_PREFS_MODE_INVALID = "prefs_mode_invalid"
        private const val KEY_PREFS_REPORT_SHOWN = "prefs_report_shown"
    }
}
