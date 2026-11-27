package com.boratreino.feature.progress

import com.boratreino.core.model.BalanceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Analysis sections: which section each card is in, which sections a state has, and the cards of each one. */
class AnalysisSectionsTest {
    private val cardsOfSection = mapOf(
        AnalysisSection.HIGHLIGHTS to listOf(
            AnalysisCard.TREND, AnalysisCard.WORKLOAD, AnalysisCard.PATTERNS, AnalysisCard.HARD_SETS, AnalysisCard.BALANCE,
        ),
        AnalysisSection.STRENGTH to listOf(
            AnalysisCard.STRENGTH, AnalysisCard.RECORDS, AnalysisCard.REP_RANGES,
            AnalysisCard.STRENGTH_VS_WEIGHT, AnalysisCard.RELATIVE_STRENGTH,
        ),
        AnalysisSection.MUSCLES to listOf(
            AnalysisCard.MUSCLE_VOLUME, AnalysisCard.WEEK_MUSCLES, AnalysisCard.BODY_MAP,
            AnalysisCard.MUSCLES, AnalysisCard.WEEKLY_VOLUME,
        ),
        AnalysisSection.HABIT to listOf(
            AnalysisCard.SUMMARY, AnalysisCard.CARDIO, AnalysisCard.TIMES, AnalysisCard.RECENCY,
        ),
        AnalysisSection.BODY to listOf(AnalysisCard.MEASUREMENTS, AnalysisCard.PROPORTIONS),
    )

    private val noHighlights = StatsSamples.full.copy(
        muscleWeeks = emptyList(),
        detailWeeks = emptyList(),
        machineTrends = emptyList(),
        workload = null,
        patterns = null,
        balance = BalanceStats(0, 0, 0, 0, 0, 0, 0),
    )

    // sectionOf

    @Test
    fun `every card is in exactly one section, the one the spec gives it`() {
        cardsOfSection.forEach { (section, cards) ->
            cards.forEach { assertEquals("$it", section, sectionOf(it)) }
        }
        val listed = cardsOfSection.values.flatten()
        assertEquals(AnalysisCard.entries.size, listed.size)
        assertEquals(AnalysisCard.entries.toSet(), listed.toSet())
    }

    @Test
    fun `there are five sections in the order of the chips`() {
        assertEquals(
            listOf("HIGHLIGHTS", "STRENGTH", "MUSCLES", "HABIT", "BODY"),
            AnalysisSection.entries.map { it.name },
        )
    }

    // analysisSections

    @Test
    fun `a full period has every section in order`() {
        assertEquals(
            AnalysisSection.entries.toList(),
            analysisSections(StatsSamples.state()),
        )
    }

    @Test
    fun `an empty period has only the body section`() {
        assertEquals(listOf(AnalysisSection.BODY), analysisSections(StatsSamples.state(StatsSamples.empty)))
    }

    @Test
    fun `before the first report there are no sections`() {
        assertEquals(emptyList<AnalysisSection>(), analysisSections(StatsUiState()))
    }

    @Test
    fun `a period with no highlight card has no highlights section`() {
        val sections = analysisSections(StatsSamples.state(noHighlights))

        assertEquals(listOf(AnalysisSection.STRENGTH, AnalysisSection.MUSCLES, AnalysisSection.HABIT, AnalysisSection.BODY), sections)
    }

    // analysisCards(state, section)

    @Test
    fun `the cards of each section of a full period are the ones of the analysis order`() {
        val state = StatsSamples.state()

        assertEquals(listOf(AnalysisCard.HARD_SETS), analysisCards(state, AnalysisSection.HIGHLIGHTS))
        assertEquals(
            listOf(AnalysisCard.STRENGTH, AnalysisCard.RECORDS, AnalysisCard.REP_RANGES, AnalysisCard.RELATIVE_STRENGTH),
            analysisCards(state, AnalysisSection.STRENGTH),
        )
        assertEquals(
            listOf(AnalysisCard.WEEK_MUSCLES, AnalysisCard.BODY_MAP, AnalysisCard.MUSCLES, AnalysisCard.WEEKLY_VOLUME),
            analysisCards(state, AnalysisSection.MUSCLES),
        )
        assertEquals(
            listOf(AnalysisCard.SUMMARY, AnalysisCard.TIMES, AnalysisCard.RECENCY),
            analysisCards(state, AnalysisSection.HABIT),
        )
        assertEquals(listOf(AnalysisCard.MEASUREMENTS), analysisCards(state, AnalysisSection.BODY))
    }

    @Test
    fun `a section with no present card has no cards`() {
        assertEquals(emptyList<AnalysisCard>(), analysisCards(StatsSamples.state(noHighlights), AnalysisSection.HIGHLIGHTS))
    }

    @Test
    fun `before the first report every section has no cards`() {
        AnalysisSection.entries.forEach { assertTrue("$it", analysisCards(StatsUiState(), it).isEmpty()) }
    }

    @Test
    fun `the cards of all sections, in section order, are all the cards in display order`() {
        val states = listOf(
            StatsSamples.state(),
            StatsSamples.state(StatsSamples.empty),
            StatsSamples.state(StatsSamples.oneWeek),
            StatsSamples.state(StatsSamples.cardioOnly),
            StatsSamples.state(noHighlights),
            StatsUiState(),
        )
        states.forEach { state ->
            val union = AnalysisSection.entries.flatMap { analysisCards(state, it) }

            assertEquals(analysisCards(state), union)
        }
    }

    @Test
    fun `a section is listed exactly when it has cards`() {
        listOf(StatsSamples.state(), StatsSamples.state(noHighlights), StatsSamples.state(StatsSamples.empty)).forEach { state ->
            assertEquals(
                AnalysisSection.entries.filter { analysisCards(state, it).isNotEmpty() },
                analysisSections(state),
            )
        }
    }
}
