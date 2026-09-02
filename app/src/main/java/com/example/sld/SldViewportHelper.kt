package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

data class SldViewportState(
    val scale: Float = 1.0f,
    val panOffset: Offset = Offset.Zero,
    val contentBounds: Rect = Rect.Zero,
    val viewportSize: Size = Size.Zero,
    val selectedItemId: String? = null
)

object SldViewportHelper {

    fun calculateFitViewport(
        contentBounds: Rect,
        viewportSize: Size,
        padding: Float = 60f,
        minScale: Float = 0.05f,
        maxScale: Float = 2.5f
    ): Pair<Float, Offset> {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) {
            return Pair(1.0f, Offset.Zero)
        }

        val graphW = maxOf(10f, if (contentBounds.width.isFinite()) contentBounds.width else 10f)
        val graphH = maxOf(10f, if (contentBounds.height.isFinite()) contentBounds.height else 10f)

        val availW = maxOf(10f, viewportSize.width - padding)
        val availH = maxOf(10f, viewportSize.height - padding)

        val rawScale = minOf(availW / graphW, availH / graphH)
        val fitScale = if (rawScale.isFinite()) rawScale.coerceIn(minScale, maxScale) else 1.0f

        val left = if (contentBounds.left.isFinite()) contentBounds.left else 0f
        val top = if (contentBounds.top.isFinite()) contentBounds.top else 0f

        val fitPanX = (viewportSize.width - graphW * fitScale) / 2f - left * fitScale
        val fitPanY = (viewportSize.height - graphH * fitScale) / 2f - top * fitScale

        val safePanX = if (fitPanX.isFinite()) fitPanX else 0f
        val safePanY = if (fitPanY.isFinite()) fitPanY else 0f

        return Pair(fitScale, Offset(safePanX, safePanY))
    }

    fun clampPan(
        zoomScale: Float,
        panOffset: Offset,
        contentBounds: Rect,
        viewportSize: Size,
        margin: Float = 200f
    ): Offset {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f || !zoomScale.isFinite()) {
            return panOffset
        }
        val safeScale = zoomScale.coerceIn(0.05f, 5.0f)
        val contentLeft = if (contentBounds.left.isFinite()) contentBounds.left * safeScale else 0f
        val contentTop = if (contentBounds.top.isFinite()) contentBounds.top * safeScale else 0f
        val contentWidth = if (contentBounds.width.isFinite()) contentBounds.width * safeScale else 100f
        val contentHeight = if (contentBounds.height.isFinite()) contentBounds.height * safeScale else 100f

        val minX = viewportSize.width - contentLeft - contentWidth - margin
        val maxX = -contentLeft + margin
        val minY = viewportSize.height - contentTop - contentHeight - margin
        val maxY = -contentTop + margin

        val clampedX = panOffset.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX))
        val clampedY = panOffset.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))

        return Offset(clampedX, clampedY)
    }

    /**
     * Phase 4 §4.1 — Calculate the visible bounding rectangle in world coordinates.
     *
     * Screen Coordinate = World Coordinate * zoomScale + panOffset
     * Inverse:
     * worldLeft = -panOffset.x / zoomScale
     * worldTop = -panOffset.y / zoomScale
     * worldRight = worldLeft + viewportSize.width / zoomScale
     * worldBottom = worldTop + viewportSize.height / zoomScale
     *
     * An optional safetyMargin (default 64f) expands the rectangle to prevent visual
     * popping along the screen boundary during active pan/zoom gestures.
     */
    fun calculateVisibleWorldRect(
        panOffset: Offset,
        zoomScale: Float,
        viewportSize: Size,
        safetyMargin: Float = 64f
    ): Rect {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f || zoomScale <= 0f || !zoomScale.isFinite()) {
            return Rect(-10000f, -10000f, 20000f, 20000f)
        }

        val worldLeft = -panOffset.x / zoomScale
        val worldTop = -panOffset.y / zoomScale
        val worldRight = worldLeft + (viewportSize.width / zoomScale)
        val worldBottom = worldTop + (viewportSize.height / zoomScale)

        return Rect(
            left = worldLeft - safetyMargin,
            top = worldTop - safetyMargin,
            right = worldRight + safetyMargin,
            bottom = worldBottom + safetyMargin
        )
    }

    /**
     * True when the node's bounding rectangle overlaps the visible world viewport.
     */
    fun isNodeVisible(nodeBounds: Rect, visibleWorldRect: Rect): Boolean {
        return nodeBounds.overlaps(visibleWorldRect)
    }

    /**
     * True when an edge's bounding envelope overlaps the visible world viewport.
     */
    fun isEdgeVisible(edgeEnvelope: Rect, visibleWorldRect: Rect): Boolean {
        return edgeEnvelope.overlaps(visibleWorldRect)
    }

    /**
     * Compute the axis-aligned bounding envelope for an edge polyline.
     */
    fun computeEdgeEnvelope(points: List<Pair<Float, Float>>): Rect {
        if (points.isEmpty()) return Rect.Zero
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (pt in points) {
            val x = pt.first
            val y = pt.second
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Phase 4 §4.3 — Map a touch/drag coordinate on the minimap into a world-space
     * canvas pan offset that centers the main canvas on the touched world coordinate.
     */
    fun calculatePanFromMinimapTouch(
        touchPoint: Offset,
        minimapSize: Size,
        contentBounds: Rect,
        viewportSize: Size,
        zoomScale: Float,
        padding: Float = 4f
    ): Offset {
        if (minimapSize.width <= 0f || minimapSize.height <= 0f || viewportSize.width <= 0f || viewportSize.height <= 0f || zoomScale <= 0f) {
            return Offset.Zero
        }

        val graphW = maxOf(10f, if (contentBounds.width.isFinite()) contentBounds.width else 10f)
        val graphH = maxOf(10f, if (contentBounds.height.isFinite()) contentBounds.height else 10f)

        val availW = maxOf(1f, minimapSize.width - padding * 2f)
        val availH = maxOf(1f, minimapSize.height - padding * 2f)

        val scale = minOf(availW / graphW, availH / graphH)
        val dx = (minimapSize.width - graphW * scale) / 2f
        val dy = (minimapSize.height - graphH * scale) / 2f

        val contentLeft = if (contentBounds.left.isFinite()) contentBounds.left else 0f
        val contentTop = if (contentBounds.top.isFinite()) contentBounds.top else 0f

        val targetWorldX = contentLeft + (touchPoint.x - dx) / scale
        val targetWorldY = contentTop + (touchPoint.y - dy) / scale

        val newPanX = -(targetWorldX * zoomScale - viewportSize.width / 2f)
        val newPanY = -(targetWorldY * zoomScale - viewportSize.height / 2f)

        return clampPan(zoomScale, Offset(newPanX, newPanY), contentBounds, viewportSize)
    }
}
