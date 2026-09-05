package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.model.UpdateStatus

/** Section label used across screens that group settings-like controls (Appearance, Updates, ...). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/**
 * Button + live status for [UpdateStatus] - checks GitHub Releases and, if allowed, downloads and
 * installs a newer build. [KeepScreenOnWhile] keeps the screen awake while a check/download is in
 * flight - both run on a plain coroutine, not a background job, so nothing here survives the app
 * leaving the foreground; keeping the screen on for that whole stretch is what actually prevents
 * that, right up through the moment the caller's ViewModel fires the install prompt.
 *
 * Shown on every screen that offers an "Updates" section (Settings, Manage houses).
 */
@Composable
fun UpdateSection(
    status: UpdateStatus,
    onUpdateClicked: () -> Unit,
    onEnableSideloadingClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val isBusy = status is UpdateStatus.Checking || status is UpdateStatus.Downloading
        KeepScreenOnWhile(keepOn = isBusy)

        Button(onClick = onUpdateClicked, enabled = !isBusy) {
            Text(text = "Check for updates")
        }

        when (status) {
            is UpdateStatus.Idle -> Unit
            is UpdateStatus.Checking -> UpdateStatusRow(text = "Checking for updates…")
            is UpdateStatus.Downloading -> UpdateStatusRow(text = "Downloading update…")
            is UpdateStatus.UpToDate ->
                Text(
                    text = "You're on the latest version (${status.currentVersionName}).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.UpdateAvailable ->
                Text(
                    text = "Version ${status.update.versionName} is available. Tap to update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.Failed ->
                Text(
                    text = "Update check failed: ${status.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            is UpdateStatus.SideloadingBlocked ->
                SideloadingWarning(onActionClick = onEnableSideloadingClicked)
        }
    }
}

@Composable
private fun SideloadingWarning(
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Allow this app to install updates to enable this action.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onActionClick) {
                Text(text = "Allow")
            }
        }
    }
}

@Composable
private fun UpdateStatusRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * Sets [android.view.View.setKeepScreenOn] for as long as [keepOn] is true, and always clears it
 * on dispose - including when this leaves composition entirely (e.g. the user backs out mid
 * download) - so the flag can never get stuck on past the update flow that requested it.
 */
@Composable
private fun KeepScreenOnWhile(keepOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}
