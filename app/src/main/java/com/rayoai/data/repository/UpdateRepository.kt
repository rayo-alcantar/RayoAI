package com.rayoai.data.repository

import com.rayoai.BuildConfig
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.data.remote.GithubApiService
import com.rayoai.data.remote.GithubRelease
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

    suspend fun checkForUpdate(): UpdateInfo? {
        val releases = githubApiService.getReleases(owner, repo)
        val channel = updatePreferences.getUpdateChannel()
        val candidate = selectCandidate(releases, channel) ?: return null
        val candidateVersion = candidate.tagName
        val currentVersion = BuildConfig.VERSION_NAME
        if (compareVersions(candidateVersion, currentVersion) <= 0) {
            return null
        }
        val apkUrl = candidate.assets.firstOrNull { asset ->
            asset.name?.endsWith(".apk", ignoreCase = true) == true
        }?.browserDownloadUrl ?: return null
        return UpdateInfo(
            version = candidateVersion,
            changelog = candidate.body?.trim().orEmpty(),
            apkUrl = apkUrl
        )
    }

    private fun selectCandidate(
        releases: List<GithubRelease>,
        channel: UpdateChannel
    ): GithubRelease? {
        val valid = releases.filter { release ->
            !release.draft && release.assets.any { it.name?.endsWith(".apk", ignoreCase = true) == true }
        }
        val stable = valid.filter { !it.isPrerelease }
        val beta = valid.filter { it.isPrerelease }
        val bestStable = bestRelease(stable)
        val bestBeta = bestRelease(beta)
        return when (channel) {
            UpdateChannel.STABLE -> bestStable
            UpdateChannel.BETA -> bestBeta
            UpdateChannel.ALL -> selectNewest(bestStable, bestBeta)
        }
    }

    private fun bestRelease(releases: List<GithubRelease>): GithubRelease? {
        return releases.maxWithOrNull { a, b ->
            compareVersions(a.tagName, b.tagName)
        }
    }

    private fun selectNewest(
        stable: GithubRelease?,
        beta: GithubRelease?
    ): GithubRelease? {
        if (stable == null) return beta
        if (beta == null) return stable
        return if (compareVersions(stable.tagName, beta.tagName) >= 0) stable else beta
    }

    private fun compareVersions(first: String, second: String): Int {
        val firstParts = extractVersionParts(first)
        val secondParts = extractVersionParts(second)
        val max = maxOf(firstParts.size, secondParts.size)
        for (index in 0 until max) {
            val a = firstParts.getOrElse(index) { 0 }
            val b = secondParts.getOrElse(index) { 0 }
            if (a != b) {
                return a.compareTo(b)
            }
        }
        return 0
    }

    private fun extractVersionParts(input: String): List<Int> {
        return Regex("\\d+").findAll(input).map { it.value.toInt() }.toList()
    }
}
