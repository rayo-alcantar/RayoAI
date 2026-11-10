package com.rayoai.presentation.ui.screens.tools.live

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.data.remote.live.GeminiLiveManager
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.live.LiveModeConfig
import com.rayoai.domain.model.live.LiveSessionState
import com.rayoai.domain.model.live.VisualSource
import com.rayoai.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LiveModeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liveManager: GeminiLiveManager,
    private val floatingState: LiveFloatingControlsState,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val sessionState: StateFlow<LiveSessionState> = liveManager.sessionState
    val visualSource: StateFlow<VisualSource> = liveManager.visualSource
    val isVideoPaused: StateFlow<Boolean> = liveManager.isVideoPaused
    val isMicMuted: StateFlow<Boolean> = liveManager.isMicMuted
    val isTalking: StateFlow<Boolean> = liveManager.isTalking
    val isSpeaking: StateFlow<Boolean> = liveManager.isSpeaking
    val latestTranscript: StateFlow<String> = liveManager.latestTranscript
    val errorMessage: StateFlow<String?> = liveManager.errorMessage
    val floatingControlsEnabled = floatingState.enabled.asStateFlow()

    fun setFloatingControlsEnabled(enabled: Boolean) {
        floatingState.enabled.value = enabled
    }

    fun setLiveScreenVisible(visible: Boolean) {
        floatingState.liveScreenVisible.value = visible
    }

    val apiKey: StateFlow<String?> = userPreferencesRepository.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val liveModel: StateFlow<String> = userPreferencesRepository.liveModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeminiModelConfig.DEFAULT_LIVE_MODEL)

    val liveVoice: StateFlow<String> = userPreferencesRepository.liveVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Kore")

    val liveThinkingLevel: StateFlow<String> = userPreferencesRepository.liveThinkingLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "minimal")

    val liveVadSensitivity: StateFlow<String> = userPreferencesRepository.liveVadSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "high")

    val livePushToTalk: StateFlow<Boolean> = userPreferencesRepository.livePushToTalk
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liveCaptureIntervalSeconds: StateFlow<Float> = userPreferencesRepository.liveCaptureIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val liveAudioOutput: StateFlow<String> = userPreferencesRepository.liveAudioOutput
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "speaker")

    val liveDontAskScreenShareConfirm: StateFlow<Boolean> = userPreferencesRepository.liveDontAskScreenShareConfirm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liveRecentSharedApps: StateFlow<List<String>> = userPreferencesRepository.liveRecentSharedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startSession(onMissingApiKey: () -> Unit) {
        viewModelScope.launch {
            val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
            if (key.isNullOrBlank()) {
                onMissingApiKey()
                return@launch
            }

            val config = LiveModeConfig(
                apiKey = key,
                model = liveModel.value,
                voice = liveVoice.value,
                thinkingLevel = liveThinkingLevel.value,
                vadSensitivity = liveVadSensitivity.value,
                pushToTalk = livePushToTalk.value,
                captureIntervalSeconds = liveCaptureIntervalSeconds.value,
                audioOutput = liveAudioOutput.value,
                languageCode = Locale.getDefault().language
            )

            liveManager.startSession(config)
        }
    }

    fun stopSession() {
        liveManager.stopSession()
    }

    fun setVisualSource(source: VisualSource) {
        liveManager.setVisualSource(source)
    }

    fun toggleVideoPause() {
        liveManager.toggleVideoPause()
    }

    fun toggleMicMute() {
        liveManager.toggleMicMute()
    }

    fun setPushToTalkState(talking: Boolean) {
        liveManager.setPushToTalkState(talking)
    }

    fun interruptCurrentResponse() {
        liveManager.interruptCurrentResponse()
    }

    fun sendBitmapFrame(bitmap: Bitmap) {
        liveManager.sendBitmapFrame(bitmap)
    }

    fun sendJpegFrame(jpegBytes: ByteArray) {
        liveManager.sendJpegFrame(jpegBytes)
    }

    fun clearError() {
        liveManager.clearError()
    }

    fun saveLiveModel(model: String) {
        viewModelScope.launch { userPreferencesRepository.saveLiveModel(model) }
    }

    fun saveLiveVoice(voice: String) {
        viewModelScope.launch { userPreferencesRepository.saveLiveVoice(voice) }
    }

    fun saveLiveThinkingLevel(level: String) {
        viewModelScope.launch { userPreferencesRepository.saveLiveThinkingLevel(level) }
    }

    fun saveLiveVadSensitivity(sensitivity: String) {
        viewModelScope.launch { userPreferencesRepository.saveLiveVadSensitivity(sensitivity) }
    }

    fun saveLivePushToTalk(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveLivePushToTalk(enabled) }
    }

    fun saveLiveCaptureIntervalSeconds(interval: Float) {
        viewModelScope.launch { userPreferencesRepository.saveLiveCaptureIntervalSeconds(interval) }
    }

    fun setAudioOutput(output: String) {
        liveManager.setAudioOutput(output)
        viewModelScope.launch { userPreferencesRepository.saveLiveAudioOutput(output) }
    }

    fun saveLiveDontAskScreenShareConfirm(dontAsk: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveLiveDontAskScreenShareConfirm(dontAsk) }
    }

    fun addRecentSharedApp(packageName: String) {
        viewModelScope.launch { userPreferencesRepository.addRecentSharedApp(packageName) }
    }

    override fun onCleared() {
        // También cubre el cierre mientras Live conecta, antes del servicio en primer plano.
        setFloatingControlsEnabled(false)
        setLiveScreenVisible(false)
        stopSession()
        LiveSessionForegroundService.stop(context)
        super.onCleared()
    }
}
