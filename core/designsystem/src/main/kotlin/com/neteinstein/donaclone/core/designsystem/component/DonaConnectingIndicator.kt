package com.neteinstein.donaclone.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The launcher icon, animated: the house sits still while its three wifi arcs light up in sequence,
 * inner to outer, on a loop — the icon's own connectivity motif read as "reaching your hub".
 *
 * The glyph is redrawn from the same `pathData` as `ic_launcher_foreground.xml` rather than loaded
 * as a drawable, because `core:designsystem` carries no `res/` directory and because animating each
 * arc separately needs them as separate paths.
 */
@Composable
fun DonaConnectingIcon(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    val transition = rememberInfiniteTransition(label = "dona-connecting")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = ARC_PATHS.size + 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "dona-connecting-phase",
    )

    Canvas(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 24)),
    ) {
        drawRect(color = DONA_GREEN)
        val scaleFactor = this.size.minDimension / ICON_VIEWPORT

        scale(scale = scaleFactor, pivot = Offset.Zero) {
            drawGlyphPath(HOUSE_PATH, strokeWidth = HOUSE_STROKE_WIDTH, alpha = 1f)
            ARC_PATHS.forEachIndexed { index, pathData ->
                drawGlyphPath(pathData, strokeWidth = ARC_STROKE_WIDTH, alpha = arcAlpha(phase, index))
            }
        }
    }
}

/** Full-screen "connecting to the hub" state: the animated icon plus a caption. Callers overlay it
 * on whatever screen kicked the login off, so the form underneath stays out of the way. */
@Composable
fun DonaConnectingIndicator(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DonaConnectingIcon()
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/** Each arc fades up as [phase] sweeps past its index and holds until the loop restarts, so the
 * three of them fill in one after another like a signal locking on. */
private fun arcAlpha(
    phase: Float,
    index: Int,
): Float = (phase - index).coerceIn(0f, 1f)

private fun DrawScope.drawGlyphPath(
    pathData: String,
    strokeWidth: Float,
    alpha: Float,
) {
    if (alpha <= 0f) return
    drawPath(
        path = pathData.toGlyphPath(),
        color = Color.White,
        alpha = alpha,
        style =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
    )
}

private fun String.toGlyphPath(): Path = PathParser().parsePathString(this).toPath()

private val DONA_GREEN = Color(0xFF00A651)

private const val ICON_VIEWPORT = 108f
private const val HOUSE_STROKE_WIDTH = 5f
private const val ARC_STROKE_WIDTH = 3f

/** Verbatim from `app/src/main/res/drawable/ic_launcher_foreground.xml`. */
private const val HOUSE_PATH = "M48,78 L48,60 L60,60 L60,78 L75,78 L75,54 L84,54 L54,27 L24,54 L33,54 L33,78 Z"

private val ARC_PATHS =
    listOf(
        "M70,28 A6,6 0 0 1 76,34",
        "M70,22 A12,12 0 0 1 82,34",
        "M70,16 A18,18 0 0 1 88,34",
    )
