package com.rayoai.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class GithubReleasePagingTest {
    private fun releases(count: Int, prefix: String = "3") = List(count) { GithubRelease("$prefix.0.$it-stable", isPrerelease = false) }

    @Test
    fun readsEveryFullPageBeforeReturning() = runBlocking {
        val pages = listOf(releases(100), releases(100, "4"), releases(1, "5"))
        val requests = mutableListOf<Int>()
        val service = object : GithubApiService {
            override suspend fun getReleases(owner: String, repo: String, page: Int, perPage: Int): List<GithubRelease> {
                assertEquals(100, perPage)
                assertEquals("rayo-alcantar", owner)
                requests += page
                return pages[page - 1]
            }
        }
        val result = service.getAllReleases("rayo-alcantar", "rayoai_for_android")
        assertEquals(listOf(1, 2, 3), requests)
        assertEquals(201, result.size)
        assertEquals("5.0.0-stable", result.last().tagName)
    }

    @Test
    fun oneShortOrEmptyPageDoesNotRequestMore() = runBlocking {
        for (count in listOf(0, 20, 99)) {
            var calls = 0
            val service = object : GithubApiService {
                override suspend fun getReleases(owner: String, repo: String, page: Int, perPage: Int): List<GithubRelease> {
                    calls++
                    return releases(count)
                }
            }
            assertEquals(count, service.getAllReleases("owner", "repo").size)
            assertEquals(1, calls)
        }
    }

    @Test(expected = IOException::class)
    fun laterPageFailureDoesNotReturnAPartialSuccessfulList() = runBlocking<Unit> {
        val service = object : GithubApiService {
            override suspend fun getReleases(owner: String, repo: String, page: Int, perPage: Int): List<GithubRelease> {
                if (page == 2) throw IOException("simulated page failure")
                return releases(100)
            }
        }
        service.getAllReleases("owner", "repo")
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowedByTheLoader() = runBlocking<Unit> {
        val service = object : GithubApiService {
            override suspend fun getReleases(owner: String, repo: String, page: Int, perPage: Int): List<GithubRelease> {
                throw CancellationException("cancelled")
            }
        }
        service.getAllReleases("owner", "repo")
    }
}
