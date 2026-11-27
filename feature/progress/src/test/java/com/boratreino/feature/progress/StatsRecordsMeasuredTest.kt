package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The records card lists the longest time and the longest distance, with or without a load. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class StatsRecordsMeasuredTest {
    @get:Rule
    val compose = createComposeRule()

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = farmersWalk.copy(id = 22L, name = "Sled push", muscleGroup = MuscleGroup.LEGS, kind = MachineKind.WEIGHT_DISTANCE, stepKg = 5.0)

    private val plank = farmersWalk.copy(id = 23L, name = "Plank", muscleGroup = MuscleGroup.CORE, kind = MachineKind.TIME)

    private val day = LocalDate.of(2026, 10, 30)

    private fun showRecords(vararg events: RecordEvent) {
        val report = StatsSamples.full.copy(records = events.toList())
        compose.setContent { StatsCardHost { RecordsCard(report, {}) } }
    }

    @Test
    fun `longest time at a load reads the seconds and the kilos, under the machine`() {
        showRecords(RecordEvent(farmersWalk, day, Record.LongestTime(weightKg = 47.5, seconds = 60)))

        compose.onNodeWithText("Longest time: 60 s at 47.5 kg").assertExists()
        compose.onNodeWithText("Farmer's walk").assertExists()
    }

    @Test
    fun `longest time with no load reads the seconds only`() {
        showRecords(RecordEvent(plank, day, Record.LongestTime(weightKg = 0.0, seconds = 60)))

        compose.onNodeWithText("Longest time: 60 s").assertExists()
        compose.onNodeWithText("Longest time: 60 s at 0 kg").assertDoesNotExist()
    }

    @Test
    fun `longest distance at a load reads the metres and the kilos`() {
        showRecords(RecordEvent(sledPush, day, Record.LongestDistance(weightKg = 40.0, distanceM = 25)))

        compose.onNodeWithText("Longest distance: 25 m at 40 kg").assertExists()
    }

    @Test
    fun `longest distance with no load reads the metres only`() {
        showRecords(RecordEvent(sledPush, day, Record.LongestDistance(weightKg = 0.0, distanceM = 25)))

        compose.onNodeWithText("Longest distance: 25 m").assertExists()
    }
}
