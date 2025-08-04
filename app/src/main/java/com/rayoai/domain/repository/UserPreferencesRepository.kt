package com.rayoai.domain.repository

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK, HIGH_CONTRAST }

interface UserPreferencesRepository {
    val apiKey: Flow<String?>
    val themeMode: Flow<ThemeMode>
    val textScale: Flow<Float>
    val autoDescribeOnShare: Flow<Boolean>
    val isFirstRun: Flow<Boolean>
    val hasShownApiUsageWarning: Flow<Boolean>

    suspend fun saveApiKey(apiKey: String)
    suspend fun saveThemeMode(mode: ThemeMode)
    suspend fun saveTextScale(scale: Float)
    suspend fun saveAutoDescribeOnShare(enabled: Boolean)
    suspend fun setFirstRun(isFirstRun: Boolean)
    suspend fun setHasShownApiUsageWarning(shown: Boolean)
}