package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseImagesSlugTest {
    @Test
    fun `a starter name gives the starter picture`() {
        assertEquals("leg-press", ExerciseImages.slugFor("Leg press", null))
    }

    @Test
    fun `the starter picture wins over the library picture of the id`() {
        // Air_Bike has its own picture, but the name is a starter with one.
        assertEquals("leg-press", ExerciseImages.slugFor("Leg press", "Air_Bike"))
    }

    @Test
    fun `the library picture is used when the name is not a starter`() {
        assertEquals("air-bike", ExerciseImages.slugFor("My own machine", "Air_Bike"))
    }

    @Test
    fun `the library picture is used when the starter with that name has none`() {
        assertNull(StarterMachines.imageFor("Lat pulldown"))
        assertEquals("air-bike", ExerciseImages.slugFor("Lat pulldown", "Air_Bike"))
    }

    @Test
    fun `no name picture and no library id gives null`() {
        assertNull(ExerciseImages.slugFor("My own machine", null))
    }

    @Test
    fun `no name picture and a library id without a picture gives null`() {
        assertNull(ExerciseImages.slugFor("My own machine", "Running"))
    }

    @Test
    fun `no name picture and an unknown library id gives null`() {
        assertNull(ExerciseImages.slugFor("My own machine", "Not_A_Real_Exercise"))
    }

    @Test
    fun `the starter name matches ignoring case, surrounding spaces and accents`() {
        assertEquals("leg-press", ExerciseImages.slugFor("  LEG PRESS  ", null))
        assertEquals("leg-press", ExerciseImages.slugFor("  LEG PRESS  ", "Air_Bike"))

        val chest = StarterMachines.imageFor("Chest press")
        assertNotNull(chest)
        assertEquals(chest, ExerciseImages.slugFor("Supino máquina", null))
    }

    @Test
    fun `a starter name with a picture gives the same slug as the starter lookup`() {
        assertEquals(StarterMachines.imageFor("Barbell bench press"), ExerciseImages.slugFor("Barbell bench press", "Air_Bike"))
    }
}
