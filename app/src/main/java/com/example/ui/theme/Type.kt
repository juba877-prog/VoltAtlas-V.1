package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Native system font family supporting English, Arabic, and engineering symbols (Ω, mm², kA, kVA, kvar, %, °C, ×, ≤, ≥)
val VoltAtlasFontFamily = FontFamily.SansSerif

val DisplayTitle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp
)

// Top bar title: 20px / 700
val ScreenTitle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp
)

// Section title / Card head name: 15-17px / 700
val SectionTitle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 22.sp
)

// Card / node name: 15-17px / 700
val CardTitle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 20.sp
)

// Body / rows: 14-15px
val BodyPrimary = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

val BodySecondary = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
)

// Uppercase labels: 11-13px, letter-spacing 0.05em
val Label = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.05.sp
)

// Stat number: 26px / 700 (tabular-nums)
val StatNumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp
)

// Detail value big: 20px / 700 (utilization %)
val DetailValueBigStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp
)

// Engineering values (tabular/monospaced figures): 22px / 700
val EngineeringValue = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp
)

val EngineeringUnit = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp
)

val StatusText = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.03.sp
)

val NavigationLabel = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 13.sp
)

// Caption: 11-13px
val Caption = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

val DisplayMediumStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp
)

val DisplaySmallStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp
)

val HeadlineLargeStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp
)

val HeadlineMediumStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp
)

val HeadlineSmallStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp
)

val BodySmallStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

val LabelMediumStyle = TextStyle(
    fontFamily = VoltAtlasFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp
)

// Map VoltAtlas Typography to Material 3
val Typography = Typography(
    displayLarge = DisplayTitle,
    displayMedium = DisplayMediumStyle,
    displaySmall = DisplaySmallStyle,
    headlineLarge = HeadlineLargeStyle,
    headlineMedium = HeadlineMediumStyle,
    headlineSmall = HeadlineSmallStyle,
    titleLarge = ScreenTitle,
    titleMedium = SectionTitle,
    titleSmall = CardTitle,
    bodyLarge = BodyPrimary,
    bodyMedium = BodySecondary,
    bodySmall = BodySmallStyle,
    labelLarge = Label,
    labelMedium = LabelMediumStyle,
    labelSmall = Caption
)
