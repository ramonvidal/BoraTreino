package com.boratreino.core.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The alarm that ends a rest: an alarm without a real end time does nothing; a real one posts "Rest over"
 * on the alert channel and claims its rest, so the rest is signalled once.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RestAlarmReceiverTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        createNotificationChannels(app)
    }

    private fun alarm(endsAtMillis: Long?): Intent = Intent(RestAlarmReceiver.ACTION_REST_OVER).apply {
        if (endsAtMillis != null) putExtra(RestAlarmReceiver.EXTRA_ENDS_AT, endsAtMillis)
    }

    private fun receive(intent: Intent) = RestAlarmReceiver().onReceive(app, intent)

    private fun postedNotifications() = shadowOf(manager).allNotifications

    @Test
    fun `an alarm with no end time posts nothing`() {
        receive(alarm(null))

        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `an alarm with an end time of zero posts nothing`() {
        receive(alarm(0L))

        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `an alarm with an end time posts rest over on the rest over channel`() {
        receive(alarm(ENDS_AT_POSTS))

        val posted = postedNotifications()
        assertEquals(1, posted.size)
        assertEquals("rest_over", posted.single().channelId)
    }

    @Test
    fun `a second alarm for the same rest leaves a single rest over notification`() {
        receive(alarm(ENDS_AT_TWICE))
        receive(alarm(ENDS_AT_TWICE))

        assertEquals(1, postedNotifications().size)
    }

    @Test
    fun `a valid alarm claims its rest`() {
        receive(alarm(ENDS_AT_CLAIMED))

        assertFalse(RestSignal.claim(ENDS_AT_CLAIMED))
    }

    @Test
    fun `a second alarm for the same rest does not claim it again`() {
        receive(alarm(ENDS_AT_CLAIMED_TWICE))
        receive(alarm(ENDS_AT_CLAIMED_TWICE))

        assertFalse(RestSignal.claim(ENDS_AT_CLAIMED_TWICE))
    }

    @Test
    fun `an alarm with no end time leaves an earlier rest over notification alone`() {
        receive(alarm(ENDS_AT_KEPT))
        receive(alarm(null))

        assertEquals(1, postedNotifications().size)
    }

    private companion object {
        // Far from the end times the other rest tests use, because the claim is kept for the whole test run.
        const val ENDS_AT_POSTS = 3_000_000_000_001L
        const val ENDS_AT_TWICE = 3_000_000_000_002L
        const val ENDS_AT_CLAIMED = 3_000_000_000_003L
        const val ENDS_AT_CLAIMED_TWICE = 3_000_000_000_004L
        const val ENDS_AT_KEPT = 3_000_000_000_005L
    }
}
