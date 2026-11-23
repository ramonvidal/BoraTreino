package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 43: reading a German bike console summary from OCR lines. Written from the contract, not the parser. */
class BikeScreenParserTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrLine(text, left, top, right, bottom)

    /** A line whose position is unknown (all-zero box), as in text-order tests. */
    private fun noBox(text: String) = OcrLine(text, 0, 0, 0, 0)

    private fun merged(vararg texts: String): List<OcrLine> = texts.map { noBox(it) }

    private fun assertDouble(expected: Double?, actual: Double?) {
        if (expected == null) assertNull(actual) else assertEquals(expected, actual!!, 0.0001)
    }

    private fun assertPhotoReading(r: BikeReading) {
        assertEquals(46, r.minutes)
        assertEquals(397, r.calories)
        assertEquals(17300, r.distanceM)
        assertNull(r.watts)
        assertNull(r.heartRate)
        assertDouble(1.2, r.wattsPerKg)
        assertEquals(48, r.tss)
        assertDouble(0.79, r.intensityFactor)
    }

    /**
     * The photo, tilted about 5 degrees: labels at x 200-500, values at x 1100-1250, rows about 100 px apart.
     * Because of the tilt, each value sits about 72 px lower than its own label, closer to the row above's
     * label than to its own would be on a flat image.
     */
    private val tiltedPhoto = listOf(
        line("ZUSAMMENFASSUNG - 00:45:52", 200, 120, 1250, 170),
        line("KALORIEN", 200, 311, 500, 351),
        line("397", 1100, 383, 1250, 423),
        line("KM", 200, 411, 500, 451),
        line("17.30", 1100, 483, 1250, 523),
        line("WATT / HR", 200, 511, 500, 551),
        line("--", 1100, 583, 1250, 623),
        line("WATT / KG", 200, 611, 500, 651),
        line("1.2", 1100, 683, 1250, 723),
        line("TSS / IF", 200, 711, 500, 751),
        line("48 / 0.79", 1100, 783, 1250, 823),
    )

    // The photo example

    @Test
    fun `the tilted photo reads every value next to its own label`() {
        assertPhotoReading(BikeScreenParser.parse(tiltedPhoto))
    }

    @Test
    fun `the photo reading is not empty`() {
        assertFalse(BikeScreenParser.parse(tiltedPhoto).isEmpty)
    }

    @Test
    fun `the photo in text order with no boxes reads the same`() {
        val lines = listOf(
            "ZUSAMMENFASSUNG - 00:45:52",
            "KALORIEN",
            "397",
            "KM",
            "17.30",
            "WATT / HR",
            "--",
            "WATT / KG",
            "1.2",
            "TSS / IF",
            "48 / 0.79",
        ).map { noBox(it) }

        assertPhotoReading(BikeScreenParser.parse(lines))
    }

    @Test
    fun `the photo with the unit of each row merged into its value reads the same`() {
        val lines = listOf(
            line("ZUSAMMENFASSUNG - 00:45:52", 200, 120, 1250, 170),
            line("KALORIEN 397", 200, 311, 1250, 351),
            line("KM 17.30", 200, 411, 1250, 451),
            line("WATT / HR --", 200, 511, 1250, 551),
            line("WATT / KG 1.2", 200, 611, 1250, 651),
            line("TSS / IF 48 / 0.79", 200, 711, 1250, 751),
        )

        assertPhotoReading(BikeScreenParser.parse(lines))
    }

    // Merged lines

    @Test
    fun `a label and its value in one line are read`() {
        val r = BikeScreenParser.parse(merged("KALORIEN 397", "KM 17.30", "WATT / KG 1.2", "TSS / IF 48 / 0.79"))

        assertEquals(397, r.calories)
        assertEquals(17300, r.distanceM)
        assertDouble(1.2, r.wattsPerKg)
        assertEquals(48, r.tss)
        assertDouble(0.79, r.intensityFactor)
    }

    @Test
    fun `WATT slash HR with two numbers gives watts and heart rate`() {
        val r = BikeScreenParser.parse(merged("WATT / HR 165 / 142"))

        assertEquals(165, r.watts)
        assertEquals(142, r.heartRate)
    }

    @Test
    fun `WATT slash HR with the values on their own line gives watts and heart rate`() {
        val lines = listOf(
            line("WATT / HR", 200, 511, 500, 551),
            line("165 / 142", 1100, 511, 1250, 551),
        )

        val r = BikeScreenParser.parse(lines)

        assertEquals(165, r.watts)
        assertEquals(142, r.heartRate)
    }

    // Numbers

    @Test
    fun `a decimal comma reads the same as a decimal point`() {
        val r = BikeScreenParser.parse(merged("KM 17,30", "WATT / KG 1,2", "TSS / IF 48 / 0,79"))

        assertEquals(17300, r.distanceM)
        assertDouble(1.2, r.wattsPerKg)
        assertDouble(0.79, r.intensityFactor)
    }

    @Test
    fun `a dash in place of a value leaves it null`() {
        val lines = listOf(
            line("WATT / HR", 200, 511, 500, 551),
            line("- -", 1100, 511, 1250, 551),
        )

        val r = BikeScreenParser.parse(lines)

        assertNull(r.watts)
        assertNull(r.heartRate)
    }

    @Test
    fun `a double dash in place of a value leaves it null`() {
        val r = BikeScreenParser.parse(merged("WATT / HR --"))

        assertNull(r.watts)
        assertNull(r.heartRate)
        assertTrue(r.isEmpty)
    }

    // English and other labels

    @Test
    fun `English labels read the same values`() {
        val r = BikeScreenParser.parse(
            merged("TIME 45:52", "CALORIES 397", "DISTANCE 17.30", "POWER / HEART RATE 165 / 142", "W/KG 1.2"),
        )

        assertEquals(46, r.minutes)
        assertEquals(397, r.calories)
        assertEquals(17300, r.distanceM)
        assertEquals(165, r.watts)
        assertEquals(142, r.heartRate)
        assertDouble(1.2, r.wattsPerKg)
    }

    @Test
    fun `KCAL is read as calories`() {
        assertEquals(397, BikeScreenParser.parse(merged("KCAL 397")).calories)
    }

    @Test
    fun `German alternatives for the labels are read`() {
        val r = BikeScreenParser.parse(merged("DAUER 45:52", "DISTANZ 17.30", "PULS 142"))

        assertEquals(46, r.minutes)
        assertEquals(17300, r.distanceM)
        assertEquals(142, r.heartRate)
    }

    @Test
    fun `ZEIT is read as the time`() {
        assertEquals(46, BikeScreenParser.parse(merged("ZEIT 45:52")).minutes)
    }

    // Time

    @Test
    fun `the header hh-mm-ss is rounded to the nearest minute`() {
        assertEquals(46, BikeScreenParser.parse(merged("ZUSAMMENFASSUNG - 00:45:52")).minutes)
        assertEquals(45, BikeScreenParser.parse(merged("ZUSAMMENFASSUNG - 00:45:29")).minutes)
        assertEquals(46, BikeScreenParser.parse(merged("ZUSAMMENFASSUNG - 00:45:31")).minutes)
    }

    @Test
    fun `a clock with hours counts the hours as minutes`() {
        assertEquals(62, BikeScreenParser.parse(merged("ZUSAMMENFASSUNG - 01:02:03")).minutes)
    }

    @Test
    fun `a mm-ss time is rounded to the nearest minute`() {
        assertEquals(46, BikeScreenParser.parse(merged("TIME 45:52")).minutes)
        assertEquals(45, BikeScreenParser.parse(merged("TIME 45:29")).minutes)
    }

    @Test
    fun `a time over 600 minutes is rejected`() {
        assertNull(BikeScreenParser.parse(merged("ZUSAMMENFASSUNG - 10:30:00")).minutes)
    }

    // Implausible values are left null

    @Test
    fun `a heart rate of 300 is rejected`() {
        assertNull(BikeScreenParser.parse(merged("HEART RATE 300")).heartRate)
    }

    @Test
    fun `a distance of 1730 km is rejected`() {
        assertNull(BikeScreenParser.parse(merged("KM 1730")).distanceM)
    }

    @Test
    fun `an intensity factor of 3 is rejected while the TSS next to it is kept`() {
        val r = BikeScreenParser.parse(merged("TSS / IF 48 / 3"))

        assertEquals(48, r.tss)
        assertNull(r.intensityFactor)
    }

    @Test
    fun `a watts per kilo of 12 is rejected`() {
        assertNull(BikeScreenParser.parse(merged("WATT / KG 12")).wattsPerKg)
    }

    @Test
    fun `a heart rate under 30 is rejected`() {
        assertNull(BikeScreenParser.parse(merged("PULS 20")).heartRate)
    }

    // Empty and garbage input

    @Test
    fun `no lines gives an empty reading`() {
        assertTrue(BikeScreenParser.parse(emptyList()).isEmpty)
    }

    @Test
    fun `blank lines give an empty reading`() {
        assertTrue(BikeScreenParser.parse(merged("", "   ", "\t")).isEmpty)
    }

    @Test
    fun `garbage text gives an empty reading`() {
        assertTrue(BikeScreenParser.parse(merged("lorem ipsum", "%%% ###", "Hello world 123 xyz")).isEmpty)
    }

    // Labels are not confused with each other

    @Test
    fun `CALORIES is read as calories, not as a CAL fragment`() {
        assertEquals(397, BikeScreenParser.parse(merged("CALORIES 397")).calories)
    }

    @Test
    fun `CAL is read as calories`() {
        assertEquals(250, BikeScreenParser.parse(merged("CAL 250")).calories)
    }

    @Test
    fun `WATT slash KG is the watts per kilo and not the watts`() {
        val r = BikeScreenParser.parse(merged("WATT / KG 1.2"))

        assertDouble(1.2, r.wattsPerKg)
        assertNull(r.watts)
    }

    @Test
    fun `WATT slash HR and WATT slash KG on one screen do not swap their values`() {
        val lines = listOf(
            line("WATT / HR", 200, 511, 500, 551),
            line("165 / 142", 1100, 511, 1250, 551),
            line("WATT / KG", 200, 611, 500, 651),
            line("2.1", 1100, 611, 1250, 651),
        )

        val r = BikeScreenParser.parse(lines)

        assertEquals(165, r.watts)
        assertEquals(142, r.heartRate)
        assertDouble(2.1, r.wattsPerKg)
    }

    // BikeReading.isEmpty

    @Test
    fun `a reading with only one value is not empty`() {
        assertFalse(BikeReading(null, null, null, null, null, 1.2, null, null).isEmpty)
    }

    @Test
    fun `a reading with nothing set is empty`() {
        assertTrue(BikeReading(null, null, null, null, null, null, null, null).isEmpty)
    }
}
