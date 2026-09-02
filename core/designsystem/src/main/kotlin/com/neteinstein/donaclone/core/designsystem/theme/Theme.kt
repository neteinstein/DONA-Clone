package com.neteinstein.donaclone.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DonaLightColorScheme = lightColorScheme(
    primary = DonaTeal40,
    onPrimary = DonaNeutral99,
    primaryContainer = DonaTeal90,
    onPrimaryContainer = DonaTeal10,
    secondary = DonaAmber40,
    onSecondary = DonaNeutral99,
    secondaryContainer = DonaAmber90,
    onSecondaryContainer = DonaTeal10,
    error = DonaRed40,
    errorContainer = DonaRed90,
    background = DonaNeutral99,
    onBackground = DonaNeutral10,
    surface = DonaNeutral99,
    onSurface = DonaNeutral10,
    surfaceVariant = DonaNeutral95,
    onSurfaceVariant = DonaNeutral20,
)

private val DonaDarkColorScheme = darkColorScheme(
    primary = DonaTeal80,
    onPrimary = DonaTeal20,
    primaryContainer = DonaTeal30,
    onPrimaryContainer = DonaTeal90,
    secondary = DonaAmber80,
    onSecondary = DonaTeal20,
    secondaryContainer = DonaAmber40,
    onSecondaryContainer = DonaAmber90,
    error = DonaRed80,
    errorContainer = DonaRed40,
    background = DonaNeutral10,
    onBackground = DonaNeutral90,
    surface = DonaNeutral10,
    onSurface = DonaNeutral90,
    surfaceVariant = DonaNeutral20,
    onSurfaceVariant = DonaNeutral90,
)

/**
 * App-wide Material3 theme. Uses dynamic (wallpaper-derived) color on Android 12+ when
 * [useDynamicColor] is true, otherwise falls back to the DONA brand teal palette in both
 * light and dark variants.
 */
@Composable
fun DonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DonaDarkColorScheme
        else -> DonaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DonaTypography,
        shapes = DonaShapes,
        content = content,
    )
}
