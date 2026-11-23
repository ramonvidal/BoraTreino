package com.boratreino.feature.progress

import com.boratreino.core.model.CardioBlock
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The bike console values in the cardio helpers: the chips' data, the default measure and the period totals (phase 42). */
class CardioMetricsBikeValuesTest {
    private val bike = Machine(30L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
    private val day = LocalDate.of(2026, 10, 20)

    private fun point(value: Double) = SessionPoint(
        sessionId = 1L,
        date = day,
        value = value,
        topSet = WorkoutSet(1L, 1L, bike.id, 0, null, null, 600, null, null, Instant.parse("2026-10-20T10:00:00Z")),
        setCount = 1,
    )

    private fun progress(cardioPoints: Map<ProgressMetric, List<SessionPoint>>) =
        MachineProgress(bike, ProgressMetric.MINUTES, emptyList(), cardioPoints = cardioPoints)

    private fun bikeBlock(minutes: Int, powerAvg: Double? = null, tssTotal: Int? = null) = CardioBlock(
        weekStart = day,
        minutes = minutes,
        distanceM = 0,
        sessions = 1,
        intensityAvg = null,
        speedKmh = null,
        powerAvg = powerAvg,
        tssTotal = tssTotal,
    )

    // The measures

    @Test
    fun `the console measures are chart measures for a cardio machine`() {
        assertTrue(ProgressMetric.POWER.isCardioValue())
        assertTrue(ProgressMetric.HEART_RATE.isCardioValue())
        assertTrue(ProgressMetric.WATTS_PER_KG.isCardioValue())
        assertTrue(ProgressMetric.TSS.isCardioValue())
        assertTrue(ProgressMetric.INTENSITY_FACTOR.isCardioValue())
        assertFalse(ProgressMetric.MINUTES.isCardioValue())
        assertFalse(ProgressMetric.WEIGHT.isCardioValue())
    }

    @Test
    fun `the chips offer the measures in the order of the data layer`() {
        assertEquals(
            listOf(
                ProgressMetric.SPEED,
                ProgressMetric.POWER,
                ProgressMetric.INTENSITY_FACTOR,
                ProgressMetric.WATTS_PER_KG,
                ProgressMetric.TSS,
                ProgressMetric.HEART_RATE,
                ProgressMetric.INTENSITY,
                ProgressMetric.DISTANCE,
                ProgressMetric.MINUTES,
            ),
            CARDIO_METRICS,
        )
    }

    @Test
    fun `only the console measures with points are available, in chip order`() {
        val p = progress(
            mapOf(
                ProgressMetric.TSS to listOf(point(48.0)),
                ProgressMetric.POWER to listOf(point(160.0)),
                ProgressMetric.HEART_RATE to emptyList(),
                ProgressMetric.WATTS_PER_KG to emptyList(),
                ProgressMetric.INTENSITY_FACTOR to listOf(point(0.79)),
            ),
        )

        assertEquals(
            listOf(ProgressMetric.POWER, ProgressMetric.INTENSITY_FACTOR, ProgressMetric.TSS),
            availableCardioMetrics(p),
        )
    }

    @Test
    fun `a heart rate or W per kg without points is not a chip`() {
        val p = progress(mapOf(ProgressMetric.HEART_RATE to emptyList(), ProgressMetric.WATTS_PER_KG to emptyList()))

        assertTrue(availableCardioMetrics(p).isEmpty())
    }

    @Test
    fun `a TSS of zero is data, so its chip shows`() {
        assertTrue(hasCardioData(ProgressMetric.TSS, listOf(point(0.0))))
    }

    @Test
    fun `power and IF points are data when they exist`() {
        assertTrue(hasCardioData(ProgressMetric.POWER, listOf(point(160.0))))
        assertFalse(hasCardioData(ProgressMetric.POWER, emptyList()))
        assertTrue(hasCardioData(ProgressMetric.INTENSITY_FACTOR, listOf(point(0.79))))
    }

    // The default measure

    @Test
    fun `power is the default when there is power and no speed`() {
        assertEquals(
            ProgressMetric.POWER,
            defaultCardioMetricOf(listOf(ProgressMetric.POWER, ProgressMetric.INTENSITY_FACTOR, ProgressMetric.MINUTES)),
        )
    }

    @Test
    fun `speed still beats power`() {
        assertEquals(
            ProgressMetric.SPEED,
            defaultCardioMetricOf(listOf(ProgressMetric.SPEED, ProgressMetric.POWER, ProgressMetric.MINUTES)),
        )
    }

    @Test
    fun `intensity factor is the default when there is no power and no speed`() {
        assertEquals(
            ProgressMetric.INTENSITY_FACTOR,
            defaultCardioMetricOf(listOf(ProgressMetric.INTENSITY_FACTOR, ProgressMetric.INTENSITY, ProgressMetric.MINUTES)),
        )
    }

    @Test
    fun `intensity is the default when there is an intensity and no IF`() {
        assertEquals(
            ProgressMetric.INTENSITY,
            defaultCardioMetricOf(listOf(ProgressMetric.INTENSITY, ProgressMetric.TSS, ProgressMetric.MINUTES)),
        )
    }

    @Test
    fun `TSS, heart rate and W per kg do not choose the default`() {
        assertEquals(
            ProgressMetric.MINUTES,
            defaultCardioMetricOf(listOf(ProgressMetric.TSS, ProgressMetric.HEART_RATE, ProgressMetric.WATTS_PER_KG, ProgressMetric.MINUTES)),
        )
    }

    // cardioTotals: power and TSS

    @Test
    fun `the period's power average weighs each block by its minutes`() {
        val totals = cardioTotals(
            listOf(
                bikeBlock(minutes = 30, powerAvg = 160.0),
                bikeBlock(minutes = 60, powerAvg = 130.0),
            ),
        )

        // (160 x 30 + 130 x 60) / 90
        assertEquals(140.0, totals.powerAvg!!, 1e-9)
    }

    @Test
    fun `blocks without power leave out of the power average`() {
        val totals = cardioTotals(
            listOf(
                bikeBlock(minutes = 30, powerAvg = 160.0),
                bikeBlock(minutes = 90, powerAvg = null),
            ),
        )

        assertEquals(160.0, totals.powerAvg!!, 1e-9)
    }

    @Test
    fun `the power average is null when no block has power`() {
        assertNull(cardioTotals(listOf(bikeBlock(minutes = 30))).powerAvg)
        assertNull(cardioTotals(emptyList()).powerAvg)
    }

    @Test
    fun `the period's TSS is the sum of the blocks' TSS`() {
        val totals = cardioTotals(
            listOf(
                bikeBlock(minutes = 30, tssTotal = 20),
                bikeBlock(minutes = 40, tssTotal = 28),
                bikeBlock(minutes = 15),
            ),
        )

        assertEquals(48, totals.tssTotal)
    }

    @Test
    fun `a block TSS of zero keeps the total at zero`() {
        assertEquals(0, cardioTotals(listOf(bikeBlock(minutes = 30, tssTotal = 0))).tssTotal)
    }

    @Test
    fun `the TSS total is null when no block has TSS`() {
        assertNull(cardioTotals(listOf(bikeBlock(minutes = 30, powerAvg = 160.0))).tssTotal)
        assertNull(cardioTotals(emptyList()).tssTotal)
    }
}
