package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Measurements card and the log sheet's content; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2000dp")
class MeasurementsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)
    private val from = LocalDate.of(2026, 8, 10)

    private val withData = listOf(
        BodyMeasurement(id = 1, date = LocalDate.of(2026, 8, 10), type = MeasurementType.WAIST, valueCm = 86.0),
        BodyMeasurement(id = 2, date = LocalDate.of(2026, 9, 14), type = MeasurementType.WAIST, valueCm = 84.5),
        BodyMeasurement(id = 3, date = LocalDate.of(2026, 10, 12), type = MeasurementType.WAIST, valueCm = 83.5),
        BodyMeasurement(id = 4, date = LocalDate.of(2026, 11, 1), type = MeasurementType.WAIST, valueCm = 82.5),
        BodyMeasurement(id = 5, date = LocalDate.of(2026, 10, 20), type = MeasurementType.ARM, valueCm = 35.0),
        BodyMeasurement(id = 6, date = LocalDate.of(2026, 10, 20), type = MeasurementType.HIPS, valueCm = 98.0),
    )

    private fun card(name: String, measurements: List<BodyMeasurement>, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            StatsCardHost(dark, fontScale) {
                MeasurementsCard(
                    measurements = measurements,
                    from = from,
                    today = today,
                    selectedType = null,
                    onType = {},
                    onLog = {},
                    onDelete = {},
                    onUndo = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun sheet(name: String, measurements: List<BodyMeasurement>, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            StatsCardHost(dark, fontScale) {
                LogMeasurementContent(measurements = measurements, initialType = MeasurementType.WAIST, onSave = { _, _ -> })
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `measurements card with data`() = card("measurements-card", withData)

    @Test
    fun `measurements card empty`() = card("measurements-card-empty", emptyList())

    @Test
    fun `measurements card dark`() = card("measurements-card-dark", withData, dark = true)

    @Test
    fun `measurements card large text`() = card("measurements-card-large", withData, fontScale = 2f)

    @Test
    fun `log measurement sheet`() = sheet("measurements-log", withData)

    @Test
    fun `log measurement sheet dark`() = sheet("measurements-log-dark", withData, dark = true)

    @Test
    fun `log measurement sheet large text`() = sheet("measurements-log-large", withData, fontScale = 2f)
}
