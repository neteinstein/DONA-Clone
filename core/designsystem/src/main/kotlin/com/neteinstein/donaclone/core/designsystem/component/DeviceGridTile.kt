package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.model.shutterStateLabel

/**
 * Visual/interaction state driving a [DeviceGridTile]'s fill and label — mirrors the Home tab's
 * click-behavior rule table: on/off devices ([Toggle]) fill solid when on, shutters/dimmers
 * ([FillLevel]) fill proportionally to how open/bright they are, sensors ([ReadOnly]) never fill
 * and only ever display a subtitle.
 */
sealed interface DeviceTileVisualState {
    data class Toggle(
        val isOn: Boolean,
    ) : DeviceTileVisualState

    /**
     * [showPercentageLabel] controls whether the subtitle reads "N%" (shutters, where the open
     * amount is the point) or just "On"/"Off" (lights/dimmers — Google Home style: the tile is a
     * light switch first, the exact brightness lives in the device detail screen, not cluttering
     * the Home tab with a second control).
     */
    data class FillLevel(
        val percentage: Int,
        val showPercentageLabel: Boolean = true,
    ) : DeviceTileVisualState

    data class ReadOnly(
        val subtitle: String?,
    ) : DeviceTileVisualState
}

/**
 * A pill-shaped, Google-Home-style device tile: icon + name + state in a horizontal row, tap for
 * the primary action, long-press to open the device detail screen. [onClick] should be a no-op
 * (not null — Compose's `combinedClickable` still needs a click handler) for read-only sensor
 * tiles.
 *
 * When [online] is false the tile is disabled outright (no tap, no long-press, dimmed content)
 * rather than showing a colored online/offline indicator — an unreachable device has no action to
 * offer, so the disabled affordance communicates that more directly than a status dot would.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceGridTile(
    name: String,
    icon: ImageVector,
    online: Boolean,
    visualState: DeviceTileVisualState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOn = visualState is DeviceTileVisualState.Toggle && visualState.isOn
    val containerColor by animateColorAsState(
        targetValue =
            when {
                !online -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isOn -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        label = "device-tile-color",
    )
    val contentColor =
        when {
            !online -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            isOn -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .height(72.dp)
                .combinedClickable(enabled = online, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(percent = 50),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (visualState is DeviceTileVisualState.FillLevel && online) {
                val fillFraction by animateFloatAsState(
                    targetValue = (visualState.percentage / 100f).coerceIn(0f, 1f),
                    label = "device-tile-fill",
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
                val subtitle =
                    when {
                        !online -> "Offline"
                        visualState is DeviceTileVisualState.Toggle -> if (visualState.isOn) "On" else "Off"
                        visualState is DeviceTileVisualState.FillLevel ->
                            if (visualState.showPercentageLabel) {
                                shutterStateLabel(visualState.percentage)
                            } else if (visualState.percentage > 0) {
                                "On"
                            } else {
                                "Off"
                            }
                        visualState is DeviceTileVisualState.ReadOnly -> visualState.subtitle
                        else -> null
                    }
                Column(
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor)
                    }
                }
            }
        }
    }
}
