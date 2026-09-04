package com.neteinstein.donaclone.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shape of GitHub's "get the latest release" REST response that this client actually needs. */
@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
