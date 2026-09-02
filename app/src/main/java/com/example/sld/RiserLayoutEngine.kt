package com.example.sld

import androidx.compose.ui.geometry.Offset
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
    const val MIN_TREE_GAP = 220f

    /**
     * Left/right padding of the MDB enclosure boundary relative to the panel's
     * positioned node box (SldCanvas draws encLeft = node.x - 36 and
     * encRight = node.x + node.width + 36). Mirrors that value so the layout
     * engine reasons about the *actually rendered* panel box outline.
     */
    const val MAIN_PANEL_ENCLOSURE_PAD = 36f

    /**
     * Mandatory horizontal clearance between a panel's actual rendered box
     * right edge and the left edge of an ATS (or the previous ATS in a
     * side-by-side pair). Reuses the existing SLD spacing token so the safety
     * margin is consistent with all other node-pair gaps.
     */
    const val ATS_TO_PANEL_MIN_GAP = MIN_HORIZONTAL_GAP

    /** Node types rendered as busbar panels — these expose explicit tap ports (Roadmap §2.3). */
    val PANEL_BUSBAR_TYPES = setOf(
        SldNodeType.BUS,
        SldNodeType.MDB,
        SldNodeType.SMDB,
        SldNodeType.DB,
        SldNodeType.MCC
    )

    /** Same set as [PANEL_BUSBAR_TYPES] keyed by node-type name (for [PositionedSldNode.panelType]). */
    val PANEL_BUSBAR_TYPE_NAMES: Set<String> = PANEL_BUSBAR_TYPES.mapTo(mutableSetOf()) { it.name }

    /** Minimum clearance between a busbar tap port and either end of the bar. */
    const val BUSBAR_TAP_END_PAD = 24f

    /**
     * Standard horizontal clearance between two switchboards connected by a
     * bus tie (Main-Tie-Main, Roadmap Phase 2 §2.3). The BUS_TIE breaker node
     * is centered in this gap at the shared busbar centerline. The effective
     * gap grows automatically when the tie label is wider than the default.
     */
    const val TIE_BREAKER_GAP = 300f

    /**
     * Minimum clearance an emergency riser must keep from any registered
     * routing obstacle (busbar rail, panel box, label envelope) when a
     * dedicated vertical corridor lane is allocated (Roadmap Phase 2 §2.4).
     */
    const val EMERGENCY_CORRIDOR_CLEARANCE = 24f

    /** Candidate lane offsets scanned around a preferred X when allocating a free vertical corridor. */
    val CORRIDOR_LANE_CANDIDATE_OFFSETS = listOf(0f, 40f, 80f, 120f, 160f, 200f, 240f, 300f, 360f, 420f)

    /**
     * Minimum separation between two adjacent busbar tap ports (Roadmap Phase 2
     * §2.3). When child-anchored taps bunch tighter than this even after the
     * sequential separation pass, the port model falls back to the proportional
     * allocation [proportionalPortX] which spreads the K ports evenly.
     */
    const val MIN_BUSBAR_PORT_SPACING = 24f

    /**
     * Proportional port model (Roadmap Phase 2 §2.3): the i-th of K ports on a
     * rail starting at [start] with span [span] sits at
     * `start + (i + 0.5) / K * span` — dead-center of its equal share of the
     * rail, so ports can never bunch against one end regardless of how the
     * feeder columns happen to be laid out. Downstream feeders still exit the
     * bottom ports (`y + height`); ATS terminals keep their dedicated
     * [SldSymbols.atsSymbolGeometry] contract (NORMAL 0.25 / EMERGENCY 0.75).
     */
    fun proportionalPortX(index: Int, count: Int, start: Float, span: Float): Float =
        start + (index + 0.5f) / count * span

    data class LayerAssignmentResult(
        val effectiveTier: Map<String, Int>,
        val nodesByTier: Map<Int, List<SldNode>>
    )

    data class BoundingBoxResult(
        val nodeWidths: Map<String, Float>
    )

    data class CoordinateAssignmentResult(
        val positionedNodes: Map<String, PositionedSldNode>,
        val busbarPorts: Map<String, List<BusbarPort>>
    )

    fun getBaseTierIndex(type: SldNodeType): Int = when (type) {
        SldNodeType.UTILITY_SOURCE -> -1 // Utility Grid sits at Tier -1 (very top above Transformer)
        SldNodeType.TRANSFORMER,
        SldNodeType.GENERATOR -> 0 // Transformer & Generator sit at Tier 0

        // Synchronizing bus sits one tier below its paralleled generators
        // (Tier 0) so GEN_SYNC_CONNECTION drops vertically into the bus; the
        // bus shares Tier 1 with the ATS boards it feeds via emergency paths.
        SldNodeType.SYNC_BUS,
        SldNodeType.MDB,
        SldNodeType.MCC,
        SldNodeType.ATS,
        SldNodeType.UPS -> 1

        // Bus ties live on the SAME tier as the boards they couple — they are
        // positioned/routed horizontally by the dedicated bus-tie pass (§2.3).
        SldNodeType.BUS_TIE -> 1

        SldNodeType.BUS -> 1

        SldNodeType.SMDB -> 2

        SldNodeType.DB -> 3

        SldNodeType.FINAL_CIRCUIT,
        SldNodeType.LOAD -> 4

        else -> 1
    }

    // =========================================================================
    // PASS 1 — LAYER ASSIGNMENT (Sugiyama Stage 1)
    // Assigns hierarchical vertical tiers (-1 to 4) without deciding any
    // horizontal coordinates or dimensions.
    // =========================================================================
    fun assignLayers(graph: ElectricalNetworkGraph): LayerAssignmentResult {
        val nodeMap = graph.nodes.associateBy { it.id }
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

                    // Do not push Transformer out of Tier 0, do not relax tier via
                    // secondary ATS emergency edges, and never relax through
                    // same-tier BUS_TIE_LINK couplers (they would wrongly push the
                    // coupled boards onto separate tiers and shove the tie below
                    // the busbar centerline). GEN_SYNC_CONNECTION stays ACTIVE so
                    // a synchronizing bus is forced one tier below its generators.
                    if (!isSourceToSource &&
                        edge.type != SldEdgeType.ATS_EMERGENCY_PATH &&
                        edge.type != SldEdgeType.BUS_TIE_LINK
                    ) {
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

        val nodesByTier = mutableMapOf<Int, MutableList<SldNode>>()
        graph.nodes.forEach { node ->
            val tier = effectiveTier[node.id] ?: 0
            nodesByTier.getOrPut(tier) { mutableListOf() }.add(node)
        }

        return LayerAssignmentResult(effectiveTier, nodesByTier)
    }

    // =========================================================================
    // PASS 2 — BOUNDING BOX & CONTENT MEASUREMENT (Sugiyama Stage 2)
    // Measures real text and icon dimensions for every node uniformly before
    // any coordinates are assigned.
    // =========================================================================
    fun computeBoundingBoxes(
        graph: ElectricalNetworkGraph,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?
    ): BoundingBoxResult {
        val nodeWidths = graph.nodes.associate { node ->
            node.id to computeNodeWidth(node, textMeasurer)
        }
        return BoundingBoxResult(nodeWidths)
    }

    // =========================================================================
    // PASS 3 — COORDINATE ASSIGNMENT & CLEARANCE (Sugiyama Stage 3)
    // The single authoritative pipeline for deciding (x, y) coordinates:
    // 3a. Subtree tree positioning (downstream roots & child branches)
    // 3b. Busbar width expansion (for panels spanning multiple children)
    // 3c. Source spine alignment (Utility, Transformer, Generator)
    // 3d. Dynamic 2D AABB collision avoidance
    // 3e. Manual position overrides
    // 3f. ATS-to-panel clearance enforcement (enforceAtsToPanelClearance)
    // 3g. Busbar port assignment (Roadmap §2.3)
    // =========================================================================
    fun assignCoordinates(
        graph: ElectricalNetworkGraph,
        circuits: List<CircuitEntity>,
        layers: LayerAssignmentResult,
        boxes: BoundingBoxResult,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset>
    ): CoordinateAssignmentResult {
        val nodeMap = graph.nodes.associateBy { it.id }
        val circuitMap = circuits.associateBy { it.id }
        val effectiveTier = layers.effectiveTier
        val nodeWidths = boxes.nodeWidths
        val tierGap = SldTheme.tierVerticalSpacing() // 288.0f

        val positionedNodesMap = mutableMapOf<String, PositionedSldNode>()
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        val parentMap = mutableMapOf<String, String>()

        graph.edges.forEach { edge ->
            // ATS emergency feeds and same-tier BUS_TIE_LINK couplers are NOT
            // parent→child vertical topology: emergency risers are geometric
            // columns and bus ties are horizontal same-tier couplers handled by
            // the dedicated bus-tie clustering pass (§2.3). GEN_SYNC_CONNECTION
            // stays in the tree so the synchronizing bus is positioned one
            // tier below its generators as a proper subtree root.
            if (edge.type != SldEdgeType.ATS_EMERGENCY_PATH &&
                edge.type != SldEdgeType.BUS_TIE_LINK
            ) {
                childrenMap.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId)
                parentMap[edge.toNodeId] = edge.fromNodeId
            }
        }

        // Branch category ordering under parent panels: DIRECT -> UPS_BACKED -> ATS_BACKED -> UNRESOLVED
        childrenMap.forEach { (parentId, children) ->
            children.sortWith(
                compareBy(
                    { childId ->
                        val childNode = nodeMap[childId]
                        val edge = graph.edges.firstOrNull { it.fromNodeId == parentId && it.toNodeId == childId }
                        when {
                            edge?.type == SldEdgeType.UNRESOLVED_PATH -> 3
                            childNode?.type == SldNodeType.ATS || edge?.type == SldEdgeType.ATS_NORMAL_PATH -> 2
                            childNode?.type == SldNodeType.UPS || edge?.type == SldEdgeType.UPS_INPUT -> 1
                            else -> 0 // DIRECT
                        }
                    },
                    { childId -> nodeMap[childId]?.rawCircuitId ?: 0 },
                    { childId -> childId }
                )
            )
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
            // BUS_TIE breakers are positioned by the dedicated same-tier pass,
            // never as free-standing subtree roots.
            if (node.type == SldNodeType.BUS_TIE) return@filter false
            val pId = parentMap[node.id]
            pId == null || (effectiveTier[pId] ?: 0) <= 0 || !nodeMap.containsKey(pId)
        }.sortedWith(
            compareBy(
                { node ->
                    when (node.type) {
                        SldNodeType.MDB -> 0
                        SldNodeType.MCC -> 1
                        SldNodeType.SMDB -> 2
                        SldNodeType.DB -> 3
                        SldNodeType.ATS -> 4
                        else -> 5
                    }
                },
                { it.rawCircuitId ?: 0 },
                { it.id }
            )
        )

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
            val nodeHeight = if (isNodeSource || node.type == SldNodeType.ATS || node.type == SldNodeType.UPS) 48f else 16f

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

                // Expand busbar width for panels with multiple children so it spans across all children's tap positions
                val isPanel = !isNodeSource || node.type == SldNodeType.MDB || node.type == SldNodeType.SMDB || node.type == SldNodeType.MCC
                if (children.size >= 2 && isPanel) {
                    val childNodes = children.mapNotNull { positionedNodesMap[it] }
                    if (childNodes.size >= 2) {
                        val tapCenters = childNodes.map {
                            if (it.panelType.uppercase().contains("ATS")) it.x + it.width * 0.25f
                            else it.x + it.width / 2f
                        }
                        val firstCenter = tapCenters.minOrNull() ?: (positionedNodesMap[node.id]!!.x + 40f)
                        val lastCenter = tapCenters.maxOrNull() ?: (positionedNodesMap[node.id]!!.x + 40f)
                        val margin = 40f
                        val spanWidth = (lastCenter - firstCenter) + margin * 2f
                        val newWidth = maxOf(nWidth, spanWidth)
                        val newX = firstCenter - margin
                        positionedNodesMap[node.id] = positionedNodesMap[node.id]!!.copy(
                            x = newX,
                            width = newWidth
                        )
                    }
                }
            }
        }

        downstreamRoots.forEach { rootNode ->
            positionSubtree(rootNode.id, currentRootX)
            val subtreeRight = getSubtreeRightBound(rootNode.id, positionedNodesMap, childrenMap, textMeasurer)
            currentRootX = maxOf(currentRootX + (subtreeWidths[rootNode.id] ?: 140f) + MIN_HORIZONTAL_GAP, subtreeRight + MIN_HORIZONTAL_GAP)
        }

        // Safe handling for any unpositioned nodes: position in a clear side lane
        nonSourceNodes.forEach { node ->
            if (!positionedNodesMap.containsKey(node.id)) {
                computeSubtreeWidth(node.id)
                positionSubtree(node.id, currentRootX)
                val subRight = getSubtreeRightBound(node.id, positionedNodesMap, childrenMap, textMeasurer)
                currentRootX = maxOf(currentRootX + (subtreeWidths[node.id] ?: 140f) + MIN_HORIZONTAL_GAP, subRight + MIN_HORIZONTAL_GAP)
            }
        }

        // Expand continuous busbar rail to cover takeoffs while respecting dedicated emergency lanes
        fun expandBusbarsAcrossTakeoffs() {
            positionedNodesMap.values.filter { nodeMap[it.id]?.type in PANEL_BUSBAR_TYPES }.forEach { panel ->
                val childEntries = (childrenMap[panel.id] ?: emptyList()).mapNotNull { positionedNodesMap[it] }
                if (childEntries.isNotEmpty()) {
                    val emergLanes = graph.edges
                        .filter { it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                        .mapNotNull { e ->
                            val genNode = positionedNodesMap[e.fromNodeId]
                            val atsNode = positionedNodesMap[e.toNodeId]
                            if (genNode != null && atsNode != null && genNode.y < panel.y + panel.height + 50f) {
                                atsNode.x + atsNode.width * 0.75f
                            } else null
                        }
                    val minEmergLane = emergLanes.minOrNull() ?: Float.POSITIVE_INFINITY
                    val maxAllowedRight = if (minEmergLane.isFinite()) minEmergLane - 20f else Float.POSITIVE_INFINITY

                    val allTapCenters = childEntries.map {
                        if (it.panelType.uppercase().contains("ATS")) it.x + it.width * 0.25f
                        else it.x + it.width / 2f
                    }
                    val minCenter = allTapCenters.minOrNull() ?: (panel.x + panel.width / 2f)
                    val rawMaxCenter = allTapCenters.maxOrNull() ?: (panel.x + panel.width / 2f)
                    val margin = BUSBAR_TAP_END_PAD + 20f
                    val newLeft = minOf(panel.x, minCenter - margin)
                    var newRight = maxOf(panel.x + panel.width, rawMaxCenter + margin)
                    if (newRight > maxAllowedRight) {
                        newRight = maxAllowedRight
                    }
                    val finalWidth = maxOf(nodeWidths[panel.id] ?: 140f, newRight - newLeft)
                    positionedNodesMap[panel.id] = panel.copy(
                        x = newLeft,
                        width = finalWidth
                    )
                }
            }
        }
        // NOTE: the §2.3 dedicated same-tier passes (bus-tie clustering +
        // synchronizing-bus centering) run LATER — after source placement,
        // collision avoidance and ATS clearance — so they see every node's
        // FINAL coordinate (see positionBusTiesAndSyncBuses call in §3f).

        expandBusbarsAcrossTakeoffs()

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
                    // Align Transformer directly above its specific fed panel / downstream root
                    val fedPanelEdge = graph.edges.firstOrNull { it.fromNodeId == node.id }
                    val fedPanelPos = fedPanelEdge?.let { positionedNodesMap[it.toNodeId] }
                        ?: downstreamRoots.firstOrNull { it.type == SldNodeType.MDB }?.let { positionedNodesMap[it.id] }
                    if (fedPanelPos != null) fedPanelPos.x + fedPanelPos.width / 2f - w / 2f else defaultSourceX
                }
                SldNodeType.UTILITY_SOURCE -> {
                    // Align Utility Grid directly above its specific connected Transformer
                    val fedTxEdge = graph.edges.firstOrNull { it.fromNodeId == node.id }
                    val fedTxPos = fedTxEdge?.let { positionedNodesMap[it.toNodeId] }
                        ?: positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("TRANSFORMER") }
                    if (fedTxPos != null) fedTxPos.x + fedTxPos.width / 2f - w / 2f else defaultSourceX
                }
                SldNodeType.GENERATOR -> {
                    // Paralleled genset: spread sibling generators symmetrically
                    // around their synchronizing bus center (Roadmap Phase 2 §2.1)
                    // so each GEN_SYNC_CONNECTION drops vertically into the bus.
                    val syncEdge = graph.edges.firstOrNull {
                        it.fromNodeId == node.id && it.type == SldEdgeType.GEN_SYNC_CONNECTION
                    }
                    val syncPos = syncEdge?.let { positionedNodesMap[it.toNodeId] }
                    if (syncPos != null) {
                        val siblings = graph.edges
                            .filter { it.toNodeId == syncEdge.toNodeId && it.type == SldEdgeType.GEN_SYNC_CONNECTION }
                            .map { it.fromNodeId }
                            .sorted()
                        val idx = siblings.indexOf(node.id).coerceAtLeast(0)
                        val n = siblings.size.coerceAtLeast(1)
                        val blockWidth = n * w + (n - 1) * MIN_HORIZONTAL_GAP
                        val blockStart = syncPos.x + syncPos.width / 2f - blockWidth / 2f
                        blockStart + idx * (w + MIN_HORIZONTAL_GAP)
                    } else {
                    // Lock Generator directly above its connected ATS emergency port as a vertically aligned column
                    val matchedAtsEdge = graph.edges.firstOrNull { it.fromNodeId == node.id && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                    val atsPos = matchedAtsEdge?.let { positionedNodesMap[it.toNodeId] }
                        ?: positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("ATS") }
                    val txPos = positionedNodesMap.values.firstOrNull { it.panelType.uppercase().contains("TRANSFORMER") }

                    if (atsPos != null) {
                        val idealGenX = (atsPos.x + atsPos.width * 0.75f) - w / 2f
                        val minClearanceX = if (txPos != null) {
                            val txBounds = getNodeVisualBounds(txPos, textMeasurer)
                            txBounds.right + MIN_HORIZONTAL_GAP
                        } else MARGIN_X

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
                    } // end of legacy "lock above ATS emergency port" else-block
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

        // 3d. Dynamic Automatic Collision Avoidance Pass
        applyDynamicCollisionAvoidance(positionedNodesMap, textMeasurer, childrenMap, graph)

        // 3e. Manual Position Overrides
        manualPositions.forEach { (nodeId, offset) ->
            val existingNode = positionedNodesMap[nodeId]
            if (existingNode != null) {
                positionedNodesMap[nodeId] = existingNode.copy(x = offset.x, y = offset.y)
            }
        }

        // 3f. ATS <-> Panel-Box Clearance Enforcement (root-cause guarantee)
        // The ATS node X is derived from the NORMAL-source panel's FINAL rendered
        // box right edge as   atsX = panelBoxRight + ATS_TO_PANEL_MIN_GAP   (plus
        // the panel's rendered width, i.e. panel.x + panel.width + enclosure pad).
        // This pass runs LAST - after subtree positioning AND after manual
        // position overrides - so a persisted stale/dragged ATS position can
        // never re-introduce an overlap with the panel box outline or with a
        // neighbouring ATS, regardless of how many feeder columns the panel box
        val rootNodeIds = downstreamRoots.map { it.id }.toSet()
        enforceAtsToPanelClearance(positionedNodesMap, textMeasurer, childrenMap, graph, rootNodeIds)

        // §2.3 dedicated same-tier passes: cluster coupled boards with
        // TIE_BREAKER_GAP and center the synchronizing bus over its gensets.
        // Runs AFTER source placement (Tier 0/−1), the dynamic collision
        // avoidance pass and the ATS clearance enforcement, so it clusters and
        // centers against every node's FINAL coordinate.
        positionBusTiesAndSyncBuses(graph, positionedNodesMap, childrenMap)

        // 3g. Busbar Port Model: every panel node exposes ordered tap ports
        // for direct feeders, UPS input takeoffs, and ATS normal-input takeoffs
        // along the SAME continuous busbar rail.
        val busbarPorts: Map<String, List<BusbarPort>> = graph.nodes
            .filter { it.type in PANEL_BUSBAR_TYPES }
            .mapNotNull { panelNode ->
                val panel = positionedNodesMap[panelNode.id] ?: return@mapNotNull null
                val childEntries = (childrenMap[panelNode.id] ?: emptyList())
                    .mapNotNull { childId -> positionedNodesMap[childId]?.let { childId to it } }
                if (childEntries.isEmpty()) return@mapNotNull null

                val minTap = panel.x + BUSBAR_TAP_END_PAD
                val maxTap = panel.x + panel.width - BUSBAR_TAP_END_PAD

                val rawTaps = childEntries.map { (childId, child) ->
                    val childTapX = if (child.panelType.uppercase().contains("ATS")) {
                        child.x + child.width * 0.25f
                    } else {
                        child.x + child.width / 2f
                    }
                    childId to childTapX
                }
                val sortedChildren = rawTaps.sortedBy { it.second }
                val n = sortedChildren.size

                val ports = if (n == 1) {
                    val (childId, idealX) = sortedChildren.first()
                    listOf(BusbarPort(portIndex = 0, childNodeId = childId, tapX = idealX.coerceIn(minTap, maxTap)))
                } else {
                    val rawPorts = sortedChildren.mapIndexed { index, (childId, idealX) ->
                        BusbarPort(portIndex = index, childNodeId = childId, tapX = idealX.coerceIn(minTap, maxTap))
                    }
                    val resultList = mutableListOf<BusbarPort>()
                    var prevX = minTap - 1f
                    rawPorts.forEachIndexed { idx, p ->
                        val remaining = rawPorts.size - 1 - idx
                        val availableMax = maxTap - remaining * 10f
                        val tapX = maxOf(p.tapX, prevX + 1f).coerceIn(minTap, availableMax)
                        resultList.add(p.copy(tapX = tapX))
                        prevX = tapX
                    }
                    // Proportional relief (Roadmap Phase 2 §2.3): if the
                    // child-anchored taps still bunch tighter than
                    // MIN_BUSBAR_PORT_SPACING after the sequential separation
                    // pass, redistribute all K ports evenly across the rail
                    // span with [proportionalPortX].
                    val bunched = resultList.zipWithNext()
                        .any { (a, b) -> b.tapX - a.tapX < MIN_BUSBAR_PORT_SPACING }
                    if (bunched && resultList.size > 1) {
                        val span = maxTap - minTap
                        resultList.mapIndexed { idx, p ->
                            p.copy(tapX = proportionalPortX(idx, resultList.size, minTap, span))
                        }
                    } else {
                        resultList
                    }
                }

                panelNode.id to ports
            }
            .toMap()

        // Phase 3 §3.1 — First-class Port Anchors: resolve every node's
        // schematic ports (TOP/BOTTOM/LEFT/RIGHT) from its FINAL position so
        // the canvas, wire routing and DXF exporter consume pre-calculated
        // anchors instead of re-deriving them with scattered hardcoded offsets.
        val finalNodes = positionedNodesMap.mapValues { (_, node) ->
            node.copy(ports = buildSchematicPorts(node, graph, busbarPorts))
        }

        return CoordinateAssignmentResult(finalNodes, busbarPorts)
    }

    /**
     * Phase 3 §3.1 — resolve the schematic port anchors for one positioned node.
     *
     * Every anchor coordinate is derived ONCE from the node's final position and
     * mirrors the exact wire-terminus math used by [routeEdges] and
     * [SldSymbols.atsSymbolGeometry], so symbols, wires and hit-testing can never
     * drift apart:
     *
     *  - ATS: TOP NORMAL (0.25·w) / TOP EMERGENCY (0.75·w) / BOTTOM OUTPUT (0.5·w).
     *  - Generators / transformers / utility sources / UPS: single BOTTOM output.
     *  - Bus ties & synchronizing buses: LEFT/RIGHT centerline coupler ports
     *    (plus a TOP rail entry for the sync bus's GEN_SYNC_CONNECTION).
     *  - Busbar panels (BUS/MDB/SMDB/DB/MCC): TOP incoming terminal, one BOTTOM
     *    tap per §3g busbar port, and LEFT/RIGHT centerline coupler ports.
     *  - Plain distribution / load nodes: single TOP incoming terminal.
     */
    private fun buildSchematicPorts(
        node: PositionedSldNode,
        graph: ElectricalNetworkGraph,
        busbarPorts: Map<String, List<BusbarPort>>
    ): Map<PortSide, List<PortAnchor>> {
        val upper = node.panelType.uppercase()
        val centerY = node.y + node.height / 2f
        val centerX = node.x + node.width / 2f

        if (upper.contains("ATS")) {
            val g = SldSymbols.atsSymbolGeometry(node.x, node.y, node.width)
            return mapOf(
                PortSide.TOP to listOf(
                    PortAnchor(id = "${node.id}.NORMAL", side = PortSide.TOP, offset = g.normalTerminal, isOccupied = true),
                    PortAnchor(id = "${node.id}.EMERGENCY", side = PortSide.TOP, offset = g.emergencyTerminal, isOccupied = true)
                ),
                PortSide.BOTTOM to listOf(
                    PortAnchor(id = "${node.id}.OUTPUT", side = PortSide.BOTTOM, offset = g.outputTerminal, isOccupied = true)
                )
            )
        }

        if (node.panelType == SldNodeType.BUS_TIE.name || node.panelType == SldNodeType.SYNC_BUS.name) {
            val anchors = mutableListOf(
                PortAnchor(id = "${node.id}.LEFT", side = PortSide.LEFT, offset = Offset(node.x, centerY)),
                PortAnchor(id = "${node.id}.RIGHT", side = PortSide.RIGHT, offset = Offset(node.x + node.width, centerY))
            )
            if (node.panelType == SldNodeType.SYNC_BUS.name) {
                anchors.add(PortAnchor(id = "${node.id}.RAIL_IN", side = PortSide.TOP, offset = Offset(centerX, node.y)))
            }
            return mapOf(
                PortSide.LEFT to listOf(anchors[0]),
                PortSide.RIGHT to listOf(anchors[1]),
                PortSide.TOP to anchors.filter { it.side == PortSide.TOP }
            )
        }

        if (node.isSource) {
            return mapOf(
                PortSide.BOTTOM to listOf(
                    PortAnchor(id = "${node.id}.OUTPUT", side = PortSide.BOTTOM, offset = Offset(centerX, node.y + 24f), isOccupied = true)
                )
            )
        }

        if (node.panelType in PANEL_BUSBAR_TYPE_NAMES) {
            val taps = (busbarPorts[node.id].orEmpty()).map { port ->
                PortAnchor(
                    id = "${node.id}.TAP.${port.childNodeId}",
                    side = PortSide.BOTTOM,
                    offset = Offset(port.tapX, node.y + node.height),
                    isOccupied = true
                )
            }
            val incoming = graph.edges.any { it.toNodeId == node.id }
            val hasTieLink = graph.edges.any {
                it.type == SldEdgeType.BUS_TIE_LINK &&
                    (it.fromNodeId == node.id || it.toNodeId == node.id)
            }
            return mapOf(
                PortSide.TOP to listOf(
                    PortAnchor(id = "${node.id}.IN", side = PortSide.TOP, offset = Offset(centerX, node.y), isOccupied = incoming)
                ),
                PortSide.BOTTOM to taps,
                PortSide.LEFT to listOf(
                    PortAnchor(id = "${node.id}.LEFT", side = PortSide.LEFT, offset = Offset(node.x, centerY), isOccupied = hasTieLink)
                ),
                PortSide.RIGHT to listOf(
                    PortAnchor(id = "${node.id}.RIGHT", side = PortSide.RIGHT, offset = Offset(node.x + node.width, centerY), isOccupied = hasTieLink)
                )
            )
        }

        return mapOf(
            PortSide.TOP to listOf(
                PortAnchor(
                    id = "${node.id}.IN",
                    side = PortSide.TOP,
                    offset = Offset(centerX, node.y),
                    isOccupied = graph.edges.any { it.toNodeId == node.id }
                )
            )
        )
    }

    // =========================================================================
    // POST-PASS — ORTHOGONAL EDGE ROUTING
    // Routes all power-flow and control edges as strict H-V-H / V-H-V polylines.
    // =========================================================================
    fun routeEdges(
        graph: ElectricalNetworkGraph,
        positionedNodesMap: Map<String, PositionedSldNode>,
        busbarPorts: Map<String, List<BusbarPort>> = emptyMap()
    ): List<PositionedSldEdge> {
        val nodeMap = graph.nodes.associateBy { it.id }

        // Routing obstacle registry (Roadmap Phase 2 §2.4): every rendered node
        // box (lane-blocking) plus its label/enclosure envelope (non-lane-blocking)
        // is registered so emergency corridors and horizontal segments can be
        // collision-checked against the actual drawing geometry.
        val obstacleField = RoutingObstacleField()
        positionedNodesMap.values.forEach { node ->
            obstacleField.registerNode(node)
            val bounds = getNodeVisualBounds(node, null)
            obstacleField.registerEnvelope(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                ownerNodeId = node.id
            )
        }

        // Shared-source ATS NORMAL incomers get staggered channels so parallel
        // wires and their NORMAL labels never overlap (see SldAtsRoutingTest).
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

            if (edge.type == SldEdgeType.UNRESOLVED_PATH) {
                val points = if (toNode != null) {
                    val portOffsetX = if (edge.fromNodeId.contains("emerg")) {
                        toNode.width * 0.75f
                    } else if (edge.fromNodeId.contains("normal")) {
                        toNode.width * 0.25f
                    } else {
                        toNode.width / 2f
                    }
                    val toXPort = toNode.x + portOffsetX
                    val toYPort = toNode.y - (if (toNode.panelType.uppercase().contains("ATS")) 24f else 12f)
                    listOf(Pair(toXPort, toYPort - 45f), Pair(toXPort, toYPort))
                } else if (fromNode != null) {
                    val fromXPort = fromNode.x + fromNode.width / 2f
                    val fromYPort = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)
                    listOf(Pair(fromXPort, fromYPort), Pair(fromXPort, fromYPort + 45f))
                } else {
                    emptyList()
                }

                if (points.isNotEmpty()) {
                    positionedEdges.add(
                        PositionedSldEdge(
                            fromId = edge.fromNodeId,
                            toId = edge.toNodeId,
                            label = edge.label ?: "",
                            pathPoints = points,
                            isUnresolved = true
                        )
                    )
                }
            } else if (fromNode != null && toNode != null && ElectricalNetworkGraph.isLateralEdge(edge)) {
                // Lateral (same-tier) couplers (Roadmap Phase 2 §2.3): bus ties
                // route horizontally at the shared busbar centerline —
                // BoardA.RIGHT_BUS_PORT → BUS_TIE → BoardB.LEFT_BUS_PORT — and
                // generator synchronizing connections drop vertically into the
                // sync bus rail. Never the vertical drop-from-source pattern.
                positionedEdges.add(
                    PositionedSldEdge(
                        fromId = edge.fromNodeId,
                        toId = edge.toNodeId,
                        label = edge.label ?: "",
                        pathPoints = routeLateralEdge(edge, fromNode, toNode)
                    )
                )
            } else if (fromNode != null && toNode != null) {
                val isToAts = toNode.panelType.uppercase().contains("ATS") || edge.type == SldEdgeType.ATS_NORMAL_PATH || edge.type == SldEdgeType.ATS_EMERGENCY_PATH

                val points = if (isToAts) {
                    // Phase 3 §3.1 — consume the pre-calculated TOP anchors when
                    // present (falling back to the identical value math so the
                    // routed termini can never drift from the symbol geometry).
                    val topPorts = toNode.ports[PortSide.TOP].orEmpty()
                    val isEmerg = edge.type == SldEdgeType.ATS_EMERGENCY_PATH
                    val anchor = if (isEmerg) {
                        topPorts.firstOrNull { it.id.endsWith(".EMERGENCY") }?.offset
                    } else {
                        topPorts.firstOrNull { it.id.endsWith(".NORMAL") }?.offset
                    }
                    val toXPort = anchor?.x
                        ?: (toNode.x + (if (isEmerg) toNode.width * 0.75f else toNode.width * 0.25f))
                    val toYPort = anchor?.y ?: (toNode.y - 24f)
                    val fromYPort = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)

                    val isParentPanel = fromNode.panelType.uppercase().contains("MDB") ||
                            fromNode.panelType.uppercase().contains("SMDB") ||
                            fromNode.panelType.uppercase().contains("MCC") ||
                            fromNode.panelType.uppercase().contains("DB")

                    val fromXPort = if (edge.type == SldEdgeType.ATS_EMERGENCY_PATH) {
                        toXPort
                    } else if (isParentPanel) {
                        val port = busbarPorts[fromNode.id]?.firstOrNull { it.childNodeId == edge.toNodeId }
                        val minTap = fromNode.x + BUSBAR_TAP_END_PAD
                        val maxTap = fromNode.x + fromNode.width - BUSBAR_TAP_END_PAD
                        port?.tapX ?: toXPort.coerceIn(minTap, maxTap)
                    } else {
                        fromNode.x + fromNode.width / 2f
                    }

                    val isEmergencyEdge = edge.type == SldEdgeType.ATS_EMERGENCY_PATH
                    val staggerStep = 20f
                    val siblingIndex = if (isEmergencyEdge) 0
                    else normalAtsIncomerIndex["${edge.fromNodeId}->${edge.toNodeId}"] ?: 0

                    val atsPoints = if (fromNode.y < toNode.y && Math.abs(fromXPort - toXPort) < 5f) {
                        listOf(Pair(toXPort, fromYPort), Pair(toXPort, toYPort))
                    } else if (fromNode.isSource && fromNode.y < toNode.y) {
                        val channelY = fromYPort + 95f
                        listOf(
                            Pair(fromXPort, fromYPort),
                            Pair(fromXPort, channelY),
                            Pair(toXPort, channelY),
                            Pair(toXPort, toYPort)
                        )
                    } else if (fromNode.y < toNode.y) {
                        val maxBottom = maxOf(fromNode.y + fromNode.height, toNode.y + toNode.height)
                        val lowChannelY = maxBottom + 20f + siblingIndex * staggerStep
                        val riseX = toNode.x - 20f - siblingIndex * staggerStep
                        val highChannelY = toYPort - 20f - siblingIndex * staggerStep
                        listOf(
                            Pair(fromXPort, fromYPort),
                            Pair(fromXPort, lowChannelY),
                            Pair(riseX, lowChannelY),
                            Pair(riseX, highChannelY),
                            Pair(toXPort, highChannelY),
                            Pair(toXPort, toYPort)
                        )
                    } else {
                        val maxBottom = maxOf(fromNode.y + fromNode.height, toNode.y + toNode.height)
                        val lowChannelY = maxBottom + 20f + siblingIndex * staggerStep
                        val riseX = toNode.x - 20f - siblingIndex * staggerStep
                        val highChannelY = toYPort - 20f - siblingIndex * staggerStep
                        listOf(
                            Pair(fromXPort, fromYPort),
                            Pair(fromXPort, lowChannelY),
                            Pair(riseX, lowChannelY),
                            Pair(riseX, highChannelY),
                            Pair(toXPort, highChannelY),
                            Pair(toXPort, toYPort)
                        )
                    }
                    if (isEmergencyEdge) {
                        routeEmergencyPathAroundObstacles(
                            obstacleField = obstacleField,
                            fromNode = fromNode,
                            toNode = toNode,
                            fromXPort = fromXPort,
                            toXPort = toXPort,
                            fromYPort = fromYPort,
                            toYPort = toYPort,
                            fallback = atsPoints
                        )
                    } else {
                        atsPoints
                    }
                } else {
                    val toCenterX = toNode.x + toNode.width / 2f
                    val fromY = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)
                    val toY = toNode.y - (if (toNode.isSource) 24f else 0f)

                    val isParentPanel = fromNode.panelType.uppercase().contains("MDB") ||
                            fromNode.panelType.uppercase().contains("SMDB") ||
                            fromNode.panelType.uppercase().contains("MCC") ||
                            fromNode.panelType.uppercase().contains("DB")

                    val fromX = if (isParentPanel && toCenterX >= fromNode.x - 10f && toCenterX <= fromNode.x + fromNode.width + 10f) {
                        toCenterX
                    } else {
                        fromNode.x + fromNode.width / 2f
                    }

                    if (Math.abs(fromX - toCenterX) < 2f) {
                        listOf(Pair(toCenterX, fromY), Pair(toCenterX, toY))
                    } else {
                        val midY = fromY + (toY - fromY) / 2f
                        listOf(
                            Pair(fromX, fromY),
                            Pair(fromX, midY),
                            Pair(toCenterX, midY),
                            Pair(toCenterX, toY)
                        )
                    }
                }

                positionedEdges.add(
                    PositionedSldEdge(
                        fromId = edge.fromNodeId,
                        toId = edge.toNodeId,
                        label = edge.label ?: "",
                        pathPoints = points,
                        isEmergency = (edge.type == SldEdgeType.ATS_EMERGENCY_PATH),
                        isUps = (edge.type == SldEdgeType.UPS_INPUT || edge.type == SldEdgeType.UPS_OUTPUT)
                    )
                )
            }
        }

        return positionedEdges
    }

    /**
     * Lateral (same-tier) edge routing (Roadmap Phase 2 §2.3).
     *
     *  - BUS_TIE_LINK: strictly horizontal at the shared busbar centerline —
     *    `BoardA.RIGHT_BUS_PORT → BUS_TIE → BoardB.LEFT_BUS_PORT`. The §2.3
     *    clustering pass has already centered the tie breaker between the two
     *    boards at the boards' centerline Y, so the wire is a single straight
     *    horizontal segment (an H-V-H elbow is emitted only if the two ends
     *    are not exactly level).
     *  - GEN_SYNC_CONNECTION: vertical drop from the generator output into the
     *    top of its synchronizing bus rail (the §2.3 pass spine-aligns each
     *    genset above the bus center, so this is normally a straight vertical).
     */
    private fun routeLateralEdge(
        edge: SldEdge,
        fromNode: PositionedSldNode,
        toNode: PositionedSldNode
    ): List<Pair<Float, Float>> {
        if (edge.type == SldEdgeType.GEN_SYNC_CONNECTION) {
            val fromX = fromNode.x + fromNode.width / 2f
            val fromY = fromNode.y + (if (fromNode.isSource) 24f else fromNode.height)
            val toX = toNode.x + toNode.width / 2f
            val toY = toNode.y
            return if (Math.abs(fromX - toX) < 2f) {
                listOf(Pair(fromX, fromY), Pair(fromX, toY))
            } else {
                val midY = fromY + (toY - fromY) / 2f
                listOf(Pair(fromX, fromY), Pair(fromX, midY), Pair(toX, midY), Pair(toX, toY))
            }
        }

        // BUS_TIE_LINK: one endpoint is the tie breaker, the other a coupled board.
        val tieIsFrom = fromNode.panelType == SldNodeType.BUS_TIE.name
        val tie = if (tieIsFrom) fromNode else toNode
        val board = if (tieIsFrom) toNode else fromNode
        val tieCenterY = tie.y + tie.height / 2f
        val boardCenterY = board.y + board.height / 2f

        val (boardPortX, tiePortX) = if (board.x + board.width <= tie.x + 1f) {
            // Board sits left of the tie: exit its RIGHT bus port, enter the tie LEFT port.
            Pair(board.x + board.width, tie.x)
        } else {
            // Board sits right of the tie: exit its LEFT bus port, enter the tie RIGHT port.
            Pair(board.x, tie.x + tie.width)
        }

        return if (Math.abs(boardCenterY - tieCenterY) < 2f) {
            listOf(Pair(boardPortX, boardCenterY), Pair(tiePortX, tieCenterY))
        } else {
            val midX = (boardPortX + tiePortX) / 2f
            listOf(
                Pair(boardPortX, boardCenterY),
                Pair(midX, boardCenterY),
                Pair(midX, tieCenterY),
                Pair(tiePortX, tieCenterY)
            )
        }
    }

    /**
     * Emergency riser collision handling (Roadmap Phase 2 §2.4).
     *
     * The straight emergency drop from the generator output to the ATS emergency
     * terminal is checked against the routing obstacle field (panel boxes,
     * busbar rails). When the preferred lane is blocked, the router allocates
     * the nearest clear vertical corridor lane — outside the blocking busbar
     * envelope (`X_lane = obstacle.right + clearance`) — and detours
     * orthogonally:
     *
     *   genOutput → V jog → H to lane → V past the obstacles → H back → ATS terminal
     *
     * Horizontal jog segments are additionally nudged into free horizontal
     * channels by [RoutingObstacleField.nudgeHorizontalSegments]. When no clear
     * lane exists at all, the straight fallback polyline is kept (best effort).
     */
    private fun routeEmergencyPathAroundObstacles(
        obstacleField: RoutingObstacleField,
        fromNode: PositionedSldNode,
        toNode: PositionedSldNode,
        fromXPort: Float,
        toXPort: Float,
        fromYPort: Float,
        toYPort: Float,
        fallback: List<Pair<Float, Float>>
    ): List<Pair<Float, Float>> {
        val ignoreOwners = setOf(fromNode.id, toNode.id)
        if (obstacleField.isVerticalLaneClear(toXPort, fromYPort, toYPort, ignoreOwners = ignoreOwners)) {
            return fallback
        }
        val laneX = obstacleField.allocateVerticalLane(
            preferredX = toXPort,
            yTop = fromYPort,
            yBottom = toYPort,
            ignoreOwners = ignoreOwners
        ) ?: return fallback

        val jogTop = fromYPort + 20f
        val jogBottom = toYPort - 20f
        val detour = listOf(
            Pair(fromXPort, fromYPort),
            Pair(fromXPort, jogTop),
            Pair(laneX, jogTop),
            Pair(laneX, jogBottom),
            Pair(toXPort, jogBottom),
            Pair(toXPort, toYPort)
        )
        // Collision pass: nudge horizontal segments into free channels.
        return obstacleField.nudgeHorizontalSegments(detour, ignoreOwners = ignoreOwners)
    }

    fun calculateLayout(
        graph: ElectricalNetworkGraph,
        circuits: List<CircuitEntity> = emptyList(),
        textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset> = emptyMap()
    ): SldEngineResult {
        // Sugiyama-Style 3-Pass Layout Pipeline
        // Pass 1: Layer Assignment
        val layers = assignLayers(graph)

        // Pass 2: Bounding Box & Content Measurement
        val boxes = computeBoundingBoxes(graph, textMeasurer)

        // Pass 3: Coordinate Assignment & Clearance Enforcement
        val coords = assignCoordinates(graph, circuits, layers, boxes, textMeasurer, manualPositions)

        // Post-Pass: Orthogonal Polyline Edge Routing
        val positionedEdges = routeEdges(graph, coords.positionedNodes, coords.busbarPorts)

        // Tier Datum Lines
        val tierGap = SldTheme.tierVerticalSpacing()
        val sortedTiers = layers.effectiveTier.values.distinct().sorted()
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

        // Calculate overall bounding box
        val allNodesList = coords.positionedNodes.values.toList()
        val minX = allNodesList.minOfOrNull { it.x - 50f } ?: 0f
        val maxX = allNodesList.maxOfOrNull { it.x + it.width + 100f } ?: 1000f
        val minY = 0f
        val maxY = allNodesList.maxOfOrNull { it.y + 100f } ?: 800f

        return SldEngineResult(
            nodes = allNodesList,
            edges = positionedEdges,
            bounds = Rect(minX, minY, maxX, maxY),
            validationIssues = graph.validationIssues,
            tierDatums = tierDatums,
            busbarPorts = coords.busbarPorts
        )
    }

    private fun isSourceNode(type: SldNodeType): Boolean = when (type) {
        SldNodeType.UTILITY_SOURCE,
        SldNodeType.TRANSFORMER,
        SldNodeType.GENERATOR,
        SldNodeType.ATS,
        SldNodeType.UPS -> true
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

    internal data class VisualBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX: Float get() = (left + right) / 2f
    }

    private data class CollisionPair(
        val leftNode: PositionedSldNode,
        val rightNode: PositionedSldNode,
        val leftBox: VisualBounds,
        val rightBox: VisualBounds
    )

    internal fun getNodeVisualBounds(node: PositionedSldNode, textMeasurer: androidx.compose.ui.text.TextMeasurer?): VisualBounds {
        val panelUpper = node.panelType.uppercase()
        val isMainPanel = panelUpper.contains("MDB") || panelUpper.contains("MAIN")
        val isAts = panelUpper.contains("ATS")
        val isGenerator = panelUpper.contains("GENERATOR")
        val isUps = panelUpper.contains("UPS")
        val isUtility = panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")
        val isTransformer = panelUpper.contains("TRANSFORMER")
        val labelWidth = measureLabelWidth(node, textMeasurer)
        val cx = node.x + node.width / 2f
        val calloutTextRight = cx + 14f + labelWidth

        return when {
            isMainPanel -> {
                VisualBounds(
                    left = node.x - 36f,
                    top = node.y - 110f,
                    right = maxOf(node.x + node.width + 36f, calloutTextRight),
                    bottom = node.y + node.height + 20f
                )
            }
            isAts -> {
                VisualBounds(
                    left = node.x - 10f,
                    top = node.y - 28f,
                    right = node.x + node.width + 14f + labelWidth,
                    bottom = node.y + 28f
                )
            }
            isGenerator -> {
                // SldCanvas: cx = node.x + width/2, cy = node.y. Size 48 (radius 21.6f).
                // Name label at cx - 50f, cy + 28f. Rating label at cx - 50f, cy + 44f (ends ~cy + 58f).
                val labelLeft = cx - 50f
                VisualBounds(
                    left = minOf(node.x, labelLeft, cx - 24f),
                    top = node.y - 24f,
                    right = maxOf(node.x + node.width, cx + 24f, labelLeft + labelWidth),
                    bottom = node.y + 60f
                )
            }
            isUps -> {
                // SldCanvas: cx = node.x + width/2, cy = node.y. Box size 36 (spanning y-18 to y+18).
                // Name label at cx + 36f, cy - 20f. Rating label at cx + 36f, cy + 6f (ends ~cy + 20f).
                val labelRight = cx + 36f + labelWidth
                VisualBounds(
                    left = minOf(node.x, cx - 24f),
                    top = node.y - 24f,
                    right = maxOf(node.x + node.width, labelRight),
                    bottom = node.y + 24f
                )
            }
            isUtility -> {
                // SldCanvas: cx = node.x + width/2, cy = node.y. Size 48 (radius 21.6f).
                // Name label at cx - 40f, cy + 28f. Rating label at cx - 40f, cy + 44f (ends ~cy + 58f).
                val labelLeft = cx - 40f
                VisualBounds(
                    left = minOf(node.x, labelLeft, cx - 24f),
                    top = node.y - 24f,
                    right = maxOf(node.x + node.width, cx + 24f, labelLeft + labelWidth),
                    bottom = node.y + 60f
                )
            }
            isTransformer -> {
                // SldCanvas: cx = node.x + width/2, cy = node.y. Two overlapping circles spanning y-24 to y+24.
                // Name label at cx + 45f, cy - 20f. Rating label at cx + 45f, cy + 6f (ends ~cy + 20f).
                val labelRight = cx + 45f + labelWidth
                VisualBounds(
                    left = minOf(node.x, cx - 24f),
                    top = node.y - 24f,
                    right = maxOf(node.x + node.width, labelRight),
                    bottom = node.y + 24f
                )
            }
            else -> {
                VisualBounds(
                    left = node.x,
                    top = node.y,
                    right = maxOf(node.x + node.width, calloutTextRight),
                    bottom = node.y + node.height
                )
            }
        }
    }

    private fun getSubtreeRightBound(
        nodeId: String,
        positionedNodesMap: Map<String, PositionedSldNode>,
        childrenMap: Map<String, List<String>>,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        visited: MutableSet<String> = mutableSetOf()
    ): Float {
        if (!visited.add(nodeId)) return 0f
        val node = positionedNodesMap[nodeId] ?: return 0f
        val box = getNodeVisualBounds(node, textMeasurer)
        var maxRight = box.right

        childrenMap[nodeId]?.forEach { childId ->
            val childRight = getSubtreeRightBound(childId, positionedNodesMap, childrenMap, textMeasurer, visited)
            maxRight = maxOf(maxRight, childRight)
        }
        return maxRight
    }

    private fun applyDynamicCollisionAvoidance(
        positionedNodesMap: MutableMap<String, PositionedSldNode>,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        childrenMap: Map<String, List<String>>,
        graph: ElectricalNetworkGraph
    ) {
        var hasCollision = true
        var iteration = 0
        val maxIterations = 15

        while (hasCollision && iteration < maxIterations) {
            hasCollision = false
            iteration++

            val allNodes = positionedNodesMap.values.toList()
            for (i in allNodes.indices) {
                for (j in (i + 1) until allNodes.size) {
                    val nodeA = allNodes[i]
                    val nodeB = allNodes[j]
                    if (nodeA.id == nodeB.id) continue

                    val areConnected = graph.edges.any {
                        (it.fromNodeId == nodeA.id && it.toNodeId == nodeB.id) ||
                        (it.fromNodeId == nodeB.id && it.toNodeId == nodeA.id)
                    }
                    if (areConnected) continue

                    val boxA = getNodeVisualBounds(nodeA, textMeasurer)
                    val boxB = getNodeVisualBounds(nodeB, textMeasurer)

                    // 2D overlap check: Y overlap requires horizontal clearance >= MIN_HORIZONTAL_GAP
                    val yOverlap = (boxA.top < boxB.bottom && boxA.bottom > boxB.top)
                    if (yOverlap) {
                        val pair = if (boxA.centerX <= boxB.centerX) {
                            CollisionPair(nodeA, nodeB, boxA, boxB)
                        } else {
                            CollisionPair(nodeB, nodeA, boxB, boxA)
                        }

                        val minAllowedRightLeft = pair.leftBox.right + MIN_HORIZONTAL_GAP
                        if (pair.rightBox.left < minAllowedRightLeft) {
                            val shiftDelta = minAllowedRightLeft - pair.rightBox.left
                            shiftSubtree(pair.rightNode.id, shiftDelta, positionedNodesMap, childrenMap)

                            // If rightNode is an ATS, co-shift connected generator; if rightNode is a Generator, co-shift connected ATS subtree
                            if (pair.rightNode.panelType.uppercase().contains("ATS")) {
                                val genEdge = graph.edges.firstOrNull { it.toNodeId == pair.rightNode.id && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                                val genNode = genEdge?.let { positionedNodesMap[it.fromNodeId] }
                                if (genNode != null) {
                                    positionedNodesMap[genNode.id] = genNode.copy(x = genNode.x + shiftDelta)
                                }
                            } else if (pair.rightNode.panelType.uppercase().contains("GENERATOR")) {
                                val atsEdge = graph.edges.firstOrNull { it.fromNodeId == pair.rightNode.id && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                                val atsNode = atsEdge?.let { positionedNodesMap[it.toNodeId] }
                                if (atsNode != null) {
                                    shiftSubtree(atsNode.id, shiftDelta, positionedNodesMap, childrenMap)
                                }
                            }
                            hasCollision = true
                        }
                    }
                }
            }
        }
    }

    /**
     * Enforces the primary SLD invariant for every ATS unit:
     *
     *     atsX  >=  panelBoxRight + ATS_TO_PANEL_MIN_GAP
     *
     * where `panelBoxRight` is the NORMAL-source panel's ACTUAL final rendered box
     * right edge:
     *
     *     panelBoxRight = panel.x + panel.width + (MDB/MAIN ? MAIN_PANEL_ENCLOSURE_PAD : 0)
     *
     * and additionally, for side-by-side units:
     *
     *     atsX[i+1]  >=  atsX[i] + ats[i].width + ATS_TO_PANEL_MIN_GAP
     *
     * This is the authoritative, panel-width-reactive anchor for the ATS X
     * coordinate. It runs after subtree positioning AND after manual position
     * overrides, so even a stale persisted/dragged ATS position (applied on top of
     * a panel that has since grown wide with extra feeder columns) is pushed to the
     * panel's actual right edge. The pass only ever shifts nodes to the right and
     * co-shifts each ATS's emergency generator + downstream subtree so routed wires
     * keep landing on the same terminals.
     */
    private fun enforceAtsToPanelClearance(
        positionedNodesMap: MutableMap<String, PositionedSldNode>,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        childrenMap: Map<String, List<String>>,
        graph: ElectricalNetworkGraph,
        rootNodeIds: Set<String>
    ) {
        var changed = true
        var iteration = 0
        val maxIterations = 8
        while (changed && iteration < maxIterations) {
            changed = false
            iteration++

            // Left-to-right so each ATS also leaves room for the next sibling.
            val atsIds = positionedNodesMap.values
                .filter { it.panelType.uppercase().contains("ATS") }
                .sortedBy { positionedNodesMap[it.id]?.x ?: it.x }
                .map { it.id }

            var prevAtsRight = Float.NEGATIVE_INFINITY
            for (atsId in atsIds) {
                val atsNode = positionedNodesMap[atsId] ?: continue
                val atsBoxLeft = atsNode.x

                // 1. Minimum left edge required by the preceding direct/UPS feeder branches of the NORMAL-source panel.
                val normalSourceId = graph.edges
                    .firstOrNull {
                        it.toNodeId == atsId && it.type == SldEdgeType.ATS_NORMAL_PATH
                    }
                    ?.fromNodeId
                val minLeftFromPanel = if (normalSourceId != null) {
                    val panel = positionedNodesMap[normalSourceId]
                    if (panel != null) {
                        val directAndUpsChildren = (childrenMap[panel.id] ?: emptyList()).filter {
                            it != atsId && positionedNodesMap[it]?.panelType?.uppercase()?.contains("ATS") != true
                        }
                        val directAndUpsRight = if (directAndUpsChildren.isNotEmpty()) {
                            directAndUpsChildren.mapNotNull { childId ->
                                positionedNodesMap[childId]?.let { getNodeVisualBounds(it, textMeasurer).right }
                            }.maxOrNull() ?: (panel.x + panel.width)
                        } else (panel.x + panel.width)
                        directAndUpsRight + ATS_TO_PANEL_MIN_GAP
                    } else 0f
                } else 0f

                // 2. Minimum left edge required by the previous ATS (side-by-side).
                val minLeftFromPrevAts =
                    if (prevAtsRight.isFinite()) prevAtsRight + ATS_TO_PANEL_MIN_GAP else 0f

                val requiredLeft = maxOf(minLeftFromPanel, minLeftFromPrevAts, atsBoxLeft)
                val shiftDelta = requiredLeft - atsBoxLeft
                if (shiftDelta > 0f) {
                    // Shift the ATS + its downstream subtree
                    shiftSubtree(atsId, shiftDelta, positionedNodesMap, childrenMap)
                    changed = true
                }

                // Always align connected emergency generator above ATS emergency terminal
                val finalAts = positionedNodesMap[atsId] ?: atsNode
                val genEdge = graph.edges.firstOrNull {
                    it.toNodeId == atsId && it.type == SldEdgeType.ATS_EMERGENCY_PATH
                }
                genEdge?.let { e ->
                    val genNode = positionedNodesMap[e.fromNodeId]
                    if (genNode != null) {
                        val idealGenX = (finalAts.x + finalAts.width * 0.75f) - genNode.width / 2f
                        positionedNodesMap[genNode.id] = genNode.copy(x = idealGenX)
                    }
                }

                val updated = positionedNodesMap[atsId] ?: atsNode
                prevAtsRight = updated.x + updated.width
            }
        }

        val graphNodeMap = graph.nodes.associateBy { it.id }
        fun expandBusbars() {
            for ((pId, pNode) in positionedNodesMap) {
                if (graphNodeMap[pId]?.type in PANEL_BUSBAR_TYPES) {
                    val childEntries = (childrenMap[pId] ?: emptyList()).mapNotNull { positionedNodesMap[it] }
                    if (childEntries.isNotEmpty()) {
                        val emergLanes = graph.edges
                            .filter { it.type == SldEdgeType.ATS_EMERGENCY_PATH }
                            .mapNotNull { e ->
                                val genNode = positionedNodesMap[e.fromNodeId]
                                val atsNode = positionedNodesMap[e.toNodeId]
                                if (genNode != null && atsNode != null && genNode.y < pNode.y + pNode.height + 50f) {
                                    atsNode.x + atsNode.width * 0.75f
                                } else null
                            }
                        val minEmergLane = emergLanes.minOrNull() ?: Float.POSITIVE_INFINITY
                        val maxAllowedRight = if (minEmergLane.isFinite()) minEmergLane - 20f else Float.POSITIVE_INFINITY

                        val allTapCenters = childEntries.map {
                            if (it.panelType.uppercase().contains("ATS")) it.x + it.width * 0.25f
                            else it.x + it.width / 2f
                        }
                        val minCenter = allTapCenters.minOrNull() ?: (pNode.x + pNode.width / 2f)
                        val rawMaxCenter = allTapCenters.maxOrNull() ?: (pNode.x + pNode.width / 2f)
                        val margin = BUSBAR_TAP_END_PAD + 20f
                        val newLeft = minOf(pNode.x, minCenter - margin)
                        var newRight = maxOf(pNode.x + pNode.width, rawMaxCenter + margin)
                        if (newRight > maxAllowedRight) {
                            newRight = maxAllowedRight
                        }
                        val finalWidth = maxOf(140f, newRight - newLeft)
                        positionedNodesMap[pId] = pNode.copy(
                            x = newLeft,
                            width = finalWidth
                        )
                    }
                }
            }
        }

        // 1. Expand continuous busbar rails across initial ATS positions
        expandBusbars()

        // 2. Ensure sibling downstream root panels stay clear of expanded busbar rails
        val siblingRoots = positionedNodesMap.values
            .filter { it.id in rootNodeIds }
            .sortedBy { it.x }
        var currentRootRight = Float.NEGATIVE_INFINITY
        for (root in siblingRoots) {
            val rootBox = getNodeVisualBounds(root, textMeasurer)
            if (currentRootRight.isFinite() && rootBox.left < currentRootRight + MIN_HORIZONTAL_GAP) {
                val shiftDelta = (currentRootRight + MIN_HORIZONTAL_GAP) - rootBox.left
                shiftSubtree(root.id, shiftDelta, positionedNodesMap, childrenMap)
                // Re-align any transformer feeding this root panel
                val txEdge = graph.edges.firstOrNull { it.toNodeId == root.id && it.type == SldEdgeType.SOURCE_CONNECTION }
                val txNode = txEdge?.let { positionedNodesMap[it.fromNodeId] }
                val updatedRoot = positionedNodesMap[root.id] ?: root
                if (txNode != null) {
                    val idealTxX = updatedRoot.x + updatedRoot.width / 2f - txNode.width / 2f
                    positionedNodesMap[txNode.id] = txNode.copy(x = idealTxX)
                    // Re-align grid source feeding this transformer
                    val gridEdge = graph.edges.firstOrNull { it.toNodeId == txNode.id }
                    val gridNode = gridEdge?.let { positionedNodesMap[it.fromNodeId] }
                    if (gridNode != null) {
                        val idealGridX = idealTxX + txNode.width / 2f - gridNode.width / 2f
                        positionedNodesMap[gridNode.id] = gridNode.copy(x = idealGridX)
                    }
                }
            }
            val updated = positionedNodesMap[root.id] ?: root
            currentRootRight = getNodeVisualBounds(updated, textMeasurer).right
        }

        // 3. Re-expand continuous busbar rails across any shifted subtrees in final positions
        expandBusbars()
    }

    private fun measureLabelWidth(node: PositionedSldNode, textMeasurer: androidx.compose.ui.text.TextMeasurer?): Float {
        if (textMeasurer == null) return node.label.length * 9f
        val style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        val l1 = textMeasurer.measure(node.label, style).size.width.toFloat()
        val l2 = textMeasurer.measure(node.ratingText, style).size.width.toFloat()
        val l3 = node.dataBlock?.cableDescription?.let { textMeasurer.measure(it, style).size.width.toFloat() } ?: 0f
        // Accounts for the third feeder-callout line SldCanvas.kt draws dynamically
        // per branch (VD%/PASS-FAIL status, length, or fault-level text). This text
        // is never a stored field, so it can't be measured directly — use worst-case
        // representative strings instead so collision spacing keeps enough margin.
        val l4 = textMeasurer.measure("VD: 99.99% PASS", style).size.width.toFloat()
        val l5 = textMeasurer.measure("Isc: 99.99 kA", style).size.width.toFloat()
        return maxOf(l1, l2, l3, l4, l5) * 1.25f
    }

    // =========================================================================
    // §2.3 SAME-TIER HORIZONTAL PASSES (Main-Tie-Main + generator sync bus)
    // =========================================================================

    /**
     * Positions BUS_TIE breakers and SYNC_BUS rails after tree packing:
     *
     *  - Bus ties: cluster the two coupled boards horizontally adjacent with a
     *    [TIE_BREAKER_GAP] clearance, then center the tie breaker between them
     *    on the shared busbar centerline (Tier-1 Y band).
     *  - Synchronizing buses: center the rail over the mean X of its paralleled
     *    generators so every GEN_SYNC_CONNECTION drops vertically.
     */
    private fun positionBusTiesAndSyncBuses(
        graph: ElectricalNetworkGraph,
        positionedNodesMap: MutableMap<String, PositionedSldNode>,
        childrenMap: Map<String, List<String>>
    ) {
        // --- Synchronizing buses: center over their paralleled generator cluster ---
        graph.nodes.filter { it.type == SldNodeType.SYNC_BUS }.forEach { syncNode ->
            val pos = positionedNodesMap[syncNode.id] ?: return@forEach
            val genEdges = graph.edges.filter {
                it.toNodeId == syncNode.id && it.type == SldEdgeType.GEN_SYNC_CONNECTION
            }
            val genCenters = genEdges.mapNotNull { positionedNodesMap[it.fromNodeId] }
                .map { it.x + it.width / 2f }
            if (genCenters.isNotEmpty()) {
                val targetX = (genCenters.sum() / genCenters.size) - pos.width / 2f
                positionedNodesMap[syncNode.id] = pos.copy(x = targetX)
            }
        }

        // --- Bus ties: cluster coupled boards + center the tie on the busbar line ---
        val tieNodes = graph.nodes.filter { it.type == SldNodeType.BUS_TIE }
        if (tieNodes.isEmpty()) return
        val processed = mutableSetOf<String>()
        tieNodes.forEach { tieNode ->
            if (!processed.add(tieNode.id)) return@forEach
            val tiePos = positionedNodesMap[tieNode.id] ?: return@forEach
            val links = graph.edges.filter {
                it.type == SldEdgeType.BUS_TIE_LINK &&
                    (it.fromNodeId == tieNode.id || it.toNodeId == tieNode.id)
            }
            val boardIds = links.map {
                if (it.fromNodeId == tieNode.id) it.toNodeId else it.fromNodeId
            }.distinct()
            val boardA = boardIds.getOrNull(0)?.let { positionedNodesMap[it] }
            val boardB = boardIds.getOrNull(1)?.let { positionedNodesMap[it] }
            if (boardA == null || boardB == null) return@forEach

            var left = boardA
            var right = boardB
            if (left.x > right.x) {
                val swap = left
                left = right
                right = swap
            }

            // Cluster: nudge the right board's entire subtree so its rendered box
            // starts at least TIE_BREAKER_GAP past the left board's right edge.
            val minLeft = left.x + left.width + TIE_BREAKER_GAP
            if (right.x < minLeft) {
                shiftSubtree(right.id, minLeft - right.x, positionedNodesMap, childrenMap)
            }
            val rightNode = positionedNodesMap[right.id] ?: return@forEach

            val centerY = left.y + left.height / 2f
            positionedNodesMap[tieNode.id] = tiePos.copy(
                x = (left.x + left.width + rightNode.x) / 2f - tiePos.width / 2f,
                y = centerY - tiePos.height / 2f
            )
        }
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

/**
 * Geometric obstacle registry for the orthogonal routing pass (Roadmap Phase 2
 * §2.4).
 *
 * The field accumulates axis-aligned rectangles for every rendered element —
 * node boxes, continuous busbar rails and label exclusion envelopes (derived
 * from [RiserLayoutEngine.getNodeVisualBounds]) — and answers the two queries
 * the router needs:
 *
 *  1. [allocateVerticalLane] — find a free vertical corridor X for emergency
 *     risers, scanning outward from the preferred X so the allocated lane sits
 *     OUTSIDE the blocking busbar envelope (`X_lane = obstacle.right + clearance`).
 *  2. [nudgeHorizontalSegments] — a post-routing collision pass that nudges
 *     horizontal segments vertically into free channels until they no longer
 *     intersect any obstacle.
 *
 * Two obstacle classes are tracked:
 *  - NODE boxes (via [registerNode]) BLOCK vertical lanes — an emergency riser
 *    must never pass through a panel box or busbar rail.
 *  - Label/enclosure envelopes (via [registerEnvelope]) do NOT block vertical
 *    lanes (wires legitimately pass through the empty label band above panels
 *    between tiers) but DO participate in the horizontal nudge pass, so jog
 *    segments avoid overprinting callout text.
 */
class RoutingObstacleField {

    private enum class Kind { NODE, ENVELOPE }

    private data class Obstacle(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val kind: Kind,
        val ownerNodeId: String?
    )

    private val obstacles = mutableListOf<Obstacle>()

    /** Number of registered obstacles (nodes + envelopes). */
    val size: Int get() = obstacles.size

    fun register(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        ownerNodeId: String? = null
    ) {
        registerObstacle(left, top, right, bottom, Kind.NODE, ownerNodeId)
    }

    /** Register a node's core box — blocks vertical lanes. */
    fun registerNode(node: PositionedSldNode) {
        registerObstacle(node.x, node.y, node.x + node.width, node.y + node.height, Kind.NODE, node.id)
    }

    /** Register a node's label/enclosure exclusion envelope — nudge-pass only. */
    fun registerEnvelope(left: Float, top: Float, right: Float, bottom: Float, ownerNodeId: String?) {
        registerObstacle(left, top, right, bottom, Kind.ENVELOPE, ownerNodeId)
    }

    private fun registerObstacle(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        kind: Kind,
        ownerNodeId: String?
    ) {
        if (right > left && bottom > top) {
            obstacles.add(Obstacle(left, top, right, bottom, kind, ownerNodeId))
        }
    }

    fun clear() = obstacles.clear()

    private fun firstBlocking(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        clearance: Float,
        ignoreOwners: Set<String>,
        includeEnvelopes: Boolean
    ): Obstacle? = obstacles.firstOrNull { o ->
        if (o.kind == Kind.ENVELOPE && !includeEnvelopes) return@firstOrNull false
        if (o.ownerNodeId != null && o.ownerNodeId in ignoreOwners) return@firstOrNull false
        x1 < o.right + clearance && x2 > o.left - clearance &&
            y1 < o.bottom + clearance && y2 > o.top - clearance
    }

    /** True when the vertical corridor at [x] between [yTop] and [yBottom] hits no node box. */
    fun isVerticalLaneClear(
        x: Float,
        yTop: Float,
        yBottom: Float,
        clearance: Float = RiserLayoutEngine.EMERGENCY_CORRIDOR_CLEARANCE,
        ignoreOwners: Set<String> = emptySet()
    ): Boolean = firstBlocking(x, yTop, x, yBottom, clearance, ignoreOwners, includeEnvelopes = false) == null

    /** True when the horizontal segment at [y] between [xLeft] and [xRight] hits no obstacle. */
    fun isHorizontalSegmentClear(
        y: Float,
        xLeft: Float,
        xRight: Float,
        clearance: Float = RiserLayoutEngine.EMERGENCY_CORRIDOR_CLEARANCE,
        ignoreOwners: Set<String> = emptySet()
    ): Boolean = firstBlocking(xLeft, y, xRight, y, clearance, ignoreOwners, includeEnvelopes = true) == null

    /**
     * Allocate the nearest free vertical corridor lane to [preferredX] for the
     * span [yTop]..[yBottom]. Scans [RiserLayoutEngine.CORRIDOR_LANE_CANDIDATE_OFFSETS]
     * outward on both sides; for a busbar-blocked preferred lane the first
     * accepted candidate therefore lands just outside the busbar envelope
     * (`busbar.right + clearance`). Returns null when every candidate is blocked.
     */
    fun allocateVerticalLane(
        preferredX: Float,
        yTop: Float,
        yBottom: Float,
        clearance: Float = RiserLayoutEngine.EMERGENCY_CORRIDOR_CLEARANCE,
        ignoreOwners: Set<String> = emptySet()
    ): Float? {
        val candidates = buildList {
            add(preferredX)
            for (offset in RiserLayoutEngine.CORRIDOR_LANE_CANDIDATE_OFFSETS) {
                if (offset != 0f) {
                    add(preferredX + offset)
                    add(preferredX - offset)
                }
            }
        }
        return candidates.firstOrNull { isVerticalLaneClear(it, yTop, yBottom, clearance, ignoreOwners) }
    }

    /**
     * Post-routing collision pass (Roadmap Phase 2 §2.4): every horizontal
     * segment that intersects an obstacle (node box, busbar rail or label
     * envelope) is nudged vertically — scanning in [NUDGE_STEP] increments up
     * and down — into the nearest free channel. Vertical segments stay vertical
     * (their endpoints keep the shared X), so the polyline remains strictly
     * orthogonal.
     */
    fun nudgeHorizontalSegments(
        points: List<Pair<Float, Float>>,
        clearance: Float = RiserLayoutEngine.EMERGENCY_CORRIDOR_CLEARANCE,
        ignoreOwners: Set<String> = emptySet(),
        maxNudges: Int = 12
    ): List<Pair<Float, Float>> {
        if (points.size < 2) return points
        val result = points.toMutableList()
        for (i in 0 until result.size - 1) {
            val (x1, y1) = result[i]
            val (x2, y2) = result[i + 1]
            if (Math.abs(y1 - y2) > 0.001f) continue // vertical segment
            if (isHorizontalSegmentClear(y1, minOf(x1, x2), maxOf(x1, x2), clearance, ignoreOwners)) continue

            val originalY = y1
            var nudgedY = originalY
            for (step in 1..maxNudges) {
                val up = originalY - step * NUDGE_STEP
                if (isHorizontalSegmentClear(up, minOf(x1, x2), maxOf(x1, x2), clearance, ignoreOwners)) {
                    nudgedY = up
                    break
                }
                val down = originalY + step * NUDGE_STEP
                if (isHorizontalSegmentClear(down, minOf(x1, x2), maxOf(x1, x2), clearance, ignoreOwners)) {
                    nudgedY = down
                    break
                }
            }
            if (nudgedY != originalY) {
                result[i] = Pair(x1, nudgedY)
                result[i + 1] = Pair(x2, nudgedY)
            }
        }
        return result
    }

    private companion object {
        const val NUDGE_STEP = 20f
    }
}
