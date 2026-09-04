package com.neteinstein.donaclone.core.network.api

import com.neteinstein.donaclone.core.network.dto.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Boundary to the public GitHub Releases REST API, used to check for and locate newer builds of
 * this app itself — see `.github/workflows/release.yml` for how each release and its signed APK
 * asset are produced. The endpoint is unauthenticated (no token needed to read public release
 * metadata).
 */
interface GitHubApi {
    @GET("repos/{repoSlug}/releases/latest")
    suspend fun getLatestRelease(
        @Path(value = "repoSlug", encoded = true) repoSlug: String,
    ): GitHubReleaseDto
}
