package com.boratreino.core.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScanModule {
    @Binds
    abstract fun bindBikeScreenScanner(impl: MlKitBikeScreenScanner): BikeScreenScanner
}
