package com.rayoai.data.remote

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("prerelease")
    val isPrerelease: Boolean,
    val draft: Boolean,
    val assets: List<GithubAsset>
)

data class GithubAsset(
    val name: String?,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String?
)
