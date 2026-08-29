package com.example.sld

import androidx.compose.ui.geometry.Rect
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.GeneratorResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import com.example.data.AtsEntity

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
    val status: SldValidationStatus = SldValidationStatus.VALID
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

data class SldEngineResult(
    val nodes: List<PositionedSldNode>,
    val edges: List<PositionedSldEdge>,
    val bounds: Rect,
    val validationIssues: List<SldValidationIssue> = emptyList(),
    val tierDatums: List<TierDatumLine> = emptyList()
)

object SldLayoutEngine {
    fun calculateLayout(
        activeProjectId: String,
        transformers: List<TransformerEntity>,
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        transformerResults: Map<String, TransformerResult> = emptyMap(),
        shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap(),
        generatorResult: GeneratorResult? = null,
        atsResult: com.example.engine.AtsResult? = null,
        atsEntity: AtsEntity? = null,
        upsResult: com.example.engine.UpsResult? = null,
        upsEntity: com.example.data.UpsEntity? = null,
        generators: List<com.example.data.GeneratorEntity> = emptyList(),
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsList: List<AtsEntity> = emptyList(),
        atsResults: Map<String, com.example.engine.AtsResult> = emptyMap(),
        upsList: List<com.example.data.UpsEntity> = emptyList(),
        upsResults: Map<String, com.example.engine.UpsResult> = emptyMap(),
        textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset> = emptyMap()
    ): SldEngineResult {
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = activeProjectId,
            transformers = transformers,
            circuits = circuits,
            sizingResults = sizingResults,
            transformerResults = transformerResults,
            shortCircuitResults = shortCircuitResults,
            generatorResult = generatorResult,
            atsResult = atsResult,
            atsEntity = atsEntity,
            upsResult = upsResult,
            upsEntity = upsEntity,
            generators = generators,
            generatorResults = generatorResults,
            atsList = atsList,
            atsResults = atsResults,
            upsList = upsList,
            upsResults = upsResults
        )

        return RiserLayoutEngine.calculateLayout(graph, circuits, textMeasurer, manualPositions)
    }
}
