package com.rayoai.presentation.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.model.Capture
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.DeleteAllCapturesUseCase
import com.rayoai.domain.usecase.DeleteCaptureUseCase
import com.rayoai.domain.usecase.GetHistoryUseCase
import com.rayoai.domain.usecase.UpdateChatHiddenStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de historial de capturas.
 * Gestiona la lógica para mostrar, eliminar y navegar desde el historial.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val deleteCaptureUseCase: DeleteCaptureUseCase,
    private val deleteAllCapturesUseCase: DeleteAllCapturesUseCase,
    private val updateChatHiddenStateUseCase: UpdateChatHiddenStateUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val showHiddenChats: StateFlow<Boolean> = userPreferencesRepository.showHiddenChats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val captures: StateFlow<List<Capture>> = showHiddenChats
        .flatMapLatest { showHidden ->
            getHistoryUseCase(showHidden)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleShowHiddenChats() {
        viewModelScope.launch {
            userPreferencesRepository.saveShowHiddenChats(!showHiddenChats.value)
        }
    }

    fun deleteCapture(capture: Capture) {
        viewModelScope.launch {
            deleteCaptureUseCase(capture)
        }
    }

    fun deleteAllCaptures() {
        viewModelScope.launch {
            deleteAllCapturesUseCase()
        }
    }

    fun toggleChatHiddenState(capture: Capture) {
        viewModelScope.launch {
            updateChatHiddenStateUseCase(capture.id, !capture.isHidden)
        }
    }
}