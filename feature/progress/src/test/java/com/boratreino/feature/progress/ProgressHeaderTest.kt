package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Progress header: the title, the period pill and sheet, and the four page chips. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h6000dp")
class ProgressHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showHeader(
        page: ProgressPage = ProgressPage.EXERCISES,
        period: StatsPeriod = StatsPeriod.THREE_MONTHS,
        showPeriod: Boolean = true,
        onPage: (ProgressPage) -> Unit = {},
        onPeriod: (StatsPeriod) -> Unit = {},
    ) {
        compose.setContent {
            StatsScreenHost {
                ProgressHeader(
                    page = page,
                    onPage = onPage,
                    period = period,
                    onPeriod = onPeriod,
                    showPeriod = showPeriod,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun showSheet(selected: StatsPeriod = StatsPeriod.THREE_MONTHS, onSelect: (StatsPeriod) -> Unit = {}) {
        compose.setContent { StatsCardHost { PeriodSheetContent(selected, onSelect) } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun notShown(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    private fun xOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.x

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun periodPill(label: String) = compose.onNodeWithContentDescription("Period: $label")

    // The title and the page chips

    @Test
    fun `the header has the Progress title`() {
        showHeader()

        shown("Progress")
    }

    @Test
    fun `the four page chips are Exercises Body Strength and Habit`() {
        showHeader()

        shown("Exercises")
        shown("Body")
        shown("Strength")
        shown("Habit")
    }

    @Test
    fun `the page chips are in one row in the order Exercises Body Strength and Habit`() {
        showHeader()

        val exercises = xOf("Exercises")
        val body = xOf("Body")
        val strength = xOf("Strength")
        val habit = xOf("Habit")
        assertTrue(exercises < body)
        assertTrue(body < strength)
        assertTrue(strength < habit)
        assertEquals(yOf("Exercises"), yOf("Habit"), 0.5f)
    }

    @Test
    fun `the chosen page chip is selected`() {
        showHeader(page = ProgressPage.STRENGTH)

        compose.onNodeWithText("Strength").assertIsSelected()
        compose.onNodeWithText("Exercises").assertIsNotSelected()
        compose.onNodeWithText("Body").assertIsNotSelected()
        compose.onNodeWithText("Habit").assertIsNotSelected()
    }

    @Test
    fun `tapping each page chip reports its page`() {
        val asked = mutableListOf<ProgressPage>()
        showHeader(page = ProgressPage.EXERCISES, onPage = { asked += it })

        compose.onNodeWithText("Body").performClick()
        compose.onNodeWithText("Strength").performClick()
        compose.onNodeWithText("Habit").performClick()
        compose.onNodeWithText("Exercises").performClick()

        assertEquals(
            listOf(ProgressPage.BODY, ProgressPage.STRENGTH, ProgressPage.HABIT, ProgressPage.EXERCISES),
            asked,
        )
    }

    // The period pill

    @Test
    fun `the period pill is shown when the period is shown`() {
        showHeader(showPeriod = true)

        periodPill("3 months").assertIsDisplayed()
        shown("3 months")
    }

    @Test
    fun `the period pill is left out when the period is not shown`() {
        showHeader(showPeriod = false)

        periodPill("3 months").assertDoesNotExist()
        notShown("3 months")
    }

    @Test
    fun `the period pill label follows the period`() {
        showHeader(period = StatsPeriod.ONE_YEAR)

        periodPill("1 year").assertIsDisplayed()
        shown("1 year")
        notShown("3 months")
    }

    @Test
    fun `the period pill says one week for the one week period`() {
        showHeader(period = StatsPeriod.ONE_WEEK)

        periodPill("1 week").assertIsDisplayed()
    }

    // The period sheet, through the header

    @Test
    fun `tapping the period pill opens the period sheet with its title and five periods`() {
        showHeader()

        periodPill("3 months").performClick()

        shown("Period")
        shown("1 week")
        shown("4 weeks")
        shown("1 year")
        shown("All")
    }

    @Test
    fun `tapping one week in the period sheet asks for one week and closes the sheet`() {
        val asked = mutableListOf<StatsPeriod>()
        showHeader(onPeriod = { asked += it })
        periodPill("3 months").performClick()

        compose.onNodeWithText("1 week").performClick()

        assertEquals(listOf(StatsPeriod.ONE_WEEK), asked)
        notShown("Period")
    }

    @Test
    fun `the period sheet is not opened when the period pill is left out`() {
        showHeader(showPeriod = false)

        notShown("Period")
        notShown("1 year")
    }

    // The period sheet on its own

    @Test
    fun `the period sheet has the title and the five periods from the shortest to All`() {
        showSheet()

        shown("Period")
        assertTrue(yOf("1 week") < yOf("4 weeks"))
        assertTrue(yOf("4 weeks") < yOf("3 months"))
        assertTrue(yOf("3 months") < yOf("1 year"))
        assertTrue(yOf("1 year") < yOf("All"))
    }

    @Test
    fun `the chosen period is the selected row`() {
        showSheet(selected = StatsPeriod.ONE_YEAR)

        compose.onNodeWithText("1 year").assertIsSelected()
        compose.onNodeWithText("1 week").assertIsNotSelected()
        compose.onNodeWithText("4 weeks").assertIsNotSelected()
        compose.onNodeWithText("3 months").assertIsNotSelected()
        compose.onNodeWithText("All").assertIsNotSelected()
    }

    @Test
    fun `each period row is at least 56 dp tall`() {
        showSheet()

        compose.onNodeWithText("1 week").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("All").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `clicking a period row reports that period`() {
        val asked = mutableListOf<StatsPeriod>()
        showSheet(onSelect = { asked += it })

        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("1 week").performClick()

        assertEquals(listOf(StatsPeriod.ALL, StatsPeriod.ONE_WEEK), asked)
    }

    @Test
    fun `clicking the chosen period row still reports it`() {
        val asked = mutableListOf<StatsPeriod>()
        showSheet(selected = StatsPeriod.THREE_MONTHS, onSelect = { asked += it })

        compose.onNodeWithText("3 months").performClick()

        assertEquals(listOf(StatsPeriod.THREE_MONTHS), asked)
    }
}
