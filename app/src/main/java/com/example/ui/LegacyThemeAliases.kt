package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sleek Interface Color Palette mapped directly to centralized Design Tokens
val DarkBackground: Color
    @Composable
    get() = com.example.ui.components.ThemeBackgroundDeep

val DarkCardBg: Color
    @Composable
    get() = com.example.ui.components.ThemeSurface

val BorderColor: Color
    @Composable
    get() = com.example.ui.components.ThemeBorderSubtle

val SleekPrimary: Color
    @Composable
    get() = com.example.ui.components.ThemePrimary

val SleekSecondary: Color
    @Composable
    get() = com.example.ui.components.ThemeCyan

val SleekPass: Color
    @Composable
    get() = com.example.ui.theme.StatusPass

val SleekFail: Color
    @Composable
    get() = com.example.ui.theme.StatusFail

val SleekWarning: Color
    @Composable
    get() = com.example.ui.theme.StatusWarning

val SlateTextSecondary: Color
    @Composable
    get() = com.example.ui.components.ThemeTextSecondary

val ThemeTextColor: Color
    @Composable
    get() = com.example.ui.components.ThemeTextPrimary
