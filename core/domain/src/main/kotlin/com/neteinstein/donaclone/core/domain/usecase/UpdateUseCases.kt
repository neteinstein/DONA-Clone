package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.UpdateInstaller
import com.neteinstein.donaclone.core.domain.repository.UpdateRepository
import com.neteinstein.donaclone.core.model.AppUpdate
import com.neteinstein.donaclone.core.model.UpdateAvailability
import java.io.File

class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): DonaResult<UpdateAvailability> = repository.checkForUpdate()
}

class DownloadUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(update: AppUpdate): DonaResult<File> = repository.downloadUpdate(update)
}

class ClearDownloadedUpdateUseCase(
    private val repository: UpdateRepository,
) {
    operator fun invoke() = repository.clearDownloadedUpdate()
}

class CanInstallUpdatesUseCase(
    private val installer: UpdateInstaller,
) {
    operator fun invoke(): Boolean = installer.canInstallPackages()
}

class InstallUpdateUseCase(
    private val installer: UpdateInstaller,
) {
    operator fun invoke(apkFile: File) = installer.installPackage(apkFile)
}

class OpenInstallPermissionSettingsUseCase(
    private val installer: UpdateInstaller,
) {
    operator fun invoke() = installer.openInstallPermissionSettings()
}
