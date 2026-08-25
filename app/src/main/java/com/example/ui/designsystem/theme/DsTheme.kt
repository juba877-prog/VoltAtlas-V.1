package com.example.ui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Extended semantic palette consumed by Ds* components that need
 * Pass / Warning / Fail / Info / Neutral meaning (Material3 has no dedicated
 * semantic slots). This is the sanctioned bridge layer between the central
 * DsColor tokens and the MaterialTheme roles.
 */
data class DsExtendedColors(
    val info: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    val onTinted: Color,          // primary text on tinted pill/chip surfaces
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
)

private val DarkExtendedColors = DsExtendedColors(
    info = DsColor.info,
    success = DsColor.success,
    warning = DsColor.warning,
    danger = DsColor.danger,
    neutral = DsColor.neutral,
    onTinted = DsColor.onSurface,
    surfaceContainer = DsColor.surfaceContainer,
    surfaceContainerHigh = DsColor.surfaceContainerHigh,
)

/**
 * Light-mode companion palette (derived only so the design system is usable in
 * light mode; it is NOT part of the approved token list).
 */
private val LightExtendedColors = DsExtendedColors(
    info = Color(0xFF0369A1),      // WCAG-safe on white
    success = Color(0xFF166534),
    warning = Color(0xFF92400E),
    danger = Color(0xFFB91C1C),
    neutral = Color(0xFF64748B),
    onTinted = Color(0xFF0F172A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE2E8F0),
)

private val DsDarkColorScheme = darkColorScheme(
    primary = DsColor.primary,
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF0F3A6B),
    onPrimaryContainer = Color(0xFFCDDFF0),
    secondary = DsColor.primary,
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1E2A38),
    onSecondaryContainer = Color(0xFF38BDF8),
    tertiary = Color(0xFF94A3B8),
    onTertiary = Color(0xFF0D1117),
    tertiaryContainer = Color(0xFF252E3D),
    onTertiaryContainer = Color(0xFFAEB6C2),
    background = DsColor.background,
    onBackground = DsColor.onSurface,
    surface = DsColor.surface,
    onSurface = DsColor.onSurface,
    surfaceVariant = DsColor.surfaceContainer,
    onSurfaceVariant = DsColor.onSurfaceVariant,
    surfaceContainer = DsColor.surfaceContainer,
    surfaceContainerHigh = DsColor.surfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF252E3D),
    inverseSurface = DsColor.onSurface,
    inverseOnSurface = DsColor.background,
    inversePrimary = Color(0xFF9ECFFF),
    outline = DsColor.outline,
    outlineVariant = DsColor.outlineVariant,
    error = Color(0xFFF0554C),        // MD3 error, kept separate from token danger
    onError = Color(0xFF0D1117),
    errorContainer = Color(0xFF3E1A19),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF3B9EFF),
)

private val DsLightColorScheme = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF64748B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E8F0),
    onTertiaryContainer = Color(0xFF334155),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = Color(0xFF3B9EFF),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF3B9EFF),
)

val LocalDsExtendedColors = staticCompositionLocalOf<DsExtendedColors?> {
    null
}

val MaterialTheme.dsExtendedColors: DsExtendedColors
    @Composable get() {
        val explicit = LocalDsExtendedColors.current
        if (explicit != null) return explicit
        val isDark = colorScheme.surface.luminance() < 0.5f
        return if (isDark) DarkExtendedColors else LightExtendedColors
    }

/**
 * Volt Atlas Design System theme.
 * Wrap your previews / dev galleries (or future re-skins) with this.
 */
@Composable
fun DsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DsDarkColorScheme else DsLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalDsExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DsType,
            shapes = DsShapes,
            content = content
        )
    }
}