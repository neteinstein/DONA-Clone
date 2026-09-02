package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.neteinstein.donaclone.core.designsystem.theme.DonaGrayOffline
import com.neteinstein.donaclone.core.designsystem.theme.DonaGreenOnline

/** A small colored dot that gently pulses while [online] is true, to draw the eye to live state. */
@Composable
fun StatusDot(
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "status-dot-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (online) 0.4f else 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "status-dot-alpha",
    )

    Box(
        modifier =
            modifier
                .size(10.dp)
                .clip(CircleShape)
                .alpha(if (online) alpha else 1f)
                .background(if (online) DonaGreenOnline else DonaGrayOffline),
    )
}
