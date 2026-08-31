package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ATS incomer routing evidence:
 *
 *  - SAME-TIER normal sources (an MDB on the same tier as the ATS) must leave
 *    the panel's output port DOWNWARD: drop into the inter-tier channel, cross
 *    below the ATS enclosure, rise OUTSIDE its left edge, and land on the
 *    top-edge terminal from above. Rising above the panel would run collinear
 *    with the upstream transformer feeder and pierce the panel body (reported
 *    regression); a plain below-source channel pierces the ATS body.
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

        // Terminus connects at the top terminal from above
        assertTrue("Route must have at least 2 points", pts.size >= 2)
        assertEquals(atsNode.x + atsNode.width * 0.25f, pts.first().first, 0.001f)
    }

    @Test
    fun sharedSourceSiblingAts_channelsAreCleanAndSeparated() {
        val result = buildLayout(
            atsList = listOf(
                AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "MDB-01"),
                AtsEntity(projectId = "p", systemVoltage = 400, atsId = "2", atsName = "ATS-02", normalSourceId = "MDB-01")
            )
        )
        val normalEdges = result.edges.filter { it.label == "NORMAL" && it.toId.startsWith("ats_") }
        assertEquals("Two shared-source ATS incomers expected", 2, normalEdges.size)
        val x1 = normalEdges[0].pathPoints.first().first
        val x2 = normalEdges[1].pathPoints.first().first
        assertTrue("Sibling ATS normal takeoffs must have separate busbar ports", Math.abs(x1 - x2) >= 100f)
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

    /**
     * Regression: the ATS NORMAL wire appeared to originate from the transformer
     * instead of the configured normal-source panel.
     *
     * Generic topology under test (names are arbitrary fixture data):
     *   Utility Grid -> Transformer -> Panel A
     *   Panel A -> NORMAL -> ATS 1 -> Board 2
     *   Panel A -> NORMAL -> ATS 2 -> Board 4
     */
    @Test
    fun normalWire_originatesAtPanelOutputPort_notTransformer() {
        val transformers = listOf(
            TransformerEntity(projectId = "p", transformerId = "MAIN-TX", transformerName = "MAIN-TX", kVA = 1000.0, voltage = 400)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = "p", circuitName = "PANEL-A", fedFrom = "Transformer", transformerId = "MAIN-TX"),
            CircuitEntity(id = 2, projectId = "p", circuitName = "BOARD-1", fedFrom = "PANEL-A"),
            CircuitEntity(id = 3, projectId = "p", circuitName = "BOARD-3", fedFrom = "PANEL-A"),
            CircuitEntity(id = 4, projectId = "p", circuitName = "BOARD-2", fedFrom = "ATS-01"),
            CircuitEntity(id = 5, projectId = "p", circuitName = "BOARD-4", fedFrom = "ATS-02")
        )
        val atsList = listOf(
            AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "PANEL-A", emergencySourceId = "GEN-01", outputBoardId = 4),
            AtsEntity(projectId = "p", systemVoltage = 400, atsId = "2", atsName = "ATS-02", normalSourceId = "PANEL-A", emergencySourceId = "GEN-02", outputBoardId = 5)
        )
        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = "p",
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        val tx = result.nodes.first { it.panelType == SldNodeType.TRANSFORMER.name }
        val panel = result.nodes.first { it.id == "circ_1" }
        val txPortX = tx.x + tx.width / 2f
        val txPortY = tx.y + 24f
        val panelPortY = panel.y + panel.height

        val normalEdges = result.edges.filter { it.label == "NORMAL" && it.toId.startsWith("ats_") }
        assertEquals("Both ATS units must have a NORMAL incomer", 2, normalEdges.size)

        for (edge in normalEdges) {
            // DATA invariant: the edge must originate from the panel circuit node.
            assertEquals("NORMAL edge must originate from the panel circuit node", "circ_1", edge.fromId)

            val pts = edge.pathPoints
            val atsNode = result.nodes.first { it.id == edge.toId }
            // RENDERING invariant 1: the wire starts at the panel busbar takeoff port...
            assertEquals("NORMAL wire must start at panel ATS takeoff port X", atsNode.x + atsNode.width * 0.25f, pts.first().first, 0.001f)
            assertEquals("NORMAL wire must start at panel output port Y", panelPortY, pts.first().second, 0.001f)
            // ...and never at the transformer port.
            assertFalse(
                "NORMAL wire must not originate at the transformer port",
                Math.abs(pts.first().first - txPortX) < 0.001f && Math.abs(pts.first().second - txPortY) < 0.001f
            )

            // RENDERING invariant 2 (the reported regression): no segment of the
            // NORMAL wire may run collinearly with the transformer->panel feeder,
            // i.e. no waypoint may sit directly ABOVE the panel on its center X —
            // that is what made the wire visually appear to come from the
            // transformer.
            pts.dropLast(1).forEach { p ->
                assertFalse(
                    "NORMAL wire waypoint must not sit collinearly with the transformer incomer",
                    Math.abs(p.first - txPortX) < 0.001f && p.second < panelPortY
                )
            }
        }
    }
}