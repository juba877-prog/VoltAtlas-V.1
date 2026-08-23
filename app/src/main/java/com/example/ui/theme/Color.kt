package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// VoltAtlas Single Source of Truth Color Design Tokens (Dark Theme Palette)
val DarkBackground = Color(0xFF0D1117)
val DarkCardBg = Color(0xFF161B22)
val SurfaceElevated = Color(0xFF1E2530)
val BorderColor = Color(0xFF2A3341)

val SleekPrimary = Color(0xFF3B9EFF)

val SleekPass = Color(0xFF3DD68C)
val SleekWarning = Color(0xFFF5A623)
val SleekFail = Color(0xFFF0554C)
val SleekSecondary = Color(0xFF3B9EFF)
val StatusNeutral = Color(0xFF94A3B8)

val ThemeTextColor = Color(0xFFF0F2F5)
val SlateTextSecondary = Color(0xFFAEB6C2)
val TextLow = Color(0xFF6B7684)

// Legacy alias mappings for components
val BackgroundDeep = DarkBackground
val Background = DarkBackground
val Surface = DarkCardBg
val SurfaceHigh = SurfaceElevated
val SurfaceSelected = Color(0xFF1E2530)

val PrimaryBlue = SleekPrimary
val PrimaryCyan = Color(0xFF35A5FF)
val PrimaryContainer = Color(0xFF1A304D)

val TextPrimary = ThemeTextColor
val TextSecondary = SlateTextSecondary
val TextTertiary = TextLow
val TextDisabled = Color(0xFF4A5463)

val StatusPass = SleekPass
val StatusWarning = SleekWarning
val StatusFail = SleekFail
val StatusReview = SleekWarning
val StatusInfo = SleekPrimary
val StatusMissing = TextLow

val BorderSubtle = BorderColor
val BorderStrong = Color(0xFF364356)
val Divider = BorderColor

// Named tokens for UI components
val SlateBorderDark = Color(0xFF2D3748)
val SlateBorderLight = Color(0xFF94A3B8)
val SelectedTabContainerDark = Color(0xFF1E204A)
val SelectedTabContainerLight = Color(0xFFE0E7FF)

// Light Theme Palette (Daytime Workspace/Printing)
val LightBackgroundDeep = Color(0xFFF1F5F9)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFE2E8F0)
val LightSurfaceSelected = Color(0xFFE0F2FE)

val LightPrimaryBlue = Color(0xFF0284C7)
val LightPrimaryCyan = Color(0xFF0EA5E9)
val LightPrimaryContainer = Color(0xFFE0F2FE)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF64748B)
val LightTextDisabled = Color(0xFF94A3B8)

val LightBorderSubtle = Color(0xFF94A3B8)
val LightBorderStrong = Color(0xFF64748B)
val LightDivider = Color(0xFFCBD5E1)
val StatusNeutralLight = Color(0xFF64748B)

val ThemeBackground: Color
    @Composable get() = if (LocalThemeIsDark.current) DarkBackground else LightBackgroundDeep

val ThemeSurface: Color
    @Composable get() = if (LocalThemeIsDark.current) DarkCardBg else LightSurface

val ThemeSurfaceElevated: Color
    @Composable get() = if (LocalThemeIsDark.current) SurfaceElevated else LightSurfaceHigh

val ThemeBorderSubtle: Color
    @Composable get() = if (LocalThemeIsDark.current) BorderColor else LightBorderSubtle

val ThemeBorderStrong: Color
    @Composable get() = if (LocalThemeIsDark.current) Color(0xFF364356) else LightBorderStrong

val ThemePrimary: Color
    @Composable get() = if (LocalThemeIsDark.current) SleekPrimary else LightPrimaryBlue

val ThemeTextPrimary: Color
    @Composable get() = if (LocalThemeIsDark.current) ThemeTextColor else LightTextPrimary

val ThemeTextSecondary: Color
    @Composable get() = if (LocalThemeIsDark.current) SlateTextSecondary else LightTextSecondary

val ThemeTextTertiary: Color
    @Composable get() = if (LocalThemeIsDark.current) TextLow else LightTextTertiary

val ThemeTextDisabled: Color
    @Composable get() = if (LocalThemeIsDark.current) Color(0xFF4A5463) else LightTextDisabled

val ThemeStatusNeutral: Color
    @Composable get() = if (LocalThemeIsDark.current) StatusNeutral else StatusNeutralLight

// VoltAtlas Engineering & Brand Semantic Tokens
val BrandPrimary: Color
    @Composable get() = ThemePrimary

val BrandAccent: Color
    @Composable get() = if (LocalThemeIsDark.current) PrimaryCyan else LightPrimaryCyan

val EngineeringValueColor: Color
    @Composable get() = ThemeTextPrimary

val EngineeringSuccess: Color
    @Composable get() = StatusPass

val EngineeringNeutral: Color
    @Composable get() = ThemeStatusNeutral

val EngineeringWarning: Color
    @Composable get() = StatusWarning

val EngineeringFailure: Color
    @Composable get() = StatusFail
