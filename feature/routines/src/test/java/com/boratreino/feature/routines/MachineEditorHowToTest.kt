package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.ExerciseLibraryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The machine editor's "How to do it" card: there only for a machine that came from the exercise library
 * and has steps, folded until it is opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineEditorHowToTest {
    private val steps = listOf("Stand tall.", "Curl the weight up.")
    private val zottman = LibraryExercise(
        id = "Zottman_Curl",
        name = "Zottman Curl",
        namePt = "Rosca Zottman",
        equipment = LibraryEquipment.DUMBBELL,
        level = LibraryLevel.BEGINNER,
        category = "strength",
        mechanic = null,
        force = null,
        primaryMuscles = listOf("biceps"),
        secondaryMuscles = emptyList(),
        steps = steps,
    )

    private class OneMachine(private val stored: Machine?) : MachineRepository {
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(stored?.takeIf { it.id == id })
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(listOfNotNull(stored))
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = error("not used by this test")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long = 0L
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private class Library(private val exercises: List<LibraryExercise>) : ExerciseLibraryRepository {
        override suspend fun all(): List<LibraryExercise> = exercises
        override suspend fun byId(id: String): LibraryExercise? = exercises.firstOrNull { it.id == id }
    }

    private fun machine(libraryId: String?) =
        Machine(5L, "Rosca Zottman", MuscleGroup.ARMS, MachineKind.WEIGHT_REPS, 2.5, null, null, false, libraryId)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(stored: Machine?, library: List<LibraryExercise> = listOf(zottman), id: Long = 5L) =
        MachineEditorViewModel(OneMachine(stored), Library(library), SavedStateHandle(mapOf(MACHINE_ID_ARG to id)))

    // The view model

    @Test
    fun `a machine from the library carries the steps of its exercise`() {
        val state = editor(machine("Zottman_Curl")).uiState.value

        assertEquals("Zottman_Curl", state.libraryId)
        assertEquals(steps, state.steps)
    }

    @Test
    fun `a machine made by hand has no steps`() {
        val state = editor(machine(null)).uiState.value

        assertTrue(state.steps.isEmpty())
    }

    @Test
    fun `a library id with no entry leaves the form working, with no steps`() {
        val vm = editor(machine("Gone_From_Library"), library = emptyList())

        assertTrue(vm.uiState.value.steps.isEmpty())
        assertEquals(false, vm.uiState.value.loading)
    }

    @Test
    fun `a new machine has no steps`() {
        val state = editor(stored = null, id = NEW_MACHINE).uiState.value

        assertTrue(state.steps.isEmpty())
    }

    // The card

    @get:Rule
    val compose = createComposeRule()

    private fun setEditor(state: MachineEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(state = state, callbacks = MachineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `the how to do it card is shown when the machine has steps`() {
        setEditor(MachineEditorUiState(loading = false, machineId = 5L, name = "Rosca Zottman", libraryId = "Zottman_Curl", steps = steps))

        compose.onNodeWithText("How to do it").assertExists()
    }

    @Test
    fun `the how to do it card is not shown without steps`() {
        setEditor(MachineEditorUiState(loading = false, machineId = 5L, name = "Home rig"))

        compose.onNodeWithText("How to do it").assertDoesNotExist()
    }

    @Test
    fun `the steps stay folded until the card is opened`() {
        setEditor(MachineEditorUiState(loading = false, machineId = 5L, name = "Rosca Zottman", libraryId = "Zottman_Curl", steps = steps))

        compose.onNodeWithText("Stand tall.").assertDoesNotExist()

        compose.onNodeWithText("How to do it").performClick()

        compose.onNodeWithText("Stand tall.").assertExists()
        compose.onNodeWithText("Curl the weight up.").assertExists()
    }
}
