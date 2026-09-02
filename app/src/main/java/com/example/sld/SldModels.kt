package com.example.sld

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import com.example.engine.ShortCircuitResult
import com.example.engine.ConduitFillResult

enum class SldNodeType {
    UTILITY_SOURCE,
    TRANSFORMER,
    GENERATOR,
    ATS,
    UPS,
    CAPACITOR_BANK,
    BUS,
    BUS_TIE,
    SYNC_BUS,
    MDB,
    SMDB,
    DB,
    MCC,
    FINAL_CIRCUIT,
    LOAD,
    UNKNOWN
}

enum class SldEdgeType {
    SOURCE_CONNECTION,
    CABLE,
    BUSWAY,
    DIRECT_CONNECTION,
    ATS_NORMAL_PATH,
    ATS_EMERGENCY_PATH,
    ATS_OUTPUT,
    UPS_INPUT,
    UPS_OUTPUT,
    BUS_TIE_LINK,
    GEN_SYNC_CONNECTION,
    UNRESOLVED_PATH
}

enum class SldValidationStatus {
    VALID,
    WARNING,
    INVALID,
    DISCONNECTED,
    DATA_REQUIRED
}

/** Side of a node box a schematic terminal lives on (Phase 3 §3.1). */
enum class PortSide { TOP, BOTTOM, LEFT, RIGHT }

/**
 * First-class schematic port anchor (Phase 3 §3.1).
 *
 * [RiserLayoutEngine] resolves one [PortAnchor] per topological terminal on a
 * positioned node — ATS NORMAL/EMERGENCY inputs, source bottom outputs, panel
 * busbar tap ports, and lateral Left/Right coupler ports — during the layout
 * pass. Canvas rendering, wire routing and DXF export consume these anchors
 * instead of re-deriving port coordinates with duplicated hardcoded offsets, so
 * the symbol geometry, the routed wire termini and the hit-testing model can
 * never drift apart.
 *
 * @property id Stable anchor id, e.g. `ats_1.NORMAL`, `circ_2.TAP.circ_3` (names
 *   are schematic role ids, never user-facing project names).
 * @property side The node-box side this terminal faces.
 * @property offset Absolute world coordinate of the terminal (same space as
 *   [PositionedSldNode.x/y]).
 * @property isOccupied True when a routed wire is connected to this anchor.
 */
@Immutable
data class PortAnchor(
    val id: String,
    val side: PortSide,
    val offset: Offset,
    val isOccupied: Boolean = false
)

enum class SldProvenance {
    VERIFIED_FORMULA,
    APPROVED_STANDARD,
    MANUFACTURER_CATALOG,
    PROJECT_INPUT,
    WORKBOOK_VERIFIED,
    LEGACY_NAME_RESOLUTION,
    ASSUMED,
    MISSING
}

enum class DisplayMode {
    OVERVIEW,
    CABLES,
    VOLTAGE_DROP,
    SHORT_CIRCUIT,
    VALIDATION
}

data class SldValidationIssue(
    val issueCode: String,
    val affectedNodeId: String,
    val componentName: String,
    val failedCondition: String,
    val referencedIdOrName: String? = null,
    val recommendedCorrection: String? = null,
    val severity: SldValidationStatus = SldValidationStatus.WARNING
)

data class SldDataBlock(
    val ratedKva: String? = null,
    val voltage: String? = null,
    val impedancePct: String? = null,
    val loadingPct: String? = null,
    val isc3PhaseKa: String? = null,
    val deviceTypeAndRating: String? = null,
    val cableDescription: String? = null,
    val parallelRuns: Int = 1,
    val lengthM: String? = null,
    val ibAmp: String? = null,
    val izAmp: String? = null,
    val voltageDropPct: String? = null,
    val cumulativeVdPct: String? = null,
    val faultLevelKa: String? = null,
    /** Transformer winding vector group, e.g. "Dyn11" (Roadmap §1.2 checklist). */
    val windingConnection: String? = null,
    /** Protective device interrupting rating (Icu), e.g. "36 kA" (Roadmap §1.2 checklist). */
    val interruptingRatingKa: String? = null,
    val statusText: String = "DATA REQUIRED",
    val customFields: Map<String, String> = emptyMap()
)

data class SldNode(
    val id: String,
    val projectId: String,
    val type: SldNodeType,
    val displayName: String,
    val parentSourceId: String? = null,
    val voltage: String? = null,
    val dataBlock: SldDataBlock,
    val status: SldValidationStatus,
    val provenance: SldProvenance,
    val targetTab: Int = 1, // 1 for Circuit, 2 for Transformer, 3 for Power Systems
    val rawCircuitId: Int? = null,
    val rawTransformerId: String? = null
)

data class SldEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val type: SldEdgeType,
    val label: String? = null,
    val status: SldValidationStatus = SldValidationStatus.VALID
)

data class ElectricalNetworkGraph(
    val activeProjectId: String,
    val nodes: List<SldNode>,
    val edges: List<SldEdge>,
    val validationIssues: List<SldValidationIssue>,
    val unconnectedNodes: List<SldNode>
) {
    companion object {
        /**
         * Edge types that connect nodes on the SAME effective tier (Roadmap
         * Phase 2 §2.3 — Main-Tie-Main bus ties, generator synchronizing
         * buses). These are routed horizontally (Right port → elbow → Left
         * port at the busbar centerline) and must be excluded from the
         * vertical `childTier >= parentTier + 1` relaxation and from the
         * parent→child topology used by subtree packing.
         */
        val LATERAL_EDGE_TYPES: Set<SldEdgeType> = setOf(
            SldEdgeType.BUS_TIE_LINK,
            SldEdgeType.GEN_SYNC_CONNECTION
        )

        fun isLateralEdge(edge: SldEdge): Boolean = edge.type in LATERAL_EDGE_TYPES
    }
}

data class SldLayoutNode(
    val node: SldNode,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val depth: Int,
    val isCollapsed: Boolean = false,
    val hiddenDescendantsCount: Int = 0
)

data class SldLayoutEdge(
    val edge: SldEdge,
    val pathPoints: List<Pair<Float, Float>>
)

data class SldLayoutResult(
    val layoutNodes: Map<String, SldLayoutNode>,
    val layoutEdges: List<SldLayoutEdge>,
    val unconnectedLayoutNodes: List<SldLayoutNode>,
    val totalWidth: Float,
    val totalHeight: Float,
    val unconnectedBoundsY: Float
)

data class TierDatumLine(
    val label: String,
    val y: Float,
    val tierIndex: Int
)
