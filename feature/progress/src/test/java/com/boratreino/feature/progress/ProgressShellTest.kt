package com.boratreino.feature.progress

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Progress tab: the Analysis level opens from the Overview and system back returns to the Overview. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h6000dp")
class ProgressShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Shows the tab over a stats view model on the full sample, and waits until the Overview has its report. */
    private fun show() {
        val stats = statsViewModel()
        compose.setContent {
            StatsScreenHost {
                ProgressShell(onOpenMachine = {}, onOpenCalendar = {}, statsViewModel = stats)
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Detailed analysis").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the Overview is the first page of the tab`() {
        show()

        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("WHAT MATTERS NOW").assertIsDisplayed()
        compose.onNodeWithText("More statistics").assertDoesNotExist()
    }

    @Test
    fun `the analysis row opens Analysis and the Overview is no longer shown`() {
        show()

        compose.onNodeWithText("Detailed analysis").performClick()

        compose.onNodeWithText("More statistics").assertIsDisplayed()
        compose.onNodeWithText("WHAT MATTERS NOW").assertDoesNotExist()
    }

    @Test
    fun `system back closes Analysis and returns to the Overview`() {
        show()
        compose.onNodeWithText("Detailed analysis").performClick()
        compose.onNodeWithText("More statistics").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("WHAT MATTERS NOW").assertIsDisplayed()
        compose.onNodeWithText("More statistics").assertDoesNotExist()
    }
}
