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

/**
 * ViewModel para la pantalla de inicio (Home Screen).
 * Gestiona el estado de la UI, la interacción con los casos de uso y la lógica de negocio
 * relacionada con la descripción de imágenes y el chat conversacional.
 */

sealed class HomeScreenState {
    object Initial : HomeScreenState()
    data class ImageCaptured(
        val imageBitmap: Bitmap,
        val description: String?,
        val imageUri: Uri?
    ) : HomeScreenState()
}

data class HomeUiState(
    val screenState: HomeScreenState = HomeScreenState.Initial,
    val isLoading: Boolean = false, // Indica si una operación de carga está en curso.
    val isAiTyping: Boolean = false, // Indica si la IA está "escribiendo" una respuesta.
    val chatMessages: List<ChatMessage> = emptyList(), // Lista de mensajes en el chat.
    val error: String? = null, // Mensaje de error a mostrar, si lo hay.
    val apiKey: String? = null, // La clave de API de Gemini, obtenida de las preferencias del usuario.
    val currentImageBitmap: Bitmap? = null, // Bitmap de la imagen actual (capturada o de galería)
    val currentImageDescription: String? = null, // Descripción de la imagen actual
    val currentImageUri: Uri? = null,
    val currentCaptureId: Long? = null,
    val currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA, // Default to back camera
    val isTimerEnabled: Boolean = false,
    val timerSeconds: Int = 0,
    val isCountingDown: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val describeImageUseCase: DescribeImageUseCase, // Caso de uso para la descripción inicial de la imagen.
    private val continueChatUseCase: ContinueChatUseCase, // Caso de uso para continuar el chat.
    private val userPreferencesRepository: UserPreferencesRepository, // Repositorio para las preferencias del usuario.
    private val saveCaptureUseCase: SaveCaptureUseCase, // Caso de uso para guardar las capturas en la DB.
    private val imageStorageManager: ImageStorageManager, // Gestor para guardar imágenes en el almacenamiento local.
    private val captureDao: CaptureDao, // DAO para acceder a las capturas guardadas.
    private val savedStateHandle: SavedStateHandle // Handle para acceder a los argumentos de navegación.
) : ViewModel() {

    // Estado mutable de la UI, expuesto como un StateFlow inmutable para la UI.
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _playCaptureSound = MutableSharedFlow<Unit>()
    val playCaptureSound: SharedFlow<Unit> = _playCaptureSound
    private val _countdownTrigger = MutableSharedFlow<Int>()
    val countdownTrigger: SharedFlow<Int> = _countdownTrigger

    init {
        // Recolectar la clave de API de las preferencias del usuario al iniciar el ViewModel.
        viewModelScope.launch {
            userPreferencesRepository.apiKey.collect { key ->
                _uiState.update { currentState -> currentState.copy(apiKey = key) }
            }
        }

        // Comprobar si se ha pasado un ID de captura para restaurar un chat.
        savedStateHandle.get<String>("captureId")?.toLongOrNull()?.let {
            restoreChatFromHistory(it)
        }
    }

    /**
     * Inicia el proceso de descripción de una imagen.
     * Guarda la imagen localmente, la envía al modelo Gemini y actualiza el chat.
     * @param image El [Bitmap] de la imagen a describir.
     */
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

    /**
     * Inicia el proceso de descripción de una imagen.
     * Guarda la imagen localmente, la envía al modelo Gemini y actualiza el chat.
     * @param image El [Bitmap] de la imagen a describir.
     */
    fun describeImage(image: Bitmap) {
        Log.d("HomeViewModel", "describeImage called")
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { currentState -> currentState.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
                return@launch
            }

            // Guardar la imagen capturada localmente y obtener su URI.
            val imageUri = imageStorageManager.saveBitmapAndGetUri(image)
            if (imageUri == null) {
                _uiState.update { currentState -> currentState.copy(error = "Error al guardar la imagen.") }
                return@launch
            }
            _playCaptureSound.emit(Unit)

            // Añadir un mensaje inicial al chat indicando que se ha capturado una imagen.
            val initialPromptMessage = ChatMessage(content = "Imagen capturada.", isFromUser = true)
            _uiState.update {
                it.copy(
                    error = null,
                    currentImageBitmap = image,
                    currentImageUri = imageUri,
                    chatMessages = listOf(initialPromptMessage) // Reiniciar mensajes para la nueva imagen
                )
            }

            // Llamar al caso de uso para describir la imagen.
            val languageCode = Locale.getDefault().language
            describeImageUseCase(apiKey, image, languageCode = languageCode).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        Log.d("HomeViewModel", "describeImage: ResultWrapper.Loading")
                        // isLoading ya está en true por triggerImageCapture o por el envío del mensaje
                    }
                    is ResultWrapper.Success -> {
                        Log.d("HomeViewModel", "describeImage: ResultWrapper.Success")
                        // Si la descripción es exitosa, añadirla al chat y guardar la captura.
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        val newCaptureId = saveCaptureUseCase(imageUri.toString(), newMessages)
                        _uiState.update { currentState ->
                            currentState.copy(
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
                        // Si hay un error, actualizar el estado de error de la UI.
                        _uiState.update { currentState ->
                            currentState.copy(isLoading = false, error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Procesa una imagen seleccionada de la galería.
     * Simplemente delega la descripción de la imagen al método `describeImage`.
     * @param image El [Bitmap] de la imagen de la galería.
     */
    fun processGalleryImage(image: Bitmap) {
        describeImage(image)
    }

    /**
     * Envía un mensaje de texto al modelo Gemini para continuar la conversación.
     * @param message El mensaje de texto del usuario.
     */
    fun sendChatMessage(message: String) {
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { currentState -> currentState.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
                return@launch
            }

            // No enviar mensajes vacíos.
            if (message.isBlank()) return@launch

            // Añadir el mensaje del usuario al chat y activar el indicador de escritura.
            val userMessage = ChatMessage(content = message, isFromUser = true)
            _uiState.update { currentState ->
                currentState.copy(
                    chatMessages = currentState.chatMessages + userMessage,
                    isAiTyping = true // Indicar que la IA está procesando
                )
            }

            // Llamar al caso de uso para continuar el chat, pasando el historial completo.
            continueChatUseCase(apiKey, message, _uiState.value.chatMessages, _uiState.value.currentImageBitmap).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        // El estado de carga ya está gestionado por isAiTyping
                    }
                    is ResultWrapper.Success -> {
                        // Si la respuesta es exitosa, añadirla al chat y desactivar el indicador.
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        _uiState.update { currentState ->
                            currentState.copy(
                                isLoading = false,
                                isAiTyping = false,
                                chatMessages = newMessages
                            )
                        }
                        _uiState.value.currentImageUri?.let { uri ->
                            saveCaptureUseCase(uri.toString(), newMessages, _uiState.value.currentCaptureId)
                        }
                    }
                    is ResultWrapper.Error -> {
                        // Si hay un error, actualizar el estado de error y desactivar el indicador.
                        _uiState.update { currentState ->
                            currentState.copy(
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

    /**
     * Establece un mensaje de error en el estado de la UI.
     * @param message El mensaje de error a mostrar.
     */
    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    /**
     * Limpia el mensaje de error del estado de la UI.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setLoading(loading: Boolean) {
        Log.d("HomeViewModel", "setLoading called with: $loading")
        _uiState.update { it.copy(isLoading = loading) }
    }

    /**
     * Restablece el estado de la pantalla de inicio a su estado inicial,
     * eliminando la imagen capturada y los mensajes de chat.
     */
    fun resetHomeScreenState() {
        _uiState.update {
            it.copy(
                screenState = HomeScreenState.Initial,
                chatMessages = emptyList(),
                currentImageBitmap = null,
                currentImageDescription = null,
                currentImageUri = null,
                isLoading = false,
                error = null
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
            Log.d("HomeViewModel", "restoreChatFromHistory: Loaded capture for ID: $captureId")
            Log.d("HomeViewModel", "restoreChatFromHistory: Chat history: ${capture?.chatHistory}")
            if (capture != null) {
                val imageUri = Uri.parse(capture.imageUri)
                val bitmap = imageStorageManager.getBitmapFromUri(imageUri)

                if (bitmap != null) {
                    _uiState.update {
                        it.copy(
                            screenState = HomeScreenState.ImageCaptured(bitmap, capture.chatHistory.lastOrNull()?.content, imageUri),
                            currentImageBitmap = bitmap,
                            currentImageDescription = capture.chatHistory.lastOrNull()?.content,
                            currentImageUri = imageUri,
                            chatMessages = capture.chatHistory,
                            currentCaptureId = capture.id
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
}