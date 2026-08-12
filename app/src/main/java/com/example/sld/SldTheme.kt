package com.example.sld

import androidx.compose.ui.graphics.Color

/**
 * Centralized Theme & Metric specification for VoltAtlas SLD (Gate C).
 * All canvas drawing operations MUST read from this single source of truth.
 */
data class SldPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val enclosureBusTaps: Color,
    val protection: Color,
    val feeders: Color,
    val earth: Color,
    val tierDatum: Color,
    val gridSource: Color,
    val gridText: Color,
    val validStatus: Color,
    val warningStatus: Color,
    val invalidStatus: Color
)

object SldTheme {
    /**
     * Base Unit U (suggest U = 8.0f at zoom scale 1.0).
     * All line weights, text sizes, and geometries are exact ratios of U.
     */
    const val BASE_U = 8.0f

    // Line-weight ladder (ratios of U)
    fun busbarStroke(u: Float = BASE_U) = 1.5f * u
    fun sourceSpineStroke(u: Float = BASE_U) = 0.9f * u
    fun feederStroke(u: Float = BASE_U) = 0.5f * u
    fun earthStroke(u: Float = BASE_U) = 0.4f * u
    fun enclosureStroke(u: Float = BASE_U) = 0.35f * u
    fun tierDatumStroke(u: Float = BASE_U) = 0.2f * u

    // Text-size ladder (ratios of U in sp/pt or canvas px)
    fun panelNameTextSize(u: Float = BASE_U) = 2.0f * u
    fun sourceRatingTextSize(u: Float = BASE_U) = 1.75f * u
    fun breakerRatingTextSize(u: Float = BASE_U) = 1.4f * u
    fun feederAnnotationTextSize(u: Float = BASE_U) = 1.15f * u
    fun notesTextSize(u: Float = BASE_U) = 1.0f * u

    // Panel Geometry ratios
    fun enclosureHeight(u: Float = BASE_U) = 5.0f * u
    fun wayPitch(u: Float = BASE_U) = 6.0f * u
    fun tapSize(u: Float = BASE_U) = 1.0f * u
    fun feederDrop(u: Float = BASE_U) = 14.0f * u
    fun breakerBoxSize(u: Float = BASE_U) = 2.0f * u
    fun tierVerticalSpacing(u: Float = BASE_U) = 36.0f * u

    val DarkPalette = SldPalette(
        isDark = true,
        background = Color(0xFF0D1117),
        surface = Color(0xFF161B22),
        textPrimary = Color(0xFFF0F6FC),
        textSecondary = Color(0xFF8B949E),
        enclosureBusTaps = Color(0xFF5B8DEF),
        protection = Color(0xFFFF6B6B),
        feeders = Color(0xFFE08A4B),
        earth = Color(0xFF4CAF50),
        tierDatum = Color(0xFF30363D),
        gridSource = Color(0xFF38BDF8),
        gridText = Color(0xFFE0F2FE),
        validStatus = Color(0xFF10B981),
        warningStatus = Color(0xFFF59E0B),
        invalidStatus = Color(0xFFEF4444)
    )

    val LightPalette = SldPalette(
        isDark = false,
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        enclosureBusTaps = Color(0xFF1E3A8A),
        protection = Color(0xFFD40000),
        feeders = Color(0xFFB4530A),
        earth = Color(0xFF188A18),
        tierDatum = Color(0xFFE2E8F0),
        gridSource = Color(0xFF0284C7),
        gridText = Color(0xFF0369A1),
        validStatus = Color(0xFF059669),
        warningStatus = Color(0xFFD97706),
        invalidStatus = Color(0xFFDC2626)
    )

    fun palette(isDark: Boolean): SldPalette = if (isDark) DarkPalette else LightPalette
}
