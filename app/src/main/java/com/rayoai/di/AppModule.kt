package com.rayoai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.db.RayoAIDatabase
import com.rayoai.data.local.db.MIGRATION_1_2
import com.rayoai.data.local.db.MIGRATION_2_3
import com.rayoai.data.local.pdfdb.PdfDao
import com.rayoai.data.local.pdfdb.PdfChatDao
import com.rayoai.data.local.pdfdb.PdfDatabase
import com.rayoai.data.local.pdfdb.PDF_MIGRATION_1_2
import com.rayoai.data.local.videodb.VideoDao
import com.rayoai.data.local.videodb.VideoDatabase
import com.rayoai.data.remote.GeminiFilesApiService
import com.rayoai.data.remote.GithubApiService
import com.rayoai.data.repository.UserPreferencesRepositoryImpl
import com.rayoai.data.repository.VideoRepositoryImpl
import com.rayoai.data.repository.VisionRepositoryImpl
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VideoRepository
import com.rayoai.domain.repository.VisionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Calificador para identificar la instancia de Retrofit usada para GitHub API.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubRetrofit

/**
 * Calificador para identificar la instancia de Retrofit usada para Gemini API.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindVisionRepository(impl: VisionRepositoryImpl): VisionRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    @Provides
    fun provideCaptureDao(database: RayoAIDatabase): CaptureDao {
        return database.captureDao()
    }

    @Provides
    @Singleton
    fun providePdfDatabase(@ApplicationContext context: Context): PdfDatabase {
        return Room.databaseBuilder(
            context,
            PdfDatabase::class.java,
            "rayo_ai_pdf_db"
        ).addMigrations(PDF_MIGRATION_1_2).build()
    }

    @Provides
    fun providePdfDao(database: PdfDatabase): PdfDao {
        return database.pdfDao()
    }

    @Provides
    fun providePdfChatDao(database: PdfDatabase): PdfChatDao {
        return database.pdfChatDao()
    }

    @Provides
    @Singleton
    fun provideVideoDatabase(@ApplicationContext context: Context): VideoDatabase {
        return Room.databaseBuilder(
            context,
            VideoDatabase::class.java,
            "rayo_ai_video_db"
        ).build()
    }

    @Provides
    fun provideVideoDao(database: VideoDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "RayoAI")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @GithubRetrofit
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGithubApiService(@GithubRetrofit retrofit: Retrofit): GithubApiService {
        return retrofit.create(GithubApiService::class.java)
    }

    // ============ GEMINI API ============

    /**
     * Retrofit dedicado para la API de Gemini.
     * Usa una base URL diferente a la de GitHub.
     */
    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Servicio de API para Google Gemini.
     */
    @Provides
    @Singleton
    fun provideGeminiApiService(
        @GeminiRetrofit retrofit: Retrofit
    ): com.rayoai.data.remote.GeminiApiService {
        return retrofit.create(com.rayoai.data.remote.GeminiApiService::class.java)
    }

    /**
     * Servicio de API para Files API de Gemini.
     */
    @Provides
    @Singleton
    fun provideGeminiFilesApiService(
        @GeminiRetrofit retrofit: Retrofit
    ): GeminiFilesApiService {
        return retrofit.create(GeminiFilesApiService::class.java)
    }
}
