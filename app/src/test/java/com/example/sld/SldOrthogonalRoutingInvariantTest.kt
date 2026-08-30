package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Engine-agnostic routing invariant:
 *
 *   Every routed SLD edge must be an axis-aligned (orthogonal) polyline —
 *   each consecutive pair of waypoints shares x or shares y. No diagonal
 *   segments are permitted anywhere in the drawing.
 *
 * The invariant is verified across all three SLD layout engines
 * (SldLayoutEngine facade, RiserLayoutEngine, ShopDrawingLayoutEngine) and
 * three generically-named fixtures covering the discriminator scenarios:
 * a narrow single-feeder panel, a wide multi-feeder panel, and a cascaded
 * ATS output chain.
 */
class SldOrthogonalRoutingInvariantTest {

    private data class Fixture(
        val name: String,
        val projectId: String,
        val transformers: List<TransformerEntity>,
        val generators: List<GeneratorEntity>,
        val atsList: List<AtsEntity>,
        val circuits: List<CircuitEntity>
    )

    /** Narrow panel: main distribution board with a single feeder + dual ATS. */
    private fun narrowSingleFeeder(projectId: String): Fixture {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId,
                circuitName = "DB-MAIN", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB", cableType = "Cu/XLPE/SWA/PVC", length_m = 25.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 400.0, overrideCableSize = "4x185+95 mm²"
            ),
            CircuitEntity(
                id = 2, projectId = projectId, circuitName = "FEEDER-A", fedFrom = "DB-MAIN", transformerId = "TX-01",
                panelType = "DB", cableType = "Cu/XLPE/SWA/PVC", length_m = 40.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 40.0, overrideCableSize = "5x6 mm²"
            ),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "ATS1-OUT-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "ATS1-OUT-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "ATS2-OUT-1", fedFrom = "ATS 2", panelType = "DB"),
            CircuitEntity(id = 21, projectId = projectId, circuitName = "ATS2-OUT-2", fedFrom = "ATS 2", panelType = "DB")
        )
        return Fixture("narrowSingleFeeder", projectId, transformers, generators, atsList, circuits)
    }

    /** Wide panel: main distribution board grown wide with six feeder columns + dual ATS. */
    private fun wideMultiFeeder(projectId: String): Fixture {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val feeders = listOf(
            CircuitEntity(id = 2, projectId = projectId, circuitName = "Feeder-N1", fedFrom = "DB-MAIN", panelType = "DB", overrideDeviceType = "MCCB", overrideDeviceSize = 40.0),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "Feeder-N2", fedFrom = "DB-MAIN", panelType = "DB", overrideDeviceType = "MCCB", overrideDeviceSize = 40.0),
            CircuitEntity(id = 4, projectId = projectId, circuitName = "Feeder-L1", fedFrom = "DB-MAIN", panelType = "DB", overrideDeviceType = "MCCB", overrideDeviceSize = 160.0),
            CircuitEntity(id = 5, projectId = projectId, circuitName = "SMDB-A", fedFrom = "DB-MAIN", panelType = "SMDB", overrideDeviceType = "MCCB", overrideDeviceSize = 250.0),
            CircuitEntity(id = 6, projectId = projectId, circuitName = "SMDB-B", fedFrom = "DB-MAIN", panelType = "SMDB", overrideDeviceType = "MCCB", overrideDeviceSize = 250.0),
            CircuitEntity(id = 7, projectId = projectId, circuitName = "Pump-1", fedFrom = "DB-MAIN", panelType = "LOAD", overrideDeviceType = "MCCB", overrideDeviceSize = 80.0)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId,
                circuitName = "DB-MAIN", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB", cableType = "Cu/XLPE/SWA/PVC", length_m = 25.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 400.0, overrideCableSize = "4x185+95 mm²"
            )
        ) + feeders + listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "ATS1-OUT-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "ATS1-OUT-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "ATS2-OUT-1", fedFrom = "ATS 2", panelType = "DB"),
            CircuitEntity(id = 21, projectId = projectId, circuitName = "ATS2-OUT-2", fedFrom = "ATS 2", panelType = "DB")
        )
        return Fixture("wideMultiFeeder", projectId, transformers, generators, atsList, circuits)
    }

    /** Cascaded ATS outputs: each ATS output board feeds a downstream DB that feeds a final load. */
    private fun cascadedAtsOutputs(projectId: String): Fixture {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId,
                circuitName = "DB-MAIN", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB", cableType = "Cu/XLPE/SWA/PVC", length_m = 25.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 400.0, overrideCableSize = "4x185+95 mm²"
            ),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "ATS1-OUT-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DB-CAS-A1", fedFrom = "ATS1-OUT-1", panelType = "DB"),
            CircuitEntity(id = 14, projectId = projectId, circuitName = "LOAD-CAS-A", fedFrom = "DB-CAS-A1"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "ATS2-OUT-1", fedFrom = "ATS 2", panelType = "DB"),
            CircuitEntity(id = 22, projectId = projectId, circuitName = "DB-CAS-B1", fedFrom = "ATS2-OUT-1", panelType = "DB"),
            CircuitEntity(id = 24, projectId = projectId, circuitName = "LOAD-CAS-B", fedFrom = "DB-CAS-B1")
        )
        return Fixture("cascadedAtsOutputs", projectId, transformers, generators, atsList, circuits)
    }

    private fun runOnAllEngines(fixture: Fixture): List<Pair<String, SldEngineResult>> {
        val facadeResult = SldLayoutEngine.calculateLayout(
            activeProjectId = fixture.projectId,
            transformers = fixture.transformers,
            circuits = fixture.circuits,
            generators = fixture.generators,
            atsList = fixture.atsList
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = fixture.projectId,
            transformers = fixture.transformers,
            circuits = fixture.circuits,
            generators = fixture.generators,
            atsList = fixture.atsList
        )
        val riserResult = RiserLayoutEngine.calculateLayout(graph, fixture.circuits)
        val shopResult = ShopDrawingLayoutEngine.calculateLayout(graph, fixture.circuits)
        return listOf(
            "SldLayoutEngine" to facadeResult,
            "RiserLayoutEngine" to riserResult,
            "ShopDrawingLayoutEngine" to shopResult
        )
    }

    private fun assertOrthogonalRouting(fixtureName: String, engineName: String, result: SldEngineResult) {
        assertTrue("$fixtureName/$engineName: layout must position nodes", result.nodes.isNotEmpty())
        assertTrue("$fixtureName/$engineName: layout must route edges", result.edges.isNotEmpty())
        val epsilon = 0.01f
        for (edge in result.edges) {
            val pts = edge.pathPoints
            assertTrue(
                "$fixtureName/$engineName: edge ${edge.fromId}->${edge.toId} must have at least 2 waypoints (got ${pts.size})",
                pts.size >= 2
            )
            for (i in 0 until pts.size - 1) {
                val (x1, y1) = pts[i]
                val (x2, y2) = pts[i + 1]
                assertTrue(
                    "$fixtureName/$engineName: edge ${edge.fromId}->${edge.toId} segment $i (($x1,$y1)->($x2,$y2)) is not orthogonal",
                    abs(x1 - x2) <= epsilon || abs(y1 - y2) <= epsilon
                )
            }
        }
    }

    @Test
    fun narrowSingleFeeder_edgesOrthogonalOnAllEngines() {
        runOnAllEngines(narrowSingleFeeder("inv-narrow")).forEach { (engineName, result) ->
            assertOrthogonalRouting("narrowSingleFeeder", engineName, result)
        }
    }

    @Test
    fun wideMultiFeeder_edgesOrthogonalOnAllEngines() {
        runOnAllEngines(wideMultiFeeder("inv-wide")).forEach { (engineName, result) ->
            assertOrthogonalRouting("wideMultiFeeder", engineName, result)
        }
    }

    @Test
    fun cascadedAtsOutputs_edgesOrthogonalOnAllEngines() {
        runOnAllEngines(cascadedAtsOutputs("inv-cascade")).forEach { (engineName, result) ->
            assertOrthogonalRouting("cascadedAtsOutputs", engineName, result)
        }
    }
}