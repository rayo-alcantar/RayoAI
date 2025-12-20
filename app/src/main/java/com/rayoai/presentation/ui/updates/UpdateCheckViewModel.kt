package com.rayoai.presentation.ui.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.data.repository.UpdateRepository
import com.rayoai.domain.model.UpdateChannel
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
    val isDownloading: Boolean = false,
    val lastCheckResult: UpdateCheckResult? = null
)

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    object BetaAvailable : UpdateCheckResult()
    data class Error(val message: String?) : UpdateCheckResult()
}

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
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, lastCheckResult = null) }
            val result = runCatching { updateRepository.checkForUpdateVerbose() }
            val response = result.getOrNull()
            val updateInfo = response?.updateInfo
            val checkResult = if (manual) {
                when {
                    result.isFailure -> {
                        val error = result.exceptionOrNull()
                        val message = error?.message?.takeIf { it.isNotBlank() } ?: error?.toString()
                        UpdateCheckResult.Error(message)
                    }
                    updateInfo == null &&
                        response?.channel == UpdateChannel.STABLE &&
                        response.hasStable == false &&
                        response.hasBeta == true -> {
                        UpdateCheckResult.BetaAvailable
                    }
                    updateInfo == null -> UpdateCheckResult.UpToDate
                    else -> null
                }
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    isChecking = false,
                    updateAvailable = updateInfo,
                    lastCheckResult = checkResult
                )
            }
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

    fun clearCheckResult() {
        _uiState.update { it.copy(lastCheckResult = null) }
    }
}
