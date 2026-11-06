package com.boratreino.core.notify

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.atomic.AtomicLong

/**
 * What the owner feels when a rest ends: a buzz, and a beep when the setting is on. The in-process timer
 * and the alarm receiver can both notice the end (the alarm is the one that works with the screen off);
 * [claim] lets only the first of them signal, so there is one buzz and one beep per rest.
 */
internal object RestSignal {
    private val claimedEndsAt = AtomicLong(0L)

    /** True for the first caller of a rest that ends at [endsAtMillis]; false for every later one. */
    fun claim(endsAtMillis: Long): Boolean {
        while (true) {
            val current = claimedEndsAt.get()
            if (current == endsAtMillis) return false
            if (claimedEndsAt.compareAndSet(current, endsAtMillis)) return true
        }
    }

    /** The same pattern as the notification's, played directly so it does not depend on notification settings. Never throws. */
    fun buzz(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(Notifications.REST_VIBRATION, -1))
        } catch (e: Throwable) {
            // No vibrator or not allowed: the notification still vibrates when it can.
        }
    }
}
