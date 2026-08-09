package com.rayoai.domain.repository

import kotlinx.coroutines.flow.Flow
import com.rayoai.domain.model.DescriptionDetail

enum class ThemeMode { SYSTEM, LIGHT, DARK, HIGH_CONTRAST }

interface UserPreferencesRepository {
    val apiKey: Flow<String?>
    val defaultModel: Flow<String>
    val imageDescriptionDetail: Flow<DescriptionDetail>
    val videoDescriptionDetail: Flow<DescriptionDetail>
    val themeMode: Flow<ThemeMode>
    val textScale: Flow<Float>
    val autoDescribeOnShare: Flow<Boolean>
    val isFirstRun: Flow<Boolean>
    val hasShownApiUsageWarning: Flow<Boolean>
    val hasRated: Flow<Boolean>
    val lastPromptTime: Flow<Long>
    val maxImagesInChat: Flow<Int>
    val hasDonated: Flow<Boolean>
    val lastDonationPromptTime: Flow<Long>
    val showHiddenChats: Flow<Boolean>
    val accessibilityQuickCaptureEnabled: Flow<Boolean>

    suspend fun saveApiKey(apiKey: String)
    suspend fun saveDefaultModel(model: String)
    suspend fun saveImageDescriptionDetail(detail: DescriptionDetail)
    suspend fun saveVideoDescriptionDetail(detail: DescriptionDetail)
    suspend fun saveThemeMode(mode: ThemeMode)
    suspend fun saveTextScale(scale: Float)
    suspend fun saveAutoDescribeOnShare(enabled: Boolean)
    suspend fun setFirstRun(isFirstRun: Boolean)
    suspend fun setHasShownApiUsageWarning(shown: Boolean)
    suspend fun saveHasRated(hasRated: Boolean)
    suspend fun saveLastPromptTime(time: Long)
    suspend fun saveMaxImagesInChat(count: Int)
    suspend fun saveHasDonated(hasDonated: Boolean)
    suspend fun saveLastDonationPromptTime(time: Long)
    suspend fun saveShowHiddenChats(show: Boolean)
    suspend fun saveAccessibilityQuickCaptureEnabled(enabled: Boolean)
}
