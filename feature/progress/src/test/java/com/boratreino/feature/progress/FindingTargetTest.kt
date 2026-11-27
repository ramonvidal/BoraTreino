package com.boratreino.feature.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a tap on each kind of finding opens. */
class FindingTargetTest {
    @Test
    fun `a frequency drop scrolls the Overview to the frequency section`() {
        assertEquals(FindingTarget.Frequency, findingTarget(OverviewSamples.frequencyDrop))
    }

    @Test
    fun `a low volume finding opens the hard sets card of Analysis`() {
        assertEquals(FindingTarget.Analysis(AnalysisCard.HARD_SETS), findingTarget(OverviewSamples.lowVolume))
    }

    @Test
    fun `a stalled exercise opens that exercise`() {
        assertEquals(FindingTarget.Machine(7L), findingTarget(OverviewSamples.stalledFlat))
    }

    @Test
    fun `a falling exercise opens that exercise too`() {
        assertEquals(FindingTarget.Machine(9L), findingTarget(OverviewSamples.stalledFalling))
    }

    @Test
    fun `a new record opens the records card of Analysis`() {
        assertEquals(FindingTarget.Analysis(AnalysisCard.RECORDS), findingTarget(OverviewSamples.newRecord))
    }
}
