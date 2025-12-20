package com.rayoai.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GithubRelease>
}
