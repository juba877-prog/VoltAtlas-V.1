package com.example.sld

import androidx.compose.ui.geometry.Rect
import com.example.data.CircuitEntity
import kotlin.math.max

object ShopDrawingLayoutEngine {

    const val BUSBAR_HEIGHT = 16f
    const val LEVEL_HEIGHT = 192f
    const val MIN_CHILD_SPACING = 160f
    const val MARGIN = 100f

    fun calculateLayout(
        graph: ElectricalNetworkGraph,
        circuits: List<CircuitEntity> = emptyList()
    ): SldEngineResult {
        val nodeMap = graph.nodes.associateBy { it.id }
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        val parentMap = mutableMapOf<String, String>()

        graph.edges.forEach { edge ->
            if (edge.type != SldEdgeType.ATS_EMERGENCY_PATH) {
                childrenMap.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId)
                parentMap[edge.toNodeId] = edge.fromNodeId
            }
        }

        val rootIds = graph.nodes
            .map { it.id }
            .filter { parentMap[it] == null }
            .sortedBy { it }

        val subtreeWidths = mutableMapOf<String, Float>()

        fun computeWidth(nodeId: String): Float {
            val children = childrenMap[nodeId] ?: emptyList()
            if (children.isEmpty()) {
                val w = MIN_CHILD_SPACING
                subtreeWidths[nodeId] = w
                return w
            }
            var totalWidth = 0f
            children.forEach { childId ->
                totalWidth += computeWidth(childId)
            }
            totalWidth += (children.size - 1) * 30f
            val w = max(MIN_CHILD_SPACING, totalWidth)
            subtreeWidths[nodeId] = w
            return w
        }

        rootIds.forEach { computeWidth(it) }

        val layoutNodes = mutableMapOf<String, PositionedSldNode>()
        val layoutEdges = mutableListOf<PositionedSldEdge>()

        fun positionNode(nodeId: String, xCenter: Float, yTop: Float) {
            val node = nodeMap[nodeId] ?: return
            val children = childrenMap[nodeId] ?: emptyList()

            val totalW = subtreeWidths[nodeId] ?: MIN_CHILD_SPACING
            var currentX = xCenter - (totalW / 2f)

            val nodeWidth = totalW
            val matchingCircuit = if (node.rawCircuitId != null) {
                circuits.find { it.id == node.rawCircuitId }
            } else {
                circuits.find { "circ_${it.id}" == node.id }
            }

            val pNodeId = node.rawCircuitId?.toString() ?: node.id
            val pNode = PositionedSldNode(
                id = pNodeId,
                label = node.displayName,
                panelType = node.type.name,
                ratingText = node.dataBlock.ratedKva ?: node.dataBlock.deviceTypeAndRating ?: "",
                statusColorHex = when (node.status) {
                    SldValidationStatus.VALID -> "#3DD68C"
                    SldValidationStatus.WARNING -> "#FFC107"
                    SldValidationStatus.INVALID -> "#F44336"
                    SldValidationStatus.DISCONNECTED -> "#9E9E9E"
                    SldValidationStatus.DATA_REQUIRED -> "#3B9EFF"
                },
                x = xCenter - 70f,
                y = yTop,
                width = 140f,
                height = BUSBAR_HEIGHT,
                circuit = matchingCircuit,
                isSource = node.type in listOf(
                    SldNodeType.UTILITY_SOURCE,
                    SldNodeType.TRANSFORMER,
                    SldNodeType.GENERATOR,
                    SldNodeType.ATS
                ),
                dataBlock = node.dataBlock
            )
            layoutNodes[pNodeId] = pNode
            layoutNodes[node.id] = pNode

            children.forEach { childId ->
                val childW = subtreeWidths[childId] ?: MIN_CHILD_SPACING
                val childCenter = currentX + (childW / 2f)
                val childY = yTop + LEVEL_HEIGHT

                positionNode(childId, childCenter, childY)

                val edge = graph.edges.find { it.fromNodeId == nodeId && it.toNodeId == childId }
                if (edge != null) {
                    layoutEdges.add(
                        PositionedSldEdge(
                            fromId = pNodeId,
                            toId = nodeMap[childId]?.rawCircuitId?.toString() ?: childId,
                            label = edge.label ?: "",
                            pathPoints = listOf(
                                Pair(childCenter, yTop + BUSBAR_HEIGHT),
                                Pair(childCenter, childY)
                            ),
                            isEmergency = false,
                            labelStaggerY = 0f
                        )
                    )
                }
                currentX += childW + 30f
            }
        }

        var currentRootX = MARGIN
        rootIds.forEach { rootId ->
            val w = subtreeWidths[rootId] ?: MIN_CHILD_SPACING
            positionNode(rootId, currentRootX + w / 2f, 0f)
            currentRootX += w + MARGIN
        }

        // Handle ATS emergency edges
        graph.edges.filter { it.type == SldEdgeType.ATS_EMERGENCY_PATH }.forEach { emEdge ->
            val fromNode = layoutNodes[emEdge.fromNodeId]
            val toNode = layoutNodes[emEdge.toNodeId]
            if (fromNode != null && toNode != null) {
                val fromCenter = fromNode.x + fromNode.width / 2f
                val toCenter = toNode.x + toNode.width / 2f
                layoutEdges.add(
                    PositionedSldEdge(
                        fromId = emEdge.fromNodeId,
                        toId = emEdge.toNodeId,
                        label = emEdge.label ?: "EMERGENCY",
                        pathPoints = listOf(
                            Pair(fromCenter, fromNode.y + 40f),
                            Pair(fromCenter, toNode.y - 50f),
                            Pair(toCenter + 20f, toNode.y - 50f),
                            Pair(toCenter + 20f, toNode.y)
                        ),
                        isEmergency = true
                    )
                )
            }
        }

        // Refine node widths for busbars
        layoutNodes.forEach { (id, pNode) ->
            val children = childrenMap[id] ?: emptyList()
            if (children.size > 1) {
                val firstId = children.firstOrNull()
                val lastId = children.lastOrNull()
                val firstChild = if (firstId != null) layoutNodes[firstId] else null
                val lastChild = if (lastId != null) layoutNodes[lastId] else null
                if (firstChild != null && lastChild != null) {
                    val firstChildCenterX = firstChild.x + firstChild.width / 2f
                    val lastChildCenterX = lastChild.x + lastChild.width / 2f
                    val newX = firstChildCenterX - 20f
                    val newWidth = (lastChildCenterX - firstChildCenterX) + 40f
                    val updated = pNode.copy(x = newX, width = maxOf(newWidth, 100f))
                    layoutNodes[id] = updated
                    layoutNodes[pNode.id] = updated
                }
            } else if (children.size == 1) {
                val childId = children.firstOrNull()
                val child = if (childId != null) layoutNodes[childId] else null
                if (child != null) {
                    val childCenterX = child.x + child.width / 2f
                    val updated = pNode.copy(x = childCenterX - 60f, width = 120f)
                    layoutNodes[id] = updated
                    layoutNodes[pNode.id] = updated
                }
            } else {
                // Leaf node
                val centerX = pNode.x + pNode.width / 2f
                val updated = pNode.copy(x = centerX - 50f, width = 100f, height = 20f)
                layoutNodes[id] = updated
                layoutNodes[pNode.id] = updated
            }
        }

        // Unconnected Nodes
        val mainMaxY = (layoutNodes.values.maxOfOrNull { it.y + it.height } ?: MARGIN) + 120f
        var unconnX = MARGIN
        var unconnY = mainMaxY
        val minUnconnWidth = 140f

        graph.unconnectedNodes.forEach { unNode ->
            val matchingCircuit = if (unNode.rawCircuitId != null) {
                circuits.find { it.id == unNode.rawCircuitId }
            } else {
                circuits.find { "circ_${it.id}" == unNode.id }
            }

            val pNode = PositionedSldNode(
                id = unNode.id,
                label = unNode.displayName,
                panelType = unNode.type.name,
                ratingText = unNode.dataBlock.ratedKva ?: unNode.dataBlock.deviceTypeAndRating ?: "STANDALONE",
                statusColorHex = "#9E9E9E",
                x = unconnX,
                y = unconnY,
                width = minUnconnWidth,
                height = 24f,
                circuit = matchingCircuit,
                isSource = unNode.type in listOf(
                    SldNodeType.UTILITY_SOURCE,
                    SldNodeType.TRANSFORMER,
                    SldNodeType.GENERATOR,
                    SldNodeType.ATS
                ),
                dataBlock = unNode.dataBlock
            )
            layoutNodes[unNode.id] = pNode

            unconnX += minUnconnWidth + 40f
            if (unconnX > 1200f) {
                unconnX = MARGIN
                unconnY += 120f
            }
        }

        val maxX = (layoutNodes.values.maxOfOrNull { it.x + it.width } ?: 1000f) + MARGIN
        val maxY = (layoutNodes.values.maxOfOrNull { it.y + it.height } ?: 1000f) + MARGIN

        return SldEngineResult(
            nodes = layoutNodes.values.distinct(),
            edges = layoutEdges,
            bounds = Rect(0f, 0f, maxX, maxY),
            validationIssues = graph.validationIssues
        )
    }
}
