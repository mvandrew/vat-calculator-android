package ru.msav.vatcalculator.storage.legacy

import android.content.Context

/**
 * Чтение старых файлов preferences без их изменения (storage.md §6).
 * Возвращает полные карты значений; выбор схемы и валидация выполняет
 * [LegacyPreferencesMigrator].
 */
object LegacyPreferencesStore {

    private const val FILE_2_2 = "ru.msav.ruvattaxcalculator.settings"
    private const val FILE_1_5 = "ru.msav.passwordgenerator.settings"

    fun readVersion22(context: Context): Map<String, Any?> = read(context, FILE_2_2)

    fun readVersion15(context: Context): Map<String, Any?> = read(context, FILE_1_5)

    private fun read(context: Context, fileName: String): Map<String, Any?> =
        context.applicationContext
            .getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .all
}
