package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.WeekStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** The Stats formatting helpers: numbers, signed percents, the empty-period rule and the volume text (English). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en")
class StatsFormattingTest {
    @get:Rule
    val compose = createComposeRule()

    private val en = Locale.US
    private val ptBr = Locale.forLanguageTag("pt-BR")

    private fun volume(kg: Double): String {
        var text = ""
        compose.setContent { text = volumeText(kg) }
        compose.waitForIdle()
        return text
    }

    // volumeText

    @Test
    fun `volume below a tonne is shown in kg`() {
        assertEquals("850 kg", volume(850.0))
    }

    @Test
    fun `volume of zero is 0 kg`() {
        assertEquals("0 kg", volume(0.0))
    }

    @Test
    fun `volume from a tonne on is shown in tonnes with one decimal`() {
        assertEquals("12.4 t", volume(12_400.0))
    }

    @Test
    fun `volume of exactly a tonne switches to tonnes`() {
        val text = volume(1000.0)
        assertTrue(text, text.endsWith(" t"))
        assertTrue(text, text.startsWith("1"))
    }

    @Test
    fun `volume in tonnes is rounded to one decimal`() {
        assertEquals("1.6 t", volume(1_560.0))
    }

    // formatDecimal

    @Test
    fun `formatDecimal drops a zero decimal`() {
        assertEquals("3", formatDecimal(3.0, 1, en))
    }

    @Test
    fun `formatDecimal keeps one decimal`() {
        assertEquals("2.8", formatDecimal(2.8, 1, en))
    }

    @Test
    fun `formatDecimal rounds to the digits asked`() {
        assertEquals("3.1", formatDecimal(3.1234, 1, en))
        assertEquals("3.12", formatDecimal(3.1234, 2, en))
        assertEquals("15", formatDecimal(14.6, 0, en))
    }

    @Test
    fun `formatDecimal does not group thousands`() {
        assertEquals("12345.5", formatDecimal(12345.5, 1, en))
    }

    @Test
    fun `formatDecimal follows the locale`() {
        assertEquals("2,8", formatDecimal(2.8, 1, ptBr))
    }

    // formatOneDecimal

    @Test
    fun `formatOneDecimal always shows one decimal`() {
        assertEquals("6.0", formatOneDecimal(6.0, en))
        assertEquals("6.4", formatOneDecimal(6.4, en))
        assertEquals("0.0", formatOneDecimal(0.0, en))
    }

    @Test
    fun `formatOneDecimal follows the locale`() {
        assertEquals("6,0", formatOneDecimal(6.0, ptBr))
    }

    // signedPercentNumber

    @Test
    fun `a rise has a plus sign`() {
        assertEquals("+6.4", signedPercentNumber(6.4, en))
    }

    @Test
    fun `a fall has a minus sign`() {
        assertEquals("-2.0", signedPercentNumber(-2.0, en))
    }

    @Test
    fun `no change has no sign`() {
        assertEquals("0.0", signedPercentNumber(0.0, en))
    }

    @Test
    fun `the sign follows the locale's decimal separator`() {
        assertEquals("+6,4", signedPercentNumber(6.4, ptBr))
        assertEquals("-2,0", signedPercentNumber(-2.0, ptBr))
    }

    // isEmptyPeriod

    @Test
    fun `a full report is not an empty period`() {
        assertFalse(StatsSamples.full.isEmptyPeriod())
    }

    @Test
    fun `a report without any training is an empty period`() {
        assertTrue(StatsSamples.empty.isEmptyPeriod())
    }

    @Test
    fun `a cardio-only report is not an empty period`() {
        assertFalse(StatsSamples.cardioOnly.isEmptyPeriod())
    }

    @Test
    fun `working sets in a single week make the period non-empty`() {
        val weeks = StatsSamples.empty.weeks.toMutableList()
        weeks[2] = WeekStats(weeks[2].weekStart, sessions = 1, trainingDays = 1, workingSets = 3, volumeKg = 900.0)
        assertFalse(StatsSamples.empty.copy(weeks = weeks).isEmptyPeriod())
    }

    @Test
    fun `a report with muscle weeks but no week totals is not an empty period`() {
        val report = StatsSamples.empty.copy(muscleWeeks = StatsSamples.full.muscleWeeks)
        assertFalse(report.isEmptyPeriod())
    }
}
