package com.pbrp.manager

import com.google.gson.annotations.SerializedName

data class DeviceCandidate(val vendor: String, val codename: String)

data class DeviceBuilds(
    @SerializedName("latest") val latest: BuildInfo?,
    @SerializedName("older_builds") val olderBuilds: List<BuildInfo>?
)

data class BuildInfo(
    val version: String,
    @SerializedName("build_type") val buildType: String,
    val date: String,
    @SerializedName("download_link") val downloadLink: String,
    @SerializedName("github_release") val githubLink: String?,
    val changelog: String?,
    val fileName: String? = null
)

// --- ADDED: GitHub API Models ---
data class GithubRelease(
    val assets: List<GithubAsset>
)

data class GithubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    val name: String
)
