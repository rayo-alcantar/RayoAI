package com.rayoai.presentation.ui.updates

import com.rayoai.data.repository.UpdateRepository
import com.rayoai.domain.model.UpdateChannel
import kotlinx.coroutines.CancellationException

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    object BetaAvailable : UpdateCheckResult()
}

internal fun manualCheckResult(manual: Boolean, response: UpdateRepository.UpdateCheckResponse?): UpdateCheckResult? {
    // A missing response is a failed request, not evidence that the app is current.
    if (!manual || response == null || response.updateInfo != null) return null
    return if (response.channel == UpdateChannel.STABLE && response.hasNewerBeta) {
        UpdateCheckResult.BetaAvailable
    } else {
        UpdateCheckResult.UpToDate
    }
}

internal fun canCheckForUpdates(enabled: Boolean, state: UpdateUiState): Boolean =
    enabled && !state.isChecking && !state.isDownloading

internal suspend fun loadUpdateCheck(
    check: suspend () -> UpdateRepository.UpdateCheckResponse
): Result<UpdateRepository.UpdateCheckResponse> = try {
    Result.success(check())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
