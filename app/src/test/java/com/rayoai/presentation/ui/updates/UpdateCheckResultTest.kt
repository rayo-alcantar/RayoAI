package com.rayoai.presentation.ui.updates

import com.rayoai.data.repository.UpdateRepository
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.domain.model.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckResultTest {
    private fun response() = UpdateRepository.UpdateCheckResponse(null, true, false, UpdateChannel.STABLE)

    @Test
    fun missingResponseNeverMeansUpToDate() {
        assertNull(manualCheckResult(manual = true, response = null))
    }

    @Test
    fun successfulManualCheckCanReportNoUpdate() {
        assertSame(UpdateCheckResult.UpToDate, manualCheckResult(true, response()))
    }

    @Test
    fun silentChecksAndAvailableUpdatesHaveNoNoUpdatesMessage() {
        assertNull(manualCheckResult(false, response()))
        assertNull(manualCheckResult(true, response().copy(updateInfo = UpdateInfo("3.3.12-stable", "notes", "https://github.com/app.apk"))))
    }

    @Test
    fun stableCanHintAboutANewerBetaEvenWhenOlderStableReleasesExist() {
        assertSame(UpdateCheckResult.BetaAvailable, manualCheckResult(true, response().copy(hasBeta = true, hasNewerBeta = true)))
        assertSame(UpdateCheckResult.UpToDate, manualCheckResult(true, response().copy(hasBeta = true)))
    }

    @Test
    fun operationalFailureIsReturnedWithoutASuccessMessage() = runBlocking {
        val failure = IOException("simulated connection failure")
        val result = loadUpdateCheck { throw failure }
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertNull(manualCheckResult(true, result.getOrNull()))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagated() = runBlocking<Unit> {
        loadUpdateCheck { throw CancellationException("cancelled") }
    }

    @Test
    fun playBuildsOrActiveChecksAndDownloadsDoNotStartAnotherCheck() {
        assertTrue(canCheckForUpdates(true, UpdateUiState()))
        assertFalse(canCheckForUpdates(false, UpdateUiState()))
        assertFalse(canCheckForUpdates(true, UpdateUiState(isChecking = true)))
        assertFalse(canCheckForUpdates(true, UpdateUiState(isDownloading = true)))
    }
}
