package com.neteinstein.donaclone.core.domain.repository

import java.io.File

/**
 * Hands a downloaded update APK (see [UpdateRepository.downloadUpdate]) to the platform's package
 * installer, and checks/opens the "install unknown apps" permission that gates sideloading it.
 */
interface UpdateInstaller {
    /** True once the user has allowed this app to install packages from outside the store it came from. */
    fun canInstallPackages(): Boolean

    /** Opens this app's "install unknown apps" toggle in system settings. */
    fun openInstallPermissionSettings()

    /** Launches the system installer for [apkFile]. Requires [canInstallPackages] to already be true. */
    fun installPackage(apkFile: File)
}
