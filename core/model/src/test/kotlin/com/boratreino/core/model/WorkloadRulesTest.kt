package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Acute:chronic workload. Today is Thursday 26 November 2026. Expected numbers are worked out by hand:
 * acute = sets in the last 7 days, chronic = sets in the last 28 days / 4, ratio = acute / chronic.
 */
class WorkloadRulesTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 26)

    private fun ago(days: Int): LocalDate = today.minusDays(days.toLong())

    /** Base sets on the first day of the 28 day window (27 days ago) and acute sets today. */
    private fun withBaseAndAcute(base: Int, acute: Int): Map<LocalDate, Int> = mapOf(ago(27) to base, today to acute)

    @Test
    fun `no load at all is NO_DATA with nothing filled`() {
        val r = WorkloadRules.report(emptyMap(), today)

        assertEquals(0, r.acuteSets)
        assertEquals(0.0, r.chronicWeeklyAverage, 0.0)
        assertNull(r.acwr)
        assertEquals(AcwrStatus.NO_DATA, r.status)
        assertNull(r.monotony)
        assertNull(r.strain)
        assertEquals(0, r.historyDays)
    }

    @Test
    fun `days with zero sets do not start the history`() {
        val r = WorkloadRules.report(mapOf(ago(40) to 0), today)

        assertEquals(AcwrStatus.NO_DATA, r.status)
        assertEquals(0, r.historyDays)
    }

    @Test
    fun `steady ten sets a day for 28 days is an acwr of 1 and OPTIMAL`() {
        val load = (0..27).associate { ago(it) to 10 }
        val r = WorkloadRules.report(load, today)

        // Acute 7 x 10 = 70, chronic 280 / 4 = 70, ratio 1.0. History exactly 28 days, so not building.
        assertEquals(70, r.acuteSets)
        assertEquals(70.0, r.chronicWeeklyAverage, 1e-12)
        assertEquals(1.0, r.acwr!!, 1e-12)
        assertEquals(AcwrStatus.OPTIMAL, r.status)
        assertEquals(28, r.historyDays)
    }

    @Test
    fun `history of 27 days is BUILDING_BASELINE with the ratio still filled`() {
        val load = (0..26).associate { ago(it) to 10 }
        val r = WorkloadRules.report(load, today)

        // Acute 70, chronic 270 / 4 = 67.5, ratio 70 / 67.5 = 1.037037.
        assertEquals(AcwrStatus.BUILDING_BASELINE, r.status)
        assertEquals(27, r.historyDays)
        assertEquals(67.5, r.chronicWeeklyAverage, 1e-12)
        assertEquals(70.0 / 67.5, r.acwr!!, 1e-12)
    }

    @Test
    fun `monotony is the population standard deviation of the last 7 days and zeros count`() {
        // Loads 7, 6, 5, 4, 3, 2, 1 from today back: mean 4, population variance 28 / 7 = 4, SD 2, monotony 2.
        val load = (0..6).associate { ago(it) to 7 - it }
        val r = WorkloadRules.report(load, today)

        assertEquals(28, r.acuteSets)
        assertEquals(2.0, r.monotony!!, 1e-12)
        assertEquals(56.0, r.strain!!, 1e-12)
    }

    @Test
    fun `zero days inside the week are part of monotony`() {
        // Only today has 7 sets. Mean 1, population variance (6 x 1 + 36) / 7 = 6, SD sqrt(6).
        // Monotony = 1 / sqrt(6) = 0.4082483, strain = 7 x 0.4082483 = 2.8577380. A sample SD would give a different value.
        val r = WorkloadRules.report(mapOf(today to 7), today)

        assertEquals(0.4082483, r.monotony!!, 1e-6)
        assertEquals(2.857738, r.strain!!, 1e-6)
    }

    @Test
    fun `monotony and strain are null when the week has no spread`() {
        val load = (0..6).associate { ago(it) to 10 }
        val r = WorkloadRules.report(load, today)

        assertEquals(70, r.acuteSets)
        assertNull(r.monotony)
        assertNull(r.strain)
    }

    @Test
    fun `the acute window is the last seven days including today`() {
        // 5 sets six days ago are in the window, 9 sets seven days ago are not.
        val r = WorkloadRules.report(mapOf(ago(6) to 5, ago(7) to 9), today)

        assertEquals(5, r.acuteSets)
        assertEquals(14.0 / 4.0, r.chronicWeeklyAverage, 1e-12)
    }

    // Status thresholds: acwr = acute / (base + acute) x 4, with 28 days of history.

    @Test
    fun `a ratio of 1 point 3 is still OPTIMAL`() {
        // Acute 13, chronic (27 + 13) / 4 = 10, ratio 1.3 exactly.
        val r = WorkloadRules.report(withBaseAndAcute(27, 13), today)

        assertEquals(1.3, r.acwr!!, 1e-12)
        assertEquals(AcwrStatus.OPTIMAL, r.status)
    }

    @Test
    fun `a ratio of 0 point 8 is OPTIMAL`() {
        // Acute 8, chronic (32 + 8) / 4 = 10, ratio 0.8 exactly.
        val r = WorkloadRules.report(withBaseAndAcute(32, 8), today)

        assertEquals(AcwrStatus.OPTIMAL, r.status)
    }

    @Test
    fun `a ratio of 0 point 5 is LOW`() {
        // Acute 5, chronic (35 + 5) / 4 = 10, ratio 0.5.
        val r = WorkloadRules.report(withBaseAndAcute(35, 5), today)

        assertEquals(0.5, r.acwr!!, 1e-12)
        assertEquals(AcwrStatus.LOW, r.status)
    }

    @Test
    fun `a ratio of 1 point 4 is HIGH`() {
        // Acute 7, chronic (13 + 7) / 4 = 5, ratio 1.4.
        val r = WorkloadRules.report(withBaseAndAcute(13, 7), today)

        assertEquals(AcwrStatus.HIGH, r.status)
    }

    @Test
    fun `a ratio of exactly 1 point 5 is still HIGH`() {
        // Acute 3, chronic (5 + 3) / 4 = 2, ratio 1.5 exactly.
        val r = WorkloadRules.report(withBaseAndAcute(5, 3), today)

        assertEquals(1.5, r.acwr!!, 1e-12)
        assertEquals(AcwrStatus.HIGH, r.status)
    }

    @Test
    fun `a ratio above 1 point 5 is VERY_HIGH`() {
        // Acute 38, chronic (62 + 38) / 4 = 25, ratio 1.52.
        val r = WorkloadRules.report(withBaseAndAcute(62, 38), today)

        assertEquals(1.52, r.acwr!!, 1e-12)
        assertEquals(AcwrStatus.VERY_HIGH, r.status)
    }

    @Test
    fun `a high ratio before 28 days of history is still BUILDING_BASELINE`() {
        val r = WorkloadRules.report(mapOf(ago(5) to 2, today to 20), today)

        assertEquals(AcwrStatus.BUILDING_BASELINE, r.status)
        assertEquals(6, r.historyDays)
    }

    @Test
    fun `no sets in the last 28 days is NO_DATA even with older history`() {
        val r = WorkloadRules.report(mapOf(ago(40) to 10), today)

        assertEquals(0, r.acuteSets)
        assertEquals(0.0, r.chronicWeeklyAverage, 0.0)
        assertNull(r.acwr)
        assertEquals(AcwrStatus.NO_DATA, r.status)
        assertEquals(41, r.historyDays)
        assertNull(r.monotony)
        assertNull(r.strain)
    }

    @Test
    fun `days after today are ignored`() {
        val steady = (0..27).associate { ago(it) to 10 }
        val withFuture = steady + (LocalDate.of(2026, 11, 27) to 100)

        val expected = WorkloadRules.report(steady, today)
        val actual = WorkloadRules.report(withFuture, today)

        assertEquals(expected, actual)
    }
}
