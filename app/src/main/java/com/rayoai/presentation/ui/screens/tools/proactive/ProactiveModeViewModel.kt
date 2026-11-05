package com.rayoai.presentation.ui.screens.tools.proactive

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.AudioRecording
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.proactive.ProactiveDetection
import com.rayoai.domain.model.proactive.ProactivePreset
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.TranscribeAudioUseCase
import com.rayoai.domain.usecase.proactive.EvaluateProactiveFrameUseCase
import com.rayoai.presentation.voice.AndroidSpeechFileTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ProactiveModeViewModel @Inject constructor(
    private val evaluateProactiveFrameUseCase: EvaluateProactiveFrameUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isTranscribingAudio = MutableStateFlow(false)
    val isTranscribingAudio: StateFlow<Boolean> = _isTranscribingAudio.asStateFlow()

    private val _selectedPreset = MutableStateFlow(ProactivePreset.LIGHT)
    val selectedPreset: StateFlow<ProactivePreset> = _selectedPreset.asStateFlow()

    private val _customCondition = MutableStateFlow("")
    val customCondition: StateFlow<String> = _customCondition.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(2)
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _latestDetection = MutableStateFlow<ProactiveDetection?>(null)
    val latestDetection: StateFlow<ProactiveDetection?> = _latestDetection.asStateFlow()

    private val _lastAnnouncedPhrase = MutableStateFlow<String?>(null)
    val lastAnnouncedPhrase: StateFlow<String?> = _lastAnnouncedPhrase.asStateFlow()

    private val _alertTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val alertTrigger: SharedFlow<String> = _alertTrigger.asSharedFlow()

    private val _speechFeedbackTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speechFeedbackTrigger: SharedFlow<String> = _speechFeedbackTrigger.asSharedFlow()

    private val _hapticTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hapticTrigger: SharedFlow<Unit> = _hapticTrigger.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val apiKey: StateFlow<String?> = userPreferencesRepository.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var lastAnnouncementTimeMs: Long = 0L
    private var wasDetected: Boolean = false

    fun toggleMonitoring(onMissingApiKey: () -> Unit) {
        if (_isMonitoring.value) {
            stopMonitoring()
        } else {
            startMonitoring(onMissingApiKey)
        }
    }

    fun startMonitoring(onMissingApiKey: () -> Unit) {
        viewModelScope.launch {
            val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
            if (key.isNullOrBlank()) {
                onMissingApiKey()
                return@launch
            }
            _isMonitoring.value = true
            _latestDetection.value = null
            _lastAnnouncedPhrase.value = null
            lastAnnouncementTimeMs = 0L
            wasDetected = false
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        _isAnalyzing.value = false
        wasDetected = false
    }

    fun setPreset(preset: ProactivePreset) {
        _selectedPreset.value = preset
        _latestDetection.value = null
        _lastAnnouncedPhrase.value = null
        lastAnnouncementTimeMs = 0L
        wasDetected = false
    }

    fun setCustomCondition(condition: String) {
        _customCondition.value = condition
        _latestDetection.value = null
        _lastAnnouncedPhrase.value = null
        lastAnnouncementTimeMs = 0L
        wasDetected = false
    }

    fun cycleInterval() {
        _intervalSeconds.value = when (_intervalSeconds.value) {
            1 -> 2
            2 -> 3
            3 -> 5
            else -> 1
        }
    }

    fun toggleTorch() {
        _isTorchOn.value = !_isTorchOn.value
    }

    fun toggleFrontCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun transcribeVoiceAndSetCustomCondition(
        context: Context,
        recording: AudioRecording,
        feedbackTemplate: String
    ) {
        viewModelScope.launch {
            val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
            if (key.isNullOrBlank()) {
                _errorMessage.value = "Clave de API no configurada."
                cleanupRecording(recording)
                return@launch
            }

            _isTranscribingAudio.value = true
            try {
                val model = GeminiModelConfig.DEFAULT_MODEL
                val text = when (val result = transcribeAudioUseCase(key, recording.wavFile, model)) {
                    is ResultWrapper.Success -> result.data
                    is ResultWrapper.Error -> {
                        AndroidSpeechFileTranscriber(context.applicationContext)
                            .transcribe(recording)
                            .getOrNull()
                    }
                    ResultWrapper.Loading -> null
                }

                if (!text.isNullOrBlank()) {
                    val cleanText = text.trim()
                    _customCondition.value = cleanText
                    _selectedPreset.value = ProactivePreset.CUSTOM
                    _latestDetection.value = null
                    _lastAnnouncedPhrase.value = null
                    lastAnnouncementTimeMs = 0L
                    wasDetected = false

                    val feedback = String.format(feedbackTemplate, cleanText)
                    _speechFeedbackTrigger.tryEmit(feedback)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error al transcribir voz"
            } finally {
                cleanupRecording(recording)
                _isTranscribingAudio.value = false
            }
        }
    }

    private fun cleanupRecording(recording: AudioRecording) {
        runCatching { recording.wavFile.delete() }
    }

    fun evaluateFrame(bitmap: Bitmap, promptCondition: String) {
        if (!_isMonitoring.value || _isAnalyzing.value) {
            bitmap.recycle()
            return
        }

        val effectiveCondition = if (_selectedPreset.value == ProactivePreset.CUSTOM && _customCondition.value.isNotBlank()) {
            _customCondition.value.trim()
        } else {
            promptCondition
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            var frameToProcess = bitmap
            try {
                val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
                if (key.isNullOrBlank()) {
                    _errorMessage.value = "API key no configurada."
                    stopMonitoring()
                    return@launch
                }

                // Asegurar escala óptima (máximo 768px) para baja latencia en tiempo real
                val maxDim = 768
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                    if (scaled != bitmap) {
                        bitmap.recycle()
                        frameToProcess = scaled
                    }
                }

                val lang = Locale.getDefault().language
                val model = GeminiModelConfig.DEFAULT_MODEL

                evaluateProactiveFrameUseCase(
                    apiKey = key,
                    image = frameToProcess,
                    condition = effectiveCondition,
                    languageCode = lang,
                    model = model
                ).collect { result ->
                    when (result) {
                        is ResultWrapper.Loading -> Unit
                        is ResultWrapper.Error -> {
                            // Ignorar error transitorio para mantener la vigilancia en tiempo real
                        }
                        is ResultWrapper.Success -> {
                            val detection = result.data
                            _latestDetection.value = detection

                            val now = System.currentTimeMillis()
                            val phrase = detection.announcement

                            if (detection.detected && !phrase.isNullOrBlank()) {
                                val isFreshDetection = !wasDetected
                                val isPhraseChanged = phrase != _lastAnnouncedPhrase.value
                                val isRepeatedCooldownExpired = (now - lastAnnouncementTimeMs) > 12_000L

                                if (isFreshDetection || isPhraseChanged || isRepeatedCooldownExpired) {
                                    _lastAnnouncedPhrase.value = phrase
                                    lastAnnouncementTimeMs = now
                                    _alertTrigger.tryEmit(phrase)
                                    _hapticTrigger.tryEmit(Unit)
                                }
                                wasDetected = true
                            } else {
                                wasDetected = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Mantener vigilancia activa ante errores transitorios
            } finally {
                frameToProcess.recycle()
                _isAnalyzing.value = false
            }
        }
    }
}
