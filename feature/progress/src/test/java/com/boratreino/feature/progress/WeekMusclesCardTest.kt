package com.boratreino.feature.progress

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.StatsReport
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Muscle groups this week": seven groups, their working sets in the report's last week, and the words the
 * card gives them. The shading is a picture, so the screenshot shows it; the words are checked here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class WeekMusclesCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = StatsSamples.full
    private val lastWeek = full.weeks.last().weekStart

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

    // Counting

    @Test
    fun `the sets of each group are counted for the last week of the report`() {
        val counts = weekSetsByGroup(
            reportWith(
                MuscleWeek(lastWeek, MuscleGroup.CHEST, 6, 1000.0),
                MuscleWeek(lastWeek, MuscleGroup.LEGS, 1, 100.0),
                MuscleWeek(lastWeek, MuscleGroup.SHOULDERS, 10, 0.0),
                MuscleWeek(lastWeek.minusWeeks(1), MuscleGroup.CORE, 20, 0.0),
            ),
        )

        assertEquals(1, counts.getValue(MuscleGroup.LEGS))
        assertEquals(6, counts.getValue(MuscleGroup.CHEST))
        assertEquals(10, counts.getValue(MuscleGroup.SHOULDERS))
        assertEquals(0, counts.getValue(MuscleGroup.CORE))
    }

    @Test
    fun `every one of the seven groups is in the count, untrained ones at zero`() {
        val counts = weekSetsByGroup(reportWith(MuscleWeek(lastWeek, MuscleGroup.ARMS, 4, 0.0)))

        assertEquals(MuscleGroup.entries.toSet(), counts.keys)
        assertEquals(4, counts.getValue(MuscleGroup.ARMS))
        assertEquals(0, counts.getValue(MuscleGroup.CARDIO))
    }

    // The card

    @Test
    fun `the card is titled Muscle groups this week`() {
        showCard(reportWith(MuscleWeek(lastWeek, MuscleGroup.CHEST, 6, 1000.0)))

        compose.onNodeWithText("Muscle groups this week").assertExists()
    }

    @Test
    fun `the description lists the seven groups with their sets, in the order of the groups`() {
        showCard(
            reportWith(
                MuscleWeek(lastWeek, MuscleGroup.CHEST, 6, 1000.0),
                MuscleWeek(lastWeek, MuscleGroup.LEGS, 1, 100.0),
                MuscleWeek(lastWeek, MuscleGroup.SHOULDERS, 10, 0.0),
                MuscleWeek(lastWeek, MuscleGroup.ARMS, 4, 0.0),
                MuscleWeek(lastWeek, MuscleGroup.BACK, 2, 0.0),
            ),
        )

        assertEquals(
            "Legs: 1 set, Chest: 6 sets, Back: 2 sets, Shoulders: 10 sets, Arms: 4 sets, Core: 0 sets, Cardio: 0 sets",
            description(),
        )
    }

    @Test
    fun `an empty report says zero sets for every group`() {
        showCard(StatsSamples.empty)

        assertEquals(
            "Legs: 0 sets, Chest: 0 sets, Back: 0 sets, Shoulders: 0 sets, Arms: 0 sets, Core: 0 sets, Cardio: 0 sets",
            description(),
        )
    }

    @Test
    fun `the week muscles card is shot`() {
        showCard(
            reportWith(
                MuscleWeek(lastWeek, MuscleGroup.CHEST, 6, 1000.0),
                MuscleWeek(lastWeek, MuscleGroup.LEGS, 1, 100.0),
                MuscleWeek(lastWeek, MuscleGroup.SHOULDERS, 12, 0.0),
                MuscleWeek(lastWeek, MuscleGroup.BACK, 4, 0.0),
            ),
        )

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/stats-week-muscles.png")
    }
}
