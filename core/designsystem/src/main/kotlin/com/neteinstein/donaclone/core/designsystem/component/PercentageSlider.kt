package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A 0-100 slider for dimmer/shutter percentage, showing live feedback while dragging and only
 * committing [onValueChangeFinished] once the user lifts their finger — matching how the hub
 * expects a single `action:2` command with the final percentage, not one per pixel dragged.
 */
@Composable
fun PercentageSlider(
    percentage: Int,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var localValue by remember(percentage) { mutableFloatStateOf(percentage.toFloat()) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue.toInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = "${localValue.toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(40.dp),
        )
    }
}
