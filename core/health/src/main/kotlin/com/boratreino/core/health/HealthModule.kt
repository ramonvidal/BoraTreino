package com.boratreino.core.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthModule {
    @Binds
    abstract fun bindHealthConnectAccess(impl: DefaultHealthConnectAccess): HealthConnectAccess

    @Binds
    abstract fun bindHealthSync(impl: DefaultHealthSync): HealthSync
}
