package ru.msav.vatcalculator.storage.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.storage.LegacyPreferencesSource

class LegacyPreferencesMigratorTest {

    private fun migrate22(values: Map<String, Any?> = emptyMap()) =
        LegacyPreferencesMigrator.migrate(values, emptyMap())

    private fun migrate15(values: Map<String, Any?> = emptyMap()) =
        LegacyPreferencesMigrator.migrate(emptyMap(), values)

    private val key22Sum = "ru.msav.ruvattaxcalculator.settings.SumAmount"
    private val key22Rate = "ru.msav.ruvattaxcalculator.settings.VatRate"
    private val key22Type = "ru.msav.ruvattaxcalculator.settings.Type"
    private val key15Sum = "ru.msav.passwordgenerator.sourceSum"
    private val key15Rate = "ru.msav.passwordgenerator.vatPercent"
    private val key15Flag = "ru.msav.passwordgenerator.allocateSumFlag"

    @Test
    fun missingBothSchemesGivesStartState() {
        val outcome = LegacyPreferencesMigrator.migrate(emptyMap(), emptyMap())
        assertEquals(LegacyPreferencesSource.NONE, outcome.report.source)
        assertEquals("", outcome.amountText)
        assertEquals("22", outcome.rateText)
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
        assertFalse(outcome.report.hasNotices)
    }

    @Test
    fun fullVersion22SchemeIsMigrated() {
        val outcome = migrate22(
            mapOf(key22Sum to 122.0f, key22Rate to 20.0f, key22Type to 1),
        )
        assertEquals(LegacyPreferencesSource.VERSION_2_2, outcome.report.source)
        assertEquals("122.00", outcome.amountText)
        assertEquals("20", outcome.rateText)
        assertEquals(VatMode.EXCLUSIVE, outcome.mode)
        assertFalse(outcome.report.hasNotices)
    }

    @Test
    fun version22TypeZeroIsInclusive() {
        val outcome = migrate22(mapOf(key22Type to 0))
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
    }

    @Test
    fun userRateIsNotReplacedByStartValue() {
        val outcome = migrate22(mapOf(key22Rate to 18.5f))
        assertEquals("18.5", outcome.rateText)
    }

    @Test
    fun partialVersion22KeepsStartValuesForMissingFields() {
        val outcome = migrate22(mapOf(key22Rate to 18.0f))
        assertEquals("", outcome.amountText)
        assertEquals("18", outcome.rateText)
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
        assertFalse(outcome.report.amountInvalid)
        assertFalse(outcome.report.modeInvalid)
    }

    @Test
    fun wrongTypeLeavesFieldEmptyWithNotice() {
        val outcome = migrate22(
            mapOf(key22Sum to "122", key22Rate to 20.0f),
        )
        assertEquals("", outcome.amountText)
        assertTrue(outcome.report.amountInvalid)
        assertEquals("20", outcome.rateText)
    }

    @Test
    fun unknownModeRequiresExplicitChoice() {
        val outcome = migrate22(mapOf(key22Type to 7))
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
        assertTrue(outcome.report.modeInvalid)
    }

    @Test
    fun rateNormalizedTo100IsInvalid() {
        val outcome = migrate22(mapOf(key22Rate to 99.99999f))
        assertEquals("", outcome.rateText)
        assertTrue(outcome.report.rateInvalid)
    }

    @Test
    fun amountOutOfRangeIsInvalid() {
        val outcome = migrate22(mapOf(key22Sum to 1.0e12f))
        assertEquals("", outcome.amountText)
        assertTrue(outcome.report.amountInvalid)
    }

    @Test
    fun precisionLossIsReported() {
        val outcome = migrate22(mapOf(key22Sum to 100.005f))
        assertEquals("100.01", outcome.amountText)
        assertTrue(outcome.report.amountRounded)
    }

    @Test
    fun version15SchemeIsMigrated() {
        val outcome = migrate15(
            mapOf(key15Sum to 100.5f, key15Rate to 18.0f, key15Flag to true),
        )
        assertEquals(LegacyPreferencesSource.VERSION_1_5, outcome.report.source)
        assertEquals("100.50", outcome.amountText)
        assertEquals("18", outcome.rateText)
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
    }

    @Test
    fun version15FalseFlagIsExclusive() {
        val outcome = migrate15(mapOf(key15Flag to false))
        assertEquals(VatMode.EXCLUSIVE, outcome.mode)
    }

    @Test
    fun version15BooleanKeyIsNotReadAsVersion22Int() {
        // Неверный тип режима 1.5 (не boolean) требует явного выбора.
        val outcome = migrate15(mapOf(key15Flag to 1))
        assertEquals(VatMode.INCLUSIVE, outcome.mode)
        assertTrue(outcome.report.modeInvalid)
    }

    @Test
    fun version22TakesPriorityOverVersion15() {
        // Хотя бы один ключ 2.2 выбирает эту схему целиком; поля поколений не смешиваются.
        val outcome = LegacyPreferencesMigrator.migrate(
            values22 = mapOf(key22Rate to 20.0f),
            values15 = mapOf(key15Sum to 555.0f, key15Rate to 18.0f, key15Flag to false),
        )
        assertEquals(LegacyPreferencesSource.VERSION_2_2, outcome.report.source)
        assertEquals("", outcome.amountText)
        assertEquals("20", outcome.rateText)
    }
}
