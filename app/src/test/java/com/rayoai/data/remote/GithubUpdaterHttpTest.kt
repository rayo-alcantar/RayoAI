package com.rayoai.data.remote

import com.rayoai.data.repository.GithubUpdatePolicy
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.presentation.ui.updates.loadUpdateCheck
import com.rayoai.presentation.ui.updates.manualCheckResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Real Retrofit/OkHttp/Gson path; the interceptor never connects to a network. */
class GithubUpdaterHttpTest {
    private fun releaseJson(version: String, beta: Boolean) = """{
        "tag_name":"$version", "prerelease":$beta, "draft":false,
        "assets":[{"name":"app-github-release.apk","browser_download_url":"https://github.com/owner/repo/releases/download/$version/app.apk"}]
    }"""

    private fun withService(failSecondPage: Boolean = false, block: suspend (GithubApiService, List<String>) -> Unit) {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/repos/rayo-alcantar/rayoai_for_android/releases", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("per_page"))
            requests += request.url.queryParameter("page")!!
            val first = requests.last() == "1"
            val body = if (first) {
                List(100) { releaseJson("3.3.5-beta", true) }.joinToString(",", "[", "]")
            } else if (failSecondPage) {
                """{"message":"API rate limit exceeded"}"""
            } else {
                "[${releaseJson("3.3.12-stable", false)}]"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (!first && failSecondPage) 403 else 200).message("Synthetic response")
                .body(body.toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.github.com/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(GithubApiService::class.java)
        try {
            runBlocking { block(api, requests) }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun newerStableOnSecondPageReachesAnOptedInBetaUser() = withService { api, requests ->
        val releases = api.getAllReleases("rayo-alcantar", "rayoai_for_android")
        val result = GithubUpdatePolicy.evaluate(releases, UpdateChannel.BETA, "3.3.11")
        assertEquals(listOf("1", "2"), requests)
        assertEquals("3.3.12-stable", result.updateInfo?.version)
        assertNull(manualCheckResult(true, result))
    }

    @Test
    fun forbiddenSecondPageReachesFailureStateRatherThanUpToDate() = withService(failSecondPage = true) { api, requests ->
        val result = loadUpdateCheck {
            GithubUpdatePolicy.evaluate(api.getAllReleases("rayo-alcantar", "rayoai_for_android"), UpdateChannel.BETA, "3.3.11")
        }
        assertEquals(listOf("1", "2"), requests)
        assertTrue(result.isFailure)
        assertEquals(403, (result.exceptionOrNull() as HttpException).code())
        assertNull(manualCheckResult(true, result.getOrNull()))
    }
}
