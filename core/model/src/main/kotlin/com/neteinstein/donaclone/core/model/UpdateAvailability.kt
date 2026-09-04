package com.neteinstein.donaclone.core.model

/** Which case applies once `UpdateRepository.checkForUpdate` succeeds — see `DonaResult`. */
sealed class UpdateAvailability {
    /** The installed build is already the latest one published on GitHub Releases. */
    data class UpToDate(val currentVersionName: String) : UpdateAvailability()

    /** GitHub's latest release is newer than the installed build. */
    data class Available(val update: AppUpdate) : UpdateAvailability()
}
