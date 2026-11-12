package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

private fun warmupState(on: Boolean, language: AppLanguage = AppLanguage.ENGLISH) = SettingsUiState(
    loaded = true,
    weeklyGoal = 4,
    reminders = ReminderSettings(enabled = false, time = LocalTime.of(18, 0)),
    restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90),
    warmupCalculator = on,
    notificationsGranted = true,
    is24Hour = true,
    installedVersion = "0.40",
    language = language,
)

/** The Warm-up card on the Settings screen; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsWarmupScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SettingsUiState, actions: SettingsActions = SettingsActions(), dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        initialPage = SettingsPage.TRAINING,
                    )
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun `warm-up card on`() {
        setContent(warmupState(on = true))
        compose.onNodeWithText("Warm-up").assertExists()
        compose.onNodeWithText("Warm-up suggestions").assertExists()
        compose.onNodeWithText("Offers warm-up sets on the exercise screen, from your working weight.").assertExists()
        shoot("settings-warmup-on")
    }

    @Test
    fun `warm-up card off`() {
        setContent(warmupState(on = false))
        compose.onNodeWithText("Warm-up suggestions").assertExists()
        shoot("settings-warmup-off")
    }

    @Test
    fun `warm-up card on dark`() {
        setContent(warmupState(on = true), dark = true)
        shoot("settings-warmup-on-dark")
    }

    @Test
    fun `warm-up card off dark`() {
        setContent(warmupState(on = false), dark = true)
        shoot("settings-warmup-off-dark")
    }

    @Test
    fun `tapping the switch row asks for the opposite`() {
        val asked = mutableListOf<Boolean>()
        setContent(warmupState(on = true), SettingsActions(onWarmupCalculator = { asked += it }))

        compose.onNodeWithText("Warm-up suggestions").performClick()

        assertEquals(listOf(false), asked)
    }

    @Test
    fun `tapping the switch row when off asks to turn it on`() {
        val asked = mutableListOf<Boolean>()
        setContent(warmupState(on = false), SettingsActions(onWarmupCalculator = { asked += it }))

        compose.onNodeWithText("Warm-up suggestions").performClick()

        assertEquals(listOf(true), asked)
    }
}

/** The Warm-up card in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SettingsWarmupPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SettingsUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = SettingsPage.TRAINING,
                    )
                }
            }
        }
    }

    @Test
    fun `warm-up card on pt`() {
        setContent(warmupState(on = true, language = AppLanguage.BAIANES))
        compose.onNodeWithText("Sugestões de aquecimento").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-warmup-on-pt.png")
    }

    @Test
    fun `warm-up card off pt`() {
        setContent(warmupState(on = false, language = AppLanguage.BAIANES))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-warmup-off-pt.png")
    }
}
