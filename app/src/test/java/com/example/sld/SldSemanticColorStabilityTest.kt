package com.example.sld

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Step 7 / Commit 4 regression proof: centralizing SLD semantic diagram colors
 * must retain every visible value. Pins:
 *  - each SldSemanticColors export const to its legacy literal (shop + riser);
 *  - Dark/Light SldPalette status fields to their legacy values.
 */
@RunWith(RobolectricTestRunner::class)
class SldSemanticColorStabilityTest {

    // ---------- export contract pins (visible values retained verbatim) ----------
    @Test
    fun shopDrawingExportColors_arePinnedToLegacyLiterals() {
        assertEquals("#3DD68C", SldSemanticColors.SHOP_VALID)
        assertEquals("#FFC107", SldSemanticColors.SHOP_WARNING)
        assertEquals("#F44336", SldSemanticColors.SHOP_INVALID)
        assertEquals("#9E9E9E", SldSemanticColors.SHOP_NEUTRAL)
        assertEquals("#3B9EFF", SldSemanticColors.SHOP_DATA_REQUIRED)
        assertEquals("#9E9E9E", SldSemanticColors.SHOP_UNCONNECTED)
    }

    @Test
    fun riserExportColors_arePinnedToLegacyLiterals() {
        assertEquals("#10B981", SldSemanticColors.RISER_VALID)
        assertEquals("#F59E0B", SldSemanticColors.RISER_WARNING)
        assertEquals("#EF4444", SldSemanticColors.RISER_INVALID)
        assertEquals("#9CA3AF", SldSemanticColors.RISER_NEUTRAL)
        assertEquals("#3B82F6", SldSemanticColors.RISER_DATA_REQUIRED)
    }

    // ---------- canvas palette pins ----------
    private fun argb(color: androidx.compose.ui.graphics.Color): Long =
        color.toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun darkPaletteStatusFields_arePinned() {
        val p = SldTheme.palette(isDark = true)
        assertEquals(0xFF10B981L, argb(p.validStatus))
        assertEquals(0xFFF59E0BL, argb(p.warningStatus))
        assertEquals(0xFFEF4444L, argb(p.invalidStatus))
    }

    @Test
    fun lightPaletteStatusFields_arePinned() {
        val p = SldTheme.palette(isDark = false)
        assertEquals(0xFF059669L, argb(p.validStatus))
        assertEquals(0xFFD97706L, argb(p.warningStatus))
        assertEquals(0xFFDC2626L, argb(p.invalidStatus))
    }
}
