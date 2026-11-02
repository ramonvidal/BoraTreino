package com.boratreino.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindMachineRepository(impl: DefaultMachineRepository): MachineRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: DefaultRoutineRepository): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindTrainingRepository(impl: DefaultTrainingRepository): TrainingRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
