package com.example.sld

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.sp
import com.example.data.CircuitEntity

/**
 * Vertical Tier Riser Layout Engine for VoltAtlas SLD (BUG-5).
 * Maps electrical network graph nodes to hierarchical vertical tiers (0 to 4)
 * with collision-free horizontal lane packing and orthogonal edge routing.
 */
object RiserLayoutEngine {

    const val MARGIN_X = 100f
    const val MARGIN_Y = 100f
    const val MIN_HORIZONTAL_GAP = 160f // Spacing between adjacent nodes & labels to prevent overlap

    fun getBaseTierIndex(type: SldNodeType): Int = when (type) {
        SldNodeType.UTILITY_SOURCE -> -1 // Utility Grid sits at Tier -1 (very top above Transformer)
        SldNodeType.TRANSFORMER,
        SldNodeType.GENERATOR -> 0 // Transformer & Generator sit at Tier 0

        SldNodeType.MDB,
        SldNodeType.MCC,
        SldNodeType.ATS -> 1

        SldNodeType.SMDB -> 2

        SldNodeType.DB -> 3

        SldNodeType.FINAL_CIRCUIT,
        SldNodeType.LOAD -> 4

        else -> 1
    }

    fun calculateLayout(
        graph: ElectricalNetworkGraph,
        circuits: List<CircuitEntity> = emptyList(),
        textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset> = emptyMap()
    ): SldEngineResult {
        val nodeMap = graph.nodes.associateBy { it.id }
        val circuitMap = circuits.associateBy { it.id }

        // 1. Calculate Effective Tier for every node
        val effectiveTier = mutableMapOf<String, Int>()
        graph.nodes.forEach { node ->
            effectiveTier[node.id] = getBaseTierIndex(node.type)
        }

        // Iterative top-down tier relaxation: childTier >= parentTier + 1
        var changed = true
        var passes = 0
        while (changed && passes < graph.nodes.size) {
            changed = false
            passes++
            graph.edges.forEach { edge ->
                val parentNode = nodeMap[edge.fromNodeId]
                val childNode = nodeMap[edge.toNodeId]
                if (parentNode != null && childNode != null) {
                    val isSourceToSource = parentNode.type == SldNodeType.UTILITY_SOURCE && childNode.type == SldNodeType.TRANSFORMER

                    // Do not push Transformer out of Tier 0, and do not relax tier via secondary ATS incomer edges
                    if (!isSourceToSource && edge.type != SldEdgeType.ATS_EMERGENCY_PATH && edge.type != SldEdgeType.ATS_NORMAL_PATH) {
                        val parentTier = effectiveTier[edge.fromNodeId] ?: 0
                        val currentChildTier = effectiveTier[edge.toNodeId] ?: 0
                        val minChildTier = parentTier + 1
                        if (minChildTier > currentChildTier) {
                            effectiveTier[edge.toNodeId] = minChildTier
                            changed = true
                        }
                    }
                }
            }
        }

        // 2. Group nodes by tier
        val nodesByTier = mutableMapOf<Int, MutableList<SldNode>>()
        graph.nodes.forEach { node ->
            val tier = effectiveTier[node.id] ?: 0
            nodesByTier.getOrPut(tier) { mutableListOf() }.add(node)
        }

        // Tier Vertical Gap based on SldTheme
        val tierGap = SldTheme.tierVerticalSpacing() // 288.0f
        val MIN_TREE_GAP = 220f

        // 3. 2D Subtree Tree Layout Algorithm
        val positionedNodesMap = mutableMapOf<String, PositionedSldNode>()

        val childrenMap = mutableMapOf<String, MutableList<String>>()
        val parentMap = mutableMapOf<String, String>()

        graph.edges.forEach { edge ->
            if (edge.type != SldEdgeType.ATS_EMERGENCY_PATH && edge.type != SldEdgeType.ATS_NORMAL_PATH) {
                childrenMap.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId)
                parentMap[edge.toNodeId] = edge.fromNodeId
            }
        }

        // Compute node width for each node
        val nodeWidths = graph.nodes.associate { node ->
            node.id to computeNodeWidth(node, textMeasurer)
        }

        // Recursive subtree width computation
        val subtreeWidths = mutableMapOf<String, Float>()
        fun computeSubtreeWidth(nodeId: String): Float {
            val nWidth = nodeWidths[nodeId] ?: 140f
            val children = childrenMap[nodeId] ?: emptyList()
            if (children.isEmpty()) {
                subtreeWidths[nodeId] = nWidth
                return nWidth
            }
            var totalChildrenWidth = 0f
            children.forEachIndexed { idx, childId ->
                if (idx > 0) totalChildrenWidth += MIN_TREE_GAP
                totalChildrenWidth += computeSubtreeWidth(childId)
            }
            val sWidth = maxOf(nWidth, totalChildrenWidth)
            subtreeWidths[nodeId] = sWidth
            return sWidth
        }

        // Compute subtrees for downstream root nodes (Tier 1+ nodes whose parent is null or Tier <= 0 source)
        val nonSourceNodes = graph.nodes.filter { (effectiveTier[it.id] ?: 0) > 0 }
        val downstreamRoots = nonSourceNodes.filter { node ->
            val pId = parentMap[node.id]
            pId == null || (effectiveTier[pId] ?: 0) <= 0 || !nodeMap.containsKey(pId)
        }.sortedBy { node ->
            when (node.type) {
                SldNodeType.MDB -> 0
                SldNodeType.MCC -> 1
                SldNodeType.ATS -> 2
                SldNodeType.SMDB -> 3
                else -> 4
            }
        }

        downstreamRoots.forEach { rootNode ->
            computeSubtreeWidth(rootNode.id)
        }

        var currentRootX = MARGIN_X

        fun positionSubtree(nodeId: String, startX: Float) {
            val node = nodeMap[nodeId] ?: return
            val tier = effectiveTier[nodeId] ?: 1
            val nWidth = nodeWidths[nodeId] ?: 140f
            val sWidth = subtreeWidths[nodeId] ?: nWidth

            val centerX = startX + sWidth / 2f
            val posX = centerX - nWidth / 2f
            val tierOffsetY = (tier + 1) * tierGap
            val posY = MARGIN_Y + tierOffsetY

            val statusHex = colorHexForStatus(node.status)
            val circ = node.rawCircuitId?.let { circuitMap[it] }

            val isNodeSource = isSourceNode(node.type)
            val nodeHeight = if (isNodeSource) 48f else 16f

            positionedNodesMap[node.id] = PositionedSldNode(
                id = node.id,
                label = node.displayName,
                panelType = node.type.name,
                ratingText = node.dataBlock.ratedKva ?: node.dataBlock.deviceTypeAndRating ?: "",
                statusColorHex = statusHex,
                x = posX,
                y = posY,
                width = nWidth,
                height = nodeHeight,
                circuit = circ,
                isSource = isNodeSource,
                dataBlock = node.dataBlock,
                status = node.status
            )

            val children = childrenMap[nodeId] ?: emptyList()
            if (children.isNotEmpty()) {
                var childrenBlockWidth = 0f
                children.forEachIndexed { idx, cId ->
                    if (idx > 0) childrenBlockWidth += MIN_TREE_GAP
                    childrenBlockWidth += (subtreeWidths[cId] ?: (nodeWidths[cId] ?: 140f))
                }

                var childStartX = startX + (sWidth - childrenBlockWidth) / 2f
                children.forEach { childId ->
                    val childSubWidth = subtreeWidths[childId] ?: (nodeWidths[childId] ?: 140f)
                    positionSubtree(childId, childStartX)
                    childStartX += childSubWidth + MIN_TREE_GAP
                }
            }
        }

        downstreamRoots.forEach { rootNode ->
            val rootSubWidth = subtreeWidths[rootNode.id] ?: (nodeWidths[rootNode.id] ?: 140f)
            positionSubtree(rootNode.id, currentRootX)
            currentRootX += rootSubWidth + 120f
        }

        // Safe handling for any unpositioned nodes: position in a clear side lane
        nonSourceNodes.forEach { node ->
            if (!positionedNodesMap.containsKey(node.id)) {
                computeSubtreeWidth(node.id)
                val subW = subtreeWidths[node.id] ?: 140f
                positionSubtree(node.id, currentRootX)
                currentRootX += subW + 120f
            }
        }

        // Position Tier 0 & Tier -1 Sources (Utility Grid, Transformer, Generator)
        val sourceNodes = graph.nodes.filter { (effectiveTier[it.id] ?: 0) <= 0 }
        var defaultSourceX = MARGIN_X
        val sortedSources = sourceNodes.sortedBy { node ->
            when (node.type) {
                SldNodeType.TRANSFORMER -> 0
                SldNodeType.UTILITY_SOURCE -> 1
                SldNodeType.GENERATOR -> 2
                else -> 3
            }
        }

        sortedSources.forEach { node ->
            val w = nodeWidths[node.id] ?: 140f
            val h = 48f
            val statusHex = colorHexForStatus(node.status)

            val tierIndex = effectiveTier[node.id] ?: 0
            val posY = MARGIN_Y + (tierIndex + 1) * tierGap

            val alignedX = when (node.type) {
                SldNodeType.TRANSFORMER -> {
                    // Align Transformer directly above its primary MDB downstream root
                    val primaryMdb = downstreamRoots.firstOrNull { it.type == SldNodeType.MDB } ?: downstreamRoots.firstOrNull()
                    val mdbPos = primaryMdb?.let { positionedNodesMap[it.id] }
                    if (mdbPos != null) mdbPos.x + mdbPos.width / 2f - w / 2f else defaultSourceX
                }
                SldNodeType.UTILITY_SOURCE -> {
                    // Align Utility Grid directly above Transformer (processed after Transformer)
                    val txPos = positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("TRANSFORMER") }
                    if (txPos != null) txPos.x + txPos.width / 2f - w / 2f else defaultSourceX
                }
                SldNodeType.GENERATOR -> {
                    // Lock Generator directly above its connected ATS emergency port as a vertically aligned column
                    val matchedAtsEdge = graph.edges.firstOrNull { it.fromNodeId == node.id && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                    val atsPos = matchedAtsEdge?.let { positionedNodesMap[it.toNodeId] }
                        ?: positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("ATS") }
                    val txPos = positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("TRANSFORMER") }

                    if (atsPos != null) {
                        val idealGenX = (atsPos.x + atsPos.width * 0.75f) - w / 2f
                        val minClearanceX = if (txPos != null) txPos.x + txPos.width + 120f else MARGIN_X

                        if (idealGenX < minClearanceX) {
                            val shiftDelta = minClearanceX - idealGenX
                            // Shift ATS and its entire downstream subtree together so they stay vertically aligned
                            shiftSubtree(atsPos.id, shiftDelta, positionedNodesMap, childrenMap)
                            idealGenX + shiftDelta
                        } else {
                            idealGenX
                        }
                    } else {
                        // Direct generator loads (no ATS) or unassigned: align above primary downstream panel or default clearance
                        val directLoadEdge = graph.edges.firstOrNull { it.fromNodeId == node.id }
                        val directLoadPos = directLoadEdge?.let { positionedNodesMap[it.toNodeId] }
                        if (directLoadPos != null) {
                            directLoadPos.x + directLoadPos.width / 2f - w / 2f
                        } else {
                            maxOf(defaultSourceX, currentRootX)
                        }
                    }
                }
                else -> defaultSourceX
            }

            positionedNodesMap[node.id] = PositionedSldNode(
                id = node.id,
                label = node.displayName,
                panelType = node.type.name,
                ratingText = node.dataBlock.ratedKva ?: node.dataBlock.deviceTypeAndRating ?: "",
                statusColorHex = statusHex,
                x = alignedX,
                y = posY,
                width = w,
                height = h,
                circuit = node.rawCircuitId?.let { circuitMap[it] },
                isSource = true,
                dataBlock = node.dataBlock,
                status = node.status
            )
            defaultSourceX = maxOf(defaultSourceX, alignedX + w + 180f)
        }

        // 3.5 Dynamic Automatic Collision Avoidance Pass
        applyDynamicCollisionAvoidance(positionedNodesMap, textMeasurer, childrenMap)

        // 3.6 Manual Position Overrides
        manualPositions.forEach { (nodeId, offset) ->
            val existingNode = positionedNodesMap[nodeId]
            if (existingNode != null) {
                positionedNodesMap[nodeId] = existingNode.copy(x = offset.x, y = offset.y)
            }
        }

        // 4. Orthogonal Edge Routing with Dedicated Source Routing Channel
        // 4a. Shared-source ATS NORMAL incomers get staggered channels so parallel
        //     wires and their NORMAL labels never overlap (see SldAtsRoutingTest).
        val normalAtsIncomerIndex = HashMap<String, Int>()
        graph.edges
            .filter { it.type != SldEdgeType.ATS_EMERGENCY_PATH }
            .filter { edge -> nodeMap[edge.toNodeId]?.type == SldNodeType.ATS || edge.type == SldEdgeType.ATS_NORMAL_PATH }
            .groupBy { it.fromNodeId }
            .values.forEach { siblings ->
                siblings.sortedBy { positionedNodesMap[it.toNodeId]?.x ?: 0f }
                    .forEachIndexed { index, edge ->
                        normalAtsIncomerIndex["${edge.fromNodeId}->${edge.toNodeId}"] = index
                    }
            }

        val positionedEdges = mutableListOf<PositionedSldEdge>()
        graph.edges.forEach { edge ->
            val fromNode = positionedNodesMap[edge.fromNodeId]
            val toNode = positionedNodesMap[edge.toNodeId]

            if (fromNode != null && toNode != null) {
                val isToAts = toNode.panelType.uppercase().contains("ATS") || edge.type == SldEdgeType.ATS_NORMAL_PATH || edge.type == SldEdgeType.ATS_EMERGENCY_PATH

                val points = if (isToAts) {
                    val portOffsetX = if (edge.type == SldEdgeType.ATS_EMERGENCY_PATH) toNode.width * 0.75f else toNode.width * 0.25f
                    val toXPort = toNode.x + portOffsetX
                    val toYPort = toNode.y - 24f
                    val fromXPort = fromNode.x + fromNode.width / 2f
                    val fromYPort = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)

                    val isEmergencyEdge = edge.type == SldEdgeType.ATS_EMERGENCY_PATH
                    val staggerStep = SldTheme.feederDrop() / 4f
                    val siblingIndex = if (isEmergencyEdge) 0
                        else normalAtsIncomerIndex["${edge.fromNodeId}->${edge.toNodeId}"] ?: 0

                    val atsPoints = if (fromNode.y + 50f < toNode.y) {
                        // Tier-0 sources (transformer/generator) sit a full tier above the
                        // ATS: their below-source channel already approaches the ATS top
                        // terminals from above (locked behavior).
                        val channelY = fromYPort + 95f + siblingIndex * staggerStep
                        if (Math.abs(fromXPort - toXPort) < 5f) {
                            listOf(Pair(toXPort, fromYPort), Pair(toXPort, toYPort))
                        } else {
                            listOf(
                                Pair(fromXPort, fromYPort),
                                Pair(fromXPort, channelY),
                                Pair(toXPort, channelY),
                                Pair(toXPort, toYPort)
                            )
                        }
                    } else {
                        // Same-tier (or below) panel sources (e.g. an MDB sharing the ATS
                        // tier): the incomer must leave the panel's output port DOWNWARD.
                        // Rising above the panel would run collinear with the upstream
                        // feeder and pass through the panel body, making the wire visually
                        // appear to originate from the transformer (Gate 3 regression).
                        // Instead: drop into the inter-tier channel, cross BELOW the ATS
                        // enclosure, rise just OUTSIDE its left edge, then approach the top
                        // terminal from above.
                        val maxBottom = maxOf(fromNode.y + fromNode.height, toNode.y + toNode.height)
                        val lowChannelY = maxBottom + staggerStep + siblingIndex * staggerStep
                        val riseX = toNode.x - staggerStep
                        val highChannelY = toYPort - staggerStep
                        listOf(
                            Pair(fromXPort, fromYPort),
                            Pair(fromXPort, lowChannelY),
                            Pair(riseX, lowChannelY),
                            Pair(riseX, highChannelY),
                            Pair(toXPort, highChannelY),
                            Pair(toXPort, toYPort)
                        )
                    }
                    atsPoints
                } else {
                    val fromX = fromNode.x + fromNode.width / 2f
                    val fromY = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)

                    val toX = toNode.x + toNode.width / 2f
                    val toY = toNode.y - (if (toNode.isSource) 24f else 0f)

                    if (fromX == toX) {
                        listOf(Pair(fromX, fromY), Pair(toX, toY))
                    } else {
                        val midY = fromY + (toY - fromY) / 2f
                        listOf(
                            Pair(fromX, fromY),
                            Pair(fromX, midY),
                            Pair(toX, midY),
                            Pair(toX, toY)
                        )
                    }
                }

                positionedEdges.add(
                    PositionedSldEdge(
                        fromId = edge.fromNodeId,
                        toId = edge.toNodeId,
                        label = edge.label ?: "",
                        pathPoints = points,
                        isEmergency = (edge.type == SldEdgeType.ATS_EMERGENCY_PATH)
                    )
                )
            }
        }

        // 5. Generate Tier Datum Lines for Populated Tiers
        val sortedTiers = effectiveTier.values.distinct().sorted()
        val tierDatums = sortedTiers.map { tierIndex ->
            val label = when (tierIndex) {
                -1 -> "UTILITY INCOMER (22kV)"
                0 -> "TRANSFORMER / GENERATOR"
                1 -> "MAIN DISTRIBUTION"
                2 -> "SUB DISTRIBUTION"
                3 -> "DISTRIBUTION"
                4 -> "FINAL CIRCUITS"
                else -> "TIER $tierIndex"
            }
            val datumY = MARGIN_Y + (tierIndex + 1) * tierGap
            TierDatumLine(
                label = label,
                y = datumY,
                tierIndex = tierIndex
            )
        }

        // 6. Calculate overall bounding box
        val allNodesList = positionedNodesMap.values.toList()
        val minX = allNodesList.minOfOrNull { it.x - 50f } ?: 0f
        val maxX = allNodesList.maxOfOrNull { it.x + it.width + 100f } ?: 1000f
        val minY = 0f
        val maxY = allNodesList.maxOfOrNull { it.y + 100f } ?: 800f

        return SldEngineResult(
            nodes = allNodesList,
            edges = positionedEdges,
            bounds = Rect(minX, minY, maxX, maxY),
            validationIssues = graph.validationIssues,
            tierDatums = tierDatums
        )
    }

    private fun isSourceNode(type: SldNodeType): Boolean = when (type) {
        SldNodeType.UTILITY_SOURCE,
        SldNodeType.TRANSFORMER,
        SldNodeType.GENERATOR,
        SldNodeType.ATS -> true
        else -> false
    }

    private fun computeNodeWidth(node: SldNode, textMeasurer: androidx.compose.ui.text.TextMeasurer?): Float {
        val nameLen = node.displayName.length
        val ratingText = node.dataBlock.ratedKva ?: node.dataBlock.deviceTypeAndRating ?: ""
        val ratingLen = ratingText.length
        val cableText = node.dataBlock.cableDescription ?: ""
        val cableLen = cableText.length
        
        val labelPixelWidth = if (textMeasurer != null) {
            val style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            // Add a 1.25x safety multiplier because the Canvas uses a custom font family
            // (e.g. Inter/Roboto) which is generally wider than the default system font used here.
            val safetyMultiplier = 1.25f
            maxOf(
                if (node.displayName.isNotEmpty()) textMeasurer.measure(node.displayName, style).size.width.toFloat() * safetyMultiplier else 0f,
                if (ratingText.isNotEmpty()) textMeasurer.measure(ratingText, style).size.width.toFloat() * safetyMultiplier else 0f,
                if (cableText.isNotEmpty()) textMeasurer.measure(cableText, style).size.width.toFloat() * safetyMultiplier else 0f
            )
        } else {
            maxOf(nameLen, ratingLen, cableLen) * 8f
        }
        
        val baseWidth = maxOf(180f, labelPixelWidth + 50f)
        
        return when (node.type) {
            SldNodeType.UTILITY_SOURCE -> baseWidth
            SldNodeType.TRANSFORMER, SldNodeType.GENERATOR -> {
                maxOf(180f, 100f + labelPixelWidth)
            }
            SldNodeType.ATS -> {
                maxOf(180f, 60f + labelPixelWidth)
            }
            else -> baseWidth
        }
    }

    private fun colorHexForStatus(status: SldValidationStatus): String = when (status) {
        SldValidationStatus.VALID -> SldSemanticColors.RISER_VALID
        SldValidationStatus.WARNING -> SldSemanticColors.RISER_WARNING
        SldValidationStatus.INVALID -> SldSemanticColors.RISER_INVALID
        SldValidationStatus.DISCONNECTED -> SldSemanticColors.RISER_NEUTRAL
        SldValidationStatus.DATA_REQUIRED -> SldSemanticColors.RISER_DATA_REQUIRED
    }

    private fun applyDynamicCollisionAvoidance(
        positionedNodesMap: MutableMap<String, PositionedSldNode>,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        childrenMap: Map<String, List<String>>
    ) {
        val nodesByY = positionedNodesMap.values.groupBy { it.y }

        var hasCollision = true
        var iteration = 0
        val maxIterations = 10

        while (hasCollision && iteration < maxIterations) {
            hasCollision = false
            iteration++

            nodesByY.forEach { (_, tierNodes) ->
                val sorted = tierNodes.sortedBy { it.x }
                for (i in 0 until sorted.size - 1) {
                    val nodeA = sorted[i]
                    val nodeB = sorted[i + 1]

                    val aLabelWidth = measureLabelWidth(nodeA, textMeasurer)
                    val aRight = maxOf(nodeA.x + nodeA.width, nodeA.x + aLabelWidth)
                    val minAllowedBLeft = aRight + MIN_HORIZONTAL_GAP

                    val currentBLeft = nodeB.x
                    if (currentBLeft < minAllowedBLeft) {
                        val shiftDelta = minAllowedBLeft - currentBLeft
                        shiftSubtree(nodeB.id, shiftDelta, positionedNodesMap, childrenMap)
                        hasCollision = true
                    }
                }
            }
        }
    }

    private fun measureLabelWidth(node: PositionedSldNode, textMeasurer: androidx.compose.ui.text.TextMeasurer?): Float {
        if (textMeasurer == null) return node.label.length * 9f
        val style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        val l1 = textMeasurer.measure(node.label, style).size.width.toFloat()
        val l2 = textMeasurer.measure(node.ratingText, style).size.width.toFloat()
        val l3 = node.dataBlock?.cableDescription?.let { textMeasurer.measure(it, style).size.width.toFloat() } ?: 0f
        return maxOf(l1, l2, l3) * 1.25f
    }

    private fun shiftSubtree(
        rootId: String,
        deltaX: Float,
        positionedNodesMap: MutableMap<String, PositionedSldNode>,
        childrenMap: Map<String, List<String>>,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        if (!visited.add(rootId)) return
        val node = positionedNodesMap[rootId] ?: return
        positionedNodesMap[rootId] = node.copy(x = node.x + deltaX)
        childrenMap[rootId]?.forEach { childId ->
            shiftSubtree(childId, deltaX, positionedNodesMap, childrenMap, visited)
        }
    }
}
