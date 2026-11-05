package com.boratreino.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Your body" card in English; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsBodyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shootCard(name: String, body: BodyCardState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            BodySection(body, SettingsActions())
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootDialog(name: String, body: BodyCardState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(loaded = true, body = body),
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `empty`() = shootCard("settings-body-empty", SettingsBodySamples.empty)

    @Test
    fun `partly filled`() = shootCard("settings-body-partial", SettingsBodySamples.partial)

    @Test
    fun `filled with the estimate`() = shootCard("settings-body-filled", SettingsBodySamples.filled)

    @Test
    fun `goal over`() = shootCard("settings-body-goal-over", SettingsBodySamples.goalOver)

    @Test
    fun `goal under`() = shootCard("settings-body-goal-under", SettingsBodySamples.goalUnder)

    @Test
    fun `basal rate typed`() = shootCard("settings-body-basal", SettingsBodySamples.manualBasal)

    @Test
    fun `with recent weights`() = shootCard("settings-body-weights", SettingsBodySamples.withWeights)

    @Test
    fun `switch off`() = shootCard("settings-body-off", SettingsBodySamples.switchedOff)

    @Test
    fun `empty dark`() = shootCard("settings-body-empty-dark", SettingsBodySamples.empty, dark = true)

    @Test
    fun `with recent weights dark`() = shootCard("settings-body-weights-dark", SettingsBodySamples.withWeights, dark = true)

    @Test
    fun `goal under dark`() = shootCard("settings-body-goal-under-dark", SettingsBodySamples.goalUnder, dark = true)

    @Test
    fun `with recent weights large text`() =
        shootCard("settings-body-weights-large", SettingsBodySamples.withWeights, fontScale = 2f)

    @Test
    fun `empty large text`() = shootCard("settings-body-empty-large", SettingsBodySamples.empty, fontScale = 2f)

    @Test
    fun `birth year pad`() = shootDialog("settings-body-dialog-birth-year", SettingsBodySamples.dialogBirthYear)

    @Test
    fun `height pad with an error`() = shootDialog("settings-body-dialog-error", SettingsBodySamples.dialogHeightError)

    @Test
    fun `weight pad`() = shootDialog("settings-body-dialog-weight", SettingsBodySamples.dialogWeight)

    @Test
    fun `basal pad`() = shootDialog("settings-body-dialog-basal", SettingsBodySamples.dialogBasal)

    @Test
    fun `log weight pad`() = shootDialog("settings-body-dialog-log-weight", SettingsBodySamples.dialogLogWeight)

    @Test
    fun `log weight pad dark`() =
        shootDialog("settings-body-dialog-log-weight-dark", SettingsBodySamples.dialogLogWeight, dark = true)
}
