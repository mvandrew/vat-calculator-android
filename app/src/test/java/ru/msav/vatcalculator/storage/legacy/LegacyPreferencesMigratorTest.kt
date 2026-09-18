package ru.msav.vatcalculator.storage.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.LegacyPreferencesSource

class LegacyPreferencesMigratorTest {

    private val sum22 = "ru.msav.ruvattaxcalculator.settings.SumAmount"
    private val rate22 = "ru.msav.ruvattaxcalculator.settings.VatRate"
    private val type22 = "ru.msav.ruvattaxcalculator.settings.Type"

    @Test
    fun completeVersion22StateIsMigrated() {
        val outcome = LegacyPreferencesMigrator.migrate(
            values22 = mapOf(sum22 to 122.0f, rate22 to 20.0f, type22 to 1),
            values15 = emptyMap(),
        )
        assertEquals(LegacyPreferencesSource.VERSION_2_2, outcome.report.source)
        assertEquals("122.00", outcome.amountText)
        assertEquals("20", outcome.rateText)
        assertEquals(VatMode.EXCLUSIVE, outcome.mode)
    }

    @Test
    fun version22HasPriorityAndDamagedFieldsAreReported() {
        val outcome = LegacyPreferencesMigrator.migrate(
            values22 = mapOf(sum22 to "broken", rate22 to 99.99999f, type22 to 7),
            values15 = mapOf(
                "ru.msav.passwordgenerator.sourceSum" to 555.0f,
                "ru.msav.passwordgenerator.vatPercent" to 18.0f,
                "ru.msav.passwordgenerator.allocateSumFlag" to false,
            ),
        )
        assertEquals(LegacyPreferencesSource.VERSION_2_2, outcome.report.source)
        assertEquals("", outcome.amountText)
        assertEquals("", outcome.rateText)
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
        assertTrue(outcome.report.amountInvalid)
        assertTrue(outcome.report.rateInvalid)
        assertTrue(outcome.report.modeInvalid)
    }
}
