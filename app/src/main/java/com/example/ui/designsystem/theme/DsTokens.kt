package com.example.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * VoltAtlas Design System — Single Source of Truth tokens.
 *
 * Phase UI-0 foundation. These EXACT hex values come from the approved
 * reference (prompt §2.1). The primary (#3B9EFF) deliberately matches the
 * value already consumed by the production theme (`com.example.ui.theme`)
 * so future screen re-skins converge on one source and never break the
 * locked golden/contrast tests.
 *
 * NOTE: All tokens are STATELESS plain values — no CompositionLocal, no
 * theming look-up. Use them directly or through [voltAtlasDsTheme].
 */
object DsColor {
    // ── Surfaces / scaffolds ───────────────────────────────────────────
    val background = Color(0xFF0D1117)
    val surface = Color(0xFF0D1117)
    val surfaceContainer = Color(0xFF161B22)
    val surfaceContainerHigh = Color(0xFF1B2029)

    // ── Outlines — subtle dividers only (NOT primary card definition) ──
    val outline = Color(0xFF242C38)
    val outlineVariant = Color(0xFF30363D)

    // ── Brand ──────────────────────────────────────────────────────────
    val primary = Color(0xFF3B9EFF)

    // ── Extended semantic palette (MD3 has no direct slots for these) ──
    val info = Color(0xFF38BDF8)
    val success = Color(0xFF22C55E)
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)
    val neutral = Color(0xFF94A3B8)

    // ── Text ───────────────────────────────────────────────────────────
    val onSurface = Color(0xFFE6EDF3)
    val onSurfaceVariant = Color(0xFF8B949E)

    /**
     * Pixel-exact mirror of production `SlateTextSecondary` (#AEB6C2) —
     * zero drift. Dark-theme value by definition; see the known-limitation
     * note on [com.example.ui.designsystem.components.DsBottomNavBar]
     * (future shell-migration concern: no light-scheme adaptation yet).
     */
    val textSecondary = Color(0xFFAEB6C2)
}

/**
 * Spacing scale — base grid 12dp.
 * 4 / 8 / 12 / 16 / 20 / 24 (+32 convenience cap).
 */
object DsSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 32.dp
}

/**
 * Shape tokens.
 * - Cards: 14dp baseline radius.
 * - Large/hero containers: 16dp.
 * - Pill (fully rounded): FAB, chips, badges, active nav indicator.
 */
object DsShape {
    val card = RoundedCornerShape(14.dp)
    val largeContainer = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** Material 3 shape mapping backed by the token set above. */
val DsShapes = Shapes(
    extraSmall = DsShape.pill,
    small = RoundedCornerShape(10.dp),
    medium = DsShape.card,
    large = DsShape.largeContainer,
    extraLarge = RoundedCornerShape(28.dp)
)
