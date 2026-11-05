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

/** The "Your body" card in Baianês; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SettingsBodyPtScreenshotTest {
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
    fun `empty pt`() = shootCard("settings-body-empty-pt", SettingsBodySamples.empty)

    @Test
    fun `filled pt`() = shootCard("settings-body-filled-pt", SettingsBodySamples.filled)

    @Test
    fun `goal over pt`() = shootCard("settings-body-goal-over-pt", SettingsBodySamples.goalOver)

    @Test
    fun `goal under pt`() = shootCard("settings-body-goal-under-pt", SettingsBodySamples.goalUnder)

    @Test
    fun `with recent weights pt`() = shootCard("settings-body-weights-pt", SettingsBodySamples.withWeights)

    @Test
    fun `with recent weights dark pt`() =
        shootCard("settings-body-weights-dark-pt", SettingsBodySamples.withWeights, dark = true)

    @Test
    fun `with recent weights large text pt`() =
        shootCard("settings-body-weights-large-pt", SettingsBodySamples.withWeights, fontScale = 2f)

    @Test
    fun `height pad with an error pt`() = shootDialog("settings-body-dialog-error-pt", SettingsBodySamples.dialogHeightError)

    @Test
    fun `log weight pad pt`() = shootDialog("settings-body-dialog-log-weight-pt", SettingsBodySamples.dialogLogWeight)
}
