package com.boratreino.feature.progress

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
                AnalysisCard.TIMES,
                AnalysisCard.RECENCY,
                AnalysisCard.MEASUREMENTS,
            ),
            analysisCards(StatsSamples.state()),
        )
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
        val cards = analysisCards(StatsSamples.state(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks)))

        assertEquals(cards.indexOf(AnalysisCard.SUMMARY) + 1, cards.indexOf(AnalysisCard.CARDIO))
    }

    @Test
    fun `the proportions card is last of the body group when there is a waist and hip series`() {
        val cards = analysisCards(StatsSamples.state(StatsSamples.full.copy(waistHip = Phase46Samples.waistHipWeekly)))

        assertEquals(AnalysisCard.MEASUREMENTS, cards[cards.size - 2])
        assertEquals(AnalysisCard.PROPORTIONS, cards.last())
    }
}
