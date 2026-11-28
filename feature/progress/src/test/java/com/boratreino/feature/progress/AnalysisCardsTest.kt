package com.boratreino.feature.progress

import com.boratreino.core.model.EnergyHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which Analysis cards a state shows, and in which order. */
class AnalysisCardsTest {
    @Test
    fun `a full period lists its cards in the order of the Analysis groups`() {
        assertEquals(
            listOf(
                AnalysisCard.HARD_SETS,
                AnalysisCard.STRENGTH,
                AnalysisCard.RECORDS,
                AnalysisCard.REP_RANGES,
                AnalysisCard.RELATIVE_STRENGTH,
                AnalysisCard.WEEK_MUSCLES,
                AnalysisCard.BODY_MAP,
                AnalysisCard.MUSCLES,
                AnalysisCard.WEEKLY_VOLUME,
                AnalysisCard.SUMMARY,
                AnalysisCard.ENERGY,
                AnalysisCard.TIMES,
                AnalysisCard.RECENCY,
                AnalysisCard.MEASUREMENTS,
            ),
            analysisCards(StatsSamples.state()),
        )
    }

    @Test
    fun `the energy card is listed right after the summary when there are energy points`() {
        val cards = analysisCards(StatsSamples.state())

        assertEquals(cards.indexOf(AnalysisCard.SUMMARY) + 1, cards.indexOf(AnalysisCard.ENERGY))
    }

    @Test
    fun `without an energy history there is no energy card`() {
        assertFalse(AnalysisCard.ENERGY in analysisCards(StatsSamples.state(energy = null)))
    }

    @Test
    fun `an energy history without points has no energy card`() {
        val none = EnergyHistory(points = emptyList(), last7AverageKcal = null, last28AverageKcal = null, averageBurnKcal = null, trainedDays = 0)

        assertFalse(AnalysisCard.ENERGY in analysisCards(StatsSamples.state(energy = none)))
    }

    @Test
    fun `an empty period lists only the measurements card`() {
        assertEquals(listOf(AnalysisCard.MEASUREMENTS), analysisCards(StatsSamples.state(StatsSamples.empty)))
    }

    @Test
    fun `before the first report no card is listed`() {
        assertEquals(emptyList<AnalysisCard>(), analysisCards(StatsUiState()))
    }

    @Test
    fun `a one week period has no strength trend but keeps its records`() {
        val cards = analysisCards(StatsSamples.state(StatsSamples.oneWeek))

        assertFalse(AnalysisCard.STRENGTH in cards)
        assertTrue(AnalysisCard.RECORDS in cards)
        assertTrue(AnalysisCard.HARD_SETS in cards)
    }

    @Test
    fun `the cardio card comes after the summary of the habit group when there are cardio blocks`() {
        val cards = analysisCards(
            StatsSamples.state(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks), energy = null),
        )

        assertEquals(cards.indexOf(AnalysisCard.SUMMARY) + 1, cards.indexOf(AnalysisCard.CARDIO))
    }

    @Test
    fun `the cardio card comes after the energy card when both are shown`() {
        val cards = analysisCards(StatsSamples.state(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks)))

        assertEquals(cards.indexOf(AnalysisCard.ENERGY) + 1, cards.indexOf(AnalysisCard.CARDIO))
    }

    @Test
    fun `the proportions card is last of the body group when there is a waist and hip series`() {
        val cards = analysisCards(StatsSamples.state(StatsSamples.full.copy(waistHip = Phase46Samples.waistHipWeekly)))

        assertEquals(AnalysisCard.MEASUREMENTS, cards[cards.size - 2])
        assertEquals(AnalysisCard.PROPORTIONS, cards.last())
    }
}
