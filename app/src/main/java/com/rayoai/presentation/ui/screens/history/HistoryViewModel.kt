package com.rayoai.presentation.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.model.Capture
import com.rayoai.domain.usecase.DeleteAllCapturesUseCase
import com.rayoai.domain.usecase.DeleteCaptureUseCase
import com.rayoai.domain.usecase.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para la pantalla de historial de capturas.
 * Gestiona la lógica para mostrar, eliminar y navegar desde el historial.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val deleteAllCapturesUseCase: DeleteAllCapturesUseCase,
    private val deleteCaptureUseCase: DeleteCaptureUseCase
) : ViewModel() {

    private val _history = MutableStateFlow<List<Capture>>(emptyList())
    val history: StateFlow<List<Capture>> = _history.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            getHistoryUseCase(showHidden = false).collect { captures ->
                _history.value = captures
            }
        }
    }

    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.OnItemLongClick -> {
                _isSelectionMode.value = true
                _selectedItemIds.update { it + event.id }
            }
            is HistoryEvent.OnItemClick -> {
                if (_isSelectionMode.value) {
                    _selectedItemIds.update { currentIds ->
                        if (event.id in currentIds) {
                            currentIds - event.id
                        } else {
                            currentIds + event.id
                        }
                    }
                    if (_selectedItemIds.value.isEmpty()) {
                        _isSelectionMode.value = false
                    }
                } else {
                    event.navigateToChat(event.id)
                }
            }
            HistoryEvent.OnClearSelection -> {
                _isSelectionMode.value = false
                _selectedItemIds.value = emptySet()
            }
            HistoryEvent.OnDeleteSelected -> {
                viewModelScope.launch {
                    val itemsToDelete = _history.value.filter { it.id in _selectedItemIds.value }
                    itemsToDelete.forEach { capture ->
                        deleteCaptureUseCase(capture)
                    }
                    _isSelectionMode.value = false
                    _selectedItemIds.value = emptySet()
                }
            }
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            deleteAllCapturesUseCase()
        }
    }
}