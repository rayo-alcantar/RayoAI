package com.rayoai.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): List<GithubRelease>
}

internal suspend fun GithubApiService.getAllReleases(owner: String, repo: String): List<GithubRelease> {
    val result = mutableListOf<GithubRelease>()
    var page = 1
    do {
        val releases = getReleases(owner, repo, page, 100)
        result.addAll(releases)
        page++
    } while (releases.size == 100)
    return result
}
