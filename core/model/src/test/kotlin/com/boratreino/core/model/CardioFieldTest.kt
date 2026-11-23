package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The number pad's cardio values: which fields take a decimal, their starting text, and typed text (phase 44). */
class CardioFieldTest {
    private val full = CardioDraft(
        minutes = 20,
        calories = 250,
        distanceM = 5000,
        intensity = 12,
        watts = 160,
        heartRate = 142,
        wattsPerKg = 2.4,
        tss = 48,
        intensityFactor = 0.79,
    )

    private val empty = CardioDraft(minutes = 0, calories = 0, distanceM = 0)

    private fun typed(field: CardioField, text: String, draft: CardioDraft = full): CardioDraft? =
        CardioRules.withField(draft, field, text)

    // allowsDecimal

    @Test
    fun `only distance, watts per kg and intensity factor take a decimal`() {
        val decimal = CardioField.entries.filter { CardioRules.allowsDecimal(it) }.toSet()

        assertEquals(setOf(CardioField.DISTANCE_KM, CardioField.WATTS_PER_KG, CardioField.INTENSITY_FACTOR), decimal)
    }

    // fieldText

    @Test
    fun `fieldText is empty for a value that is not set or is zero`() {
        val zeros = CardioDraft(
            minutes = 0, calories = 0, distanceM = 0, intensity = 0, watts = 0, heartRate = 0,
            wattsPerKg = 0.0, tss = 0, intensityFactor = 0.0,
        )

        CardioField.entries.forEach { field ->
            assertEquals("$field of an empty draft", "", CardioRules.fieldText(empty, field))
            assertEquals("$field of a zero draft", "", CardioRules.fieldText(zeros, field))
        }
    }

    @Test
    fun `fieldText shows distance in kilometres without trailing zeros`() {
        assertEquals("17.3", CardioRules.fieldText(empty.copy(distanceM = 17300), CardioField.DISTANCE_KM))
        assertEquals("17", CardioRules.fieldText(empty.copy(distanceM = 17000), CardioField.DISTANCE_KM))
        assertEquals("0.05", CardioRules.fieldText(empty.copy(distanceM = 50), CardioField.DISTANCE_KM))
    }

    @Test
    fun `fieldText shows watts per kg and intensity factor without trailing zeros`() {
        assertEquals("1", CardioRules.fieldText(empty.copy(wattsPerKg = 1.0), CardioField.WATTS_PER_KG))
        assertEquals("1.5", CardioRules.fieldText(empty.copy(wattsPerKg = 1.5), CardioField.WATTS_PER_KG))
        assertEquals("0.7", CardioRules.fieldText(empty.copy(intensityFactor = 0.7), CardioField.INTENSITY_FACTOR))
        assertEquals("0.79", CardioRules.fieldText(empty.copy(intensityFactor = 0.79), CardioField.INTENSITY_FACTOR))
    }

    @Test
    fun `fieldText shows the whole numbers as they are`() {
        assertEquals("20", CardioRules.fieldText(full, CardioField.MINUTES))
        assertEquals("250", CardioRules.fieldText(full, CardioField.CALORIES))
        assertEquals("12", CardioRules.fieldText(full, CardioField.INTENSITY))
        assertEquals("160", CardioRules.fieldText(full, CardioField.WATTS))
        assertEquals("142", CardioRules.fieldText(full, CardioField.HEART_RATE))
        assertEquals("48", CardioRules.fieldText(full, CardioField.TSS))
    }

    // withField: text forms

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(12, typed(CardioField.MINUTES, "  12  ")!!.minutes)
    }

    @Test
    fun `a comma or a point is the decimal separator`() {
        assertEquals(12500, typed(CardioField.DISTANCE_KM, "12,5")!!.distanceM)
        assertEquals(12500, typed(CardioField.DISTANCE_KM, "12.5")!!.distanceM)
        assertEquals(2.4, typed(CardioField.WATTS_PER_KG, "2,4")!!.wattsPerKg!!, 1e-9)
        assertEquals(0.65, typed(CardioField.INTENSITY_FACTOR, "0,65")!!.intensityFactor!!, 1e-9)
    }

    @Test
    fun `a point with no digits after it, or none before it, is still a number`() {
        assertEquals(5, typed(CardioField.MINUTES, "5.")!!.minutes)
        assertEquals(500, typed(CardioField.DISTANCE_KM, ".5")!!.distanceM)
    }

    @Test
    fun `a blank text clears minutes, calories and distance to 0`() {
        assertEquals(0, typed(CardioField.MINUTES, "")!!.minutes)
        assertEquals(0, typed(CardioField.CALORIES, "   ")!!.calories)
        assertEquals(0, typed(CardioField.DISTANCE_KM, "")!!.distanceM)
    }

    @Test
    fun `a blank text clears the optional values to null`() {
        assertNull(typed(CardioField.INTENSITY, "")!!.intensity)
        assertNull(typed(CardioField.WATTS, "")!!.watts)
        assertNull(typed(CardioField.HEART_RATE, " ")!!.heartRate)
        assertNull(typed(CardioField.WATTS_PER_KG, "")!!.wattsPerKg)
        assertNull(typed(CardioField.TSS, "")!!.tss)
        assertNull(typed(CardioField.INTENSITY_FACTOR, "")!!.intensityFactor)
    }

    @Test
    fun `text that is not a plain number returns null for every field`() {
        listOf("abc", "1,2,3", "1e3", "-3", "+5", "1.2.3").forEach { text ->
            CardioField.entries.forEach { field ->
                assertNull("$field with '$text'", typed(field, text))
            }
        }
    }

    // withField: clamping and rounding

    @Test
    fun `minutes are clamped to the most a cardio set can hold`() {
        assertEquals(CardioRules.MAX_MINUTES, typed(CardioField.MINUTES, "600")!!.minutes)
        assertEquals(CardioRules.MAX_MINUTES, typed(CardioField.MINUTES, "700")!!.minutes)
    }

    @Test
    fun `calories are clamped to their maximum`() {
        assertEquals(CardioRules.MAX_CALORIES, typed(CardioField.CALORIES, "9999")!!.calories)
    }

    @Test
    fun `distance is typed in kilometres and stored in metres, clamped to 200 km`() {
        assertEquals(200_000, typed(CardioField.DISTANCE_KM, "9999")!!.distanceM)
        assertEquals(17_346, typed(CardioField.DISTANCE_KM, "17.3456")!!.distanceM)
    }

    @Test
    fun `intensity is clamped to 999`() {
        assertEquals(999, typed(CardioField.INTENSITY, "999")!!.intensity)
        assertEquals(999, typed(CardioField.INTENSITY, "1000")!!.intensity)
    }

    @Test
    fun `watts are clamped to 2000`() {
        assertEquals(2000, typed(CardioField.WATTS, "2000")!!.watts)
        assertEquals(2000, typed(CardioField.WATTS, "2500")!!.watts)
    }

    @Test
    fun `heart rate is clamped to 30 to 250`() {
        assertEquals(30, typed(CardioField.HEART_RATE, "10")!!.heartRate)
        assertEquals(250, typed(CardioField.HEART_RATE, "300")!!.heartRate)
        assertEquals(142, typed(CardioField.HEART_RATE, "142")!!.heartRate)
    }

    @Test
    fun `TSS is clamped to 999`() {
        assertEquals(999, typed(CardioField.TSS, "999")!!.tss)
        assertEquals(999, typed(CardioField.TSS, "1000")!!.tss)
    }

    @Test
    fun `watts per kg is clamped to 10 and kept to one decimal`() {
        assertEquals(10.0, typed(CardioField.WATTS_PER_KG, "10.0")!!.wattsPerKg!!, 1e-9)
        assertEquals(10.0, typed(CardioField.WATTS_PER_KG, "12")!!.wattsPerKg!!, 1e-9)
        assertEquals(3.1, typed(CardioField.WATTS_PER_KG, "3.14")!!.wattsPerKg!!, 1e-9)
        assertEquals(3.2, typed(CardioField.WATTS_PER_KG, "3.16")!!.wattsPerKg!!, 1e-9)
    }

    @Test
    fun `intensity factor is clamped to 2 and kept to two decimals`() {
        assertEquals(2.0, typed(CardioField.INTENSITY_FACTOR, "2.0")!!.intensityFactor!!, 1e-9)
        assertEquals(2.0, typed(CardioField.INTENSITY_FACTOR, "2.5")!!.intensityFactor!!, 1e-9)
        assertEquals(0.79, typed(CardioField.INTENSITY_FACTOR, "0.789")!!.intensityFactor!!, 1e-9)
    }

    @Test
    fun `a decimal typed into a whole-number field is rounded half up`() {
        assertEquals(46, typed(CardioField.MINUTES, "45.5")!!.minutes)
        assertEquals(45, typed(CardioField.MINUTES, "45.4")!!.minutes)
        assertEquals(250, typed(CardioField.CALORIES, "249.5")!!.calories)
    }

    @Test
    fun `a huge string of digits never throws and is clamped`() {
        val huge = "9".repeat(400)

        CardioField.entries.forEach { field ->
            assertNotNull("$field with a huge number", typed(field, huge))
        }
        assertEquals(CardioRules.MAX_MINUTES, typed(CardioField.MINUTES, huge)!!.minutes)
        assertEquals(200_000, typed(CardioField.DISTANCE_KM, huge)!!.distanceM)
        assertEquals(2000, typed(CardioField.WATTS, huge)!!.watts)
        assertEquals(10.0, typed(CardioField.WATTS_PER_KG, huge)!!.wattsPerKg!!, 1e-9)
    }

    // withField: zeros in the optional fields

    @Test
    fun `typing 0 in an optional field keeps it at 0`() {
        assertEquals(0, typed(CardioField.INTENSITY, "0")!!.intensity)
        assertEquals(0, typed(CardioField.WATTS, "0")!!.watts)
        assertEquals(0, typed(CardioField.TSS, "0")!!.tss)
        assertEquals(0.0, typed(CardioField.WATTS_PER_KG, "0")!!.wattsPerKg!!, 1e-9)
        assertEquals(0.0, typed(CardioField.INTENSITY_FACTOR, "0,0")!!.intensityFactor!!, 1e-9)
    }

    @Test
    fun `typing 0 in heart rate clears it to null`() {
        assertNull(typed(CardioField.HEART_RATE, "0")!!.heartRate)
    }

    @Test
    fun `typing 0 in minutes, calories or distance sets it to 0`() {
        assertEquals(0, typed(CardioField.MINUTES, "0")!!.minutes)
        assertEquals(0, typed(CardioField.CALORIES, "0")!!.calories)
        assertEquals(0, typed(CardioField.DISTANCE_KM, "0")!!.distanceM)
    }

    // withField: the rest of the draft

    @Test
    fun `only the typed field changes`() {
        assertEquals(full.copy(watts = 150), typed(CardioField.WATTS, "150"))
        assertEquals(full.copy(distanceM = 12_500), typed(CardioField.DISTANCE_KM, "12,5"))
        assertEquals(full.copy(minutes = 0), typed(CardioField.MINUTES, ""))
    }

    @Test
    fun `a typed value on an empty draft sets just that value`() {
        assertEquals(CardioDraft(minutes = 0, calories = 0, distanceM = 0, tss = 99), typed(CardioField.TSS, "99", empty))
    }
}
