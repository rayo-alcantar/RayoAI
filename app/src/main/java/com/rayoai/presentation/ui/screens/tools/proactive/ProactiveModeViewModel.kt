package com.rayoai.presentation.ui.screens.tools.proactive

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.AudioRecording
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.proactive.PersonalObject
import com.rayoai.domain.model.proactive.ProactiveDetection
import com.rayoai.domain.model.proactive.ProactivePreset
import com.rayoai.domain.model.proactive.ProactivePresetModel
import com.rayoai.domain.model.proactive.ProactiveQuickTarget
import com.rayoai.domain.repository.PersonalObjectRepository
import com.rayoai.domain.repository.ProactivePresetRepository
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.TranscribeAudioUseCase
import com.rayoai.domain.usecase.proactive.EvaluateProactiveFrameUseCase
import com.rayoai.domain.usecase.proactive.GenerateVisualProfileUseCase
import com.rayoai.presentation.voice.AndroidSpeechFileTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
    private val userPreferencesRepository: UserPreferencesRepository,
    private val proactivePresetRepository: ProactivePresetRepository,
    private val personalObjectRepository: PersonalObjectRepository,
    private val generateVisualProfileUseCase: GenerateVisualProfileUseCase,
    val imageStorageManager: ImageStorageManager
) : ViewModel() {

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isTranscribingAudio = MutableStateFlow(false)
    val isTranscribingAudio: StateFlow<Boolean> = _isTranscribingAudio.asStateFlow()

    // Modo simple / directo (predeterminado)
    private val _selectedQuickTarget = MutableStateFlow(ProactiveQuickTarget.OBJECT)
    val selectedQuickTarget: StateFlow<ProactiveQuickTarget> = _selectedQuickTarget.asStateFlow()

    private val _customCondition = MutableStateFlow("")
    val customCondition: StateFlow<String> = _customCondition.asStateFlow()

    // Objeto personal seleccionado en modo directo (null = ninguna selección de objeto personal)
    private val _selectedPersonalObject = MutableStateFlow<PersonalObject?>(null)
    val selectedPersonalObject: StateFlow<PersonalObject?> = _selectedPersonalObject.asStateFlow()

    // Presets y Objetos Personales reactivos
    val presets: StateFlow<List<ProactivePresetModel>> = proactivePresetRepository.presets
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ProactivePresetModel.getDefaultPresets()
        )

    val personalObjects: StateFlow<List<PersonalObject>> = personalObjectRepository.personalObjects
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    // Preset activo (null = Modo de búsqueda directa / simple predeterminado)
    private val _activePreset = MutableStateFlow<ProactivePresetModel?>(null)
    val activePreset: StateFlow<ProactivePresetModel?> = _activePreset.asStateFlow()

    private val _temporaryVoiceCondition = MutableStateFlow<String?>(null)
    val temporaryVoiceCondition: StateFlow<String?> = _temporaryVoiceCondition.asStateFlow()

    private val _isGeneratingProfile = MutableStateFlow(false)
    val isGeneratingProfile: StateFlow<Boolean> = _isGeneratingProfile.asStateFlow()

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
    private var startMonitoringJob: Job? = null
    private var frameAnalysisJob: Job? = null

    private fun resetDetectionState() {
        _latestDetection.value = null
        _lastAnnouncedPhrase.value = null
        lastAnnouncementTimeMs = 0L
        wasDetected = false
        consecutiveNonDetectionFrames = 0
    }

    fun toggleMonitoring(onMissingApiKey: () -> Unit) {
        if (_isMonitoring.value) {
            stopMonitoring()
        } else {
            startMonitoring(onMissingApiKey)
        }
    }

    fun startMonitoring(onMissingApiKey: () -> Unit) {
        if (_isMonitoring.value || startMonitoringJob?.isActive == true) return
        startMonitoringJob = viewModelScope.launch {
            val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
            if (key.isNullOrBlank()) {
                onMissingApiKey()
                return@launch
            }
            _isMonitoring.value = true
            resetDetectionState()
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        startMonitoringJob?.cancel()
        startMonitoringJob = null
        val pendingAnalysis = frameAnalysisJob
        frameAnalysisJob = null
        pendingAnalysis?.cancel()
        _isAnalyzing.value = false
        wasDetected = false
        consecutiveNonDetectionFrames = 0
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    fun setQuickTarget(target: ProactiveQuickTarget) {
        _selectedQuickTarget.value = target
        _selectedPersonalObject.value = null
        _activePreset.value = null // Vuelve al modo directo
        _temporaryVoiceCondition.value = null
        resetDetectionState()
    }

    fun setCustomCondition(condition: String) {
        _customCondition.value = condition
        _selectedPersonalObject.value = null
        _activePreset.value = null
        _temporaryVoiceCondition.value = null
        resetDetectionState()
    }

    fun selectPersonalObject(personalObject: PersonalObject) {
        _selectedPersonalObject.value = personalObject
        _activePreset.value = null
        _temporaryVoiceCondition.value = null
        resetDetectionState()
    }

    fun clearSelectedPersonalObject() {
        _selectedPersonalObject.value = null
        resetDetectionState()
    }

    fun loadPreset(preset: ProactivePresetModel) {
        _activePreset.value = preset
        _selectedPersonalObject.value = null
        _temporaryVoiceCondition.value = null
        resetDetectionState()
    }

    fun unloadPreset() {
        _activePreset.value = null
        _temporaryVoiceCondition.value = null
        resetDetectionState()
    }

    fun savePreset(preset: ProactivePresetModel) {
        viewModelScope.launch {
            proactivePresetRepository.savePreset(preset)
            if (_activePreset.value?.id == preset.id) {
                _activePreset.value = preset
            }
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            proactivePresetRepository.deletePreset(presetId)
            if (_activePreset.value?.id == presetId) {
                _activePreset.value = null
            }
        }
    }

    fun savePersonalObject(personalObject: PersonalObject) {
        viewModelScope.launch {
            personalObjectRepository.savePersonalObject(personalObject)
            if (_selectedPersonalObject.value?.id == personalObject.id) {
                _selectedPersonalObject.value = personalObject
            }
        }
    }

    fun deletePersonalObject(id: String) {
        viewModelScope.launch {
            personalObjectRepository.deletePersonalObject(id)
            if (_selectedPersonalObject.value?.id == id) {
                _selectedPersonalObject.value = null
            }
        }
    }

    fun generateVisualProfile(
        imageUris: List<Uri>,
        objectName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val key = apiKey.value ?: userPreferencesRepository.apiKey.first()
            if (key.isNullOrBlank()) {
                onError("Clave de API no configurada.")
                return@launch
            }

            val bitmaps = imageUris.mapNotNull { uri ->
                imageStorageManager.getBitmapFromUri(uri)
            }

            if (bitmaps.isEmpty()) {
                onError("No se pudieron cargar las fotos de referencia.")
                return@launch
            }

            _isGeneratingProfile.value = true
            try {
                val lang = Locale.getDefault().language
                val model = GeminiModelConfig.DEFAULT_MODEL

                generateVisualProfileUseCase(
                    apiKey = key,
                    images = bitmaps,
                    objectName = objectName,
                    languageCode = lang,
                    model = model
                ).collect { result ->
                    when (result) {
                        is ResultWrapper.Loading -> Unit
                        is ResultWrapper.Error -> {
                            _isGeneratingProfile.value = false
                            onError(result.message)
                        }
                        is ResultWrapper.Success -> {
                            _isGeneratingProfile.value = false
                            onSuccess(result.data.trim())
                        }
                    }
                }
            } catch (e: Exception) {
                _isGeneratingProfile.value = false
                onError(e.localizedMessage ?: "Error al analizar fotos")
            } finally {
                bitmaps.forEach { runCatching { it.recycle() } }
            }
        }
    }

    fun clearTemporaryVoiceCondition() {
        _temporaryVoiceCondition.value = null
        resetDetectionState()
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
                    if (!_isMonitoring.value) {
                        _customCondition.value = cleanTarget
                        _selectedQuickTarget.value = ProactivePreset.CUSTOM
                        _selectedPersonalObject.value = null
                        _activePreset.value = null
                    }
                    resetDetectionState()

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

    fun evaluateFrame(bitmap: Bitmap, defaultPromptCondition: String = "") {
        if (!_isMonitoring.value || _isAnalyzing.value) {
            bitmap.recycle()
            return
        }

        val voiceOverride = _temporaryVoiceCondition.value
        val personalObj = _selectedPersonalObject.value
        val currentPreset = _activePreset.value
        val currentQuick = _selectedQuickTarget.value
        val customText = _customCondition.value
        val lang = Locale.getDefault().language
        val allPersonalObjects = personalObjects.value

        val effectiveCondition = when {
            !voiceOverride.isNullOrBlank() -> voiceOverride.trim()
            personalObj != null -> {
                val desc = personalObj.visualDescription.trim()
                if (lang.startsWith("es", ignoreCase = true)) {
                    """
                    Objetivo prioritario de vigilancia:
                    - ${personalObj.name} (perfil visual exacto: $desc)

                    Instrucciones para el anuncio:
                    Si detectas en el fotograma este objeto personal específico según sus características visuales distintivas, establece "detected": true y elabora una frase concisa de 3 a 8 palabras optimizada para TextToSpeech indicando que lo encontraste y su posición relativa (ejemplo: '${personalObj.name} sobre la mesa al frente'). Si no está visible, responde con "detected": false y "announcement": null.
                    """.trimIndent()
                } else {
                    """
                    Priority surveillance target:
                    - ${personalObj.name} (exact visual profile: $desc)

                    Alert instructions:
                    If you detect this specific personal object in the frame according to its distinctive visual traits, set "detected": true and create a concise phrase of 3 to 8 words optimized for TextToSpeech stating that it was located and its relative location (e.g., '${personalObj.name} on the table ahead'). If not visible, set "detected": false and "announcement": null.
                    """.trimIndent()
                }
            }
            currentPreset != null -> currentPreset.buildConditionPrompt(lang, allPersonalObjects)
            currentQuick == ProactivePreset.CUSTOM && customText.isNotBlank() -> customText.trim()
            else -> defaultPromptCondition.ifBlank { "Objetos y cambios en el entorno" }
        }

        _isAnalyzing.value = true
        val analysis = viewModelScope.launch(start = CoroutineStart.LAZY) {
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
                    if (!_isMonitoring.value || frameAnalysisJob !== coroutineContext[Job]) return@collect
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Mantener vigilancia activa en silencio
            } finally {
                if (frameToProcess !== bitmap) frameToProcess.recycle()
                if (frameAnalysisJob === coroutineContext[Job]) {
                    frameAnalysisJob = null
                    _isAnalyzing.value = false
                }
            }
        }
        frameAnalysisJob = analysis
        analysis.invokeOnCompletion { bitmap.recycle() }
        analysis.start()
    }
}
