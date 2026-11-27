package com.boratreino.feature.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved page name to a page: the current names map to themselves, old ones fall back to Overview. */
class ProgressPageTest {
    @Test
    fun `the tab has two pages, Overview then Exercises`() {
        assertEquals(listOf(ProgressPage.OVERVIEW, ProgressPage.EXERCISES), ProgressPage.entries)
    }

    @Test
    fun `the saved names of the two pages map to themselves`() {
        assertEquals(ProgressPage.OVERVIEW, progressPageOf("OVERVIEW"))
        assertEquals(ProgressPage.EXERCISES, progressPageOf("EXERCISES"))
    }

    @Test
    fun `the old page names fall back to Overview`() {
        assertEquals(ProgressPage.OVERVIEW, progressPageOf("BODY"))
        assertEquals(ProgressPage.OVERVIEW, progressPageOf("STRENGTH"))
        assertEquals(ProgressPage.OVERVIEW, progressPageOf("HABIT"))
    }

    @Test
    fun `no saved name, or an unknown one, is Overview`() {
        assertEquals(ProgressPage.OVERVIEW, progressPageOf(null))
        assertEquals(ProgressPage.OVERVIEW, progressPageOf(""))
        assertEquals(ProgressPage.OVERVIEW, progressPageOf("SOMETHING_ELSE"))
    }
}
