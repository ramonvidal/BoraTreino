package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

private fun editorItem(
    name: String,
    machineId: Long,
    position: Int,
    sets: Int,
    min: Int,
    max: Int,
    group: MuscleGroup = MuscleGroup.LEGS,
    kind: MachineKind = MachineKind.WEIGHT_REPS,
) = RoutineEditorItem(
    machineName = name,
    item = RoutineItem(routineId = 2L, machineId = machineId, position = position, targetSets = sets, repsMin = min, repsMax = max),
    muscleGroup = group,
    kind = kind,
)

private val compactRows = listOf(
    editorItem("Leg press", 1L, 0, 3, 8, 12),
    editorItem("Leg extension", 2L, 1, 3, 10, 15),
    editorItem("Lat pulldown", 3L, 2, 4, 6, 10, MuscleGroup.BACK),
)

private val editorState = RoutineEditorUiState(
    loading = false,
    routineId = 2L,
    name = "Routine B",
    shortName = "B",
    items = compactRows,
    available = listOf(MachineGroup(MuscleGroup.BACK, listOf(fakeMachine(4L, "Seated row", MuscleGroup.BACK)))),
)

/** The auto-saving routine editor: compact rows, no Save button, name in the title; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutineEditorV2ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setEditor(
        state: RoutineEditorUiState,
        callbacks: RoutineEditorCallbacks = RoutineEditorCallbacks(),
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        RoutineEditorContent(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun `compact rows show the name and the target`() {
        setEditor(editorState)
        shoot("routine-editor-v2")

        compose.onNodeWithText("Routine B").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("3 × 8–12").assertExists()
        compose.onNodeWithText("3 × 10–15").assertExists()
        compose.onNodeWithText("4 × 6–10").assertExists()
        compose.onNodeWithText("Add exercise").assertExists()
    }

    @Test
    fun `there is no Save and no bottom Delete button`() {
        setEditor(editorState)

        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Delete routine").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `dark`() {
        setEditor(editorState, dark = true)
        shoot("routine-editor-v2-dark")
    }

    @Test
    fun `large font`() {
        setEditor(editorState, fontScale = 1.6f)
        shoot("routine-editor-v2-large-font")
        compose.onNodeWithText("Leg press").assertExists()
    }

    @Test
    fun `day chips for a routine on specific days`() {
        setEditor(editorState.copy(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        shoot("routine-editor-v2-weekdays")
        compose.onNodeWithText("On specific days").assertExists()
        compose.onNodeWithText("When do you train this?").assertExists()
    }

    @Test
    fun `a cardio row has no target`() {
        val state = editorState.copy(
            items = compactRows.take(1) + editorItem("Treadmill", 7L, 1, 1, 1, 1, MuscleGroup.CARDIO, MachineKind.CARDIO),
        )
        setEditor(state)
        shoot("routine-editor-v2-cardio")

        compose.onNodeWithText("Treadmill").assertExists()
        compose.onNodeWithText("3 × 8–12").assertExists()
        // No "1 x 1" for the treadmill.
        compose.onNodeWithText("1 × 1").assertDoesNotExist()
    }

    @Test
    fun `an empty routine says so and still offers Add machine`() {
        setEditor(editorState.copy(items = emptyList()))
        shoot("routine-editor-v2-empty")

        compose.onNodeWithText("No exercises yet.").assertExists()
        compose.onNodeWithText("Add exercise").assertExists()
    }

    @Test
    fun `empty routine dark`() {
        setEditor(editorState.copy(items = emptyList()), dark = true)
        shoot("routine-editor-v2-empty-dark")
    }

    @Test
    fun `three dot menu offers Duplicate and Delete`() {
        val calls = mutableListOf<String>()
        setEditor(editorState, RoutineEditorCallbacks(onDuplicate = { calls += "duplicate" }, onDelete = { calls += "delete" }))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Duplicate").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        shoot("routine-editor-v2-menu")
        compose.onNodeWithText("Duplicate").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("duplicate", "delete"), calls)
    }

    @Test
    fun `delete confirm dialog`() {
        val calls = mutableListOf<String>()
        setEditor(
            editorState.copy(confirmDelete = true),
            RoutineEditorCallbacks(onConfirmDelete = { calls += "confirm" }, onDismissDelete = { calls += "dismiss" }),
        )

        compose.onNodeWithText("Delete this routine?").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routine-editor-v2-delete.png")
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `row buttons call back with the row index`() {
        val calls = mutableListOf<String>()
        setEditor(
            editorState,
            RoutineEditorCallbacks(
                onBack = { calls += "back" },
                onOpenPicker = { calls += "picker" },
                onOpenTarget = { calls += "target $it" },
                onMoveItem = { index, up -> calls += "move $index $up" },
                onRemoveItem = { calls += "remove $it" },
            ),
        )

        compose.onNodeWithText("3 × 10–15").performClick()
        compose.onAllNodesWithContentDescription("Move down")[0].performClick()
        compose.onAllNodesWithContentDescription("Move up")[2].performClick()
        compose.onNodeWithContentDescription("Remove Leg extension").performClick()
        compose.onNodeWithText("Add exercise").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf("target 2", "move 0 false", "move 2 true", "remove 1", "picker", "back"),
            calls,
        )
    }

    @Test
    fun `typing in the title reports the new name`() {
        val names = mutableListOf<String>()
        setEditor(editorState, RoutineEditorCallbacks(onNameChange = { names += it }))

        compose.onNodeWithText("Routine B").performTextInput(" day")

        // The cursor may sit at either end of the field, so only check what was typed went through.
        assertTrue(names.isNotEmpty() && names.last().contains("day"))
    }

    @Test
    fun `the machine picker lists the machines that can still be added`() {
        setEditor(editorState.copy(pickerOpen = true))

        compose.onNodeWithText("Seated row").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routine-editor-v2-picker.png")
    }
}

/** The editor in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutineEditorV2PtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setEditor(state: RoutineEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutineEditorContent(state = state, callbacks = RoutineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `compact rows pt`() {
        setEditor(editorState)
        compose.onNodeWithText("Adicionar exercício").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routine-editor-v2-pt.png")
    }

    @Test
    fun `empty routine pt`() {
        setEditor(editorState.copy(items = emptyList()))
        compose.onNodeWithText("Nenhum exercício ainda.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routine-editor-v2-empty-pt.png")
    }
}
