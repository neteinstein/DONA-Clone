package com.neteinstein.donaclone.core.data.update

import android.content.Context
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.UpdateRepository
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.UpdateAvailability
import com.neteinstein.donaclone.core.network.api.GitHubApi
import com.neteinstein.donaclone.core.network.dto.GitHubReleaseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * [UpdateRepository] backed by the public GitHub Releases REST API for this project's own repo —
 * see `.github/workflows/release.yml` for how each release and its signed APK asset are produced.
 *
 * Release tags are `"v<versionName>.<buildNumber>"` (see the release workflow's `release_tag`
 * step) — [AppUpdate.versionCode] is parsed from the tag's trailing numeric segment (the CI run
 * number baked into `versionCode` at build time, see `app/build.gradle.kts`'s `BUILD_NUMBER`),
 * not `versionName`, since this app's `versionName` is static between builds and can't be used to
 * detect whether a newer build exists.
 */
class UpdateRepositoryImpl(
    private val api: GitHubApi,
    private val context: Context,
    private val repoSlug: String,
    private val downloadClient: OkHttpClient,
) : UpdateRepository {
    override suspend fun checkForUpdate(): DonaResult<UpdateAvailability> =
        withContext(Dispatchers.IO) {
            donaResultCatching {
                val update = parseGitHubRelease(api.getLatestRelease(repoSlug))
                if (update.versionCode > currentVersionCode()) {
                    UpdateAvailability.Available(update)
                } else {
                    UpdateAvailability.UpToDate(currentVersionName())
                }
            }
        }

    override suspend fun downloadUpdate(update: AppUpdate): DonaResult<File> =
        withContext(Dispatchers.IO) {
            donaResultCatching {
                val updatesDir = updatesDir()
                // Only one downloaded update is ever "current" - clear out anything left over
                // from a previous check before writing the new one.
                updatesDir.deleteRecursively()
                updatesDir.mkdirs()

                val apkFile = File(updatesDir, "DonaClone-${update.versionName}.apk")
                downloadToFile(url = update.apkDownloadUrl, destination = apkFile)
                apkFile
            }
        }

    override fun clearDownloadedUpdate() {
        updatesDir().deleteRecursively()
    }

    private fun updatesDir(): File = File(context.cacheDir, UPDATE_CACHE_DIR_NAME)

    // getPackageInfo(String, Int) is deprecated in favor of the PackageInfoFlags overload added
    // in API 33, but minSdk is 30 - there's no non-deprecated way to read this below API 33.
    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Int = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    private fun downloadToFile(
        url: String,
        destination: File,
    ) {
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed with HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("APK download had an empty response body")
            destination.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
    }

    private companion object {
        const val UPDATE_CACHE_DIR_NAME = "updates"
    }
}

/**
 * Parses a GitHub "get the latest release" API response into an [AppUpdate]. Kept as a standalone
 * top-level function (rather than a private method) so it's directly unit-testable without
 * mocking [Context] or the network.
 */
internal fun parseGitHubRelease(release: GitHubReleaseDto): AppUpdate {
    val versionName = release.tagName.removePrefix("v")
    val versionCode =
        versionName.substringAfterLast('.').toIntOrNull()
            ?: error("Release tag \"${release.tagName}\" has no trailing build number")

    val apkAsset =
        release.assets.firstOrNull { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
            ?: error("Latest release ($versionName) has no APK attached")

    return AppUpdate(
        versionName = versionName,
        versionCode = versionCode,
        apkDownloadUrl = apkAsset.browserDownloadUrl,
    )
}
