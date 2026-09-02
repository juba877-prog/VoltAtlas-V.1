package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 §4.3 — Two-Way Interactive Minimap Coordinate Mapping Unit Tests.
 */
class SldMinimapNavigationTest {

    @Test
    fun touchingCenterOfMinimap_centersMainCanvasOnWorldCenter() {
        val minimapSize = Size(100f, 100f)
        val contentBounds = Rect(0f, 0f, 1000f, 1000f)
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f

        val centerTouch = Offset(50f, 50f)
        val panOffset = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = centerTouch,
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale,
            padding = 0f
        )

        // Verify that world coordinate (500, 500) projects to screen coordinate (400, 300)
        val screenCenterX = 500f * zoomScale + panOffset.x
        val screenCenterY = 500f * zoomScale + panOffset.y

        assertEquals("Screen center X must be half of viewport width (400px)", 400f, screenCenterX, 1.0f)
        assertEquals("Screen center Y must be half of viewport height (300px)", 300f, screenCenterY, 1.0f)
    }

    @Test
    fun draggingAcrossMinimap_producesSmoothContinuousPanOffsets() {
        val minimapSize = Size(100f, 100f)
        val contentBounds = Rect(0f, 0f, 2000f, 2000f)
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f

        val pan1 = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = Offset(25f, 25f),
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale
        )
        val pan2 = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = Offset(50f, 50f),
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale
        )
        val pan3 = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = Offset(75f, 75f),
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale
        )

        assertTrue("Moving touch rightward/downward must decrease panOffset", pan1.x > pan2.x && pan2.x > pan3.x)
        assertTrue("Moving touch downward must decrease panOffset Y", pan1.y > pan2.y && pan2.y > pan3.y)
    }

    @Test
    fun extremeTouches_areSafelyClamped() {
        val minimapSize = Size(100f, 100f)
        val contentBounds = Rect(0f, 0f, 1000f, 1000f)
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f

        val extremeTopLeft = Offset(-500f, -500f)
        val panTopLeft = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = extremeTopLeft,
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale
        )
        assertTrue("Extreme top-left touch must produce finite pan coordinates", panTopLeft.x.isFinite() && panTopLeft.y.isFinite())

        val extremeBottomRight = Offset(1000f, 1000f)
        val panBottomRight = SldViewportHelper.calculatePanFromMinimapTouch(
            touchPoint = extremeBottomRight,
            minimapSize = minimapSize,
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            zoomScale = zoomScale
        )
        assertTrue("Extreme bottom-right touch must produce finite pan coordinates", panBottomRight.x.isFinite() && panBottomRight.y.isFinite())
    }
}
