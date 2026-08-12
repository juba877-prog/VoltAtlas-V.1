package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalThemeIsDark = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = PrimaryCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E2A38),
    onSecondaryContainer = PrimaryCyan,
    tertiary = StatusPass,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0F382A),
    onTertiaryContainer = StatusPass,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLow = BackgroundDeep,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = Color(0xFF252E3D),
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    inversePrimary = LightPrimaryBlue,
    outline = BorderColor,
    outlineVariant = Color(0xFF202734),
    error = SleekFail,
    errorContainer = Color(0xFF3E1A19),
    onError = Color.White,
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = PrimaryBlue
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightPrimaryCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F1F8),
    onSecondaryContainer = LightPrimaryCyan,
    tertiary = StatusPass,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1F2E2),
    onTertiaryContainer = StatusPass,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightBackgroundDeep,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFCBD5E1),
    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightBackground,
    inversePrimary = PrimaryBlue,
    outline = LightBorderSubtle,
    outlineVariant = LightDivider,
    error = SleekFail,
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
    surfaceTint = LightPrimaryBlue
)

val VoltAtlasMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(VoltAtlasShapes.small),
    medium = RoundedCornerShape(VoltAtlasShapes.medium),
    large = RoundedCornerShape(VoltAtlasShapes.large),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoltAtlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default to preserve status semantics
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = VoltAtlasMaterialShapes,
            content = content
        )
    }
}

