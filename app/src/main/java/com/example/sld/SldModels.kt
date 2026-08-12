package com.example.sld

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
    UPS_OUTPUT
}

enum class SldValidationStatus {
    VALID,
    WARNING,
    INVALID,
    DISCONNECTED,
    DATA_REQUIRED
}

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
)

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
