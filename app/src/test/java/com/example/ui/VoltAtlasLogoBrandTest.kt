package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.boot.VoltAtlasLogo
import com.example.ui.theme.BrandMarkBlue
import com.example.ui.theme.BrandMarkGreen
import com.example.ui.theme.StatusNeutral
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 7 logo regression proof.
 *
 * The approved VoltAtlas mark is:
 *   - LEFT V stroke  : approved VoltAtlas blue (#08B7E8 = VoltAtlasLogo.BLUE)
 *   - RIGHT V stroke : approved VoltAtlas blue (#08B7E8)
 *   - HORIZONTAL BRIDGE : approved VoltAtlas green (#19C979 = VoltAtlasLogo.GREEN)
 *
 * This test pins the authoritative Canvas-logo constants, the centralized
 * BrandMark* tokens, and - critically - samples the ACTUAL RENDERED PIXELS of
 * the home/project screen's production monogram at the geometric centre of the
 * left V stroke, right V stroke and horizontal bridge, proving the real draw
 * wiring (not just token definitions) in BOTH Light and Dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class VoltAtlasLogoBrandTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val ApprovedVoltAtlasBlue = Color(VoltAtlasLogo.BLUE)   // 0xFF08B7E8
    private val ApprovedVoltAtlasGreen = Color(VoltAtlasLogo.GREEN) // 0xFF19C979

    @Test
    fun canonicalCanvasLogo_pinsApprovedBrandTokens() {
        assertEquals(0xFF08B7E8L, VoltAtlasLogo.BLUE)
        assertEquals(0xFF19C979L, VoltAtlasLogo.GREEN)
        assertEquals(ApprovedVoltAtlasBlue, BrandMarkBlue)
        assertEquals(ApprovedVoltAtlasGreen, BrandMarkGreen)
        // The bridge must never regress to the grey StatusNeutral token.
        assertNotEquals(StatusNeutral, BrandMarkGreen)
        assertNotEquals(ApprovedVoltAtlasBlue, ApprovedVoltAtlasGreen)
    }

    private fun argb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

    /** Nearest pixel to (cx,cy) within radius whose channels are within tol of expected. */
    private fun findRenderedPixel(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        radius: Int,
        expected: Color,
        tol: Int
    ): Long? {
        val e = argb(expected)
        val er = ((e shr 16) and 0xFF).toInt()
        val eg = ((e shr 8) and 0xFF).toInt()
        val eb = (e and 0xFF).toInt()
        var best: Long? = null
        var bestDist = Int.MAX_VALUE
        for (y in (cy - radius)..(cy + radius)) {
            if (y < 0 || y >= bitmap.height) continue
            for (x in (cx - radius)..(cx + radius)) {
                if (x < 0 || x >= bitmap.width) continue
                val p = bitmap.getPixel(x, y)
                val pr = ((p shr 16) and 0xFF).toInt()
                val pg = ((p shr 8) and 0xFF).toInt()
                val pb = (p and 0xFF).toInt()
                if (Math.abs(pr - er) <= tol && Math.abs(pg - eg) <= tol && Math.abs(pb - eb) <= tol) {
                    val d = Math.abs(x - cx) + Math.abs(y - cy)
                    if (d < bestDist) {
                        bestDist = d
                        best = p.toLong() and 0xFFFFFFFFL
                    }
                }
            }
        }
        return best
    }

    /**
     * Renders the REAL production ProjectPickerScreen (which owns the VA
     * monogram Canvas) and samples the actual drawn pixels at the geometric
     * centre of each stroke.
     */
    private fun renderHomeMonogramAndAssertIdentity(darkTheme: Boolean) {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = darkTheme) {
                ProjectPickerScreen(
                    projects = emptyList(),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val decorView = composeTestRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            decorView.width, decorView.height, Bitmap.Config.ARGB_8888
        )
        decorView.draw(Canvas(bitmap))

        // Monogram Box: 60.dp square, horizontally centred. Its vertical
        // position is derived from the rendered bridge below (robust to any
        // header layout drift).
        val d = composeTestRule.activity.resources.displayMetrics.density

        val tol = 6      // AA headroom around exact stroke centres
        val radius = 14  // px search window for stroke centre
        val mode = if (darkTheme) "dark" else "light"

        // Derive the monogram frame FROM THE RENDER ITSELF: the bridge is the
        // topmost exact-green run inside the horizontal band where only the
        // bridge can exist (central x-range of the box).
        val B = 60f * d                       // box side in px
        val L = bitmap.width / 2f - B / 2f    // box left (horizontally centred)
        val strokeHalf = 3f * d               // 6.dp stroke -> half-width

        val scanFromX = (L + 0.38f * B).toInt()
        val scanToX = (L + 0.62f * B).toInt()
        var bridgeTopY = -1
        scan@ for (y in 0 until bitmap.height) {
            for (x in scanFromX..scanToX) {
                if ((bitmap.getPixel(x, y).toLong() and 0xFFFFFFFFL) == argb(ApprovedVoltAtlasGreen)) {
                    bridgeTopY = y
                    break@scan
                }
            }
        }
        assertTrue(
            "$mode: no rendered bridge (approved green) found in central band",
            bridgeTopY >= 0
        )
        // bridge centreline y = T + 0.53*B ; bridgeTopY = that - strokeHalf
        val boxTop = bridgeTopY - 0.53f * B + strokeHalf

        fun px(relX: Float, relY: Float): Pair<Int, Int> =
            Pair((L + relX * B).toInt(), (boxTop + relY * B).toInt())

        println(
            "LogoBrand diag[$mode]: size=${bitmap.width}x${bitmap.height} d=$d " +
                "bridgeTopY=$bridgeTopY boxTop=$boxTop"
        )

        // Sample points clear of stroke intersections:
        val (lx, ly) = px(0.3575f, 0.4425f)   // LEFT V stroke @ t=0.25 along arm
        val (rx, ry) = px(0.6425f, 0.4425f)   // RIGHT V stroke @ t=0.25 along arm
        val (bx, by) = px(0.500f, 0.530f)     // midpoint of horizontal BRIDGE

        val renderedLeftVStrokeColor = findRenderedPixel(bitmap, lx, ly, radius, ApprovedVoltAtlasBlue, tol)
        val renderedRightVStrokeColor = findRenderedPixel(bitmap, rx, ry, radius, ApprovedVoltAtlasBlue, tol)
        val renderedBridgeStrokeColor = findRenderedPixel(bitmap, bx, by, radius, ApprovedVoltAtlasGreen, tol)

        assertEquals(
            "$mode LEFT V stroke must render the approved VoltAtlas blue #08B7E8",
            argb(ApprovedVoltAtlasBlue), renderedLeftVStrokeColor
        )
        assertEquals(
            "$mode RIGHT V stroke must render the approved VoltAtlas blue #08B7E8",
            argb(ApprovedVoltAtlasBlue), renderedRightVStrokeColor
        )
        assertEquals(
            "$mode horizontal BRIDGE stroke must render the approved VoltAtlas green #19C979",
            argb(ApprovedVoltAtlasGreen), renderedBridgeStrokeColor
        )

        val vStroke = requireNotNull(renderedLeftVStrokeColor)
        val bridge = requireNotNull(renderedBridgeStrokeColor)
        assertNotEquals(
            "$mode identity violation: V stroke colour ($vStroke) must differ from bridge colour ($bridge)",
            vStroke, bridge
        )
        assertTrue("rendered colors must be opaque", (vStroke ushr 24) == 0xFFL && (bridge ushr 24) == 0xFFL)
    }

    @Test
    fun homeMonogram_light_leftAndRightVStrokesBlue_bridgeGreen() {
        renderHomeMonogramAndAssertIdentity(darkTheme = false)
    }

    @Test
    fun homeMonogram_dark_leftAndRightVStrokesBlue_bridgeGreen() {
        renderHomeMonogramAndAssertIdentity(darkTheme = true)
    }
}
