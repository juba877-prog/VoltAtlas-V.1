package com.example.sld

import kotlin.math.max

object SldHierarchicalLayoutEngine {

    const val NODE_WIDTH = 300f
    const val NODE_HEIGHT = 160f
    const val HORIZONTAL_SPACING = 80f
    const val VERTICAL_SPACING = 160f
    const val MARGIN = 50f

    fun calculateLayout(
        graph: ElectricalNetworkGraph,
        collapsedNodeIds: Set<String> = emptySet()
    ): SldLayoutResult {
        val nodeMap = graph.nodes.associateBy { it.id }
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        val parentMap = mutableMapOf<String, String>()

        graph.edges.forEach { edge ->
            if (edge.type != SldEdgeType.ATS_EMERGENCY_PATH) {
                childrenMap.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId)
                parentMap[edge.toNodeId] = edge.fromNodeId
            }
        }

        // Find root nodes (nodes in main graph that have no parent in main graph)
        val rootIds = graph.nodes
            .map { it.id }
            .filter { parentMap[it] == null || !nodeMap.containsKey(parentMap[it]) }
            .sorted() // Deterministic ordering

        // Calculate descendant counts for collapse UI badge
        fun countAllDescendants(nodeId: String): Int {
            val children = childrenMap[nodeId] ?: emptyList()
            var count = children.size
            for (child in children) {
                count += countAllDescendants(child)
            }
            return count
        }

        val hiddenDescendantsCounts = graph.nodes.associate { node ->
            node.id to countAllDescendants(node.id)
        }

        // Compute subtree width for each node (excluding collapsed branches)
        val subtreeWidths = mutableMapOf<String, Float>()
        fun computeSubtreeWidth(nodeId: String): Float {
            if (collapsedNodeIds.contains(nodeId)) {
                subtreeWidths[nodeId] = NODE_WIDTH
                return NODE_WIDTH
            }
            val children = (childrenMap[nodeId] ?: emptyList()).filter { nodeMap.containsKey(it) }
            if (children.isEmpty()) {
                subtreeWidths[nodeId] = NODE_WIDTH
                return NODE_WIDTH
            }

            var totalChildWidth = 0f
            children.forEachIndexed { index, childId ->
                if (index > 0) totalChildWidth += HORIZONTAL_SPACING
                totalChildWidth += computeSubtreeWidth(childId)
            }
            val width = max(NODE_WIDTH, totalChildWidth)
            subtreeWidths[nodeId] = width
            return width
        }

        rootIds.forEach { computeSubtreeWidth(it) }

        // Position nodes hierarchically
        val layoutNodes = mutableMapOf<String, SldLayoutNode>()
        var currentRootX = MARGIN

        fun positionNode(nodeId: String, depth: Int, startX: Float, width: Float) {
            val sNode = nodeMap[nodeId] ?: return
            val centerX = startX + width / 2f
            val nodeX = centerX - NODE_WIDTH / 2f
            val nodeY = MARGIN + depth * (NODE_HEIGHT + VERTICAL_SPACING)

            val isCollapsed = collapsedNodeIds.contains(nodeId)
            val hiddenCount = if (isCollapsed) hiddenDescendantsCounts[nodeId] ?: 0 else 0

            layoutNodes[nodeId] = SldLayoutNode(
                node = sNode,
                x = nodeX,
                y = nodeY,
                width = NODE_WIDTH,
                height = NODE_HEIGHT,
                depth = depth,
                isCollapsed = isCollapsed,
                hiddenDescendantsCount = hiddenCount
            )

            if (!isCollapsed) {
                val children = (childrenMap[nodeId] ?: emptyList()).filter { nodeMap.containsKey(it) }
                var childStartX = startX
                children.forEach { childId ->
                    val childWidth = subtreeWidths[childId] ?: NODE_WIDTH
                    positionNode(childId, depth + 1, childStartX, childWidth)
                    childStartX += childWidth + HORIZONTAL_SPACING
                }
            }
        }

        rootIds.forEach { rootId ->
            val rWidth = subtreeWidths[rootId] ?: NODE_WIDTH
            positionNode(rootId, depth = 0, startX = currentRootX, width = rWidth)
            currentRootX += rWidth + HORIZONTAL_SPACING * 2
        }

        val mainMaxY = layoutNodes.values.maxOfOrNull { it.y + it.height } ?: MARGIN
        val mainMaxX = max(layoutNodes.values.maxOfOrNull { it.x + it.width } ?: (MARGIN + NODE_WIDTH), currentRootX)

        // Route Orthogonal Edges
        val layoutEdges = mutableListOf<SldLayoutEdge>()
        graph.edges.forEach { edge ->
            val parentLayout = layoutNodes[edge.fromNodeId]
            val childLayout = layoutNodes[edge.toNodeId]

            if (parentLayout != null && childLayout != null) {
                var px = parentLayout.x + parentLayout.width / 2f
                var py = parentLayout.y + parentLayout.height
                var cx = childLayout.x + childLayout.width / 2f
                var cy = childLayout.y

                // Custom port positioning for ATS
                if (edge.type == SldEdgeType.ATS_NORMAL_PATH) {
                    cx = childLayout.x + childLayout.width * 0.25f
                } else if (edge.type == SldEdgeType.ATS_EMERGENCY_PATH) {
                    cx = childLayout.x + childLayout.width * 0.75f
                } else if (edge.type == SldEdgeType.ATS_OUTPUT) {
                    px = parentLayout.x + parentLayout.width * 0.5f
                    py = parentLayout.y + parentLayout.height
                }

                val midY = py + (cy - py) / 2f

                val path = listOf(
                    Pair(px, py),
                    Pair(px, midY),
                    Pair(cx, midY),
                    Pair(cx, cy)
                )

                layoutEdges.add(
                    SldLayoutEdge(
                        edge = edge,
                        pathPoints = path
                    )
                )
            }
        }

        // Layout Unconnected Nodes Area
        val unconnectedLayoutNodes = mutableListOf<SldLayoutNode>()
        val unconnectedStartY = mainMaxY + VERTICAL_SPACING
        var unconnX = MARGIN
        var unconnY = unconnectedStartY + 40f // Margin for title header

        graph.unconnectedNodes.sortedBy { it.displayName }.forEachIndexed { index, unNode ->
            val layoutNode = SldLayoutNode(
                node = unNode,
                x = unconnX,
                y = unconnY,
                width = NODE_WIDTH,
                height = NODE_HEIGHT,
                depth = -1,
                isCollapsed = false,
                hiddenDescendantsCount = 0
            )
            unconnectedLayoutNodes.add(layoutNode)

            unconnX += NODE_WIDTH + HORIZONTAL_SPACING
            if (unconnX + NODE_WIDTH > mainMaxX && (index + 1) % 4 == 0) {
                unconnX = MARGIN
                unconnY += NODE_HEIGHT + VERTICAL_SPACING / 2f
            }
        }

        val totalHeight = max(mainMaxY, unconnY + NODE_HEIGHT) + MARGIN
        val totalWidth = max(mainMaxX, unconnX + NODE_WIDTH) + MARGIN

        return SldLayoutResult(
            layoutNodes = layoutNodes,
            layoutEdges = layoutEdges,
            unconnectedLayoutNodes = unconnectedLayoutNodes,
            totalWidth = totalWidth,
            totalHeight = totalHeight,
            unconnectedBoundsY = unconnectedStartY
        )
    }
}
