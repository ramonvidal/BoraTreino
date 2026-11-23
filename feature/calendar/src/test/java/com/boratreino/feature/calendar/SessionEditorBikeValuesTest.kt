package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private val bikeValuesBike = Machine(33L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

private val bikeValuesSet = CardioRules.toSet(
    id = 9020L,
    sessionId = CalendarSamples.mondaySession.session.id,
    machineId = bikeValuesBike.id,
    position = 0,
    draft = CardioDraft(30, 250, 3200, 12),
    loggedAt = Instant.parse("2026-11-02T21:20:00Z"),
)

private val bikeValuesRow = CardioRules.toSet(
    id = 9021L,
    sessionId = CalendarSamples.mondaySession.session.id,
    machineId = bikeValuesBike.id,
    position = 1,
    draft = CardioDraft(minutes = 20, calories = 0, distanceM = 0, watts = 160, heartRate = 142, intensityFactor = 0.79),
    loggedAt = Instant.parse("2026-11-02T21:40:00Z"),
)

/** Monday's session with a bike that has a set of 30 min and a set with bike values (20 min, 160 W, 142 bpm, IF 0.79). */
private val bikeValuesDetail = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let {
        SessionDetail(it.session, it.routine, it.machines + SessionMachine(bikeValuesBike, listOf(bikeValuesSet, bikeValuesRow)))
    },
    machines = CalendarSamples.editorState.machines + bikeValuesBike,
)

/** The session editor's cardio sheet with the Bike values section: collapsed, expanded, auto-opened, and the set rows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorBikeValuesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState, actions: SessionEditorActions = SessionEditorActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun editing(
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
    ) = bikeValuesDetail.copy(
        editor = SetEdit(
            set = bikeValuesSet,
            machine = bikeValuesBike,
            number = 1,
            weightKg = 0.0,
            reps = 10,
            minutes = 30,
            calories = 250,
            distanceM = 3200,
            intensity = 12,
            watts = watts,
            heartRate = heartRate,
            wattsPerKg = wattsPerKg,
            tss = tss,
            intensityFactor = intensityFactor,
        ),
    )

    private fun tapBikeValues() {
        compose.onNodeWithText("Bike values").performClick()
    }

    // Collapsed and expanded

    @Test
    fun `the bike values section is collapsed by default`() {
        setContent(editing())

        compose.onNodeWithText("Bike values").assertExists()
        compose.onNodeWithContentDescription("Increase Watts").assertDoesNotExist()
        compose.onNodeWithText("Watts").assertDoesNotExist()
    }

    @Test
    fun `tapping Bike values shows the five console steppers`() {
        setContent(editing())

        tapBikeValues()

        compose.onNodeWithContentDescription("Increase Watts").assertExists()
        compose.onNodeWithContentDescription("Increase Heart rate").assertExists()
        compose.onNodeWithContentDescription("Increase Watts/kg").assertExists()
        compose.onNodeWithContentDescription("Increase TSS").assertExists()
        compose.onNodeWithContentDescription("Increase IF").assertExists()
    }

    @Test
    fun `the section opens by itself when the set has watts`() {
        setContent(editing(watts = 160))

        compose.onNodeWithContentDescription("Increase Watts").assertExists()
    }

    @Test
    fun `the section opens by itself when the set has an intensity factor`() {
        setContent(editing(intensityFactor = 0.79))

        compose.onNodeWithContentDescription("Increase IF").assertExists()
    }

    // Steppers

    @Test
    fun `the stepper values show the logged numbers`() {
        setContent(editing(watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79))

        compose.onNodeWithText("160", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("142", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2.4", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("48", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("0.79", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a stepper that is not logged shows a dash`() {
        setContent(editing(watts = 160))

        // Heart rate, W/kg, TSS and IF are not logged; other places may show dashes too.
        val dashes = compose.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected at least four dashes, found ${dashes.size}", dashes.size >= 4)
    }

    @Test
    fun `the bike steppers report each step`() {
        val watts = mutableListOf<Boolean>()
        val heartRate = mutableListOf<Boolean>()
        val wattsPerKg = mutableListOf<Boolean>()
        val tss = mutableListOf<Boolean>()
        val intensityFactor = mutableListOf<Boolean>()
        setContent(
            editing(),
            SessionEditorActions(
                onStepEditorWatts = { watts += it },
                onStepEditorHeartRate = { heartRate += it },
                onStepEditorWattsPerKg = { wattsPerKg += it },
                onStepEditorTss = { tss += it },
                onStepEditorIntensityFactor = { intensityFactor += it },
            ),
        )
        tapBikeValues()

        compose.onNodeWithContentDescription("Increase Watts").performClick()
        compose.onNodeWithContentDescription("Decrease Watts").performClick()
        compose.onNodeWithContentDescription("Increase Heart rate").performClick()
        compose.onNodeWithContentDescription("Decrease Watts/kg").performClick()
        compose.onNodeWithContentDescription("Increase TSS").performClick()
        compose.onNodeWithContentDescription("Decrease IF").performClick()

        assertEquals(listOf(true, false), watts)
        assertEquals(listOf(true), heartRate)
        assertEquals(listOf(false), wattsPerKg)
        assertEquals(listOf(true), tss)
        assertEquals(listOf(false), intensityFactor)
    }

    // The set rows

    @Test
    fun `a set row with bike values shows its watts, bpm and IF`() {
        setContent(bikeValuesDetail)

        compose.onNodeWithText("20 min · 160 W · 142 bpm · IF 0.79").assertExists()
    }

    @Test
    fun `a set row without bike values shows only its minutes, calories, distance and intensity`() {
        setContent(bikeValuesDetail)

        compose.onNodeWithText("30 min · 250 kcal · 3.2 km · int. 12").assertExists()
    }
}
