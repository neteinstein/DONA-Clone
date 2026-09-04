package com.neteinstein.donaclone.core.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.neteinstein.donaclone.core.domain.repository.UpdateInstaller
import java.io.File

/**
 * Hands a downloaded APK (see [UpdateRepositoryImpl.downloadUpdate]) to the system Package
 * Installer, and checks/deep-links into the "install unknown apps" (sideloading) permission
 * screen that gates it - the API 26+ replacement for the old device-wide "Unknown sources"
 * toggle, granted per-app instead.
 */
class UpdateInstallerImpl(
    private val context: Context,
) : UpdateInstaller {
    override fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    override fun openInstallPermissionSettings() {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    /**
     * Uses a [FileProvider] `content://` Uri rather than a plain `file://` one: a cache-dir-backed
     * file can't be shared as a raw `file://` Uri with another app (the Package Installer) under
     * this app's targetSdk — that throws `FileUriExposedException` — so the manifest declares a
     * `FileProvider` whose authority matches [FILE_PROVIDER_AUTHORITY_SUFFIX] below, scoped to
     * exactly the cache subdirectory the update APK is downloaded into (see
     * `update_file_paths.xml`, `UpdateRepositoryImpl`).
     */
    override fun installPackage(apkFile: File) {
        val apkUri =
            FileProvider.getUriForFile(context, "${context.packageName}.$FILE_PROVIDER_AUTHORITY_SUFFIX", apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    private companion object {
        // Must exactly match the FileProvider <provider> authority declared in
        // AndroidManifest.xml (that side prefixes it with "${applicationId}.").
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = "update.fileprovider"
    }
}
