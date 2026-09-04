package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.UpdateAvailability
import java.io.File

/** Boundary between the update-check logic and GitHub Releases (production: `UpdateRepositoryImpl`). */
interface UpdateRepository {
    /**
     * Compares the installed build's version code against GitHub's latest release and returns
     * which [UpdateAvailability] case applies.
     */
    suspend fun checkForUpdate(): DonaResult<UpdateAvailability>

    /** Downloads [update]'s APK to local storage, returning the file it was written to. */
    suspend fun downloadUpdate(update: AppUpdate): DonaResult<File>

    /** Deletes any APK previously written by [downloadUpdate]. A no-op if nothing was downloaded. */
    fun clearDownloadedUpdate()
}
