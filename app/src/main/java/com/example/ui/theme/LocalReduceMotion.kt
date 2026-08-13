package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing app-wide system reduced-motion preference.
 * When true, animation specs across all screens switch to instant or fast-fade transitions.
 */
val LocalReduceMotion = compositionLocalOf { false }
