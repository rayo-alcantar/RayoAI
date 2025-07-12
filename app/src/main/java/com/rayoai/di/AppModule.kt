package com.rayoai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.db.RayoAIDatabase
import com.rayoai.data.repository.UserPreferencesRepositoryImpl
import com.rayoai.data.repository.VisionRepositoryImpl
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VisionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindVisionRepository(impl: VisionRepositoryImpl): VisionRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("user_prefs") }
        )
    }

    @Provides
    @Singleton
    fun provideRayoAIDatabase(@ApplicationContext context: Context): RayoAIDatabase {
        return Room.databaseBuilder(
            context,
            RayoAIDatabase::class.java,
            "rayo_ai_db"
        ).build()
    }

    @Provides
    fun provideCaptureDao(database: RayoAIDatabase): CaptureDao {
        return database.captureDao()
    }
}