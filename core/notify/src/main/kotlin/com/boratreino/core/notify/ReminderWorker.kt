package com.boratreino.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.ReminderContent
import com.boratreino.core.model.ReminderRules
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.settings.TrainingSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.time.temporal.WeekFields

/** Posts the training reminder (when it applies today), then queues the next one. Always succeeds. */
@HiltWorker
internal class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: TrainingSettings,
    private val training: TrainingRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
    private val scheduler: WorkManagerReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            remind()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Skip this one; the next is queued below.
        }
        try {
            scheduler.schedule(afterRun = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Next app start queues it again.
        }
        return Result.success()
    }

    private suspend fun remind() {
        val reminders = settings.reminders.first()
        if (!reminders.enabled) return
        val today = LocalDate.now(clock)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val plan = training.observeTodayPlan().first()
        val dates = history.observeTrainingDates().first()
        val content = ReminderRules.contentFor(
            trainedToday = today in dates,
            restDay = plan.restDay,
            routineName = plan.suggested?.name,
            daysTrainedThisWeek = WeekGoalRules.daysTrained(dates, today, firstDayOfWeek),
            goal = settings.weeklyGoal.first(),
            today = today,
            firstDayOfWeek = firstDayOfWeek,
        ) ?: return
        post(content)
    }

    private fun post(content: ReminderContent) {
        val context = applicationContext
        when (content) {
            is ReminderContent.NextUp -> Notifications.postReminder(
                context,
                title = content.routineName?.let { context.getString(R.string.notify_reminder_next_up, it) }
                    ?: context.getString(R.string.notify_reminder_time_to_train),
                text = null,
            )
            is ReminderContent.BehindGoal -> Notifications.postReminder(
                context,
                title = context.getString(R.string.notify_reminder_behind_goal, content.daysTrained, content.goal),
                text = content.routineName?.let { context.getString(R.string.notify_reminder_next_routine, it) },
            )
        }
    }
}
