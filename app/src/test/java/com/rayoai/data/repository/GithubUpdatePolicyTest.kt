package com.rayoai.data.repository

import com.rayoai.data.remote.GithubAsset
import com.rayoai.data.remote.GithubRelease
import com.rayoai.domain.model.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUpdatePolicyTest {
    private fun release(version: String, beta: Boolean = false, draft: Boolean = false) = GithubRelease(
        tagName = version, isPrerelease = beta, draft = draft, body = "  Release notes  ",
        assets = listOf(GithubAsset("app-github-release.apk", "https://github.com/owner/repo/releases/download/$version/app.apk"))
    )

    @Test
    fun betaOptInReceivesNewerStableEvenWhenTheNewestBetaIsOlder() {
        val result = GithubUpdatePolicy.evaluate(
            listOf(release("3.3.5-beta", beta = true), release("3.3.11-stable")), UpdateChannel.BETA, "3.3.8"
        )
        assertEquals("3.3.11-stable", result.updateInfo?.version)
    }

    @Test
    fun stableDoesNotOptUsersIntoABeta() {
        val result = GithubUpdatePolicy.evaluate(
            listOf(release("3.3.11-stable"), release("3.3.12-beta", beta = true)), UpdateChannel.STABLE, "3.3.11"
        )
        assertNull(result.updateInfo)
        assertTrue(result.hasStable)
        assertTrue(result.hasNewerBeta)
    }

    @Test
    fun betaAndLegacyAllSelectTheNewestOfBothKinds() {
        for (channel in listOf(UpdateChannel.BETA, UpdateChannel.ALL)) {
            val result = GithubUpdatePolicy.evaluate(
                listOf(release("3.3.11-stable"), release("3.3.12-beta", beta = true)), channel, "3.3.11"
            )
            assertEquals("3.3.12-beta", result.updateInfo?.version)
            assertEquals(UpdateChannel.BETA, result.channel)
        }
    }

    @Test
    fun stableWinsEqualNumericVersionRegardlessOfApiOrder() {
        val releases = listOf(release("3.3.12-beta", beta = true), release("3.3.12-stable"))
        for (ordered in listOf(releases, releases.reversed())) {
            assertEquals("3.3.12-stable", GithubUpdatePolicy.evaluate(ordered, UpdateChannel.BETA, "3.3.11").updateInfo?.version)
        }
    }

    @Test
    fun doesNotOfferEqualOrOlderVersions() {
        for (current in listOf("3.3.12", "3.3.13", "3.3.12-beta")) {
            assertNull(GithubUpdatePolicy.evaluate(listOf(release("3.3.12-stable")), UpdateChannel.BETA, current).updateInfo)
        }
    }

    @Test
    fun invalidNewerAssetsOrTagsDoNotHideALegitimateUpdate() {
        val invalid = listOf(
            release("9.0.0-stable", draft = true),
            release("8.0.0-stable").copy(assets = emptyList()),
            release("7.0.0-stable").copy(assets = listOf(GithubAsset("app.apk", null))),
            release("6.0.0-stable").copy(assets = listOf(GithubAsset("app.apk", "  "))),
            release("5.0.0-stable").copy(assets = listOf(GithubAsset("app.aab", "https://github.com/app.aab"))),
            release("build-999")
        )
        val result = GithubUpdatePolicy.evaluate(invalid + release("3.3.12-stable"), UpdateChannel.STABLE, "3.3.11")
        assertEquals("3.3.12-stable", result.updateInfo?.version)
        assertEquals("Release notes", result.updateInfo?.changelog)
    }

    @Test
    fun selectsAUsableApkIfAnotherApkAssetHasNoUrl() {
        val valid = release("3.3.12-stable")
        val candidate = valid.copy(assets = listOf(GithubAsset("broken.apk", null)) + valid.assets)
        assertEquals(valid.assets.first().browserDownloadUrl,
            GithubUpdatePolicy.evaluate(listOf(candidate), UpdateChannel.STABLE, "3.3.11").updateInfo?.apkUrl)
    }

    @Test
    fun oldBetaDoesNotProduceANewerBetaHint() {
        val result = GithubUpdatePolicy.evaluate(listOf(release("3.3.5-beta", beta = true)), UpdateChannel.STABLE, "3.3.11")
        assertTrue(result.hasBeta)
        assertFalse(result.hasNewerBeta)
        assertNull(result.updateInfo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedInstalledVersionFailsInsteadOfClaimingUpToDate() {
        GithubUpdatePolicy.evaluate(listOf(release("3.3.12-stable")), UpdateChannel.STABLE, "unknown")
    }
}
