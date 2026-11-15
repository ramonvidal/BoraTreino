package com.boratreino.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the Measurements card shows and what its taps report, checked on the texts it draws (English). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2000dp")
class MeasurementsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)
    private val from = LocalDate.of(2026, 8, 1)

    private val august = LocalDate.of(2026, 8, 10)
    private val october = LocalDate.of(2026, 10, 20)

    private fun waist(id: Long, date: LocalDate, cm: Double) =
        BodyMeasurement(id = id, date = date, type = MeasurementType.WAIST, valueCm = cm)

    /** Counts the nodes whose text is [text]. */
    private fun count(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    /** The only delete icon on the card (the tests that use it have one entry). */
    private fun onNodeDelete() = compose.onNode(hasContentDescription("Delete measurement of", substring = true))

    private fun card(
        measurements: List<BodyMeasurement>,
        selected: MeasurementType? = null,
        onType: (MeasurementType) -> Unit = {},
        onLog: () -> Unit = {},
        onDelete: (BodyMeasurement) -> Unit = {},
        onUndo: (BodyMeasurement) -> Unit = {},
    ) {
        compose.setContent {
            StatsCardHost {
                MeasurementsCard(
                    measurements = measurements,
                    from = from,
                    today = today,
                    selectedType = selected,
                    onType = onType,
                    onLog = onLog,
                    onDelete = onDelete,
                    onUndo = onUndo,
                )
            }
        }
    }

    // Title, chips and the empty state

    @Test
    fun `the card has its title and a chip for each type`() {
        card(emptyList())

        shown("Measurements")
        shown("Waist")
        shown("Hips")
        shown("Chest")
        shown("Arm")
        shown("Thigh")
        shown("Neck")
    }

    @Test
    fun `without measurements the empty state and the log button are shown`() {
        card(emptyList())

        shown("No measurements yet")
        shown("Log measurement")
    }

    @Test
    fun `the log button calls onLog`() {
        var logged = 0
        card(emptyList(), onLog = { logged++ })

        compose.onNodeWithText("Log measurement").performClick()

        assertEquals(1, logged)
    }

    @Test
    fun `the log button is shown with measurements too`() {
        var logged = 0
        card(listOf(waist(1, august, 84.5)), onLog = { logged++ })

        compose.onNodeWithText("Log measurement").performClick()

        assertEquals(1, logged)
    }

    // The latest value and the change

    @Test
    fun `the latest value of the selected type and its change over the period are shown`() {
        card(listOf(waist(1, august, 84.5), waist(2, october, 82.5)), selected = MeasurementType.WAIST)

        assertTrue(count("82.5 cm") >= 1)
        shown("-2.0 cm in the period")
    }

    @Test
    fun `a rise in the change has a plus sign`() {
        card(listOf(waist(1, august, 82.5), waist(2, october, 84.5)), selected = MeasurementType.WAIST)

        shown("+2.0 cm in the period")
    }

    @Test
    fun `with a single measurement there is no change to show`() {
        card(listOf(waist(1, october, 82.5)), selected = MeasurementType.WAIST)

        compose.onAllNodesWithText("in the period", substring = true).assertCountEquals(0)
    }

    @Test
    fun `with no selected type the first type with data is shown`() {
        card(listOf(BodyMeasurement(id = 1, date = october, type = MeasurementType.ARM, valueCm = 35.0)))

        assertTrue(count("35 cm") >= 1)
    }

    // Type chips

    @Test
    fun `tapping a type chip selects that type and reports it`() {
        val picked = mutableListOf<MeasurementType>()
        val data = listOf(
            waist(1, october, 82.5),
            BodyMeasurement(id = 2, date = october, type = MeasurementType.ARM, valueCm = 35.0),
        )
        compose.setContent {
            var selected by remember { mutableStateOf<MeasurementType?>(null) }
            StatsCardHost {
                MeasurementsCard(
                    measurements = data,
                    from = from,
                    today = today,
                    selectedType = selected,
                    onType = {
                        selected = it
                        picked += it
                    },
                    onLog = {},
                    onDelete = {},
                    onUndo = {},
                )
            }
        }
        assertTrue(count("82.5 cm") >= 1)

        compose.onNodeWithText("Arm").performClick()

        assertEquals(listOf(MeasurementType.ARM), picked)
        assertEquals(0, count("82.5 cm"))
        assertTrue(count("35 cm") >= 1)
    }

    // The history

    @Test
    fun `the history lists at most five entries of the selected type`() {
        val waist = (1..7).map { waist(it.toLong(), october.minusDays((7 - it).toLong()), 70.5 + it) }
        val hips = BodyMeasurement(id = 99, date = october, type = MeasurementType.HIPS, valueCm = 99.5)
        card(waist + hips, selected = MeasurementType.WAIST)

        // The two oldest (71.5 and 72.5) drop out.
        assertEquals(0, count("71.5 cm"))
        assertEquals(0, count("72.5 cm"))
        assertTrue(count("73.5 cm") >= 1)
        assertTrue(count("77.5 cm") >= 1)
        assertEquals(0, count("99.5 cm"))
        compose.onAllNodes(hasContentDescription("Delete measurement of", substring = true)).assertCountEquals(5)
    }

    @Test
    fun `tapping the delete icon of an entry calls onDelete with it`() {
        val entry = waist(7, october, 77.5)
        val deleted = mutableListOf<BodyMeasurement>()
        card(listOf(entry), selected = MeasurementType.WAIST, onDelete = { deleted += it })

        onNodeDelete().performClick()

        assertEquals(listOf(entry), deleted)
    }

    @Test
    fun `after a delete an undo row is shown and its undo calls onUndo with the entry`() {
        val entry = waist(7, october, 77.5)
        val undone = mutableListOf<BodyMeasurement>()
        card(listOf(entry), selected = MeasurementType.WAIST, onUndo = { undone += it })

        onNodeDelete().performClick()
        shown("Measurement deleted")

        compose.onNodeWithText("Undo").performClick()

        assertEquals(listOf(entry), undone)
        compose.onAllNodesWithText("Measurement deleted").assertCountEquals(0)
    }

    // The chart

    @Test
    fun `the chart is drawn for two or more points`() {
        card(listOf(waist(1, august, 84.5), waist(2, october, 82.5)), selected = MeasurementType.WAIST)

        compose.onNodeWithContentDescription("Waist: 84.5 cm to 82.5 cm").assertIsDisplayed()
    }

    @Test
    fun `there is no chart for a single point`() {
        card(listOf(waist(1, october, 82.5)), selected = MeasurementType.WAIST)

        compose.onAllNodes(hasContentDescription("cm to", substring = true)).assertCountEquals(0)
    }
}
