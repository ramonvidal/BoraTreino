package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryFilter
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The full-screen exercise library, drawn from its state: what it shows, what a tap reports, and which
 * filter values the chips offer. Written from the screen's behaviour.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class ExerciseLibraryContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun starter(name: String): StarterMachine = StarterMachines.all.single { it.name == name }

    private fun starterRow(name: String, group: MuscleGroup = MuscleGroup.CHEST) =
        PickerRow(key = "s:$name", name = name, group = group, starter = starter(name))

    private fun libraryRow(
        id: String,
        name: String,
        group: MuscleGroup = MuscleGroup.ARMS,
        added: Boolean = false,
    ) = PickerRow(
        key = "l:$id",
        name = name,
        group = group,
        libraryId = id,
        equipment = LibraryEquipment.DUMBBELL,
        level = LibraryLevel.BEGINNER,
        added = added,
    )

    private fun state(
        rows: List<PickerRow> = emptyList(),
        filter: LibraryFilter = LibraryFilter(),
        selectedStarters: Set<String> = emptySet(),
        selectedLibrary: Set<String> = emptySet(),
    ) = RoutinesUiState(
        pickerGroups = rows.groupBy { it.group }.map { (group, list) -> PickerGroup(group, list) },
        pickerAvailable = true,
        filter = filter,
        selectedStarters = selectedStarters,
        selectedLibrary = selectedLibrary,
    )

    private fun show(state: RoutinesUiState, callbacks: RoutinesCallbacks = RoutinesCallbacks()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ExerciseLibraryContent(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    // Top bar and search

    @Test
    fun `the top bar says Add exercises and the back arrow calls onBack`() {
        var back = 0
        show(state(), RoutinesCallbacks(onBack = { back++ }))

        compose.onNodeWithText("Add exercises").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, back)
    }

    @Test
    fun `the search field has its placeholder and passes the typed text on`() {
        var typed: String? = null
        show(state(), RoutinesCallbacks(onSearchStarters = { typed = it }))

        compose.onNodeWithText("Search exercises").assertExists()
        compose.onNodeWithText("Search exercises").performTextReplacement("sup")

        assertEquals("sup", typed)
    }

    @Test
    fun `the count line counts a single result in the singular`() {
        show(state(rows = listOf(libraryRow("Zottman_Curl", "Zottman Curl"))))
        compose.onNodeWithText("1 exercise").assertExists()
    }

    @Test
    fun `the count line counts several results`() {
        show(
            state(
                rows = listOf(
                    starterRow("Barbell bench press"),
                    starterRow("Incline barbell bench press"),
                    libraryRow("Zottman_Curl", "Zottman Curl"),
                ),
            ),
        )
        compose.onNodeWithText("3 exercises").assertExists()
    }

    @Test
    fun `no results says so`() {
        show(state(filter = LibraryFilter(query = "zzz")))

        compose.onNodeWithText("No matches.").assertExists()
    }

    // Selection

    @Test
    fun `with two exercises ticked the sticky button says Add 2`() {
        show(
            state(
                rows = listOf(starterRow("Barbell bench press"), libraryRow("Zottman_Curl", "Zottman Curl")),
                selectedStarters = setOf("Barbell bench press"),
                selectedLibrary = setOf("Zottman_Curl"),
            ),
        )

        compose.onNodeWithText("Add 2").assertExists()
    }

    @Test
    fun `with nothing ticked there is no Add button`() {
        show(state(rows = listOf(libraryRow("Zottman_Curl", "Zottman Curl"))))

        compose.onNodeWithText("Add 0").assertDoesNotExist()
        compose.onNodeWithText("Add 1").assertDoesNotExist()
    }

    @Test
    fun `tapping Add N calls onAddSelectedStarters`() {
        var calls = 0
        show(
            state(
                rows = listOf(libraryRow("Zottman_Curl", "Zottman Curl")),
                selectedLibrary = setOf("Zottman_Curl"),
            ),
            RoutinesCallbacks(onAddSelectedStarters = { calls++ }),
        )

        compose.onNodeWithText("Add 1").performClick()

        assertEquals(1, calls)
    }

    @Test
    fun `the Add N count includes starters and library exercises together`() {
        show(
            state(
                rows = listOf(
                    starterRow("Barbell bench press"),
                    starterRow("Incline barbell bench press"),
                    libraryRow("Zottman_Curl", "Zottman Curl"),
                ),
                selectedStarters = setOf("Barbell bench press", "Incline barbell bench press"),
                selectedLibrary = setOf("Zottman_Curl"),
            ),
        )

        compose.onNodeWithText("Add 3").assertExists()
    }

    @Test
    fun `tapping an exercise reports its library id`() {
        val toggled = mutableListOf<String>()
        show(
            state(rows = listOf(libraryRow("Zottman_Curl", "Zottman Curl"))),
            RoutinesCallbacks(onToggleLibrary = { toggled += it }),
        )

        compose.onNodeWithText("Zottman Curl").performClick()

        assertEquals(listOf("Zottman_Curl"), toggled)
    }

    @Test
    fun `tapping a starter reports that starter`() {
        val toggled = mutableListOf<String>()
        show(
            state(rows = listOf(starterRow("Barbell bench press"))),
            RoutinesCallbacks(onToggleStarter = { toggled += it.name }),
        )

        compose.onNodeWithText("Barbell bench press").performClick()

        assertEquals(listOf("Barbell bench press"), toggled)
    }

    @Test
    fun `an exercise already in the gym is marked Added and cannot be ticked`() {
        val toggled = mutableListOf<String>()
        show(
            state(rows = listOf(libraryRow("Concentration_Curls", "Concentration Curls", added = true))),
            RoutinesCallbacks(onToggleLibrary = { toggled += it }),
        )

        compose.onNodeWithText("Added").assertExists()
        compose.onNodeWithText("Concentration Curls").performClick()

        assertTrue(toggled.isEmpty())
        compose.onNodeWithText("Add 1").assertDoesNotExist()
    }

    // How to do it

    @Test
    fun `the info button of an exercise opens how to do it for that row`() {
        val shown = mutableListOf<String>()
        show(
            state(rows = listOf(libraryRow("Zottman_Curl", "Zottman Curl"))),
            RoutinesCallbacks(onShowDetail = { shown += it.key }),
        )

        compose.onNodeWithContentDescription("How to do Zottman Curl").performClick()

        assertEquals(listOf("l:Zottman_Curl"), shown)
    }

    @Test
    fun `a row without instructions has no info button`() {
        show(state(rows = listOf(starterRow("Barbell bench press"))))

        compose.onNodeWithContentDescription("How to do Barbell bench press").assertDoesNotExist()
    }

    @Test
    fun `a library exercise without a picture is listed with its info button and no licence credit`() {
        show(state(rows = listOf(libraryRow("Pull-Up", "Pull-Up"))))

        compose.onNodeWithText("Pull-Up").assertExists()
        compose.onNodeWithContentDescription("How to do Pull-Up").assertExists()
        compose.onNodeWithText("Exercise illustrations", substring = true).assertDoesNotExist()
    }

    // Filters

    @Test
    fun `the three filter chips are offered with no value chosen`() {
        show(state())

        compose.onNodeWithText("Muscle group").assertExists()
        compose.onNodeWithText("Equipment").assertExists()
        compose.onNodeWithText("Level").assertExists()
    }

    @Test
    fun `the muscle group chip opens All and the groups, and picking one reports it`() {
        val picked = mutableListOf<MuscleGroup?>()
        show(state(), RoutinesCallbacks(onGroupFilter = { picked += it }))

        compose.onNodeWithText("Muscle group").performClick()
        compose.onNodeWithText("All").assertExists()
        compose.onNodeWithText("Arms").performClick()

        assertEquals(listOf<MuscleGroup?>(MuscleGroup.ARMS), picked)
    }

    @Test
    fun `a chosen group shows on the chip, and All in its menu clears it`() {
        val picked = mutableListOf<MuscleGroup?>()
        show(state(filter = LibraryFilter(group = MuscleGroup.ARMS)), RoutinesCallbacks(onGroupFilter = { picked += it }))

        compose.onNodeWithText("Arms").performClick()
        compose.onNodeWithText("All").performClick()

        assertEquals(listOf<MuscleGroup?>(null), picked)
    }

    @Test
    fun `the equipment chip opens the equipment options and picking one reports it`() {
        val picked = mutableListOf<LibraryEquipment?>()
        show(state(), RoutinesCallbacks(onEquipmentFilter = { picked += it }))

        compose.onNodeWithText("Equipment").performClick()
        compose.onNodeWithText("Barbell").performClick()

        assertEquals(listOf<LibraryEquipment?>(LibraryEquipment.BARBELL), picked)
    }

    @Test
    fun `the level chip opens the levels and picking one reports it`() {
        val picked = mutableListOf<LibraryLevel?>()
        show(state(), RoutinesCallbacks(onLevelFilter = { picked += it }))

        compose.onNodeWithText("Level").performClick()
        compose.onNodeWithText("Expert").performClick()

        assertEquals(listOf<LibraryLevel?>(LibraryLevel.EXPERT), picked)
    }

    @Test
    fun `chosen equipment and level show their values on the chips`() {
        show(state(filter = LibraryFilter(equipment = LibraryEquipment.DUMBBELL, level = LibraryLevel.BEGINNER)))

        compose.onNodeWithText("Dumbbell").assertExists()
        compose.onNodeWithText("Beginner").assertExists()
    }

    @Test
    fun `Clear is not offered without a filter`() {
        show(state())

        compose.onNodeWithText("Clear").assertDoesNotExist()
    }

    @Test
    fun `tapping Clear calls onClearFilters`() {
        var cleared = 0
        show(
            state(filter = LibraryFilter(group = MuscleGroup.ARMS)),
            RoutinesCallbacks(onClearFilters = { cleared++ }),
        )

        compose.onNodeWithText("Clear").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `the search text alone does not show Clear`() {
        show(state(filter = LibraryFilter(query = "curl")))

        compose.onNodeWithText("Clear").assertDoesNotExist()
    }
}
