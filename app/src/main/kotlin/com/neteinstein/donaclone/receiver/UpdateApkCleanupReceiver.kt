package com.neteinstein.donaclone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neteinstein.donaclone.core.domain.repository.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Deletes the APK `UpdateRepositoryImpl` downloaded to `context.cacheDir` once this app finishes
 * being updated through it, so it doesn't sit there wasting space indefinitely.
 *
 * Listens for [Intent.ACTION_MY_PACKAGE_REPLACED] specifically: it's one of the few broadcasts
 * exempted from Android 8+'s implicit-broadcast background restrictions (delivered only to the
 * app that was just updated, and only via a manifest-declared receiver like this one), making it
 * the only reliable signal for "the update this app itself downloaded just finished installing".
 * Deleting the file any earlier - e.g. right after firing the install `Intent` in
 * `UpdateInstallerImpl.installPackage` - would be unsafe, since the system Package Installer may
 * still be reading it via the `FileProvider` `content://` Uri at that point.
 */
class UpdateApkCleanupReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val updateRepository: UpdateRepository by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateRepository.clearDownloadedUpdate()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
