package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = JudicialGoldBright,
    onPrimary = JudicialNavyDark,
    primaryContainer = JudicialNavyLight,
    onPrimaryContainer = IvoryText,
    secondary = JudicialGold,
    onSecondary = Color.White,
    secondaryContainer = MidnightSurfaceElevated,
    onSecondaryContainer = JudicialGoldBright,
    tertiary = VerifiedEmerald,
    onTertiary = Color.White,
    background = MidnightBackground,
    onBackground = IvoryText,
    surface = MidnightSurface,
    onSurface = IvoryText,
    surfaceVariant = MidnightSurfaceElevated,
    onSurfaceVariant = SlateTextDark,
    outline = OutlineDark,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val LightColorScheme = lightColorScheme(
    primary = JudicialNavy,
    onPrimary = Color.White,
    primaryContainer = JudicialGoldSoft,
    onPrimaryContainer = JudicialNavyDark,
    secondary = JudicialGold,
    onSecondary = Color.White,
    secondaryContainer = IvorySurfaceVariant,
    onSecondaryContainer = CharcoalText,
    tertiary = VerifiedEmerald,
    onTertiary = Color.White,
    background = IvoryBackground,
    onBackground = CharcoalText,
    surface = IvorySurface,
    onSurface = CharcoalText,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = OutlineWarm,
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D),
)

@Composable
fun RedatDagnaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
