package com.boratreino.core.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A rest's end is claimed once, so the buzz and the beep happen once per rest. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RestSignalTest {
    @Test
    fun `the first claim of an end time is true and a second claim of it is false`() {
        val endsAt = 1_000_000_000_001L

        assertTrue(RestSignal.claim(endsAt))
        assertFalse(RestSignal.claim(endsAt))
    }

    @Test
    fun `a claim of a different end time is true`() {
        assertTrue(RestSignal.claim(2_000_000_000_001L))
        assertTrue(RestSignal.claim(2_000_000_000_002L))
    }

    @Test
    fun `zero is claimed like any other end time`() {
        assertTrue(RestSignal.claim(0L))
        assertFalse(RestSignal.claim(0L))
    }
}
