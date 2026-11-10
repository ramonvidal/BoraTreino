package com.boratreino.feature.progress

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.IndirectWeek
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.StatsReport
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Muscle groups": seven groups, their average sets a week over the whole period, and the words the card gives them.
 * The shading is a picture, so the screenshot shows it; the words and the numbers are checked here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class WeekMusclesCardTest {
    @get:Rule
    val compose = createComposeRule()

    /** Thirteen weeks: legs 10 a week, chest 8 a week, back 6 in one week (6 / 13 = 0.46, shown as 0.5). */
    private val full = StatsSamples.full

    private fun reportWith(vararg muscleWeeks: MuscleWeek): StatsReport = full.copy(muscleWeeks = muscleWeeks.toList())

    private fun showCard(report: StatsReport) {
        compose.setContent {
            StatsCardHost { WeekMusclesCard(report, Modifier) }
        }
    }

    /** The card's spoken description: one entry per group. */
    private fun description(): String =
        compose.onNodeWithContentDescription("Legs: ", substring = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()

    // The card

    @Test
    fun `the card is titled Muscle groups`() {
        showCard(full)

        compose.onNodeWithText("Muscle groups").assertIsDisplayed()
    }

    @Test
    fun `the note says the sets are averages per week in this period and that indirect sets count as half`() {
        showCard(full)

        compose.onNodeWithText("Average sets per week in this period (indirect sets count as half)").assertIsDisplayed()
    }

    @Test
    fun `the squares show the average sets a week to one decimal`() {
        showCard(full)

        compose.onNodeWithText("10.0", useUnmergedTree = true).assertIsDisplayed()   // legs
        compose.onNodeWithText("8.0", useUnmergedTree = true).assertIsDisplayed()    // chest
        compose.onNodeWithText("0.5", useUnmergedTree = true).assertIsDisplayed()    // back: 6 sets over 13 weeks
    }

    @Test
    fun `the description lists the seven groups with their average and direct sets, in the order of the groups`() {
        showCard(full)

        assertEquals(
            "Legs: 10.0 sets a week (10.0 direct), Chest: 8.0 sets a week (8.0 direct), " +
                "Back: 0.5 sets a week (0.5 direct), Shoulders: 0.0 sets a week (0.0 direct), " +
                "Arms: 0.0 sets a week (0.0 direct), Core: 0.0 sets a week (0.0 direct), " +
                "Cardio: 0.0 sets a week (0.0 direct)",
            description(),
        )
    }

    @Test
    fun `indirect sets count as half in the average and stay out of the direct figure`() {
        val withIndirect = full.copy(
            indirectWeeks = listOf(IndirectWeek(full.weeks.first().weekStart, MuscleGroup.SHOULDERS, 13)),
        )
        showCard(withIndirect)

        // 0.5 x 13 indirect sets over 13 weeks
        assertTrue(description().contains("Shoulders: 0.5 sets a week (0.0 direct)"))
    }

    @Test
    fun `an empty report says zero sets a week for every group`() {
        showCard(StatsSamples.empty)

        assertEquals(
            "Legs: 0.0 sets a week (0.0 direct), Chest: 0.0 sets a week (0.0 direct), " +
                "Back: 0.0 sets a week (0.0 direct), Shoulders: 0.0 sets a week (0.0 direct), " +
                "Arms: 0.0 sets a week (0.0 direct), Core: 0.0 sets a week (0.0 direct), " +
                "Cardio: 0.0 sets a week (0.0 direct)",
            description(),
        )
    }

    @Test
    fun `the muscle groups card is shot`() {
        compose.setContent {
            StatsCardHost {
                WeekMusclesCard(
                    reportWith(
                        MuscleWeek(full.weeks.last().weekStart, MuscleGroup.CHEST, 6, 1000.0),
                        MuscleWeek(full.weeks.last().weekStart, MuscleGroup.LEGS, 1, 100.0),
                        MuscleWeek(full.weeks.last().weekStart, MuscleGroup.SHOULDERS, 12, 0.0),
                        MuscleWeek(full.weeks.last().weekStart, MuscleGroup.BACK, 4, 0.0),
                    ),
                    Modifier,
                )
            }
        }

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/stats-week-muscles.png")
    }
}
