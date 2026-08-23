package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ATS incomer routing evidence:
 *
 *  - SAME-TIER normal sources (an MDB on the same tier as the ATS) must route
 *    their channel ABOVE the ATS enclosure so the wire lands on the top-edge
 *    terminal from above. Routing below pierces the symbol body (regression).
 *  - The tier-0 (transformer) incomer keeps its original below-source channel,
 *    which already approaches the ATS from above.
 *  - Shared-source sibling ATS incomers must use staggered channels so wires
 *    and NORMAL labels never overlap.
 */
class SldAtsRoutingTest {

    private fun buildLayout(atsList: List<AtsEntity>, extraCircuits: List<CircuitEntity> = emptyList()): SldEngineResult {
        val transformers = listOf(
            TransformerEntity(projectId = "p", transformerId = "TX-01", transformerName = "TX-01", kVA = 1000.0, voltage = 400)
        )
        val circuits = mutableListOf(
            CircuitEntity(id = 1, projectId = "p", circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01")
        )
        circuits.addAll(extraCircuits)
        return SldLayoutEngine.calculateLayout(
            activeProjectId = "p",
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )
    }

    private fun enclosureSpan(node: PositionedSldNode): Triple<Float, Float, Pair<Float, Float>> {
        val g = SldSymbols.atsSymbolGeometry(node.x, node.y, node.width)
        val pad = (g.emergencyTerminal.x - g.normalTerminal.x) * 0.08f
        return Triple(g.normalTerminal.x - pad, g.emergencyTerminal.x + pad, g.normalTerminal.y to g.outputTerminal.y)
    }
    @Test
    fun sameTierNormalIncomer_routesAboveEnclosure_andNeverPiercesSymbol() {
        val result = buildLayout(
            atsList = listOf(AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "MDB-01")),
            extraCircuits = listOf(CircuitEntity(id = 2, projectId = "p", circuitName = "PANEL-A", fedFrom = "ATS-01"))
        )
        val atsNode = result.nodes.first { it.panelType == SldNodeType.ATS.name }
        val (encLeft, encRight, encTopToBottom) = enclosureSpan(atsNode)
        val encTop = encTopToBottom.first

        val normalEdge = result.edges.last { it.toId == atsNode.id && it.label == "NORMAL" }
        val pts = normalEdge.pathPoints

        // The terminus must be the top-edge terminal itself.
        assertEquals(atsNode.x + atsNode.width * 0.25f, pts.last().first, 0.001f)
        assertEquals(encTop, pts.last().second, 0.001f)

        // Every intermediate waypoint must stay STRICTLY ABOVE the enclosure top:
        // a waypoint at/below the top edge means the wire pierces the symbol body.
        val internals = pts.dropLast(1)
        assertTrue("Fixture must produce an orthogonal multi-point route", internals.isNotEmpty())
        for (p in internals) {
            assertTrue(
                "Waypoint (${p.first}, ${p.second}) pierces the ATS enclosure (top=$encTop)",
                p.second < encTop - 0.5f || p.first < encLeft || p.first > encRight
            )
        }
    }

    @Test
    fun sharedSourceSiblingAts_channelsAreStaggered() {
        val result = buildLayout(
            atsList = listOf(
                AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "MDB-01"),
                AtsEntity(projectId = "p", systemVoltage = 400, atsId = "2", atsName = "ATS-02", normalSourceId = "MDB-01")
            )
        )
        val channels = result.edges
            .filter { it.label == "NORMAL" && it.toId.startsWith("ats_") }
            .map { it.pathPoints[1].second }
            .sorted()
        assertEquals("Two shared-source ATS incomers expected", 2, channels.size)
        val expectedStep = com.example.sld.SldTheme.feederDrop() / 4f
        assertEquals(
            "Sibling channels must be staggered by exactly one step",
            expectedStep, channels[1] - channels[0], 0.001f
        )
    }

    @Test
    fun transformerIncomer_keepsOriginalBelowSourceChannel() {
        val result = buildLayout(
            atsList = listOf(AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "TX-01"))
        )
        val txNode = result.nodes.first { it.panelType == SldNodeType.TRANSFORMER.name }
        val normalEdge = result.edges.last { it.label == "NORMAL" && it.toId.startsWith("ats_") }
        val pts = normalEdge.pathPoints

        assertTrue(pts.size >= 4)
        // Locked behavior: tier-0 source exits from its bottom (y+24) and the
        // channel stays at fromY+95, which already approaches the ATS from above.
        assertEquals(txNode.y + 24f + 95f, pts[1].second, 0.001f)
    }
}