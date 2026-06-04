package com.rayoai.presentation.ui.updates

import android.content.Context
import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.BuildConfig
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
import retrofit2.HttpException
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateAvailable: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    val lastCheckResult: UpdateCheckResult? = null,
    val error: UpdateError? = null
)

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    object BetaAvailable : UpdateCheckResult()
}

data class UpdateError(
    val title: String,
    val details: String
)

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val updatePreferences: UpdatePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            checkSilentlyOnStart()
        }
    }

    private fun checkSilentlyOnStart() {
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, lastCheckResult = null, error = null) }
            val result = runCatching { updateRepository.checkForUpdateVerbose() }
            val response = result.getOrNull()
            val updateInfo = response?.updateInfo
            val checkResult = if (manual) {
                when {
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
            val error = result.exceptionOrNull()?.let { formatError(it) }
            _uiState.update {
                it.copy(
                    isChecking = false,
                    updateAvailable = updateInfo,
                    lastCheckResult = checkResult,
                    error = error
                )
            }
        }
    }

    fun startDownload(context: Context) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        val updateInfo = _uiState.value.updateAvailable ?: return
        if (_uiState.value.isDownloading) return
        runCatching {
            UpdateInstaller.downloadUpdate(context, updateInfo, updatePreferences)
        }.onSuccess {
            _uiState.update { it.copy(isDownloading = true, error = null) }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    error = UpdateError(
                        title = "Error al descargar la actualización",
                        details = throwable.toString()
                    )
                )
            }
        }
    }

    fun onDownloadComplete(context: Context, downloadId: Long) {
        val pending = updatePreferences.getPendingUpdate() ?: return
        if (pending.downloadId != downloadId) return

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return

            when (cursor.getInt(statusIndex)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _uiState.update { it.copy(updateAvailable = null, isDownloading = false) }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getColumnValue(DownloadManager.COLUMN_REASON)
                    updatePreferences.clearPendingUpdate()
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            error = UpdateError(
                                title = "Error al descargar la actualización",
                                details = "DownloadManager falló. Código de razón: ${reason ?: "desconocido"}"
                            )
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null, isDownloading = false) }
    }

    fun clearCheckResult() {
        _uiState.update { it.copy(lastCheckResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatError(throwable: Throwable): UpdateError {
        val title = "Error al comprobar actualizaciones"
        val details = when (throwable) {
            is HttpException -> {
                val response = throwable.response()
                val code = response?.code() ?: throwable.code()
                val message = response?.message().orEmpty()
                val url = response?.raw()?.request?.url?.toString().orEmpty()
                val body = runCatching { response?.errorBody()?.string().orEmpty() }.getOrNull().orEmpty()
                buildString {
                    append("HTTP ").append(code)
                    if (message.isNotBlank()) append(" ").append(message)
                    if (url.isNotBlank()) append("\nURL: ").append(url)
                    if (body.isNotBlank()) append("\nBody: ").append(body)
                }
            }
            else -> throwable.toString()
        }
        return UpdateError(title = title, details = details.ifBlank { throwable.toString() })
    }

    private fun android.database.Cursor.getColumnValue(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getInt(index) else null
    }
}
