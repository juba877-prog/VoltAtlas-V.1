package com.example.boot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * VoltAtlasLogo
 * -------------
 * EXACT geometric definition of the VoltAtlas logo, ported 1:1 from the
 * official Android vector drawable (voltatlas_va_logo.xml):
 *
 *   viewport 108 x 108
 *   Blue/Cyan V : path "M 34,38 L 54,72 L 74,38"   stroke #08B7E8  width 8  round
 *   Green bar   : path "M 44,55 L 64,55"            stroke #19C979  width 8  round
 *
 * IMPORTANT: The logo is STROKE-BASED (two round-capped strokes), NOT a filled
 * shape. Draw it with Stroke(width = STROKE_W * scale, cap = Round, join = Round)
 * so it matches the brand asset pixel-for-pixel and can never be distorted.
 *
 * Design space = the original 108x108 viewport. All points below are in that
 * space, then scaled by [scale] and centered on [center].
 */
object VoltAtlasLogo {

    // ---- Brand palette (EXACT values from the vector drawable) -------------
    const val NAVY: Long   = 0xFF0A1428      // background (app choice)
    const val BLUE: Long   = 0xFF08B7E8      // V stroke  (official)
    const val GREEN: Long  = 0xFF19C979      // bar stroke (official)
    const val GLOW: Long   = 0xFF7FE6FF      // scan / glow highlight (derived tint)

    // ---- Original viewport + stroke ----------------------------------------
    const val VIEWPORT = 108f
    const val STROKE_W = 8f            // in viewport units; multiply by scale

    // ---- Exact vector points (viewport units) ------------------------------
    // V: M 34,38  L 54,72  L 74,38
    private val V_TOP_LEFT  = Offset(34f, 38f)
    private val V_TIP       = Offset(54f, 72f)
    private val V_TOP_RIGHT = Offset(74f, 38f)
    // Green crossbar: M 44,55  L 64,55
    private val BAR_LEFT    = Offset(44f, 55f)
    private val BAR_RIGHT   = Offset(64f, 55f)

    /**
     * Maps a viewport point (0..108) into actual canvas pixels, centered on [center].
     */
    private fun map(p: Offset, center: Offset, scale: Float): Offset {
        val originX = center.x - (VIEWPORT / 2f) * scale
        val originY = center.y - (VIEWPORT / 2f) * scale
        return Offset(originX + p.x * scale, originY + p.y * scale)
    }

    /** Stroke width in pixels for a given [scale]. */
    fun strokeWidthPx(scale: Float): Float = STROKE_W * scale

    /**
     * The full "V" as an OPEN stroked polyline: top-left -> tip -> top-right.
     * Draw with Stroke(strokeWidthPx(scale), cap = Round, join = Round).
     */
    fun vPath(center: Offset, scale: Float): Path = Path().apply {
        val a = map(V_TOP_LEFT, center, scale)
        val b = map(V_TIP, center, scale)
        val c = map(V_TOP_RIGHT, center, scale)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
    }

    /**
     * LEFT arm of the V (top-left -> tip). Used by PathMeasure for the
     * "energy travels down the arm and becomes the V" morph.
     */
    fun leftArm(center: Offset, scale: Float): Path = Path().apply {
        val a = map(V_TOP_LEFT, center, scale)
        val b = map(V_TIP, center, scale)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
    }

    /**
     * RIGHT arm of the V (top-right -> tip). Mirror of the left, energy flows
     * from the top opening down to the shared tip.
     */
    fun rightArm(center: Offset, scale: Float): Path = Path().apply {
        val a = map(V_TOP_RIGHT, center, scale)
        val b = map(V_TIP, center, scale)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
    }

    /**
     * The green crossbar. [reveal] (0..1) controls how much is drawn from the
     * left endpoint toward the right (for the slide-in + lock animation).
     * Draw with Stroke(strokeWidthPx(scale), cap = Round).
     */
    fun barPath(center: Offset, scale: Float, reveal: Float = 1f): Path = Path().apply {
        val r = reveal.coerceIn(0f, 1f)
        val left  = map(BAR_LEFT, center, scale)
        val right = map(BAR_RIGHT, center, scale)
        val endX = left.x + (right.x - left.x) * r
        moveTo(left.x, left.y)
        lineTo(endX, left.y)
    }

    /** Bounding size of the logo in pixels. */
    fun boundingSize(scale: Float): Size = Size(VIEWPORT * scale, VIEWPORT * scale)

    /** Map tip point to canvas pixels. */
    fun tip(center: Offset, scale: Float): Offset = map(V_TIP, center, scale)
}

@Composable
fun VoltAtlasLogo(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(VoltAtlasLogo.BLUE),
    accentColor: Color = Color(VoltAtlasLogo.GREEN),
    tightFit: Boolean = false
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        if (tightFit) {
            // Tight bounding box of the logo mark (mark spans x in [34, 74], y in [38, 72] plus stroke)
            val contentW = 48f
            val contentH = 42f
            val scale = min(size.width / contentW, size.height / contentH)
            val strokePx = VoltAtlasLogo.strokeWidthPx(scale)

            fun mapTight(p: Offset): Offset {
                return Offset(
                    center.x + (p.x - 54f) * scale,
                    center.y + (p.y - 55f) * scale
                )
            }

            val vPath = Path().apply {
                val a = mapTight(Offset(34f, 38f))
                val b = mapTight(Offset(54f, 72f))
                val c = mapTight(Offset(74f, 38f))
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
            }
            val barPath = Path().apply {
                val left = mapTight(Offset(44f, 55f))
                val right = mapTight(Offset(64f, 55f))
                moveTo(left.x, left.y)
                lineTo(right.x, right.y)
            }

            drawPath(
                path = vPath,
                color = primaryColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = barPath,
                color = accentColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            val scale = min(size.width, size.height) / VoltAtlasLogo.VIEWPORT
            val strokePx = VoltAtlasLogo.strokeWidthPx(scale)

            drawPath(
                path = VoltAtlasLogo.vPath(center, scale),
                color = primaryColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = VoltAtlasLogo.barPath(center, scale, 1f),
                color = accentColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

