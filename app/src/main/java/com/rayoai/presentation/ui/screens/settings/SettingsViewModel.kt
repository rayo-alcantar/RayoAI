package com.rayoai.presentation.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de ajustes (Settings Screen).
 * Gestiona el estado de la UI y la interacción con el repositorio de preferencias del usuario.
 */
data class SettingsUiState(
    val isApiKeySaved: Boolean = false,
    val currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val currentTextScale: Float = 1.0f,
    val currentAutoDescribeOnShare: Boolean = false,
    val maxImagesInChat: String = "3",
    val navigateTo: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val userPreferencesRepository: UserPreferencesRepository // Repositorio de preferencias del usuario.
) : ViewModel() {

    // Estado mutable de la UI, expuesto como un StateFlow inmutable para la UI.
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Recolectar las preferencias del usuario al iniciar el ViewModel.
        viewModelScope.launch {
            userPreferencesRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(currentThemeMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.textScale.collect { scale ->
                _uiState.update { it.copy(currentTextScale = scale) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoDescribeOnShare.collect { enabled ->
                _uiState.update { it.copy(currentAutoDescribeOnShare = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.maxImagesInChat.collect { count ->
                _uiState.update { it.copy(maxImagesInChat = count.toString()) }
            }
        }
    }

    /**
     * Guarda la clave de API proporcionada por el usuario.
     * @param apiKey La clave de API a guardar.
     */
    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveApiKey(apiKey)
            _uiState.update { it.copy(isApiKeySaved = true) }
        }
    }

    /**
     * Limpia el estado de `isApiKeySaved` después de que se ha mostrado el mensaje de éxito.
     */
    fun clearApiKeySavedStatus() {
        _uiState.update { it.copy(isApiKeySaved = false) }
    }

    /**
     * Guarda el modo de tema seleccionado por el usuario.
     * @param mode El [ThemeMode] a guardar.
     */
    fun saveThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeMode(mode)
        }
    }

    /**
     * Guarda la escala de texto seleccionada por el usuario.
     * @param scale La escala de texto a guardar.
     */
    fun saveTextScale(scale: Float) {
        viewModelScope.launch {
            userPreferencesRepository.saveTextScale(scale)
        }
    }

    /**
     * Guarda el estado del interruptor de auto-descripción al compartir.
     * @param enabled `true` para habilitar, `false` para deshabilitar.
     */
    fun saveAutoDescribeOnShare(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveAutoDescribeOnShare(enabled)
        }
    }

    fun onMaxImagesInChatChanged(text: String) {
        // Allow only digits and ensure it's not empty before updating the state
        if (text.all { it.isDigit() }) {
            _uiState.update { it.copy(maxImagesInChat = text) }
        }
    }

    fun saveMaxImagesInChat() {
        viewModelScope.launch {
            val count = _uiState.value.maxImagesInChat.toIntOrNull() ?: 3
            userPreferencesRepository.saveMaxImagesInChat(count)
        }
    }

    fun onNavigateToApiInstructions() {
        _uiState.update { it.copy(navigateTo = "api_instructions") }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateTo = null) }
    }
}