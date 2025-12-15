package com.rayoai.data.repository

import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {

    private val _apiKey = MutableStateFlow<String?>(null)
    override val apiKey: Flow<String?> = _apiKey

    private val _defaultModel = MutableStateFlow(GeminiModelConfig.DEFAULT_MODEL)
    override val defaultModel: Flow<String> = _defaultModel

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: Flow<ThemeMode> = _themeMode

    private val _textScale = MutableStateFlow(1.0f)
    override val textScale: Flow<Float> = _textScale

    private val _autoDescribeOnShare = MutableStateFlow(false)
    override val autoDescribeOnShare: Flow<Boolean> = _autoDescribeOnShare

    private val _isFirstRun = MutableStateFlow(true)
    override val isFirstRun: Flow<Boolean> = _isFirstRun

    private val _hasShownApiUsageWarning = MutableStateFlow(false)
    override val hasShownApiUsageWarning: Flow<Boolean> = _hasShownApiUsageWarning

    private val _hasRated = MutableStateFlow(false)
    override val hasRated: Flow<Boolean> = _hasRated

    private val _lastPromptTime = MutableStateFlow(0L)
    override val lastPromptTime: Flow<Long> = _lastPromptTime

    private val _maxImagesInChat = MutableStateFlow(3)
    override val maxImagesInChat: Flow<Int> = _maxImagesInChat

    private val _hasDonated = MutableStateFlow(false)
    override val hasDonated: Flow<Boolean> = _hasDonated

    private val _lastDonationPromptTime = MutableStateFlow(0L)
    override val lastDonationPromptTime: Flow<Long> = _lastDonationPromptTime

    private val _showHiddenChats = MutableStateFlow(false)
    override val showHiddenChats: Flow<Boolean> = _showHiddenChats

    override suspend fun saveApiKey(apiKey: String) {
        _apiKey.value = apiKey
    }

    override suspend fun saveDefaultModel(model: String) {
        _defaultModel.value = model
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun saveTextScale(scale: Float) {
        _textScale.value = scale
    }

    override suspend fun saveAutoDescribeOnShare(enabled: Boolean) {
        _autoDescribeOnShare.value = enabled
    }

    override suspend fun setFirstRun(isFirstRun: Boolean) {
        _isFirstRun.value = isFirstRun
    }

    override suspend fun setHasShownApiUsageWarning(shown: Boolean) {
        _hasShownApiUsageWarning.value = shown
    }

    override suspend fun saveHasRated(hasRated: Boolean) {
        _hasRated.value = hasRated
    }

    override suspend fun saveLastPromptTime(time: Long) {
        _lastPromptTime.value = time
    }

    override suspend fun saveMaxImagesInChat(count: Int) {
        _maxImagesInChat.value = count
    }

    override suspend fun saveHasDonated(hasDonated: Boolean) {
        _hasDonated.value = hasDonated
    }

    override suspend fun saveLastDonationPromptTime(time: Long) {
        _lastDonationPromptTime.value = time
    }

    override suspend fun saveShowHiddenChats(show: Boolean) {
        _showHiddenChats.value = show
    }
}
