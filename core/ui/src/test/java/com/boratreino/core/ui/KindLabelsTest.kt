package com.boratreino.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetMeasure
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The labels for the time and distance kinds: set drafts, records, summaries and targets. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class KindLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    /** Runs [content] in composition and returns the text it produced. */
    private fun text(content: @Composable () -> String): String {
        var result = ""
        compose.setContent { result = content() }
        return result
    }

    private fun machine(kind: MachineKind) = Machine(
        id = 1L,
        name = "Machine",
        muscleGroup = MuscleGroup.LEGS,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    @Test
    fun `weight and time draft reads kilograms then seconds`() {
        val m = machine(MachineKind.WEIGHT_TIME)

        assertEquals("47.5 kg · 30 s", text { draftText(m, SetDraft(weightKg = 47.5, reps = 0, seconds = 30)) })
    }

    @Test
    fun `weight and time draft with no load reads only seconds`() {
        val m = machine(MachineKind.WEIGHT_TIME)

        assertEquals("30 s", text { draftText(m, SetDraft(weightKg = 0.0, reps = 0, seconds = 30)) })
    }

    @Test
    fun `weight and distance draft reads kilograms then metres`() {
        val m = machine(MachineKind.WEIGHT_DISTANCE)

        assertEquals("40 kg · 20 m", text { draftText(m, SetDraft(weightKg = 40.0, reps = 0, distanceM = 20)) })
    }

    @Test
    fun `weight and distance draft with no load reads only metres`() {
        val m = machine(MachineKind.WEIGHT_DISTANCE)

        assertEquals("20 m", text { draftText(m, SetDraft(weightKg = 0.0, reps = 0, distanceM = 20)) })
    }

    @Test
    fun `time draft reads seconds only`() {
        val m = machine(MachineKind.TIME)

        assertEquals("45 s", text { draftText(m, SetDraft(weightKg = 0.0, reps = 0, seconds = 45)) })
    }

    @Test
    fun `set label of a stored time set uses the same text as its draft`() {
        val m = machine(MachineKind.WEIGHT_TIME)
        val set = WorkoutSet(
            id = 1L, sessionId = 1L, machineId = 1L, position = 0, weightKg = 47.5, reps = null,
            seconds = 30, calories = null, distanceM = null, loggedAt = Instant.EPOCH,
        )

        assertEquals("47.5 kg · 30 s", text { setLabel(m, set) })
    }

    @Test
    fun `seconds text and metres text always carry their unit`() {
        assertEquals("45 s", text { secondsText(45) })
        assertEquals("20 m", text { metresText(20) })
    }

    @Test
    fun `amount label names the measure of the kind`() {
        assertEquals("Reps", text { amountLabel(machine(MachineKind.WEIGHT_REPS)) })
        assertEquals("Time (s)", text { amountLabel(machine(MachineKind.WEIGHT_TIME)) })
        assertEquals("Time (s)", text { amountLabel(machine(MachineKind.TIME)) })
        assertEquals("Distance (m)", text { amountLabel(machine(MachineKind.WEIGHT_DISTANCE)) })
    }

    @Test
    fun `target of a time kind shows the sets only`() {
        val item = RoutineItem(routineId = 1L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)

        assertEquals("3 sets", text { targetText(item, MachineKind.TIME) })
        assertEquals("3 sets", text { targetText(item, MachineKind.WEIGHT_DISTANCE) })
    }

    @Test
    fun `target of a reps kind keeps the rep range`() {
        val item = RoutineItem(routineId = 1L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)

        assertEquals("3 × 8–12", text { targetText(item, MachineKind.WEIGHT_REPS) })
    }

    @Test
    fun `longest time record names the seconds and the load`() {
        assertEquals("New record: 60 s at 40 kg", text { recordText(Record.LongestTime(weightKg = 40.0, seconds = 60)) })
    }

    @Test
    fun `longest time record without load names only the seconds`() {
        assertEquals("New record: 60 s", text { recordText(Record.LongestTime(weightKg = 0.0, seconds = 60)) })
    }

    @Test
    fun `longest distance record names the metres and the load`() {
        assertEquals("New record: 25 m at 40 kg", text { recordText(Record.LongestDistance(weightKg = 40.0, distanceM = 25)) })
    }

    @Test
    fun `longest distance record without load names only the metres`() {
        assertEquals("New record: 25 m", text { recordText(Record.LongestDistance(weightKg = 0.0, distanceM = 25)) })
    }

    @Test
    fun `uniform measured summary reads count times amount at the load`() {
        val sets = List(3) { SetDraft(weightKg = 47.5, reps = 0, seconds = 30) }
        val summary = SetSummary.Measured(SetMeasure.SECONDS, sets)

        assertEquals(true, summary.uniform)
        assertEquals("3 × 30 s at 47.5 kg", text { setSummaryText(summary) })
    }

    @Test
    fun `uniform measured summary with no load drops the weight`() {
        val sets = List(3) { SetDraft(weightKg = 0.0, reps = 0, seconds = 45) }
        val summary = SetSummary.Measured(SetMeasure.SECONDS, sets)

        assertEquals("3 × 45 s", text { setSummaryText(summary) })
    }

    @Test
    fun `non uniform measured summary lists each set`() {
        val sets = listOf(
            SetDraft(weightKg = 47.5, reps = 0, seconds = 30),
            SetDraft(weightKg = 45.0, reps = 0, seconds = 25),
        )
        val summary = SetSummary.Measured(SetMeasure.SECONDS, sets)

        assertEquals(false, summary.uniform)
        assertEquals("47.5 kg · 30 s, 45 kg · 25 s", text { setSummaryText(summary) })
    }
}
