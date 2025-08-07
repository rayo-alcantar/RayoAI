package com.rayoai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AUTO_DESCRIBE_ON_SHARE = booleanPreferencesKey("auto_describe_on_share")
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        val HAS_SHOWN_API_USAGE_WARNING = booleanPreferencesKey("has_shown_api_usage_warning")
        val HAS_RATED = booleanPreferencesKey("has_rated")
        val LAST_PROMPT_TIME = longPreferencesKey("last_prompt_time")
    }

    override val apiKey: Flow<String?> = dataStore.data.map {
        it[PreferencesKeys.API_KEY]
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.valueOf(it[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    override val textScale: Flow<Float> = dataStore.data.map {
        it[PreferencesKeys.TEXT_SCALE] ?: 1.0f
    }

    override val autoDescribeOnShare: Flow<Boolean> = dataStore.data.map {
        it[PreferencesKeys.AUTO_DESCRIBE_ON_SHARE] ?: false
    }

    override val isFirstRun: Flow<Boolean> = dataStore.data.map {
        it[PreferencesKeys.IS_FIRST_RUN] ?: true
    }

    override val hasShownApiUsageWarning: Flow<Boolean> = dataStore.data.map {
        it[PreferencesKeys.HAS_SHOWN_API_USAGE_WARNING] ?: false
    }

    override val hasRated: Flow<Boolean> = dataStore.data.map {
        it[PreferencesKeys.HAS_RATED] ?: false
    }

    override val lastPromptTime: Flow<Long> = dataStore.data.map {
        it[PreferencesKeys.LAST_PROMPT_TIME] ?: 0L
    }

    override suspend fun saveApiKey(apiKey: String) {
        dataStore.edit {
            it[PreferencesKeys.API_KEY] = apiKey
        }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit {
            it[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun saveTextScale(scale: Float) {
        dataStore.edit {
            it[PreferencesKeys.TEXT_SCALE] = scale
        }
    }

    override suspend fun saveAutoDescribeOnShare(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.AUTO_DESCRIBE_ON_SHARE] = enabled
        }
    }

    override suspend fun setFirstRun(isFirstRun: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.IS_FIRST_RUN] = isFirstRun
        }
    }

    override suspend fun setHasShownApiUsageWarning(shown: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.HAS_SHOWN_API_USAGE_WARNING] = shown
        }
    }

    override suspend fun saveHasRated(hasRated: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.HAS_RATED] = hasRated
        }
    }

    override suspend fun saveLastPromptTime(time: Long) {
        dataStore.edit {
            it[PreferencesKeys.LAST_PROMPT_TIME] = time
        }
    }
}