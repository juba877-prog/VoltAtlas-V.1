package com.example.sld

import com.example.data.AtsEntity
import com.example.data.BusTieEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // =========================================================================
    // Phase 3 — Main-Tie-Main horizontal routing + multi-generator emergency
    // =========================================================================

    private fun doesSegmentIntersectBox(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float
    ): Boolean {
        val isVertical = abs(p1.first - p2.first) < 1e-3f
        val isHorizontal = abs(p1.second - p2.second) < 1e-3f
        if (isVertical) {
            val segX = p1.first
            val minY = minOf(p1.second, p2.second)
            val maxY = maxOf(p1.second, p2.second)
            return (segX >= boxLeft && segX <= boxRight) && (minY < boxBottom && maxY > boxTop)
        } else if (isHorizontal) {
            val segY = p1.second
            val minX = minOf(p1.first, p2.first)
            val maxX = maxOf(p1.first, p2.first)
            return (segY >= boxTop && segY <= boxBottom) && (minX < boxRight && maxX > boxLeft)
        }
        return false
    }

    /**
     * Generic Main-Tie-Main topology (names are arbitrary fixture data):
     *
     *   Transformer -> Board A (MDB)   [strict id 1]
     *   Transformer -> Board B (MDB)   [strict id 2]
     *   BusTieEntity(sourceBoardId = 1, targetBoardId = 2)
     *
     * Invariants:
     *  - Strict relational IDs synthesize exactly 2 BUS_TIE_LINK edges, no
     *    validation issues.
     *  - Both tie links route HORIZONTALLY at the boards' shared busbar
     *    centerline (BoardA.RIGHT → BUS_TIE → BoardB.LEFT) — never the vertical
     *    drop-from-source pattern.
     *  - No tie-wire segment crosses any other node's box (no vertical
     *    collisions).
     */
    @Test
    fun mainTieMain_routesHorizontallyAtBusbarCenterline() {
        val projectId = "inv-mtm"
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId, circuitName = "BOARD-A",
                fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"
            ),
            CircuitEntity(
                id = 2, projectId = projectId, circuitName = "BOARD-B",
                fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"
            )
        )
        val busTies = listOf(
            BusTieEntity(
                projectId = projectId, id = 1, busTieId = "TIE-01", name = "Bus Tie 1",
                sourceBoardId = 1, targetBoardId = 2
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            busTies = busTies
        )
        assertTrue(
            "Strict board refs must resolve — no validation issues expected",
            graph.validationIssues.isEmpty()
        )
        val tieLinks = graph.edges.filter { it.type == SldEdgeType.BUS_TIE_LINK }
        assertEquals("Main-Tie-Main must synthesize exactly 2 BUS_TIE_LINK edges", 2, tieLinks.size)

        val result = RiserLayoutEngine.calculateLayout(graph, circuits)
        assertOrthogonalRouting("mainTieMain", "RiserLayoutEngine", result)

        val boardA = result.nodes.first { it.id == "circ_1" }
        val boardB = result.nodes.first { it.id == "circ_2" }
        val centerlineY = boardA.y + boardA.height / 2f
        assertEquals(
            "Coupled boards must share one busbar centerline",
            centerlineY, boardB.y + boardB.height / 2f, 0.01f
        )

        val tieEdges = result.edges.filter { edge ->
            tieLinks.any { link -> link.fromNodeId == edge.fromId && link.toNodeId == edge.toId }
        }
        assertEquals("Both BUS_TIE_LINK edges must be routed", 2, tieEdges.size)

        for (edge in tieEdges) {
            val pts = edge.pathPoints
            assertTrue("Tie edge ${edge.fromId}->${edge.toId} needs >= 2 waypoints", pts.size >= 2)
            for (p in pts) {
                assertEquals(
                    "Tie wire ${edge.fromId}->${edge.toId} must run at the busbar centerline",
                    centerlineY, p.second, 0.01f
                )
            }
        }

        // No vertical collisions: horizontal tie wires cross no other node box.
        for (edge in tieEdges) {
            for (i in 0 until edge.pathPoints.size - 1) {
                val p1 = edge.pathPoints[i]
                val p2 = edge.pathPoints[i + 1]
                for (node in result.nodes) {
                    if (node.id == edge.fromId || node.id == edge.toId) continue
                    assertFalse(
                        "Tie wire segment $p1->$p2 must not cross node box ${node.id}",
                        doesSegmentIntersectBox(
                            p1, p2, node.x, node.y, node.x + node.width, node.y + node.height
                        )
                    )
                }
            }
        }
    }

    /**
     * Generic paralleled-generator topology (names are arbitrary fixture data):
     *
     *   Generator 1 ─┐
     *                ├─ (shared syncBusId) → SYNC_BUS → ATS_EMERGENCY_PATH → ATS 1
     *   Generator 2 ─┘
     *   Transformer -> Board A (MDB) -> ATS 1 (NORMAL)
     *
     * Invariant: every ATS_EMERGENCY_PATH segment stays clear of the main
     * board's busbar rectangle — emergency risers must never intersect the
     * NORMAL source rail.
     */
    @Test
    fun multiGeneratorEmergencyLines_avoidMainBusbarRectangle() {
        val projectId = "inv-sync-emerg"
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(
                projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1",
                manualSizeKVA = 40.0, syncBusId = "SYNC-A"
            ),
            GeneratorEntity(
                projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2",
                manualSizeKVA = 30.0, syncBusId = "SYNC-A"
            )
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId, atsId = "ATS-01", atsName = "ATS 1",
                normalSourceId = "BOARD-A", emergencySourceId = "GEN-01", outputBoardId = 10
            )
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId, circuitName = "BOARD-A",
                fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"
            ),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "ATS1-OUT-1", fedFrom = "ATS 1", panelType = "DB")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )
        assertTrue(
            "Paralleled generators must synthesize a sync bus without issues",
            graph.validationIssues.isEmpty()
        )
        assertTrue(
            "Sync bus must be synthesized from the shared syncBusId",
            graph.nodes.any { it.type == SldNodeType.SYNC_BUS }
        )

        val result = RiserLayoutEngine.calculateLayout(graph, circuits)
        assertOrthogonalRouting("multiGenEmergency", "RiserLayoutEngine", result)

        val mdb = result.nodes.first { it.id == "circ_1" }
        val emergencyEdges = result.edges.filter { it.isEmergency }
        assertTrue("Fixture must route at least one emergency edge", emergencyEdges.isNotEmpty())

        for (edge in emergencyEdges) {
            for (i in 0 until edge.pathPoints.size - 1) {
                val p1 = edge.pathPoints[i]
                val p2 = edge.pathPoints[i + 1]
                assertFalse(
                    "Emergency segment ${edge.fromId}->${edge.toId} $p1->$p2 must not " +
                        "intersect the main busbar rectangle",
                    doesSegmentIntersectBox(p1, p2, mdb.x, mdb.y, mdb.x + mdb.width, mdb.y + mdb.height)
                )
            }
        }
    }
}