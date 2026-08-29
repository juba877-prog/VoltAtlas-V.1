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
}
