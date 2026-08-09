package com.rayoai.di

import com.rayoai.data.repository.FakeUserPreferencesRepository
import com.rayoai.domain.repository.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class]
)
object TestDataModule {

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(): UserPreferencesRepository {
        return FakeUserPreferencesRepository()
    }
}
