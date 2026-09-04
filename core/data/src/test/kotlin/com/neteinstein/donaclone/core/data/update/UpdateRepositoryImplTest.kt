package com.neteinstein.donaclone.core.data.update

import com.neteinstein.donaclone.core.network.dto.GitHubReleaseAssetDto
import com.neteinstein.donaclone.core.network.dto.GitHubReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateRepositoryImplTest {
    @Test
    fun `parses version and download url from the tag and apk asset`() {
        val release =
            GitHubReleaseDto(
                tagName = "v0.1.0.123",
                assets =
                    listOf(
                        GitHubReleaseAssetDto(
                            name = "DonaClone-0.1.0.123.apk",
                            browserDownloadUrl = "https://example.com/DonaClone-0.1.0.123.apk",
                        ),
                    ),
            )

        val update = parseGitHubRelease(release)

        assertEquals("0.1.0.123", update.versionName)
        assertEquals(123, update.versionCode)
        assertEquals("https://example.com/DonaClone-0.1.0.123.apk", update.apkDownloadUrl)
    }

    @Test
    fun `picks the apk asset among other release assets`() {
        val release =
            GitHubReleaseDto(
                tagName = "v0.1.0.456",
                assets =
                    listOf(
                        GitHubReleaseAssetDto(name = "checksums.txt", browserDownloadUrl = "https://example.com/checksums.txt"),
                        GitHubReleaseAssetDto(name = "DonaClone-0.1.0.456.apk", browserDownloadUrl = "https://example.com/app.apk"),
                    ),
            )

        val update = parseGitHubRelease(release)

        assertEquals("https://example.com/app.apk", update.apkDownloadUrl)
    }

    @Test
    fun `throws when the latest release has no apk asset`() {
        val release =
            GitHubReleaseDto(
                tagName = "v0.1.0.789",
                assets = listOf(GitHubReleaseAssetDto(name = "checksums.txt", browserDownloadUrl = "https://example.com/checksums.txt")),
            )

        assertThrows(IllegalStateException::class.java) { parseGitHubRelease(release) }
    }

    @Test
    fun `throws when the tag has no trailing build number`() {
        val release =
            GitHubReleaseDto(
                tagName = "v0.1.0-beta",
                assets = listOf(GitHubReleaseAssetDto(name = "DonaClone.apk", browserDownloadUrl = "https://example.com/app.apk")),
            )

        assertThrows(IllegalStateException::class.java) { parseGitHubRelease(release) }
    }
}
