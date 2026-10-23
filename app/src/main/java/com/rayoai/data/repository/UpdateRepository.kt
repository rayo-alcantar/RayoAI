package com.rayoai.data.repository

import com.rayoai.BuildConfig
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.data.remote.GithubApiService
import com.rayoai.data.remote.getAllReleases
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.domain.model.UpdateInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val githubApiService: GithubApiService,
    private val updatePreferences: UpdatePreferences
) {
    private val owner = "rayo-alcantar"
    private val repo = "rayoai_for_android"

    data class UpdateCheckResponse(
        val updateInfo: UpdateInfo?,
        val hasStable: Boolean,
        val hasBeta: Boolean,
        val channel: UpdateChannel,
        val hasNewerBeta: Boolean = false
    )

    suspend fun checkForUpdate(): UpdateInfo? = checkForUpdateVerbose().updateInfo

    suspend fun checkForUpdateVerbose(): UpdateCheckResponse {
        val releases = githubApiService.getAllReleases(owner, repo)
        // Read after the request: a channel change while fetching uses the new choice.
        return GithubUpdatePolicy.evaluate(releases, updatePreferences.getUpdateChannel(), BuildConfig.VERSION_NAME)
    }
}
