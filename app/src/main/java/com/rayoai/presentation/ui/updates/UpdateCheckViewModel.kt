package com.rayoai.presentation.ui.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.data.repository.UpdateRepository
import com.rayoai.domain.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateAvailable: UpdateInfo? = null,
    val isDownloading: Boolean = false
)

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val updatePreferences: UpdatePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        checkSilentlyOnStart()
    }

    private fun checkSilentlyOnStart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            val updateInfo = runCatching { updateRepository.checkForUpdate() }.getOrNull()
            _uiState.update { it.copy(isChecking = false, updateAvailable = updateInfo) }
        }
    }

    fun startDownload(context: Context) {
        val updateInfo = _uiState.value.updateAvailable ?: return
        if (_uiState.value.isDownloading) return
        UpdateInstaller.downloadUpdate(context, updateInfo, updatePreferences)
        _uiState.update { it.copy(isDownloading = true) }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null, isDownloading = false) }
    }
}
