package com.example.sld

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SldMinimap(
    layoutResult: SldLayoutResult,
    viewportSize: Size,
    panOffset: Offset,
    zoomScale: Float,
    manualPositions: Map<String, Offset> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val padding = 4f
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        
        val graphW = layoutResult.totalWidth
        val graphH = layoutResult.totalHeight
        
        if (graphW <= 0f || graphH <= 0f) return@Canvas
        
        val scale = minOf(w / graphW, h / graphH)
        val dx = (size.width - graphW * scale) / 2f
        val dy = (size.height - graphH * scale) / 2f
        
        // Draw Connected & Unconnected Nodes as small gray rectangles
        val allNodes = layoutResult.layoutNodes.values + layoutResult.unconnectedLayoutNodes
        allNodes.forEach { lNode ->
            val manualPos = manualPositions[lNode.node.id]
            val nx = manualPos?.x ?: lNode.x
            val ny = manualPos?.y ?: lNode.y
            
            drawRect(
                color = Color.Gray.copy(alpha = 0.5f),
                topLeft = Offset(dx + nx * scale, dy + ny * scale),
                size = Size(lNode.width * scale, lNode.height * scale)
            )
        }
        
        // Draw Viewport Rectangle
        val viewLeft = (-panOffset.x / zoomScale)
        val viewTop = (-panOffset.y / zoomScale)
        val viewWidth = (viewportSize.width / zoomScale)
        val viewHeight = (viewportSize.height / zoomScale)
        
        drawRect(
            color = Color(0xFF0EA5E9),
            topLeft = Offset(dx + viewLeft * scale, dy + viewTop * scale),
            size = Size(viewWidth * scale, viewHeight * scale),
            style = Stroke(width = 1.5f)
        )
    }
}
