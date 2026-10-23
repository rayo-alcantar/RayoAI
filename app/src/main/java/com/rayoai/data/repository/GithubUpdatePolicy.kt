package com.rayoai.data.repository

import com.rayoai.data.remote.GithubAsset
import com.rayoai.data.remote.GithubRelease
import com.rayoai.domain.model.AppVersion
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.domain.model.UpdateInfo

/** Pure selection logic shared by the repository and its JVM regression tests. */
internal object GithubUpdatePolicy {
    private fun GithubAsset.isDownloadableApk(): Boolean =
        name?.endsWith(".apk", ignoreCase = true) == true && !browserDownloadUrl.isNullOrBlank()

    fun evaluate(releases: List<GithubRelease>, selectedChannel: UpdateChannel, currentVersion: String): UpdateRepository.UpdateCheckResponse {
        val current = requireNotNull(AppVersion.parse(currentVersion)) { "Invalid installed app version." }
        val channel = selectedChannel.canonical
        val valid = releases.filter { release ->
            !release.draft && AppVersion.parse(release.tagName) != null && release.assets.any { it.isDownloadableApk() }
        }
        val candidates = valid.filter { channel != UpdateChannel.STABLE || !it.isPrerelease }
        val candidate = candidates.maxWithOrNull(
            compareBy<GithubRelease> { AppVersion.parse(it.tagName)!! }.thenBy { !it.isPrerelease }
        )
        val update = candidate?.takeIf { AppVersion.parse(it.tagName)!! > current }?.let {
            UpdateInfo(
                version = it.tagName,
                changelog = it.body?.trim().orEmpty(),
                apkUrl = it.assets.first { asset -> asset.isDownloadableApk() }.browserDownloadUrl!!
            )
        }
        return UpdateRepository.UpdateCheckResponse(
            updateInfo = update,
            hasStable = valid.any { !it.isPrerelease },
            hasBeta = valid.any { it.isPrerelease },
            channel = channel,
            hasNewerBeta = valid.any { it.isPrerelease && AppVersion.parse(it.tagName)!! > current }
        )
    }
}
