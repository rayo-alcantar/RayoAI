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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val chatMessages: List<ChatMessage> = emptyList(), // Lista de mensajes en el chat.
    val error: String? = null, // Mensaje de error a mostrar, si lo hay.
    val apiKey: String? = null, // La clave de API de Gemini, obtenida de las preferencias del usuario.
    val currentImageBitmap: Bitmap? = null, // Bitmap de la imagen actual (capturada o de galería)
    val currentImageDescription: String? = null, // Descripción de la imagen actual
    val currentImageUri: Uri? = null // URI de la imagen actual guardada
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val describeImageUseCase: DescribeImageUseCase, // Caso de uso para la descripción inicial de la imagen.
    private val continueChatUseCase: ContinueChatUseCase, // Caso de uso para continuar el chat.
    private val userPreferencesRepository: UserPreferencesRepository, // Repositorio para las preferencias del usuario.
    private val saveCaptureUseCase: SaveCaptureUseCase, // Caso de uso para guardar las capturas en la DB.
    private val imageStorageManager: ImageStorageManager // Gestor para guardar imágenes en el almacenamiento local.
) : ViewModel() {

    // Estado mutable de la UI, expuesto como un StateFlow inmutable para la UI.
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Recolectar la clave de API de las preferencias del usuario al iniciar el ViewModel.
        viewModelScope.launch {
            userPreferencesRepository.apiKey.collect { key ->
                _uiState.update { it.copy(apiKey = key) }
            }
        }
    }

    /**
     * Inicia el proceso de descripción de una imagen.
     * Guarda la imagen localmente, la envía al modelo Gemini y actualiza el chat.
     * @param image El [Bitmap] de la imagen a describir.
     */
    fun describeImage(image: Bitmap) {
        val currentApiKey = _uiState.value.apiKey
        // Verificar si la API Key está configurada.
        if (currentApiKey.isNullOrBlank()) {
            _uiState.update { it.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
            return
        }

        viewModelScope.launch {
            // Guardar la imagen capturada localmente y obtener su URI.
            val imageUri = imageStorageManager.saveBitmapAndGetUri(image)
            if (imageUri == null) {
                _uiState.update { it.copy(error = "Error al guardar la imagen.") }
                return@launch
            }

            // Añadir un mensaje inicial al chat indicando que se ha capturado una imagen.
            val initialPromptMessage = ChatMessage(content = "Imagen capturada.", isFromUser = true)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentImageBitmap = image,
                    currentImageUri = imageUri,
                    chatMessages = listOf(initialPromptMessage) // Reiniciar mensajes para la nueva imagen
                )
            }

            // Llamar al caso de uso para describir la imagen.
            describeImageUseCase(currentApiKey, image).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is ResultWrapper.Success -> {
                        // Si la descripción es exitosa, añadirla al chat y guardar la captura.
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                chatMessages = newMessages,
                                currentImageDescription = result.data,
                                screenState = HomeScreenState.ImageCaptured(image, result.data, imageUri)
                            )
                        }
                        saveCaptureUseCase(imageUri.toString(), result.data) // Guardar en la base de datos.
                    }
                    is ResultWrapper.Error -> {
                        // Si hay un error, actualizar el estado de error de la UI.
                        _uiState.update {
                            it.copy(isLoading = false, error = result.message)
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
        val currentApiKey = _uiState.value.apiKey
        // Verificar si la API Key está configurada.
        if (currentApiKey.isNullOrBlank()) {
            _uiState.update { it.copy(error = "API Key no configurada. Por favor, ve a Ajustes.") }
            return
        }

        // No enviar mensajes vacíos.
        if (message.isBlank()) return

        // Añadir el mensaje del usuario al chat.
        val userMessage = ChatMessage(content = message, isFromUser = true)
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMessage) }

        viewModelScope.launch {
            // Llamar al caso de uso para continuar el chat, pasando el historial completo.
            continueChatUseCase(currentApiKey, message, _uiState.value.chatMessages).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is ResultWrapper.Success -> {
                        // Si la respuesta es exitosa, añadirla al chat.
                        val newMessages = _uiState.value.chatMessages +
                                ChatMessage(content = result.data, isFromUser = false)
                        _uiState.update {
                            it.copy(isLoading = false, chatMessages = newMessages)
                        }
                    }
                    is ResultWrapper.Error -> {
                        // Si hay un error, actualizar el estado de error de la UI.
                        _uiState.update {
                            it.copy(isLoading = false, error = result.message)
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

    
}