package com.rayoai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementación concreta de [UserPreferencesRepository] que utiliza Jetpack DataStore
 * para almacenar y recuperar las preferencias del usuario de forma asíncrona y segura.
 */
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    // Definición de las claves para las preferencias en DataStore.
    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AUTO_DESCRIBE_ON_SHARE = booleanPreferencesKey("auto_describe_on_share")
    }

    /**
     * [Flow] que emite la clave de API actual del usuario.
     */
    override val apiKey: Flow<String?> = dataStore.data.map {
        it[PreferencesKeys.API_KEY]
    }

    /**
     * [Flow] que emite el modo de tema actual del usuario.
     * Si no hay un valor guardado, por defecto es [ThemeMode.SYSTEM].
     */
    override val themeMode: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.valueOf(it[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    /**
     * [Flow] que emite la escala de texto actual del usuario.
     * Si no hay un valor guardado, por defecto es 1.0f (escala normal).
     */
    override val textScale: Flow<Float> = dataStore.data.map {
        it[PreferencesKeys.TEXT_SCALE] ?: 1.0f
    }

    /**
     * [Flow] que emite el estado del interruptor 'auto-describir al compartir'.
     * Si no hay un valor guardado, por defecto es `false`.
     */
    override val autoDescribeOnShare: Flow<Boolean> = dataStore.data.map {
        it[PreferencesKeys.AUTO_DESCRIBE_ON_SHARE] ?: false
    }

    /**
     * Guarda la clave de API proporcionada en DataStore.
     * @param apiKey La clave de API a guardar.
     */
    override suspend fun saveApiKey(apiKey: String) {
        dataStore.edit {
            it[PreferencesKeys.API_KEY] = apiKey
        }
    }

    /**
     * Guarda el modo de tema seleccionado por el usuario en DataStore.
     * @param mode El [ThemeMode] a guardar.
     */
    override suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit {
            it[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    /**
     * Guarda la escala de texto seleccionada por el usuario en DataStore.
     * @param scale La escala de texto a guardar (ej. 1.0f, 1.3f).
     */
    override suspend fun saveTextScale(scale: Float) {
        dataStore.edit {
            it[PreferencesKeys.TEXT_SCALE] = scale
        }
    }

    /**
     * Guarda el estado del interruptor 'auto-describir al compartir' en DataStore.
     * @param enabled `true` si la auto-descripción está habilitada, `false` en caso contrario.
     */
    override suspend fun saveAutoDescribeOnShare(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.AUTO_DESCRIBE_ON_SHARE] = enabled
        }
    }
}