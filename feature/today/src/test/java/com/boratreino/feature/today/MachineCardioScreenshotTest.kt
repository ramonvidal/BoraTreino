package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private val treadmill = Machine(
    id = 20L,
    name = "Treadmill",
    muscleGroup = MuscleGroup.CARDIO,
    kind = MachineKind.CARDIO,
    stepKg = 1.0,
    setupNote = "Incline 2",
    restSeconds = null,
    archived = false,
)

private fun cardioSet(id: Long, position: Int, draft: CardioDraft, at: String) =
    CardioRules.toSet(id, 1L, treadmill.id, position, draft, Instant.parse(at))

private val lastVisit: Instant = Instant.parse("2026-10-24T18:00:00Z")

/** Last time 30 min / 250 kcal / 3.2 km, one set logged today. */
private val withDistance = MachineUiState(
    loaded = true,
    machine = treadmill,
    lastTime = SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 3200),
    lastTimeAt = lastVisit,
    sets = listOf(cardioSet(1, 0, CardioDraft(15, 120, 1600), "2026-10-31T18:12:00Z")),
    cardio = CardioDraft(15, 120, 1600),
    cardioSameAgain = null,
    cardioDistanceShown = true,
)

/** Last time 30 min / 250 kcal and no distance: "Add distance" instead of the stepper. */
private val withoutDistance = MachineUiState(
    loaded = true,
    machine = treadmill,
    lastTime = SetSummary.Cardio(count = 2, minutes = 40, calories = 340, distanceM = 0),
    lastTimeAt = lastVisit,
    sets = emptyList(),
    cardio = CardioDraft(30, 250, 0),
    cardioSameAgain = CardioDraft(30, 250, 0),
    cardioDistanceShown = false,
)

/** First time ever: 20 minutes, nothing else, no Same again. */
private val firstTime = MachineUiState(
    loaded = true,
    machine = treadmill.copy(setupNote = null),
    lastTime = null,
    lastTimeAt = null,
    sets = emptyList(),
    cardio = CardioDraft(CardioRules.DEFAULT_MINUTES, 0, 0),
    cardioSameAgain = null,
    cardioDistanceShown = false,
)

/** The cardio machine screen: steppers for minutes, calories and distance; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineCardioScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MachineUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `with a distance last time shows the distance stepper`() {
        shoot("machine-cardio", withDistance)
        compose.onNodeWithText("Minutes").assertExists()
        compose.onNodeWithText("Calories").assertExists()
        compose.onNodeWithText("Distance (km)").assertExists()
        compose.onNodeWithText("Add distance").assertDoesNotExist()
        compose.onNodeWithText("Log 15 min · 120 kcal · 1.6 km").assertExists()
    }

    @Test
    fun `without a distance there is only the Add distance button`() {
        shoot("machine-cardio-no-distance", withoutDistance)
        compose.onNodeWithText("Minutes").assertExists()
        compose.onNodeWithText("Add distance").assertExists()
        compose.onNodeWithText("Distance (km)").assertDoesNotExist()
        compose.onNodeWithText("Same again").assertDoesNotExist()
    }

    @Test
    fun `first time on a cardio machine`() {
        shoot("machine-cardio-first-time", firstTime)
        compose.onNodeWithText("First time on this exercise").assertExists()
        compose.onNodeWithText("Same again").assertDoesNotExist()
        compose.onNodeWithText("Log 20 min").assertExists()
    }

    @Test
    fun `no weight or reps steppers on a cardio machine`() {
        shoot("machine-cardio-steppers", withoutDistance)
        compose.onNodeWithText("Reps").assertDoesNotExist()
        compose.onNodeWithText("Weight (kg)").assertDoesNotExist()
        compose.onNodeWithText("Set type: Normal").assertDoesNotExist()
        compose.onNodeWithText("Log 30 min · 250 kcal").assertExists()
    }

    @Test
    fun `Log is disabled when minutes and calories are 0`() {
        shoot("machine-cardio-empty-draft", withoutDistance.copy(cardio = CardioDraft(0, 0, 0)))
        compose.onNodeWithText("Log -").assertIsNotEnabled()
    }

    @Test
    fun `dark`() = shoot("machine-cardio-dark", withDistance, dark = true)

    @Test
    fun `dark without distance`() = shoot("machine-cardio-no-distance-dark", withoutDistance, dark = true)

    @Test
    fun `large text`() = shoot("machine-cardio-large", withDistance, fontScale = 2f)
}

/** The cardio machine screen in Baianês, to check the translated text fits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachineCardioPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MachineUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `with distance pt`() {
        shoot("machine-cardio-pt", withDistance)
        compose.onNodeWithText("Minutos").assertExists()
        compose.onNodeWithText("Calorias").assertExists()
        compose.onNodeWithText("Distância (km)").assertExists()
    }

    @Test
    fun `without distance pt`() {
        shoot("machine-cardio-no-distance-pt", withoutDistance)
        compose.onNodeWithText("Pôr distância").assertExists()
    }

    @Test
    fun `first time pt`() = shoot("machine-cardio-first-time-pt", firstTime)
}
