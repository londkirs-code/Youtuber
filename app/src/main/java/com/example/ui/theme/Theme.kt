package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianColorScheme = darkColorScheme(
    primary = ScarletRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonGlow.copy(alpha = 0.25f),
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = ObsidianDark,
    secondaryContainer = NeonCyan.copy(alpha = 0.2f),
    onSecondaryContainer = Color.White,
    background = ObsidianDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = LiveBadgeColor,
    onError = Color.White
)

private val AmoledColorScheme = darkColorScheme(
    primary = ScarletRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonGlow.copy(alpha = 0.25f),
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = NeonCyan.copy(alpha = 0.2f),
    onSecondaryContainer = Color.White,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = PureBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = LiveBadgeColor,
    onError = Color.White
)

@Composable
fun TuneVaultTheme(
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isAmoled) AmoledColorScheme else ObsidianColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
