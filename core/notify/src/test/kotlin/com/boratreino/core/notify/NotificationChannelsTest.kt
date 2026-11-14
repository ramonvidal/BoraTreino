package com.boratreino.core.notify

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The rest channels: the countdown stays quiet, the alert is loud and has its own channel. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationChannelsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        createNotificationChannels(app)
    }

    @Test
    fun `the rest channel for the countdown is created at the default importance`() {
        val channel = requireNotNull(manager.getNotificationChannel("rest"))

        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `the rest over channel is created at high importance`() {
        val channel = requireNotNull(manager.getNotificationChannel("rest_over"))

        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `the rest over channel vibrates and has a sound`() {
        val channel = requireNotNull(manager.getNotificationChannel("rest_over"))

        assertTrue(channel.shouldVibrate())
        assertNotNull(channel.sound)
    }

    @Test
    fun `creating the channels again keeps the rest over channel`() {
        createNotificationChannels(app)

        assertEquals(NotificationManager.IMPORTANCE_HIGH, requireNotNull(manager.getNotificationChannel("rest_over")).importance)
    }
}
