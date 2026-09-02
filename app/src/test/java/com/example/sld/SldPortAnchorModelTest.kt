package com.example.sld

import androidx.compose.ui.geometry.Offset
import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 §3.1 — First-Class Port Anchor model verification.
 *
 *  - ATS nodes expose TOP NORMAL (0.25·w) / TOP EMERGENCY (0.75·w) and
 *    BOTTOM OUTPUT (0.5·w) anchors matching [SldSymbols.atsSymbolGeometry].
 *  - Source nodes (generator / transformer / utility / UPS) expose a BOTTOM
 *    output anchor at the wire terminus (centerX, y + 24f).
 *  - Busbar panels expose one BOTTOM tap anchor per busbar port, a TOP incoming
 *    terminal and LEFT/RIGHT centerline coupler anchors.
 *  - The routed ATS wire terminus equals the pre-calculated NORMAL anchor, and
 *    [SldSymbols.atsSymbolGeometry] consumes the anchors directly.
 */
class SldPortAnchorModelTest {

    private fun buildRiserResult(): SldEngineResult {
        val projectId = "port-anchor"
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0)
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId, atsId = "ATS-01", atsName = "ATS 1",
                normalSourceId = "DB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10
            )
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId, circuitName = "DB-MAIN", fedFrom = "Transformer",
                transformerId = "TX-01", panelType = "MDB"
            ),
            CircuitEntity(
                id = 2, projectId = projectId, circuitName = "FEEDER-A", fedFrom = "DB-MAIN",
                panelType = "DB"
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
        return RiserLayoutEngine.calculateLayout(graph, circuits)
    }

    private fun node(result: SldEngineResult, id: String): PositionedSldNode =
        result.nodes.first { it.id == id }

    @Test
    fun atsNode_exposesNormalEmergencyAndOutputAnchors() {
        val result = buildRiserResult()
        val ats = node(result, "ats_ATS-01")

        val top = ats.ports[PortSide.TOP].orEmpty()
        assertEquals("ATS must expose two TOP terminals", 2, top.size)

        val normal = top.firstOrNull { it.id.endsWith(".NORMAL") }
        val emergency = top.firstOrNull { it.id.endsWith(".EMERGENCY") }
        assertNotNull("ATS NORMAL anchor must exist", normal)
        assertNotNull("ATS EMERGENCY anchor must exist", emergency)
        assertEquals(ats.x + ats.width * 0.25f, normal!!.offset.x, 0.001f)
        assertEquals(ats.y - 24f, normal.offset.y, 0.001f)
        assertEquals(ats.x + ats.width * 0.75f, emergency!!.offset.x, 0.001f)
        assertEquals(ats.y - 24f, emergency.offset.y, 0.001f)
        assertTrue("NORMAL anchor is occupied by the incomer wire", normal.isOccupied)

        val output = ats.ports[PortSide.BOTTOM].orEmpty().firstOrNull()
        assertNotNull("ATS OUTPUT anchor must exist", output)
        assertEquals(ats.x + ats.width * 0.5f, output!!.offset.x, 0.001f)
        assertEquals(ats.y + 24f, output.offset.y, 0.001f)
    }

    @Test
    fun atsSymbolGeometry_consumesPrecalculatedAnchors() {
        val result = buildRiserResult()
        val ats = node(result, "ats_ATS-01")

        val geometry = SldSymbols.atsSymbolGeometry(ats)
        assertEquals(
            "Normal terminal must come from the NORMAL anchor",
            ats.ports[PortSide.TOP]!!.first { it.id.endsWith(".NORMAL") }.offset,
            geometry.normalTerminal
        )
        assertEquals(
            "Emergency terminal must come from the EMERGENCY anchor",
            ats.ports[PortSide.TOP]!!.first { it.id.endsWith(".EMERGENCY") }.offset,
            geometry.emergencyTerminal
        )
        assertEquals(
            "Output terminal must come from the OUTPUT anchor",
            ats.ports[PortSide.BOTTOM]!!.first().offset,
            geometry.outputTerminal
        )

        // The routed NORMAL incomer must terminate exactly on the anchor.
        val normalEdge = result.edges.first { it.toId == ats.id && it.label == "NORMAL" }
        val terminus = normalEdge.pathPoints.last()
        assertEquals("Wire terminus X must equal NORMAL anchor X", geometry.normalTerminal.x, terminus.first, 0.001f)
        assertEquals("Wire terminus Y must equal NORMAL anchor Y", geometry.normalTerminal.y, terminus.second, 0.001f)
    }

    @Test
    fun sourceAndPanelNodes_exposeExpectedAnchors() {
        val result = buildRiserResult()

        // Generator source: single BOTTOM output at (centerX, y + 24f).
        val gen = node(result, "gen_GEN-01")
        val genOut = gen.ports[PortSide.BOTTOM].orEmpty().firstOrNull()
        assertNotNull("Generator must expose a BOTTOM output anchor", genOut)
        assertEquals(gen.x + gen.width / 2f, genOut!!.offset.x, 0.001f)
        assertEquals(gen.y + 24f, genOut.offset.y, 0.001f)

        // MDB panel: TOP incoming terminal + one BOTTOM tap per busbar port + LEFT/RIGHT couplers.
        val panel = node(result, "circ_1")
        assertEquals("Panel must expose a TOP incoming terminal", 1, panel.ports[PortSide.TOP].orEmpty().size)
        val taps = panel.ports[PortSide.BOTTOM].orEmpty()
        assertTrue("Panel must expose at least one BOTTOM busbar tap anchor", taps.isNotEmpty())
        val busbarPorts = result.busbarPorts[panel.id].orEmpty()
        assertEquals("One tap anchor per busbar port", busbarPorts.size, taps.size)
        busbarPorts.forEachIndexed { i, port ->
            assertEquals(port.tapX, taps[i].offset.x, 0.001f)
            assertEquals(panel.y + panel.height, taps[i].offset.y, 0.001f)
        }
        assertEquals(
            "Panel LEFT coupler at centerline",
            Offset(panel.x, panel.y + panel.height / 2f),
            panel.ports[PortSide.LEFT]!!.first().offset
        )
        assertEquals(
            "Panel RIGHT coupler at centerline",
            Offset(panel.x + panel.width, panel.y + panel.height / 2f),
            panel.ports[PortSide.RIGHT]!!.first().offset
        )

        // A panel feeder wire must originate at the modeled tap anchor X.
        val tapChild = taps.first().id.substringAfterLast(".TAP.")
        val feederEdge = result.edges.firstOrNull { it.fromId == panel.id && it.toId == tapChild }
        assertNotNull("Feeder wire to tap child must exist", feederEdge)
        assertEquals("Feeder wire must originate at the tap anchor X", taps.first().offset.x, feederEdge!!.pathPoints.first().first, 0.001f)
    }
}