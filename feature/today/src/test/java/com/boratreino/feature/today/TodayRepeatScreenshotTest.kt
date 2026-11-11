package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val offer = RepeatOfferUi(machineCount = 4, setCount = 12)

/** The ficha the samples show, with its id set (as the view model does), and the offer on. */
private val nextUp = TodaySamples.routineBNotStarted.copy(
    routines = TodaySamples.routineBNotStarted.routines?.copy(activeId = TodaySamples.routineB.id),
    repeatOffer = offer,
)
private val trainingNow = TodaySamples.routineBInProgress.copy(
    routines = TodaySamples.routineBInProgress.routines?.copy(activeId = TodaySamples.routineB.id),
    repeatOffer = offer,
)

/**
 * "Repeat last time" is one quiet text row under the exercise list; it opens the two repeat options.
 * Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayRepeatScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false, actions: TodayActions = TodayActions()) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `the two big repeat buttons are gone and one Repeat last time row is shown`() {
        shoot("today-repeat", nextUp)

        compose.onNodeWithText("Repeat last time").assertExists()
        compose.onNodeWithText("Same as last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
    }

    @Test
    fun `next up dark`() = shoot("today-repeat-dark", nextUp, dark = true)

    @Test
    fun `the repeat menu offers both options with the counts`() {
        shoot("today-repeat-menu", nextUp)

        compose.onNodeWithText("Repeat last time").performClick()

        compose.onNodeWithText("Same as last time").assertExists()
        compose.onNodeWithText("4 exercises · 12 sets").assertExists()
        compose.onNodeWithText("Same, with suggested weights").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-repeat-menu-open.png")
    }

    @Test
    fun `training now shows the repeat row and the finish text button`() {
        shoot("today-repeat-session", trainingNow)

        compose.onNodeWithText("Repeat last time").assertExists()
        compose.onNodeWithText("Finish training").assertExists()
    }

    @Test
    fun `training now dark`() = shoot("today-repeat-session-dark", trainingNow, dark = true)

    @Test
    fun `no offer no repeat row before a session`() {
        shoot("today-repeat-none", nextUp.copy(repeatOffer = null))

        compose.onNodeWithText("Repeat last time").assertDoesNotExist()
        compose.onNodeWithText("Same as last time").assertDoesNotExist()
    }

    @Test
    fun `no offer no repeat row during a session`() {
        shoot("today-repeat-none-session", trainingNow.copy(repeatOffer = null))

        compose.onNodeWithText("Repeat last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
    }

    @Test
    fun `hidden while editing before a session`() {
        shoot("today-repeat-editing", nextUp.copy(editing = true))

        compose.onNodeWithText("Repeat last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
    }

    @Test
    fun `hidden while editing during a session`() {
        shoot("today-repeat-editing-session", trainingNow.copy(editing = true))

        compose.onNodeWithText("Repeat last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
    }

    @Test
    fun `the repeat options report their own action`() {
        var same = 0
        var suggested = 0
        shoot(
            "today-repeat-actions",
            nextUp,
            actions = TodayActions(onRepeatSame = { same++ }, onRepeatSuggested = { suggested++ }),
        )

        compose.onNodeWithText("Repeat last time").performClick()
        compose.onNodeWithText("Same as last time").performClick()
        assertEquals(1 to 0, same to suggested)

        compose.onNodeWithText("Repeat last time").performClick()
        compose.onNodeWithText("Same, with suggested weights").performClick()
        assertEquals(1 to 1, same to suggested)
    }
}

/** The same cards in Baianês, to check the translated texts fit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class TodayRepeatPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `next up pt`() = shoot("today-repeat-pt", nextUp)

    @Test
    fun `training now pt`() = shoot("today-repeat-session-pt", trainingNow)

    @Test
    fun `training now pt dark`() = shoot("today-repeat-session-pt-dark", trainingNow, dark = true)
}
