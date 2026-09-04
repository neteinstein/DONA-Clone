package com.neteinstein.donaclone.core.model

/** A GitHub release newer than the currently installed build, as identified by `UpdateRepository.checkForUpdate`. */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkDownloadUrl: String,
)
