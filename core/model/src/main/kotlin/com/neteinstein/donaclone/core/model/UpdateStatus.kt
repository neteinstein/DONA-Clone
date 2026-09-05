package com.neteinstein.donaclone.core.model

/**
 * Presentation state of the in-app "Update to latest" flow (see `CheckForUpdateUseCase` and
 * `DownloadUpdateUseCase`) - shared by every screen that surfaces an "Updates" section (Settings,
 * Manage houses), so it lives here rather than in a single feature module.
 */
sealed class UpdateStatus {
    /** Nothing in flight - the button's normal resting state. */
    data object Idle : UpdateStatus()

    data object Checking : UpdateStatus()

    /** The installed build is already the latest one published on GitHub Releases. */
    data class UpToDate(val currentVersionName: String) : UpdateStatus()

    /** A newer build exists and is ready to be downloaded/installed on button tap. */
    data class UpdateAvailable(val update: AppUpdate) : UpdateStatus()

    data object Downloading : UpdateStatus()

    /**
     * A newer release exists, but the OS won't let this app install it yet. The "Updates" section
     * shows a warning banner whose action opens the system "install unknown apps" page for this
     * app; the user is expected to tap "Update to latest" again afterwards, which re-checks and
     * proceeds automatically now that the OS allows it.
     */
    data object SideloadingBlocked : UpdateStatus()

    data class Failed(val message: String) : UpdateStatus()
}

fun UpdateAvailability.toUpdateStatus(): UpdateStatus =
    when (this) {
        is UpdateAvailability.UpToDate -> UpdateStatus.UpToDate(currentVersionName)
        is UpdateAvailability.Available -> UpdateStatus.UpdateAvailable(update)
    }
