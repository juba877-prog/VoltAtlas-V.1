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
    val customFields: Map<String, String> = emptyMap(),

    // ================================================================
    // DARK-DATA SLOTS (Phase 1 — pre-computed, now plumbed end-to-end)
    // ================================================================

    /** Derated cable ampacity Iz (A) from [CableSizingEngine.capacity_derated_Iz_A]. */
    val cableIzA: String? = null,

    /** Design current Ib (A) — already mirrored in [ibAmp]; this slot carries the raw numeric. */
    val cableIbA: String? = null,

    /** Thermal utilization % from [CableSizingEngine.utilizationPct]. */
    val cableUtilizationPct: String? = null,

    /** Neutral conductor size mm² (from [SizingResult.neutral_mm2]). */
    val cableNeutralMm2: String? = null,

    /** CPC / earth conductor size mm² (from [SizingResult.earthCPC_mm2]). */
    val cableCpcMm2: String? = null,

    /** Installation method (from [CircuitEntity.installation]). */
    val cableInstallation: String? = null,

    /** Voltage-drop design limit % (from [CircuitEntity.maxVDpct], as percent). */
    val voltageDropLimitPct: String? = null,

    /** Transformer positive-sequence impedance Zt (mΩ) — computed, never rendered before. */
    val transformerZtMOhm: String? = null,

    /** Real MV/HV primary-side supply voltage (e.g. "22 kV") from [TransformerEntity.primaryVoltageV] (G5). */
    val transformerPrimaryVoltage: String? = null,

    /** Utility grid short-circuit MVA at the point of supply (S_kQ), e.g. "250 MVA" (G4). */
    val gridFaultMVA: String? = null,

    /** Transformer single-phase earth-fault current Isc₁ (kA) — computed, never rendered before. */
    val transformerIsc1PhaseKa: String? = null,

    /** ATS required rated short-time withstand current Icw (kA) from [AtsResult.requiredIcw]. */
    val atsRequiredIcwKa: String? = null,

    /** ATS required rated peak withstand current Icm (kA) from [AtsResult.requiredIcm]. */
    val atsRequiredIcmKa: String? = null,

    /** ATS governing design current (A) from [AtsResult.governingCurrent]. */
    val atsGoverningCurrentA: String? = null,

    /** ATS mains-failure detection → generator-start transfer time (s). */
    val atsTransferTimeS: String? = null,

    /** UPS battery bank capacity from [UpsResult.batteryAh] (Ah) + DC bus voltage. */
    val upsBatteryAh: String? = null,

    /** UPS estimated battery strings from [UpsResult.estimatedBatteryStrings]. */
    val upsBatteryStrings: String? = null,

    /** Generator motor-starting kVA from [GeneratorResult.motorStartingKVA]. */
    val generatorMotorStartingKva: String? = null,

    /** Generator step load at start (kVA) from [GeneratorResult.stepLoadAtStart]. */
    val generatorStepLoadKva: String? = null,

    /** Generator/UPS output frequency (Hz) from the real ATS/UPS systemFrequency column. */
    val voltageFrequencyHz: String? = null,

    // ================================================================
    // BOARD HEADER SLOTS (stored on CircuitEntity, now surfaced — 1B)
    // ================================================================

    /** Busbar current rating (A) from [CircuitEntity.busbarRatingA] or [BusbarSizingEngine]. */
    val busbarRatingA: String? = null,

    /** Board short-circuit rating (kA) from [CircuitEntity.scRatingKA] or [BusbarSizingEngine.icwKA]. */
    val scRatingKA: String? = null,

    /** Busbar physical arrangement profile (e.g. "1×(40×10) mm²") from [BusbarSizingEngine]. */
    val busbarSize: String? = null,

    /** Busbar dynamic peak withstand (kA) from [BusbarSizingEngine.ipkKA]. */
    val busbarIpkKa: String? = null,

    /** Busbar neutral conductor cross-section from [BusbarSizingEngine]. */
    val busbarNeutral: String? = null,

    /** Busbar protective earth conductor cross-section from [BusbarSizingEngine]. */
    val busbarPe: String? = null,

    /** Busbar conductor material (e.g. "Copper" or "Aluminium") from [BusbarSizingEngine]. */
    val busbarMaterial: String? = null,

    /** Ingress protection rating from [CircuitEntity.ipRating]. */
    val ipRating: String? = null,

    /** Physical panel location from [CircuitEntity.panelLocation]. */
    val panelLocation: String? = null,

    /** Number of ways from [CircuitEntity.numberOfWays]. */
    val numberOfWays: String? = null,

    /** Main incoming device (type × count) from [CircuitEntity.mainIncomingType/Count]. */
    val mainIncoming: String? = null,

    // ================================================================
    // PROTECTION / ADS VERDICT SLOTS (ProtectionEngine / ADS — 1B)
    // ================================================================

    /** Overload coordination verdict (Ib <= In <= Iz) from [ProtectionResult.overloadStatus]. */
    val protectionOverload: String? = null,

    /** Short-circuit verdict (Icu >= Isc) from [ProtectionResult.shortCircuitStatus]. */
    val protectionShortCircuit: String? = null,

    /** Cable thermal-withstand verdict from [ProtectionResult.cableWithstandStatus]. */
    val protectionCableWithstand: String? = null,

    /** Selectivity verdict from [ProtectionResult.selectivityStatus]. */
    val protectionSelectivity: String? = null,

    /** Earth-fault loop impedance Zs (Ω) from [AdsResult.calculatedZs]. */
    val adsZsOhm: String? = null,

    /** Maximum permitted Zs (Ω) from [AdsResult.maxZsAllowed]. */
    val adsMaxZsOhm: String? = null,

    /** Disconnection time from [AdsResult.disconnectionTime]. */
    val adsDisconnectionTime: String? = null,

    /** ADS overall verdict from [AdsResult.status]. */
    val adsStatus: String? = null,

    // ================================================================
    // BOARD AGGREGATION SLOTS (PanelScheduleEngine roll-up — Phase 2A)
    // ================================================================

    /** Total Connected Load (TCL) of the board subtree from [PanelScheduleRecursiveResult.totalPanelLoadKVA]. */
    val boardTotalConnectedKva: String? = null,

    /** Total Demand Load (TDL) = TCL × demand factor of the board circuit. */
    val boardDemandKva: String? = null,

    /** Demand factor applied to TCL (from [CircuitEntity.demandFactor]), as percent. */
    val boardDemandFactorPct: String? = null,

    /** Board design current derived from TDL at the board's real phase voltage. */
    val boardDesignCurrentA: String? = null,

    /** Phase unbalance across L1/L2/L3 from [PanelScheduleRecursiveResult.unbalancePct]. */
    val boardUnbalancePct: String? = null
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
