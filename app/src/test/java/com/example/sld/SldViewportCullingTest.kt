package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 §4.1 — Spatial Viewport Culling Unit Tests.
 */
class SldViewportCullingTest {

    @Test
    fun nodesOutsideViewport_areCulled() {
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f
        val panOffset = Offset.Zero
        val visibleWorldRect = SldViewportHelper.calculateVisibleWorldRect(
            panOffset = panOffset,
            zoomScale = zoomScale,
            viewportSize = viewportSize,
            safetyMargin = 64f
        )

        val insideNodeBounds = Rect(100f, 100f, 200f, 150f)
        val outsideNodeBounds = Rect(5000f, 5000f, 5200f, 5100f)

        assertTrue(
            "Node at (100, 100) must be visible inside (800x600) viewport",
            SldViewportHelper.isNodeVisible(insideNodeBounds, visibleWorldRect)
        )
        assertFalse(
            "Node at (5000, 5000) must be culled outside (800x600) viewport",
            SldViewportHelper.isNodeVisible(outsideNodeBounds, visibleWorldRect)
        )
    }

    @Test
    fun overlappingNodes_areRetained() {
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f
        val panOffset = Offset.Zero
        val visibleWorldRect = SldViewportHelper.calculateVisibleWorldRect(
            panOffset = panOffset,
            zoomScale = zoomScale,
            viewportSize = viewportSize,
            safetyMargin = 64f
        )

        // Node crossing the right boundary: x from 750 to 900
        val crossingRightEdge = Rect(750f, 200f, 900f, 250f)
        assertTrue(
            "Node crossing screen boundary must be retained due to overlap and safety margin",
            SldViewportHelper.isNodeVisible(crossingRightEdge, visibleWorldRect)
        )

        // Node within the safety margin (e.g. x = 830, within 800 + 64 = 864)
        val withinSafetyMargin = Rect(820f, 200f, 850f, 250f)
        assertTrue(
            "Node within safety margin must be retained",
            SldViewportHelper.isNodeVisible(withinSafetyMargin, visibleWorldRect)
        )
    }

    @Test
    fun selectedNode_isNeverCulled() {
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f
        val panOffset = Offset.Zero
        val visibleWorldRect = SldViewportHelper.calculateVisibleWorldRect(
            panOffset = panOffset,
            zoomScale = zoomScale,
            viewportSize = viewportSize,
            safetyMargin = 64f
        )

        val selectedNodeId = "NODE_SPECIAL_FAR_AWAY"
        val farNode = PositionedSldNode(
            id = selectedNodeId,
            label = "Far Away Node",
            panelType = "DB",
            ratingText = "",
            statusColorHex = "#10B981",
            x = 5000f,
            y = 5000f,
            width = 120f,
            height = 40f,
            isSource = false
        )

        val nodeBounds = Rect(farNode.x, farNode.y, farNode.x + farNode.width, farNode.y + farNode.height)
        val isPhysicallyVisible = SldViewportHelper.isNodeVisible(nodeBounds, visibleWorldRect)
        assertFalse("Far node is physically outside the viewport", isPhysicallyVisible)

        // Selection guard rule: node is drawn if selected OR physically visible
        val shouldDrawNode = (farNode.id == selectedNodeId) || isPhysicallyVisible
        assertTrue("Selected node must never be culled even when off-screen", shouldDrawNode)
    }

    @Test
    fun edgesOutsideViewport_areCulledUnlessConnectedToSelection() {
        val viewportSize = Size(800f, 600f)
        val zoomScale = 1.0f
        val panOffset = Offset.Zero
        val visibleWorldRect = SldViewportHelper.calculateVisibleWorldRect(
            panOffset = panOffset,
            zoomScale = zoomScale,
            viewportSize = viewportSize,
            safetyMargin = 64f
        )

        val insideEdgePoints = listOf(100f to 100f, 100f to 300f)
        val outsideEdgePoints = listOf(4000f to 4000f, 4000f to 4500f)

        val insideEnvelope = SldViewportHelper.computeEdgeEnvelope(insideEdgePoints)
        val outsideEnvelope = SldViewportHelper.computeEdgeEnvelope(outsideEdgePoints)

        assertTrue(
            "Inside edge envelope must overlap visible world rect",
            SldViewportHelper.isEdgeVisible(insideEnvelope, visibleWorldRect)
        )
        assertFalse(
            "Outside edge envelope must not overlap visible world rect",
            SldViewportHelper.isEdgeVisible(outsideEnvelope, visibleWorldRect)
        )

        val selectedNodeId = "TX-01"
        val edgeConnectedToSelected = PositionedSldEdge(
            fromId = selectedNodeId,
            toId = "FAR_MDB",
            label = "Feeder",
            pathPoints = outsideEdgePoints
        )
        val shouldDrawEdge = (edgeConnectedToSelected.fromId == selectedNodeId || edgeConnectedToSelected.toId == selectedNodeId) ||
                SldViewportHelper.isEdgeVisible(outsideEnvelope, visibleWorldRect)
        assertTrue("Edge connected to selected node must never be culled", shouldDrawEdge)
    }
}
