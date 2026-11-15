package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The log measurement sheet's content: where the value starts, the steps, the type and Save (English). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2000dp")
class LogMeasurementContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)

    private fun waist(id: Long, date: LocalDate, cm: Double) =
        BodyMeasurement(id = id, date = date, type = MeasurementType.WAIST, valueCm = cm)

    private fun count(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun sheet(
        measurements: List<BodyMeasurement> = emptyList(),
        initial: MeasurementType = MeasurementType.WAIST,
        onSave: (MeasurementType, Double) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            StatsCardHost {
                LogMeasurementContent(measurements = measurements, initialType = initial, onSave = onSave)
            }
        }
    }

    @Test
    fun `without a measurement of the type the value starts at 80 cm`() {
        sheet()

        shown("80 cm")
    }

    @Test
    fun `the value starts at the latest value of the type`() {
        sheet(
            measurements = listOf(
                waist(1, LocalDate.of(2026, 9, 1), 90.0),
                waist(2, LocalDate.of(2026, 10, 20), 82.5),
            ),
        )

        shown("82.5 cm")
        assertEquals(0, count("90 cm"))
    }

    @Test
    fun `the plus and minus buttons change the value by half a centimetre`() {
        sheet()

        compose.onNodeWithContentDescription("Increase Value in cm").performClick()
        shown("80.5 cm")

        compose.onNodeWithContentDescription("Decrease Value in cm").performClick()
        compose.onNodeWithContentDescription("Decrease Value in cm").performClick()
        shown("79.5 cm")
    }

    @Test
    fun `a type chip changes the value the sheet starts at`() {
        sheet(measurements = listOf(waist(1, today, 82.5)), initial = MeasurementType.WAIST)
        shown("82.5 cm")

        compose.onNodeWithText("Chest").performClick()

        assertEquals(0, count("82.5 cm"))
        shown("80 cm")

        compose.onNodeWithText("Waist").performClick()

        shown("82.5 cm")
    }

    @Test
    fun `save reports the type and the value that is shown`() {
        val saved = mutableListOf<Pair<MeasurementType, Double>>()
        sheet(
            measurements = listOf(waist(1, today, 82.5)),
            initial = MeasurementType.WAIST,
            onSave = { type, cm -> saved += type to cm },
        )

        compose.onNodeWithContentDescription("Increase Value in cm").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(MeasurementType.WAIST to 83.0), saved)
    }

    @Test
    fun `save after choosing another type reports that type at its start value`() {
        val saved = mutableListOf<Pair<MeasurementType, Double>>()
        sheet(onSave = { type, cm -> saved += type to cm })

        compose.onNodeWithText("Hips").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(MeasurementType.HIPS to 80.0), saved)
    }
}
