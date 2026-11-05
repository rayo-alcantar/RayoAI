package com.rayoai.presentation.ui.screens.tools.proactive

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.AudioRecording
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.proactive.ProactiveDetection
import com.rayoai.domain.model.proactive.ProactivePresetModel
import com.rayoai.domain.repository.ProactivePresetRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ProactiveModeViewModel @Inject constructor(
    private val evaluateProactiveFrameUseCase: EvaluateProactiveFrameUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val proactivePresetRepository: ProactivePresetRepository
) : ViewModel() {

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isTranscribingAudio = MutableStateFlow(false)
    val isTranscribingAudio: StateFlow<Boolean> = _isTranscribingAudio.asStateFlow()

    val presets: StateFlow<List<ProactivePresetModel>> = proactivePresetRepository.presets
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ProactivePresetModel.getDefaultPresets()
        )

    val activePresetId: StateFlow<String> = proactivePresetRepository.activePresetId
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ProactivePresetModel.PRESET_STREET_ID
        )

    val activePreset: StateFlow<ProactivePresetModel> = combine(presets, activePresetId) { list, id ->
        list.find { it.id == id }
            ?: list.firstOrNull()
            ?: ProactivePresetModel.createDefaultStreetPreset()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ProactivePresetModel.createDefaultStreetPreset()
    )

    private val _temporaryVoiceCondition = MutableStateFlow<String?>(null)
    val temporaryVoiceCondition: StateFlow<String?> = _temporaryVoiceCondition.asStateFlow()

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
    private var consecutiveNonDetectionFrames: Int = 0

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
            consecutiveNonDetectionFrames = 0
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        _isAnalyzing.value = false
        wasDetected = false
        consecutiveNonDetectionFrames = 0
    }

    fun selectPreset(presetId: String) {
        viewModelScope.launch {
            _temporaryVoiceCondition.value = null
            _latestDetection.value = null
            _lastAnnouncedPhrase.value = null
            lastAnnouncementTimeMs = 0L
            wasDetected = false
            consecutiveNonDetectionFrames = 0
            proactivePresetRepository.setActivePresetId(presetId)
        }
    }

    fun savePreset(preset: ProactivePresetModel) {
        viewModelScope.launch {
            proactivePresetRepository.savePreset(preset)
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            proactivePresetRepository.deletePreset(presetId)
        }
    }

    fun clearTemporaryVoiceCondition() {
        _temporaryVoiceCondition.value = null
        _latestDetection.value = null
        _lastAnnouncedPhrase.value = null
        lastAnnouncementTimeMs = 0L
        wasDetected = false
        consecutiveNonDetectionFrames = 0
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

    /**
     * Limpia y extrae el objetivo principal en lenguaje natural para la confirmación de voz y UI.
     */
    fun extractNaturalLanguageTarget(spokenText: String): String {
        var cleaned = spokenText.trim().trim('.', ',', '!', '¡', '?', '¿', '"', '\'')

        val patterns = listOf(
            Regex("^(che rayo|rayo|por favor|che)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(buscame|buscá|buscar|busca|encontrame|encontrá|encuentra|encontrar)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(avisame si ves|avisame cuando veas|avisame si hay|avisame si|avisame)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(fijate si ves|fijate si hay|fijate si|fijate|mira si ves|mira si hay|mira si|mira)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(quiero encontrar|quiero que busques|quiero ver si|quiero ver)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(decime si ves|decime si hay|decime si|decime)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(donde estan|donde esta|donde deje|donde dejé|dónde están|dónde está|dónde dejé)\\s+", RegexOption.IGNORE_CASE)
        )

        var changed = true
        while (changed) {
            changed = false
            for (pattern in patterns) {
                if (pattern.containsMatchIn(cleaned)) {
                    cleaned = cleaned.replace(pattern, "").trim()
                    changed = true
                }
            }
        }

        val articlePattern = Regex("^(el|la|los|las|un|una|unos|unas|mi|mis)\\s+", RegexOption.IGNORE_CASE)
        if (articlePattern.containsMatchIn(cleaned)) {
            cleaned = cleaned.replace(articlePattern, "").trim()
        }

        return cleaned.ifBlank { spokenText.trim() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun transcribeVoiceAndSetCustomCondition(
        context: Context,
        recording: AudioRecording
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
                    val rawSpoken = text.trim()
                    val cleanTarget = extractNaturalLanguageTarget(rawSpoken)

                    _temporaryVoiceCondition.value = cleanTarget
                    _latestDetection.value = null
                    _lastAnnouncedPhrase.value = null
                    lastAnnouncementTimeMs = 0L
                    wasDetected = false
                    consecutiveNonDetectionFrames = 0

                    _speechFeedbackTrigger.tryEmit("Buscando: $cleanTarget")
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

    fun evaluateFrame(bitmap: Bitmap, fallbackCondition: String = "") {
        if (!_isMonitoring.value || _isAnalyzing.value) {
            bitmap.recycle()
            return
        }

        val voiceOverride = _temporaryVoiceCondition.value
        val lang = Locale.getDefault().language
        val effectiveCondition = if (!voiceOverride.isNullOrBlank()) {
            voiceOverride.trim()
        } else {
            val presetPrompt = activePreset.value.buildConditionPrompt(lang)
            if (presetPrompt.isNotBlank()) presetPrompt else fallbackCondition
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

                // Escalar a 768px máximo para respuesta ágil en tiempo real
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
                            // Silencio ante fallo transitorio de frame
                        }
                        is ResultWrapper.Success -> {
                            val detection = result.data
                            _latestDetection.value = detection

                            val now = System.currentTimeMillis()
                            val phrase = detection.announcement

                            if (detection.detected && !phrase.isNullOrBlank()) {
                                consecutiveNonDetectionFrames = 0
                                val isFirstTimeDetected = !wasDetected
                                val isCooldownExpired = (now - lastAnnouncementTimeMs) > 16_000L

                                // Solo anuncia cuando localiza por primera vez o tras el cooldown continuo
                                if (isFirstTimeDetected || isCooldownExpired) {
                                    _lastAnnouncedPhrase.value = phrase
                                    lastAnnouncementTimeMs = now
                                    _alertTrigger.tryEmit(phrase)
                                    _hapticTrigger.tryEmit(Unit)
                                }
                                wasDetected = true
                            } else {
                                consecutiveNonDetectionFrames++
                                // Requiere al menos 2 frames consecutivos sin detección para considerarlo fuera de vista
                                if (consecutiveNonDetectionFrames >= 2) {
                                    wasDetected = false
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Mantener vigilancia activa en silencio
            } finally {
                frameToProcess.recycle()
                _isAnalyzing.value = false
            }
        }
    }
}
