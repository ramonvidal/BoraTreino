package com.boratreino

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.notify.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BoraTreinoApp : Application(), Configuration.Provider {
    // Lets background workers (training reminders) get their dependencies from Hilt.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        // Re-arms the next reminder (or cancels it when reminders are off) on every start.
        reminderScheduler.reschedule()
    }
}
