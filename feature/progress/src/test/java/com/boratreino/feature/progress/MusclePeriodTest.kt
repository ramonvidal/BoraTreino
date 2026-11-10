package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StatsRules
import com.boratreino.core.model.WorkoutSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The muscle cards follow the selected period: the same sets are an average over the weeks of the period, so a longer
 * period gives a smaller average. The reports are built by the statistics rules, as the page builds them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class MusclePeriodTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)

    /** Twelve chest sets in the current week, and nothing else. */
    private fun reportFor(period: StatsPeriod): StatsReport {
        val bench = Machine(1, "Bench press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
        val start = Instant.parse("2026-11-02T10:00:00Z")
        val session = Session(1, start, null, null, null)
        val sets = (0 until 12).map { i ->
            WorkoutSet(
                id = i + 1L,
                sessionId = 1L,
                machineId = 1L,
                position = i,
                weightKg = 60.0,
                reps = 8,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = start.plusSeconds(60L * i),
            )
        }
        return StatsRules.report(period, today, DayOfWeek.MONDAY, 3, listOf(bench), listOf(session), sets, ZoneOffset.UTC)
    }

    @Test
    fun `the muscle groups card averages the same sets over four weeks and over three months`() {
        compose.setContent {
            StatsCardHost {
                Column {
                    WeekMusclesCard(reportFor(StatsPeriod.FOUR_WEEKS))
                    WeekMusclesCard(reportFor(StatsPeriod.THREE_MONTHS))
                }
            }
        }

        // 12 sets over 4 weeks, and over 13 weeks
        compose.onNodeWithContentDescription("Chest: 3.0 sets a week", substring = true).assertExists()
        compose.onNodeWithContentDescription("Chest: 0.9 sets a week", substring = true).assertExists()
    }

    @Test
    fun `the body map averages the same sets over four weeks and over three months`() {
        compose.setContent {
            StatsCardHost {
                Column {
                    BodyMapCard(reportFor(StatsPeriod.FOUR_WEEKS))
                    BodyMapCard(reportFor(StatsPeriod.THREE_MONTHS))
                }
            }
        }

        compose.onNodeWithContentDescription("Chest: 3.0 sets a week", substring = true).assertExists()
        compose.onNodeWithContentDescription("Chest: 0.9 sets a week", substring = true).assertExists()
    }
}
