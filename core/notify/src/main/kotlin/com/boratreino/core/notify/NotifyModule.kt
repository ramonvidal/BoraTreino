package com.boratreino.core.notify

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotifyModule {
    @Binds
    @Singleton
    abstract fun bindRestTimer(impl: DefaultRestTimer): RestTimer

    @Binds
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler
}
