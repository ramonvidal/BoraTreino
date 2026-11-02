package com.boratreino.core.notify

/** Keeps one training reminder queued: the next one, at the chosen weekdays and time. */
interface ReminderScheduler {
    /**
     * Reads the reminder settings and replaces the queued reminder: cancelled when reminders are off,
     * else one at the next fire time. Call at app start and after every reminder setting change.
     */
    fun reschedule()
}
