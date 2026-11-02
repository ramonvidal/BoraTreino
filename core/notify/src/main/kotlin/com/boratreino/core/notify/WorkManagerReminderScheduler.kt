package com.boratreino.core.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.boratreino.core.model.ReminderRules
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One unique one-time work, "training-reminder", at the next fire time. The worker queues the one after it. */
@Singleton
internal class WorkManagerReminderScheduler internal constructor(
    private val context: Context,
    private val settings: TrainingSettings,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : ReminderScheduler {

    @Inject
    constructor(@ApplicationContext context: Context, settings: TrainingSettings, clock: Clock) :
        this(context, settings, clock, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override fun reschedule() {
        scope.launch {
            try {
                schedule(afterRun = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // WorkManager not ready or settings unreadable: the next app start tries again.
            }
        }
    }

    /**
     * Queues the next reminder. [afterRun] is for the worker itself: replacing a running work would cancel
     * it, so the next one is appended and starts when the current one finishes.
     */
    internal suspend fun schedule(afterRun: Boolean) {
        val manager = WorkManager.getInstance(context)
        val next = ReminderRules.nextFireTime(settings.reminders.first(), ZonedDateTime.now(clock))
        if (next == null) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = Duration.between(ZonedDateTime.now(clock), next).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        val policy = if (afterRun) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        manager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    internal companion object {
        const val WORK_NAME = "training-reminder"
    }
}
