package com.rayoai.data.remote

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("prerelease")
    val isPrerelease: Boolean,
    @SerializedName("draft")
    val draft: Boolean = false,
    @SerializedName("assets")
    val assets: List<GithubAsset> = emptyList()
)

data class GithubAsset(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String? = null
)
