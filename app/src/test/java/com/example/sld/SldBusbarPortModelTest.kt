package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Busbar port model contract (Roadmap §2.3):
 *
 *  - Every panel node (BUS/MDB/SMDB/DB/MCC) exposes exactly one explicit tap
 *    port per non-ATS child, ordered left-to-right along the bar.
 *  - Every tap lies strictly inside the bar span with a [RiserLayoutEngine.BUSBAR_TAP_END_PAD]
 *    clearance from either end — a tap can never land off the rail.
 *  - Taps are strictly increasing and well separated — feeders can never bunch
 *    up or cross each other along the bar.
 *  - The model is BINDING, not decorative: each tap X equals the child node's
 *    top-center X and the origin X of the drawn feeder wire.
 *  - ATS children never consume busbar ports (they own AtsSymbolGeometry terminals).
 */
class SldBusbarPortModelTest {

    private val eps = 0.01f

    private fun baseFixture(projectId: String, feeders: List<CircuitEntity>): SldEngineResult {
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
            )
        ) + feeders + listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "ATS1-OUT-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "ATS2-OUT-1", fedFrom = "ATS 2", panelType = "DB")
        )
        return SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )
    }

    private fun feeder(projectId: String, id: Int, name: String): CircuitEntity = CircuitEntity(
        id = id, projectId = projectId, circuitName = name, fedFrom = "DB-MAIN",
        panelType = "DB", overrideDeviceType = "MCCB", overrideDeviceSize = 40.0
    )

    private val panelTypes = RiserLayoutEngine.PANEL_BUSBAR_TYPES.map { it.name }.toSet()

    private fun assertPortContract(result: SldEngineResult, fixtureName: String) {
        val nodesById = result.nodes.associateBy { it.id }
        assertTrue("$fixtureName: fixture must produce busbar ports", result.busbarPorts.isNotEmpty())
        for ((panelId, ports) in result.busbarPorts) {
            val panel = nodesById[panelId]
                ?: throw AssertionError("$fixtureName: port panel $panelId not found in nodes")
            assertTrue("$fixtureName: $panelId must be a busbar panel type", panel.panelType in panelTypes)
            assertTrue("$fixtureName: $panelId must have at least one port", ports.isNotEmpty())
            var prevTap = Float.NEGATIVE_INFINITY
            ports.forEachIndexed { index, port ->
                assertEquals("$fixtureName: $panelId port indices must be 0..n-1 ordered", index, port.portIndex)
                assertTrue(
                    "$fixtureName: $panelId port ${port.childNodeId} tapX must lie inside the bar span",
                    port.tapX in (panel.x + RiserLayoutEngine.BUSBAR_TAP_END_PAD)..(panel.x + panel.width - RiserLayoutEngine.BUSBAR_TAP_END_PAD)
                )
                assertTrue(
                    "$fixtureName: $panelId taps must be strictly increasing and well spaced (gap=${port.tapX - prevTap})",
                    port.tapX - prevTap >= 100f
                )
                val child = nodesById[port.childNodeId]
                    ?: throw AssertionError("$fixtureName: port child ${port.childNodeId} not found in nodes")
                assertEquals(
                    "$fixtureName: $panelId port ${port.childNodeId} must bind to the child's top-center X",
                    child.x + child.width / 2f, port.tapX, eps
                )
                val feederEdge = result.edges.firstOrNull { it.fromId == panelId && it.toId == port.childNodeId }
                    ?: throw AssertionError("$fixtureName: missing feeder wire $panelId -> ${port.childNodeId}")
                assertEquals(
                    "$fixtureName: $panelId feeder wire to ${port.childNodeId} must originate at the modeled tap X",
                    port.tapX, feederEdge.pathPoints.first().first, eps
                )
                prevTap = port.tapX
            }
        }
        val atsPortChildren = result.busbarPorts.values.flatten().filter { it.childNodeId.startsWith("ats_") }
        assertTrue("$fixtureName: ATS children must never consume busbar ports", atsPortChildren.isEmpty())
    }

    @Test
    fun widePanel_exposesOnePortPerFeeder() {
        val projectId = "port-wide"
        val result = baseFixture(projectId, (2..7).map { feeder(projectId, it, "FEEDER-$it") })
        val ports = result.busbarPorts["circ_1"]
            ?: throw AssertionError("wide panel must expose busbar ports")
        assertEquals("wide panel must expose one port per feeder child", 6, ports.size)
        assertPortContract(result, "wideMultiFeeder")
    }

    @Test
    fun narrowPanel_exposesSinglePort() {
        val projectId = "port-narrow"
        val result = baseFixture(projectId, listOf(feeder(projectId, 2, "FEEDER-A")))
        val ports = result.busbarPorts["circ_1"]
            ?: throw AssertionError("narrow panel must expose a busbar port")
        assertEquals("narrow panel must expose exactly one port", 1, ports.size)
        assertPortContract(result, "narrowSingleFeeder")
    }

    @Test
    fun panelWithOnlyAtsChildren_exposesNoBusbarPorts() {
        // Main panel whose only children are ATS normal incomers: ATS never
        // consumes busbar ports, so the panel must have none at all.
        val projectId = "port-ats-only"
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId,
                circuitName = "DB-MAIN", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB"
            )
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DB-MAIN", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )
        assertTrue(
            "panel with only ATS children must expose no busbar ports, got ${result.busbarPorts.keys}",
            result.busbarPorts.isEmpty()
        )
    }
}