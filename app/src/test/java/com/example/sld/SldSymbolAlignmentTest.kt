package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AtsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic geometry-level evidence for the SLD symbol corrections:
 *
 *  1. ATS input terminals land EXACTLY on the layout-provided NORMAL/EMERGENCY
 *     port coordinates (RiserLayoutEngine: x+width*0.25 / x+width*0.75, y-24f)
 *     and the output terminal matches the downstream feeder origin
 *     (x+width*0.5, y+24f) - proven against a real calculateLayout() result.
 *  2. ATS symbol geometry scales with node width (multi-instance safe).
 *  3. Breaker terminal dots/stroke are ratio-based and remain larger than the
 *     feeder wire stroke at phone densities (legibility floor at default zoom).
 */
class SldSymbolAlignmentTest {

    private fun buildLayoutWithAts(atsList: List<AtsEntity>): SldEngineResult {
        val transformers = listOf(
            TransformerEntity(
                projectId = "p",
                transformerId = "TX-01",
                transformerName = "TX-01",
                kVA = 1000.0,
                voltage = 400
            )
        )
        val mdb = CircuitEntity(id = 1, projectId = "p", circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01")
        val atsPanel = CircuitEntity(id = 2, projectId = "p", circuitName = "PANEL-A", fedFrom = "ATS-01")
        val circuits = listOf(mdb, atsPanel)
        return SldLayoutEngine.calculateLayout(
            activeProjectId = "p",
            transformers = transformers,
            circuits = circuits,
            atsResults = mapOf("1" to AtsResult(finalRating = 1600.0, governingCurrent = 1443.0, status = "PASS")),
            atsList = atsList
        )
    }
    @Test
    fun atsInputTerminals_landExactlyOnLayoutPorts() {
        val result = buildLayoutWithAts(
            listOf(AtsEntity(projectId = "p", systemVoltage = 400, atsId = "1", atsName = "ATS-01", normalSourceId = "TX-01"))
        )
        val atsNode = result.nodes.first { it.panelType == SldNodeType.ATS.name }
        val geometry = SldSymbols.atsSymbolGeometry(atsNode.x, atsNode.y, atsNode.width)

        // Every ATS-incoming edge must terminate exactly on the matching symbol terminal.
        val atsIncoming = result.edges.filter { it.toId == atsNode.id && (it.label == "NORMAL" || it.label == "EMERGENCY") }
        assertTrue("Fixture must produce at least the NORMAL incomer", atsIncoming.isNotEmpty())
        for (edge in atsIncoming) {
            val terminus = edge.pathPoints.last()
            val expectedX = if (edge.label == "EMERGENCY") geometry.emergencyTerminal.x else geometry.normalTerminal.x
            assertEquals("$edge-label terminus X must equal symbol terminal X", expectedX, terminus.first, 0.001f)
            assertEquals("$edge-label terminus Y must equal symbol terminal Y", geometry.normalTerminal.y, terminus.second, 0.001f)
        }

        // Output terminal must equal the downstream feeder origin (source-classified node).
        val outEdge = result.edges.first { it.fromId == atsNode.id }
        val origin = outEdge.pathPoints.first()
        assertEquals(geometry.outputTerminal.x, origin.first, 0.001f)
        assertEquals(geometry.outputTerminal.y, origin.second, 0.001f)
    }

    @Test
    fun atsGeometry_scalesWithNodeWidth_multiInstanceSafe() {
        val narrow = SldSymbols.atsSymbolGeometry(0f, 0f, 140f)
        val wide = SldSymbols.atsSymbolGeometry(0f, 0f, 280f)

        assertEquals(35f, narrow.normalTerminal.x, 0.0001f)
        assertEquals(105f, narrow.emergencyTerminal.x, 0.0001f)
        assertEquals(140f, wide.emergencyTerminal.x - wide.normalTerminal.x, 0.0001f)
        assertEquals(narrow.normalTerminal.y, wide.normalTerminal.y, 0.0001f)
        assertEquals(narrow.outputTerminal.x * 2f, wide.outputTerminal.x, 0.0001f)
    }

    @Test
    fun breakerDots_exceedWireStroke_atPhoneDensity() {
        val pixel6Density = 2.625f
        val sizePx = SldTheme.breakerBoxSize() * pixel6Density
        // Terminal dot diameter must exceed the feeder wire stroke so the contact
        // reads as a device on the line rather than a kink in it.
        assertTrue(
            "Dot diameter ${SldSymbols.breakerDotRadius(sizePx) * 2f} must exceed feeder stroke ${SldTheme.feederStroke()}",
            SldSymbols.breakerDotRadius(sizePx) * 2f > SldTheme.feederStroke()
        )
    }

    @Test
    fun breaker_meetsMinimumLegibleSpanAtDefaultZoom() {
        // At zoom 1.0 on a Pixel-6-class display the breaker span must be at least
        // 40px (~15dp), i.e. clearly visible without zoom assistance.
        val pixel6Density = 2.625f
        val sizePx = SldTheme.breakerBoxSize() * pixel6Density
        assertTrue("Breaker span $sizePx px is below the legibility floor", sizePx >= 40f)
        assertTrue(SldSymbols.breakerStrokeWidth(sizePx) >= SldTheme.feederStroke())
    }
}