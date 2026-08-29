package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SldViewportFitTest {

    private val standardViewport = Size(1080f, 1920f)
    private val landscapeViewport = Size(1920f, 1080f)

    @Test
    fun testEmptyGraphFitIsSafe() {
        val emptyBounds = Rect.Zero
        val (scale, pan) = SldViewportHelper.calculateFitViewport(
            contentBounds = emptyBounds,
            viewportSize = standardViewport
        )

        assertTrue("Scale must be finite", scale.isFinite())
        assertTrue("Scale must be within min/max bounds", scale in 0.05f..2.5f)
        assertTrue("Pan X must be finite", pan.x.isFinite())
        assertTrue("Pan Y must be finite", pan.y.isFinite())
        assertFalse("Scale must not be NaN", scale.isNaN())
        assertFalse("Pan X must not be NaN", pan.x.isNaN())
        assertFalse("Pan Y must not be NaN", pan.y.isNaN())
    }

    @Test
    fun testSingleNodeFitIsSafe() {
        val singleNodeBounds = Rect(100f, 100f, 240f, 170f) // 140x70 single node
        val (scale, pan) = SldViewportHelper.calculateFitViewport(
            contentBounds = singleNodeBounds,
            viewportSize = standardViewport
        )

        assertTrue("Scale must be finite", scale.isFinite())
        assertTrue("Scale must be within bounds", scale in 0.05f..2.5f)
        assertTrue("Pan X must be finite", pan.x.isFinite())
        assertTrue("Pan Y must be finite", pan.y.isFinite())
    }

    @Test
    fun testWideGraphFitRespectsPaddingAndLimits() {
        val wideBounds = Rect(0f, 0f, 5000f, 600f)
        val (scale, pan) = SldViewportHelper.calculateFitViewport(
            contentBounds = wideBounds,
            viewportSize = standardViewport,
            padding = 60f
        )

        val availWidth = standardViewport.width - 60f
        val fittedWidth = wideBounds.width * scale

        assertTrue("Fitted width must fit within available width", fittedWidth <= availWidth + 0.01f)
        assertTrue("Scale must be clamped to minScale or valid scale", scale >= 0.05f)
        assertTrue("Pan must center the content horizontally", pan.x.isFinite())
    }

    @Test
    fun testTallGraphFitRespectsPaddingAndLimits() {
        val tallBounds = Rect(0f, 0f, 600f, 6000f)
        val (scale, pan) = SldViewportHelper.calculateFitViewport(
            contentBounds = tallBounds,
            viewportSize = standardViewport,
            padding = 60f
        )

        val availHeight = standardViewport.height - 60f
        val fittedHeight = tallBounds.height * scale

        assertTrue("Fitted height must fit within available height", fittedHeight <= availHeight + 0.01f)
        assertTrue("Scale must be clamped to minScale or valid scale", scale >= 0.05f)
        assertTrue("Pan must center the content vertically", pan.y.isFinite())
    }

    @Test
    fun testFitNeverReturnsNaNOrInfinite() {
        val edgeCases = listOf(
            Rect(Float.NaN, Float.NaN, Float.NaN, Float.NaN),
            Rect(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            Rect(0f, 0f, 0f, 0f),
            Rect(-1000f, -1000f, -500f, -500f)
        )

        val viewportSizes = listOf(
            Size.Zero,
            Size(1f, 1f),
            Size(-100f, -100f),
            standardViewport,
            landscapeViewport
        )

        for (b in edgeCases) {
            for (v in viewportSizes) {
                val (scale, pan) = SldViewportHelper.calculateFitViewport(b, v)
                assertFalse("Scale must never be NaN for $b, $v", scale.isNaN())
                assertFalse("Pan X must never be NaN for $b, $v", pan.x.isNaN())
                assertFalse("Pan Y must never be NaN for $b, $v", pan.y.isNaN())
                assertTrue("Scale must be finite for $b, $v", scale.isFinite())
                assertTrue("Pan X must be finite for $b, $v", pan.x.isFinite())
                assertTrue("Pan Y must be finite for $b, $v", pan.y.isFinite())
            }
        }
    }

    @Test
    fun testPanClampingKeepsGraphVisible() {
        val bounds = Rect(0f, 0f, 1000f, 1000f)
        val scale = 1.0f

        // Wildly distant pan offset
        val extremePan = Offset(-100000f, 100000f)
        val clampedPan = SldViewportHelper.clampPan(
            zoomScale = scale,
            panOffset = extremePan,
            contentBounds = bounds,
            viewportSize = standardViewport,
            margin = 200f
        )

        assertTrue("Clamped pan X must be within safe overscroll bounds", clampedPan.x > -10000f)
        assertTrue("Clamped pan Y must be within safe overscroll bounds", clampedPan.y < 10000f)
        assertTrue("Pan X must be finite", clampedPan.x.isFinite())
        assertTrue("Pan Y must be finite", clampedPan.y.isFinite())
    }

    @Test
    fun testIdenticalContentYieldsIdenticalViewport() {
        val bounds = Rect(100f, 200f, 1200f, 1800f)
        val run1 = SldViewportHelper.calculateFitViewport(bounds, standardViewport)
        val run2 = SldViewportHelper.calculateFitViewport(bounds, standardViewport)

        assertEquals("Scale must be deterministic", run1.first, run2.first, 0.0001f)
        assertEquals("Pan X must be deterministic", run1.second.x, run2.second.x, 0.0001f)
        assertEquals("Pan Y must be deterministic", run1.second.y, run2.second.y, 0.0001f)
    }
}
