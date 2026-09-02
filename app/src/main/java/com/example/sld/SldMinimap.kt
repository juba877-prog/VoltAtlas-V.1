package com.example.sld

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top-Right Collapsible Radar Minimap Overview for VoltAtlas Single Line Diagram.
 *
 * Design Specifications:
 * 1. Compact 110x75dp bounds positioned cleanly at Top-Right without bottom collisions.
 * 2. Glassmorphism dark background (Color(0xE6131B2E)) with subtle 1.dp border (Color(0xFF334155)).
 * 3. Mini header bar ("RADAR OVERVIEW") with close/minimize button.
 * 4. Real-time two-way touch and drag coordinate mapping.
 */
@Composable
fun SldMinimap(
    engineResult: SldEngineResult,
    viewportSize: Size,
    panOffset: Offset,
    zoomScale: Float,
    onPanChanged: ((Offset) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    manualPositions: Map<String, Offset> = emptyMap(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val bounds = remember(engineResult.bounds) {
        Rect(
            left = if (engineResult.bounds.left.isFinite()) engineResult.bounds.left else 0f,
            top = if (engineResult.bounds.top.isFinite()) engineResult.bounds.top else 0f,
            right = maxOf(100f, if (engineResult.bounds.right.isFinite()) engineResult.bounds.right else 100f),
            bottom = maxOf(100f, if (engineResult.bounds.bottom.isFinite()) engineResult.bounds.bottom else 100f)
        )
    }

    val currentPanChangedState = rememberUpdatedState(onPanChanged)
    val currentViewportSizeState = rememberUpdatedState(viewportSize)
    val currentZoomScaleState = rememberUpdatedState(zoomScale)
    val currentBoundsState = rememberUpdatedState(bounds)

    // Memoize node bounds for minimap rendering
    val memoizedNodes = remember(engineResult.nodes, manualPositions) {
        engineResult.nodes.map { node ->
            val manual = manualPositions[node.id]
            val nx = manual?.x ?: node.x
            val ny = manual?.y ?: node.y
            val isSource = node.isSource
            val isMain = node.panelType.uppercase().contains("MDB") || node.panelType.uppercase().contains("MAIN")
            MinimapNode(
                id = node.id,
                x = nx,
                y = ny,
                width = node.width,
                height = node.height,
                isSource = isSource,
                isMain = isMain
            )
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val cardBg = if (isDarkTheme) Color(0xE6131B2E) else Color(0xF2F8FAFC)
    val headerBg = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.95f) else Color(0xFFE2E8F0)
    val borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .clip(shape)
            .background(cardBg)
            .border(1.dp, borderColor, shape)
    ) {
        // Mini Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(headerBg)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RADAR OVERVIEW",
                color = Color(0xFF94A3B8),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Minimap",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Minimap Canvas Body with interactive touch / drag gestures
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val vSize = currentViewportSizeState.value
                        val zScale = currentZoomScaleState.value
                        val b = currentBoundsState.value
                        val newPan = SldViewportHelper.calculatePanFromMinimapTouch(
                            touchPoint = tapOffset,
                            minimapSize = size.let { Size(it.width.toFloat(), it.height.toFloat()) },
                            contentBounds = b,
                            viewportSize = vSize,
                            zoomScale = zScale
                        )
                        currentPanChangedState.value?.invoke(newPan)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val vSize = currentViewportSizeState.value
                        val zScale = currentZoomScaleState.value
                        val b = currentBoundsState.value
                        val newPan = SldViewportHelper.calculatePanFromMinimapTouch(
                            touchPoint = change.position,
                            minimapSize = size.let { Size(it.width.toFloat(), it.height.toFloat()) },
                            contentBounds = b,
                            viewportSize = vSize,
                            zoomScale = zScale
                        )
                        currentPanChangedState.value?.invoke(newPan)
                    }
                }
        ) {
            val padding = 3f
            val w = size.width - padding * 2f
            val h = size.height - padding * 2f

            val graphW = maxOf(10f, bounds.width)
            val graphH = maxOf(10f, bounds.height)

            if (graphW <= 0f || graphH <= 0f || w <= 0f || h <= 0f) return@Canvas

            val scale = minOf(w / graphW, h / graphH)
            val dx = (size.width - graphW * scale) / 2f
            val dy = (size.height - graphH * scale) / 2f

            val contentLeft = bounds.left
            val contentTop = bounds.top

            // Draw miniature equipment nodes
            val defaultNodeColor = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
            val mainNodeColor = Color(0xFFE08A4B) // Copper for MDB
            val sourceNodeColor = Color(0xFF38BDF8) // Cyan for TX/Gen

            memoizedNodes.forEach { mNode ->
                val nx = dx + (mNode.x - contentLeft) * scale
                val ny = dy + (mNode.y - contentTop) * scale
                val nw = maxOf(2f, mNode.width * scale)
                val nh = maxOf(2f, mNode.height * scale)

                val color = when {
                    mNode.isSource -> sourceNodeColor
                    mNode.isMain -> mainNodeColor
                    else -> defaultNodeColor
                }

                drawRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(nx, ny),
                    size = Size(nw, nh)
                )
            }

            // Draw active viewport indicator box
            if (zoomScale > 0f && viewportSize.width > 0f && viewportSize.height > 0f) {
                val viewWorldLeft = -panOffset.x / zoomScale
                val viewWorldTop = -panOffset.y / zoomScale
                val viewWorldWidth = viewportSize.width / zoomScale
                val viewWorldHeight = viewportSize.height / zoomScale

                val boxLeft = dx + (viewWorldLeft - contentLeft) * scale
                val boxTop = dy + (viewWorldTop - contentTop) * scale
                val boxWidth = viewWorldWidth * scale
                val boxHeight = viewWorldHeight * scale

                val indicatorColor = Color(0xFF0EA5E9)

                // Fill
                drawRect(
                    color = indicatorColor.copy(alpha = 0.22f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight)
                )

                // Outline stroke
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

/** Internal model for memoized minimap rendering */
private data class MinimapNode(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val isSource: Boolean,
    val isMain: Boolean
)

/**
 * Backward-compatible overload for SldLayoutResult.
 */
@Composable
fun SldMinimap(
    layoutResult: SldLayoutResult,
    viewportSize: Size,
    panOffset: Offset,
    zoomScale: Float,
    onPanChanged: ((Offset) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    manualPositions: Map<String, Offset> = emptyMap(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val engineResult = remember(layoutResult) {
        SldEngineResult(
            nodes = layoutResult.layoutNodes.values.map {
                PositionedSldNode(
                    id = it.node.id,
                    label = it.node.displayName,
                    panelType = it.node.type.name,
                    ratingText = "",
                    statusColorHex = "#10B981",
                    x = it.x,
                    y = it.y,
                    width = it.width,
                    height = it.height,
                    isSource = it.node.type in setOf(
                        SldNodeType.UTILITY_SOURCE,
                        SldNodeType.TRANSFORMER,
                        SldNodeType.GENERATOR,
                        SldNodeType.ATS,
                        SldNodeType.UPS
                    )
                )
            },
            edges = layoutResult.layoutEdges.map {
                PositionedSldEdge(
                    fromId = it.edge.fromNodeId,
                    toId = it.edge.toNodeId,
                    label = "",
                    pathPoints = it.pathPoints
                )
            },
            bounds = Rect(0f, 0f, layoutResult.totalWidth, layoutResult.totalHeight),
            tierDatums = emptyList()
        )
    }

    SldMinimap(
        engineResult = engineResult,
        viewportSize = viewportSize,
        panOffset = panOffset,
        zoomScale = zoomScale,
        onPanChanged = onPanChanged,
        onClose = onClose,
        manualPositions = manualPositions,
        isDarkTheme = isDarkTheme,
        modifier = modifier
    )
}
