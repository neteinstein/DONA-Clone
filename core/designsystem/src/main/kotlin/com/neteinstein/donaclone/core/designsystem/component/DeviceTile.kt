package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun BinaryOutputSwitch(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "switch-track-color",
    )
    Switch(
        checked = isOn,
        onCheckedChange = onToggle,
        modifier = modifier,
        colors = SwitchDefaults.colors(checkedTrackColor = trackColor),
    )
}
