package com.boratreino.core.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The beep never throws, whatever audio is (not) available. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RestBeepTest {
    @Test
    fun `playing the beep does not throw`() {
        RestBeep.play()
    }

    @Test
    fun `playing the beep twice in a row does not throw`() {
        RestBeep.play()
        RestBeep.play()
    }
}
