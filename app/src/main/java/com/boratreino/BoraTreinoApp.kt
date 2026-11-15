package com.boratreino

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.boratreino.core.health.HealthSync
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.notify.createNotificationChannels
import com.boratreino.widget.NextWorkoutWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class BoraTreinoApp : Application(), Configuration.Provider {
    // Lets background workers (training reminders) get their dependencies from Hilt.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var healthSync: HealthSync

    @Inject lateinit var widgetUpdater: NextWorkoutWidgetUpdater

    /** Lives as long as the process; a failing job never takes the others down. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        // Re-arms the next reminder (or cancels it when reminders are off) on every start.
        reminderScheduler.reschedule()
        // Sends finished trainings to Health Connect when the user turned that on.
        healthSync.start(applicationScope)
        // Keeps the home-screen widget fresh while the process lives (only draws when one is placed).
        widgetUpdater.start(applicationScope)
    }
}
