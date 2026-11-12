package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterMachineImagesTest {
    private fun starter(englishName: String): StarterMachine =
        StarterMachines.all.first { it.name == englishName }

    @Test
    fun `English starter name gives its picture slug`() {
        assertEquals("leg-press", StarterMachines.imageFor("Leg press"))
    }

    @Test
    fun `Portuguese starter name gives the same picture slug`() {
        val bench = starter("Barbell bench press")
        assertEquals("Supino reto", bench.namePt)
        assertEquals(StarterMachines.imageFor("Barbell bench press"), StarterMachines.imageFor(bench.namePt))
        assertNotNull(StarterMachines.imageFor(bench.namePt))
    }

    @Test
    fun `matching ignores case, surrounding spaces and accents`() {
        val expected = StarterMachines.imageFor("Leg press")
        assertEquals(expected, StarterMachines.imageFor("  LEG PRESS  "))
        assertEquals(expected, StarterMachines.imageFor("leg press"))

        val chest = StarterMachines.imageFor("Chest press")
        assertNotNull(chest)
        assertEquals(chest, StarterMachines.imageFor("supino maquina"))
        assertEquals(chest, StarterMachines.imageFor("Supino máquina"))
    }

    @Test
    fun `custom machine name has no picture`() {
        assertNull(StarterMachines.imageFor("My own machine"))
    }

    @Test
    fun `blank name has no picture`() {
        assertNull(StarterMachines.imageFor(""))
        assertNull(StarterMachines.imageFor("   "))
    }

    @Test
    fun `starters without a good picture have none`() {
        assertNull(StarterMachines.imageFor("Lat pulldown"))
        assertNull(StarterMachines.imageFor("Shrugs"))
        assertNull(StarterMachines.imageFor("Hip thrust"))
        assertNull(StarterMachines.imageFor("Puxada frontal"))
    }

    @Test
    fun `exactly 57 starters have a picture`() {
        assertEquals(57, StarterMachines.all.count { it.image != null })
    }

    @Test
    fun `upright row has its picture in English and Portuguese`() {
        assertEquals("upright-barbell-rows", StarterMachines.imageFor("Upright row"))
        assertEquals("upright-barbell-rows", StarterMachines.imageFor("Remada alta"))
    }

    @Test
    fun `overhead dumbbell extension has its picture in English and Portuguese`() {
        assertEquals("standing-triceps-extension-2", StarterMachines.imageFor("Overhead dumbbell extension"))
        assertEquals("standing-triceps-extension-2", StarterMachines.imageFor("Tríceps francês"))
    }

    @Test
    fun `skull crusher has its picture in English and Portuguese`() {
        assertEquals("lying-triceps-press-with-barbell", StarterMachines.imageFor("Skull crusher"))
        assertEquals("lying-triceps-press-with-barbell", StarterMachines.imageFor("Tríceps testa"))
    }

    @Test
    fun `cardio starters have no picture`() {
        assertNull(StarterMachines.imageFor("Treadmill"))
        assertNull(StarterMachines.imageFor("Esteira"))
        assertTrue(StarterMachines.all.filter { it.kind == MachineKind.CARDIO }.all { it.image == null })
    }

    @Test
    fun `two starters may share a picture slug`() {
        val biceps = starter("Cable biceps curl")
        val curl = starter("Cable curl")
        assertNotNull(biceps.image)
        assertEquals(biceps.image, curl.image)
        assertEquals(biceps.image, StarterMachines.imageFor("Cable curl"))
    }

    @Test
    fun `every picture slug is non-blank lowercase letters, digits and hyphens`() {
        val slugs = StarterMachines.all.mapNotNull { it.image }
        assertTrue(slugs.isNotEmpty())
        val pattern = Regex("[a-z0-9-]+")
        slugs.forEach { slug ->
            assertTrue("slug '$slug' is blank", slug.isNotBlank())
            assertTrue("slug '$slug' has other characters", pattern.matches(slug))
        }
    }

    @Test
    fun `credit constants name the author, the source and the license`() {
        assertTrue(ExerciseImages.AUTHOR.contains("Priday"))
        assertTrue(ExerciseImages.SOURCE_URL.startsWith("https://github.com/everkinetic"))
        assertEquals("CC BY-SA 4.0", ExerciseImages.LICENSE_NAME)
        assertTrue(ExerciseImages.LICENSE_URL.contains("creativecommons.org/licenses/by-sa/4.0"))
    }
}
