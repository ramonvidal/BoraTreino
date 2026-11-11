package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.PreviousComparison
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The "Training complete" content drawn from its state alone: the title, the four numbers, records, the
 * comparison and the buttons. Written from the summary and the actions it takes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodaySummarySheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val date = LocalDate.of(2026, 11, 9)

    private fun machine(id: Long, name: String) = Machine(
        id = id,
        name = name,
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun summary(
        routineName: String? = "Routine A",
        minutes: Int? = 62,
        sets: Int = 9,
        volumeKg: Double = 980.0,
        machines: Int = 3,
        records: List<RecordEvent> = emptyList(),
        previous: PreviousComparison? = null,
    ) = TrainingSummary(
        sessionId = 5L,
        routineName = routineName,
        date = date,
        durationMinutes = minutes,
        workingSets = sets,
        volumeKg = volumeKg,
        machineCount = machines,
        records = records,
        previous = previous,
    )

    private fun setContent(
        ui: SummaryUi,
        actions: SummaryActions = SummaryActions(),
    ) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TrainingSummaryContent(ui, actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    // Title, date and the four numbers

    @Test
    fun `the title, the routine name and the date are shown`() {
        setContent(SummaryUi(summary()))

        compose.onNodeWithText("Training complete").assertExists()
        compose.onNodeWithText("Routine A").assertExists()
        compose.onNodeWithText("November", substring = true).assertExists()
    }

    @Test
    fun `a free session is named Free training`() {
        setContent(SummaryUi(summary(routineName = null)))

        compose.onNodeWithText("Free training").assertExists()
    }

    @Test
    fun `the four numbers show duration in minutes, sets, volume in kilograms and exercises`() {
        setContent(SummaryUi(summary(minutes = 62, sets = 9, volumeKg = 980.0, machines = 3)))

        compose.onNodeWithText("62 min").assertExists()
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("980 kg").assertExists()
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("Duration").assertExists()
        compose.onNodeWithText("Sets").assertExists()
        compose.onNodeWithText("Volume").assertExists()
        compose.onNodeWithText("Exercises").assertExists()
    }

    @Test
    fun `a volume of 12400 kg is shown in tonnes`() {
        setContent(SummaryUi(summary(volumeKg = 12400.0)))

        compose.onNodeWithText("12.4 t").assertExists()
        compose.onNodeWithText("12400 kg").assertDoesNotExist()
    }

    @Test
    fun `a volume of exactly 1000 kg is shown in tonnes`() {
        setContent(SummaryUi(summary(volumeKg = 1000.0)))

        compose.onNodeWithText("1.0 t").assertExists()
    }

    @Test
    fun `a session with no real duration shows a dash for it`() {
        setContent(SummaryUi(summary(minutes = null)))

        compose.onNodeWithText("-").assertExists()
        compose.onNodeWithText("62 min").assertDoesNotExist()
    }

    // Records

    @Test
    fun `records of the session are listed in words`() {
        val bench = machine(1L, "Leg press")
        val records = listOf(
            RecordEvent(bench, date, Record.MostReps(80.0, 12)),
            RecordEvent(bench, date, Record.HeaviestWeight(100.0)),
        )
        setContent(SummaryUi(summary(records = records)))

        compose.onNodeWithText("Records").assertExists()
        compose.onNodeWithText("Leg press: Most reps 12 at 80 kg").assertExists()
        compose.onNodeWithText("Leg press: Heaviest 100 kg").assertExists()
    }

    @Test
    fun `no records means no records heading`() {
        setContent(SummaryUi(summary(records = emptyList())))

        compose.onNodeWithText("Records").assertDoesNotExist()
    }

    @Test
    fun `at most five record lines are shown in the sheet`() {
        val records = (1..6).map { RecordEvent(machine(it.toLong(), "M$it"), date, Record.HeaviestWeight(100.0)) }
        setContent(SummaryUi(summary(records = records)))

        compose.onNodeWithText("M5: Heaviest 100 kg").assertExists()
        compose.onNodeWithText("M6: Heaviest 100 kg").assertDoesNotExist()
    }

    // Comparison

    @Test
    fun `without a previous session there is no comparison`() {
        setContent(SummaryUi(summary(previous = null)))

        compose.onNodeWithText("Compared with last time").assertDoesNotExist()
    }

    @Test
    fun `a higher volume and more sets are shown in neutral words`() {
        val previous = PreviousComparison(volumeKg = 900.0, workingSets = 7, volumeChangePercent = 8.4, setsChange = 2)
        setContent(SummaryUi(summary(previous = previous)))

        compose.onNodeWithText("Compared with last time").assertExists()
        compose.onNodeWithText("Volume +8 % vs last time").assertExists()
        compose.onNodeWithText("Sets +2").assertExists()
    }

    @Test
    fun `a lower volume and fewer sets are shown with a minus`() {
        val previous = PreviousComparison(volumeKg = 1000.0, workingSets = 10, volumeChangePercent = -3.0, setsChange = -1)
        setContent(SummaryUi(summary(previous = previous)))

        compose.onNodeWithText("Volume -3 % vs last time").assertExists()
        compose.onNodeWithText("Sets -1").assertExists()
    }

    @Test
    fun `the same volume is shown as same and the same sets as same`() {
        val previous = PreviousComparison(volumeKg = 980.0, workingSets = 9, volumeChangePercent = 0.0, setsChange = 0)
        setContent(SummaryUi(summary(previous = previous)))

        compose.onNodeWithText("Volume same as last time").assertExists()
        compose.onNodeWithText("Sets same as last time").assertExists()
    }

    @Test
    fun `a previous volume of zero shows no volume line`() {
        val previous = PreviousComparison(volumeKg = 0.0, workingSets = 0, volumeChangePercent = null, setsChange = 9)
        setContent(SummaryUi(summary(previous = previous)))

        compose.onNodeWithText("Sets +9").assertExists()
        compose.onNodeWithText("Volume same as last time").assertDoesNotExist()
        compose.onNodeWithText("Volume +0 % vs last time").assertDoesNotExist()
    }

    // Buttons

    @Test
    fun `Share, Not finished yet and Close call their actions`() {
        var shared = 0
        var notFinished = 0
        var closed = 0
        setContent(
            SummaryUi(summary()),
            SummaryActions(
                onShare = { shared++ },
                onNotFinished = { notFinished++ },
                onClose = { closed++ },
            ),
        )

        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Not finished yet").performClick()
        compose.onNodeWithText("Close").performClick()

        assertEquals(1, shared)
        assertEquals(1, notFinished)
        assertEquals(1, closed)
    }

    @Test
    fun `Update your routine is offered only when the routine would change`() {
        setContent(SummaryUi(summary(), canUpdateRoutine = false))

        compose.onNodeWithText("Update your routine").assertDoesNotExist()
    }

    @Test
    fun `Update your routine calls its action`() {
        var updated = 0
        setContent(
            SummaryUi(summary(), canUpdateRoutine = true),
            SummaryActions(onUpdateRoutine = { updated++ }),
        )

        compose.onNodeWithText("Update your routine").performClick()

        assertEquals(1, updated)
    }

    @Test
    fun `after the routine was updated the sheet says so and offers Undo instead`() {
        var undone = 0
        setContent(
            SummaryUi(
                summary(),
                canUpdateRoutine = false,
                routineUpdate = RoutineUpdate(emptyList(), emptyList()),
            ),
            SummaryActions(onUndoRoutineUpdate = { undone++ }),
        )

        compose.onNodeWithText("Routine updated").assertExists()
        compose.onNodeWithText("Update your routine").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()

        assertEquals(1, undone)
    }

    @Test
    fun `the training card shot`() {
        val bench = machine(1L, "Leg press")
        setContent(
            SummaryUi(
                summary(
                    records = listOf(RecordEvent(bench, date, Record.HeaviestWeight(100.0))),
                    previous = PreviousComparison(volumeKg = 900.0, workingSets = 7, volumeChangePercent = 8.4, setsChange = 2),
                ),
                canUpdateRoutine = true,
            ),
        )

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-summary-sheet.png")
    }
}
