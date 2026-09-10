package com.example.sld

import androidx.compose.ui.geometry.Rect
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.GeneratorResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import com.example.data.AtsEntity
import com.example.data.BusTieEntity

data class PositionedSldNode(
    val id: String,
    val label: String,
    val panelType: String,
    val ratingText: String,
    val statusColorHex: String,
    val x: Float,
    val y: Float,
    val width: Float = 140f,
    val height: Float = 70f,
    val circuit: CircuitEntity? = null,
    val isSource: Boolean = false,
    val dataBlock: SldDataBlock? = null,
    val status: SldValidationStatus = SldValidationStatus.VALID,
    /** Resolved schematic ports keyed by side (Phase 3 §3.1). */
    val ports: Map<PortSide, List<PortAnchor>> = emptyMap()
)

data class PositionedSldEdge(
    val fromId: String,
    val toId: String,
    val label: String,
    val pathPoints: List<Pair<Float, Float>>,
    val isEmergency: Boolean = false,
    val labelStaggerY: Float = 0f,
    val isUnresolved: Boolean = false,
    val isUps: Boolean = false
)

/**
 * Explicit busbar tap port (Roadmap §2.3).
 *
 * A busbar is not a single connection point — it is a shared rail with one
 * port per outgoing feeder. [RiserLayoutEngine] computes one [BusbarPort] per
 * non-ATS child of every panel node (BUS/MDB/SMDB/DB/MCC); [SldCanvas] draws
 * the feeder taps at exactly these X positions, so the model and the rendering
 * cannot drift apart (contract-tested by SldBusbarPortModelTest).
 *
 * ATS nodes are deliberately excluded: they expose their own dedicated
 * terminal geometry via [SldSymbols.atsSymbolGeometry] and never consume
 * busbar ports.
 */
data class BusbarPort(
    /** 0-based tap index, ordered left-to-right along the bar. */
    val portIndex: Int,
    /** The downstream node this port feeds. */
    val childNodeId: String,
    /** Absolute X of the tap point on the busbar (same coordinate space as [PositionedSldNode.x]). */
    val tapX: Float
)

data class SldEngineResult(
    val nodes: List<PositionedSldNode>,
    val edges: List<PositionedSldEdge>,
    val bounds: Rect,
    val validationIssues: List<SldValidationIssue> = emptyList(),
    val tierDatums: List<TierDatumLine> = emptyList(),
    /** Panel node id → its busbar tap ports, ordered left-to-right (Roadmap §2.3). */
    val busbarPorts: Map<String, List<BusbarPort>> = emptyMap()
)

object SldLayoutEngine {
    fun calculateLayout(
        activeProjectId: String,
        transformers: List<TransformerEntity>,
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        transformerResults: Map<String, TransformerResult> = emptyMap(),
        shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap(),
        generators: List<com.example.data.GeneratorEntity> = emptyList(),
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsList: List<AtsEntity> = emptyList(),
        atsResults: Map<String, com.example.engine.AtsResult> = emptyMap(),
        upsList: List<com.example.data.UpsEntity> = emptyList(),
        upsResults: Map<String, com.example.engine.UpsResult> = emptyMap(),
        textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset> = emptyMap(),
        busTies: List<BusTieEntity> = emptyList(),
        protectionResults: Map<Int, com.example.engine.ProtectionResult> = emptyMap(),
        adsResults: Map<Int, com.example.engine.AdsResult> = emptyMap()
    ): SldEngineResult {
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = activeProjectId,
            transformers = transformers,
            circuits = circuits,
            sizingResults = sizingResults,
            transformerResults = transformerResults,
            shortCircuitResults = shortCircuitResults,
            generators = generators,
            generatorResults = generatorResults,
            atsList = atsList,
            atsResults = atsResults,
            upsList = upsList,
            upsResults = upsResults,
            protectionResults = protectionResults,
            adsResults = adsResults,
            busTies = busTies
        )

        return RiserLayoutEngine.calculateLayout(graph, circuits, textMeasurer, manualPositions)
    }
}
