package com.rayoai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.rayoai.domain.repository.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

// Importación necesaria para preferencesDataStoreFile
import androidx.datastore.preferences.preferencesDataStoreFile

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryImplTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testCoroutineDispatcher = UnconfinedTestDispatcher()
    private val testCoroutineScope = TestScope(testCoroutineDispatcher + Job())

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepositoryImpl

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testCoroutineScope,
            produceFile = { testContext.preferencesDataStoreFile("test_prefs") }
        )
        repository = UserPreferencesRepositoryImpl(dataStore)
    }

    @After
    fun teardown() {
        File(testContext.filesDir, "datastore/").deleteRecursively()
    }

    @Test
    fun saveApiKey_retrievesCorrectly() = testCoroutineScope.runTest {
        val testApiKey = "test_api_key"

        // Lanzar la recolección del Flow en una corrutina separada
        val job = launch { 
            repository.apiKey.test {
                // El primer valor emitido será el valor por defecto (null o vacío)
                // o el valor previamente guardado si existe.
                // Para esta prueba, esperamos el valor que acabamos de guardar.
                assertEquals(testApiKey, awaitItem())
            }
        }

        // Guardar la API Key
        repository.saveApiKey(testApiKey)

        // Cancelar el job de recolección para que la prueba pueda terminar.
        job.cancel()
    }

    @Test
    fun saveThemeMode_retrievesCorrectly() = testCoroutineScope.runTest {
        val testThemeMode = ThemeMode.DARK
        repository.saveThemeMode(testThemeMode)

        repository.themeMode.test {
            assertEquals(testThemeMode, awaitItem())
        }
    }

    @Test
    fun saveTextScale_retrievesCorrectly() = testCoroutineScope.runTest {
        val testTextScale = 1.5f
        repository.saveTextScale(testTextScale)

        repository.textScale.test {
            assertEquals(testTextScale, awaitItem())
        }
    }

    @Test
    fun saveAutoDescribeOnShare_retrievesCorrectly() = testCoroutineScope.runTest {
        val testAutoDescribe = true
        repository.saveAutoDescribeOnShare(testAutoDescribe)

        repository.autoDescribeOnShare.test {
            assertEquals(testAutoDescribe, awaitItem())
        }
    }
}