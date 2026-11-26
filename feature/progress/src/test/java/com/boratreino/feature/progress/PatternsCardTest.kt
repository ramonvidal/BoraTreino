package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.PatternReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "When you lift best" card: its two charts with their session counts, and what it says before it has enough sessions. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PatternsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(patterns: PatternReport?) {
        compose.setContent { StatsCardHost { PatternsCard(patterns = patterns) } }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().assertIsDisplayed()

    @Test
    fun `with enough sessions the card shows its note and both charts`() {
        show(Phase46Samples.patterns(sessionsUsed = 18, enough = true))

        shown("When you lift best")
        compose.onNodeWithText("From 18 sessions.", substring = true).assertIsDisplayed()
        shown("By weekday")
        shown("By days since the last session of the same exercise")
    }

    @Test
    fun `each bucket shows how many sessions it is made of`() {
        show(Phase46Samples.patterns(sessionsUsed = 18, enough = true))

        shown("n=6")
        shown("n=2")
        shown("n=1")
    }

    @Test
    fun `the footnote says that the grey bars have too few sessions to trust`() {
        show(Phase46Samples.patterns(sessionsUsed = 18, enough = true))

        shown("Whiskers are the standard error. Grey bars have fewer than 3 sessions: too few to trust.")
    }

    @Test
    fun `with too few sessions the card says how many it needs and how many it has`() {
        show(Phase46Samples.patterns(sessionsUsed = 9, enough = false))

        shown("When you lift best")
        shown("Needs 15 sessions (you have 9)")
        compose.onNodeWithText("By weekday").assertDoesNotExist()
    }

    @Test
    fun `no patterns draw no card`() {
        show(null)

        compose.onNodeWithText("When you lift best").assertDoesNotExist()
    }
}
