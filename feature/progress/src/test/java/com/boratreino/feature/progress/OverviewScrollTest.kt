package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A short screen, so the frequency section starts below the fold and the finding tap has to scroll to it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h120dp")
class OverviewScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping the frequency finding scrolls down to the frequency section`() {
        compose.setContent {
            OverviewHost(OverviewSamples.state(findings = listOf(OverviewSamples.frequencyDrop)))
        }

        compose.onNodeWithText("Training fell to 1.5 days a week").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Frequency").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Frequency").assertIsDisplayed()
    }
}
