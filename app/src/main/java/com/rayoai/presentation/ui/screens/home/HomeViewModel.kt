package com.rayoai.presentation.ui.screens.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.usecase.ContinueChatUseCase
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.SaveCaptureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.camera.core.CameraSelector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import java.util.Locale
import androidx.lifecycle.SavedStateHandle
import com.rayoai.data.local.db.CaptureDao
import javax.inject.Inject

sealed class HomeScreenState { // Forcing re-evaluation
    object Initial : HomeScreenState()
    data class ImageCaptured(
        val imageBitmap: Bitmap,
        val description: String?,
        val imageUri: Uri? // Asegurarse de que sea nullable
    ) : HomeScreenState()
}

data class HomeUiState(
    val screenState: HomeScreenState = HomeScreenState.Initial,
    val isLoading: Boolean = false,
    val isAiTyping: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val apiKey: String? = null,
    val currentImageBitmap: Bitmap? = null,
    val currentImageDescription: String? = null,
    val currentImageUri: Uri? = null,
    val currentCaptureId: Long? = null,
    val currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    val isTimerEnabled: Boolean = false,
    val timerSeconds: Int = 0,
    val isCountingDown: Boolean = false,
    val selectedImageUris: List<Uri> = emptyList(),
    val showApiUsageWarning: Boolean = false,
    val showAddImageDialog: Boolean = false,
    val showRatingBanner: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val describeImageUseCase: DescribeImageUseCase,
    private val continueChatUseCase: ContinueChatUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCaptureUseCase: SaveCaptureUseCase,
    private val imageStorageManager: ImageStorageManager,
    private val captureDao: CaptureDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _playCaptureSound = MutableSharedFlow<Unit>()
    val playCaptureSound: SharedFlow<Unit> = _playCaptureSound
    private val _countdownTrigger = MutableSharedFlow<Int>()
    val countdownTrigger: SharedFlow<Int> = _countdownTrigger

    init {
        viewModelScope.launch {
            userPreferencesRepository.apiKey.collect { key ->
                _uiState.update { it.copy(apiKey = key) }
            }
        }
        savedStateHandle.get<String>("captureId")?.toLongOrNull()?.let {
            restoreChatFromHistory(it)
        }

        viewModelScope.launch {
            val hasRated = userPreferencesRepository.hasRated.first()
            if (!hasRated) {
                val lastPromptTime = userPreferencesRepository.lastPromptTime.first()
                val currentTime = System.currentTimeMillis()
                val threeDaysInMillis = 72 * 60 * 60 * 1000L // 72 hours in milliseconds
                if (lastPromptTime == 0L || (currentTime - lastPromptTime) > threeDaysInMillis) {
                    _uiState.update { it.copy(showRatingBanner = true) }
                }
            }
        }
    }

    fun onRateLaterClicked() {
        viewModelScope.launch {
            userPreferencesRepository.saveLastPromptTime(System.currentTimeMillis())
            _uiState.update { it.copy(showRatingBanner = false) }
        }
    }

    fun onRateNowClicked() {
        viewModelScope.launch {
            userPreferencesRepository.saveHasRated(true)
            _uiState.update { it.copy(showRatingBanner = false) }
        }
    }

    fun setTimerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isTimerEnabled = enabled) }
    }

    fun setTimerSeconds(seconds: Int) {
        _uiState.update { it.copy(timerSeconds = seconds) }
    }

    fun triggerImageCapture() {
        Log.d("HomeViewModel", "triggerImageCapture called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("HomeViewModel", "isLoading set to true by triggerImageCapture")
            if (_uiState.value.isTimerEnabled && _uiState.value.timerSeconds > 0) {
                _uiState.update { it.copy(isCountingDown = true) }
                for (i in _uiState.value.timerSeconds downTo 1) {
                    _countdownTrigger.emit(i)
                    delay(1000L)
                }
                _uiState.update { it.copy(isCountingDown = false) }
            }
        }
    }

    fun describeImage(image: Bitmap) {
        Log.d("HomeViewModel", "describeImage called")
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
                return@launch
            }

            val imageUri = imageStorageManager.saveBitmapAndGetUri(image)
            if (imageUri == null) {
                _uiState.update { it.copy(error = "Error al guardar la imagen.") }
                return@launch
            }
            _playCaptureSound.emit(Unit)

            val initialPromptMessage = ChatMessage(content = "Imagen capturada.", isFromUser = true)
            _uiState.update {
                it.copy(
                    error = null,
                    currentImageBitmap = image,
                    currentImageUri = imageUri,
                    chatMessages = listOf(initialPromptMessage),
                    selectedImageUris = listOf(imageUri) // Add the described image to selectedImageUris
                )
            }

            val languageCode = Locale.getDefault().language
            describeImageUseCase(apiKey, image, languageCode = languageCode).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        Log.d("HomeViewModel", "describeImage: ResultWrapper.Loading")
                    }
                    is ResultWrapper.Success -> {
                        Log.d("HomeViewModel", "describeImage: ResultWrapper.Success")
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        val finalImageUri: Uri? = imageUri
                        val newCaptureId = saveCaptureUseCase(_uiState.value.selectedImageUris.map { it.toString() }, newMessages)
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                chatMessages = newMessages,
                                currentImageDescription = result.data,
                                screenState = HomeScreenState.ImageCaptured(image, result.data, imageUri),
                                currentCaptureId = newCaptureId
                            )
                        }
                    }
                    is ResultWrapper.Error -> {
                        Log.d("HomeViewModel", "describeImage: ResultWrapper.Error: ${result.message}")
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
            }
        }
    }

    fun processGalleryImage(image: Bitmap) {
        describeImage(image)
    }

    fun sendChatMessage(message: String) {
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
                return@launch
            }

            if (message.isBlank() && _uiState.value.selectedImageUris.isEmpty()) return@launch

            val userMessage = ChatMessage(content = message, isFromUser = true)
            _uiState.update { 
                it.copy(
                    chatMessages = it.chatMessages + userMessage,
                    isAiTyping = true
                )
            }

            val imageBitmaps = _uiState.value.selectedImageUris.mapNotNull { uri ->
                imageStorageManager.getBitmapFromUri(uri)
            }

            continueChatUseCase(apiKey, message, _uiState.value.chatMessages, imageBitmaps).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                    }
                    is ResultWrapper.Success -> {
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        val currentImageUris = _uiState.value.selectedImageUris.map { it.toString() }
                        val newCaptureId = saveCaptureUseCase(currentImageUris, newMessages, _uiState.value.currentCaptureId)
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isAiTyping = false,
                                chatMessages = newMessages,
                                selectedImageUris = emptyList(),
                                currentCaptureId = newCaptureId
                            )
                        }
                    }
                    is ResultWrapper.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isAiTyping = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setLoading(loading: Boolean) {
        Log.d("HomeViewModel", "setLoading called with: $loading")
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun resetHomeScreenState() {
        _uiState.update {
            it.copy(
                screenState = HomeScreenState.Initial,
                chatMessages = emptyList(),
                currentImageBitmap = null,
                currentImageDescription = null,
                currentImageUri = null,
                isLoading = false,
                error = null,
                selectedImageUris = emptyList()
            )
        }
    }

    fun saveImageToGallery(bitmap: Bitmap) {
        viewModelScope.launch {
            val uri = imageStorageManager.saveBitmapToGallery(bitmap)
            if (uri != null) {
                _uiState.update { it.copy(error = "Imagen guardada en la galería.", currentImageUri = uri) }
            } else {
                _uiState.update { it.copy(error = "Error al guardar la imagen.") }
            }
        }
    }

    fun toggleCamera() {
        _uiState.update { currentState ->
            val newSelector = if (currentState.currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            currentState.copy(currentCameraSelector = newSelector)
        }
    }

    private fun restoreChatFromHistory(captureId: Long) {
        viewModelScope.launch {
            val capture = captureDao.getCaptureById(captureId)
            if (capture != null) {
                val imageUris = capture.imageUris.map { Uri.parse(it) }
                val firstImageUri = imageUris.firstOrNull()
                val firstImageBitmap = firstImageUri?.let { imageStorageManager.getBitmapFromUri(it) }

                if (firstImageBitmap != null) {
                    _uiState.update {
                        it.copy(
                            screenState = HomeScreenState.ImageCaptured(firstImageBitmap, capture.chatHistory.lastOrNull()?.content, imageUris.firstOrNull()),
                            currentImageBitmap = firstImageBitmap,
                            currentImageDescription = capture.chatHistory.lastOrNull()?.content,
                            currentImageUri = imageUris.firstOrNull(),
                            chatMessages = capture.chatHistory,
                            currentCaptureId = capture.id,
                            selectedImageUris = imageUris
                        )
                    }
                } else {
                    setError("No se pudo cargar la imagen del historial.")
                }
            } else {
                setError("No se pudo encontrar la captura en el historial.")
            }
        }
    }

    fun deleteChat(captureId: Long) {
        viewModelScope.launch {
            val capture = captureDao.getCaptureById(captureId)
            capture?.let { 
                imageStorageManager.deleteImages(it.imageUris.map { uriString -> Uri.parse(uriString) })
                captureDao.deleteCapture(captureId)
            }
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            val allCaptures = captureDao.getAllCapturesList()
            allCaptures.forEach { capture ->
                imageStorageManager.deleteImages(capture.imageUris.map { uriString -> Uri.parse(uriString) })
            }
            captureDao.deleteAllCaptures()
        }
    }

    fun onAddImageRequest() {
        viewModelScope.launch {
            val hasShownWarning = userPreferencesRepository.hasShownApiUsageWarning.first()
            if (!hasShownWarning) {
                _uiState.update { it.copy(showApiUsageWarning = true) }
            } else {
                _uiState.update { it.copy(showAddImageDialog = true) }
            }
        }
    }

    fun onApiUsageWarningDismissed() {
        viewModelScope.launch {
            userPreferencesRepository.setHasShownApiUsageWarning(true)
            _uiState.update { it.copy(showApiUsageWarning = false, showAddImageDialog = true) }
        }
    }

    fun onAddImageDialogDismissed() {
        _uiState.update { it.copy(showAddImageDialog = false) }
    }

    fun onImagesSelected(uris: List<Uri>) {
        val currentSelectedCount = _uiState.value.selectedImageUris.size
        val remainingSlots = 3 - currentSelectedCount
        val newUris = uris.take(remainingSlots)
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris + newUris) }
    }

    fun removeSelectedImage(uri: Uri) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris - uri) }
    }

    fun getTmpFileUri(): Uri {
        return imageStorageManager.getTmpFileUri()
    }
}
