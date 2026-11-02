package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemeColors(
    val obsidianDark: Color,      // Main window & screen background
    val surfaceDark: Color,       // Bar & container surface
    val surfaceCard: Color,       // Card / dialog background
    val surfaceHighlight: Color,  // Hover / elevated highlight
    val borderDark: Color,        // Dividers and outlines
    val textPrimary: Color,       // Primary text & primary icons
    val textSecondary: Color,     // Secondary text & muted labels
    val textTertiary: Color,      // Disabled / caption text
    val pureBlack: Color,
    val neonCyan: Color,          // Accent cyan/blue (adaptive to theme)
    val scarletRed: Color = Color(0xFFE50914),
    val crimsonGlow: Color = Color(0xFFFF2A4B),
    val liveBadgeColor: Color = Color(0xFFFF1744),
    val successGreen: Color = Color(0xFF10B981),
    val warningAmber: Color = Color(0xFFF59E0B)
)

val DarkThemeColors = AppThemeColors(
    obsidianDark = Color(0xFF08090C),
    surfaceDark = Color(0xFF13161F),
    surfaceCard = Color(0xFF1A1E29),
    surfaceHighlight = Color(0xFF262C3A),
    borderDark = Color(0xFF2C3242),
    textPrimary = Color(0xFFF8F9FA),
    textSecondary = Color(0xFFE2E8F0),   // High contrast crisp text on dark
    textTertiary = Color(0xFFCBD5E1),    // Clear tertiary text
    pureBlack = Color(0xFF000000),
    neonCyan = Color(0xFF00E5FF)
)

val AmoledThemeColors = AppThemeColors(
    obsidianDark = Color(0xFF000000),
    surfaceDark = Color(0xFF0A0C10),
    surfaceCard = Color(0xFF12151D),
    surfaceHighlight = Color(0xFF1E222D),
    borderDark = Color(0xFF222736),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFE2E8F0),
    textTertiary = Color(0xFFCBD5E1),
    pureBlack = Color(0xFF000000),
    neonCyan = Color(0xFF00E5FF)
)

val LightThemeColors = AppThemeColors(
    obsidianDark = Color(0xFFF1F3F5),      // Modern distinct light gray background
    surfaceDark = Color(0xFFFFFFFF),       // Crisp white navigation & app bars
    surfaceCard = Color(0xFFFFFFFF),       // Pure white cards
    surfaceHighlight = Color(0xFFE2E8F0),  // Interactive pressed / pill surface
    borderDark = Color(0xFFCBD5E1),        // Clear, crisp visible light borders (Slate-300)
    textPrimary = Color(0xFF0F172A),       // Slate-900 high contrast deep text
    textSecondary = Color(0xFF475569),     // Slate-600 legible secondary text
    textTertiary = Color(0xFF64748B),      // Slate-500 muted text
    pureBlack = Color(0xFFFFFFFF),
    neonCyan = Color(0xFF0284C7)           // Sky-700 / Deep Cobalt Blue (high contrast on white)
)

val LocalAppColors = staticCompositionLocalOf { DarkThemeColors }

private val DarkM3ColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF2A4B).copy(alpha = 0.25f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00E5FF),
    onSecondary = DarkThemeColors.obsidianDark,
    secondaryContainer = Color(0xFF00E5FF).copy(alpha = 0.2f),
    onSecondaryContainer = Color.White,
    background = DarkThemeColors.obsidianDark,
    onBackground = DarkThemeColors.textPrimary,
    surface = DarkThemeColors.surfaceDark,
    onSurface = DarkThemeColors.textPrimary,
    surfaceVariant = DarkThemeColors.surfaceCard,
    onSurfaceVariant = DarkThemeColors.textSecondary,
    outline = DarkThemeColors.borderDark,
    error = Color(0xFFFF1744),
    onError = Color.White
)

private val AmoledM3ColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF2A4B).copy(alpha = 0.25f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00E5FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00E5FF).copy(alpha = 0.2f),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = AmoledThemeColors.textPrimary,
    surface = Color.Black,
    onSurface = AmoledThemeColors.textPrimary,
    surfaceVariant = AmoledThemeColors.surfaceDark,
    onSurfaceVariant = AmoledThemeColors.textSecondary,
    outline = AmoledThemeColors.borderDark,
    error = Color(0xFFFF1744),
    onError = Color.White
)

private val LightM3ColorScheme = lightColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE50914).copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFFE50914),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    background = LightThemeColors.obsidianDark,
    onBackground = LightThemeColors.textPrimary,
    surface = LightThemeColors.surfaceDark,
    onSurface = LightThemeColors.textPrimary,
    surfaceVariant = LightThemeColors.surfaceCard,
    onSurfaceVariant = LightThemeColors.textSecondary,
    outline = LightThemeColors.borderDark,
    error = Color(0xFFFF1744),
    onError = Color.White
)

@Composable
fun TuneVaultTheme(
    isDarkMode: Boolean = true,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val appColors = when {
        !isDarkMode -> LightThemeColors
        isAmoled -> AmoledThemeColors
        else -> DarkThemeColors
    }

    val colorScheme = when {
        !isDarkMode -> LightM3ColorScheme
        isAmoled -> AmoledM3ColorScheme
        else -> DarkM3ColorScheme
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
