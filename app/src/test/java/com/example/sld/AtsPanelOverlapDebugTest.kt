package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reproduction harness for the owner-reported Gate-3 overlap:
 *   "ATS symbol/icon overlaps the MDB panel box outline (rectangle around DP-G3)".
 *
 * Dumps every positioned node + the SldCanvas-drawn geometry so we can prove
 * where the ATS sits relative to the panel box right edge for a NARROW panel
 * (1 feeder) vs a WIDE panel (6 feeder columns) — the strongest discriminator
 * for the "panel box width vs ATS fixed offset" hypothesis.
 */
class AtsPanelOverlapDebugTest {

    private fun buildGate3(feederChildren: List<CircuitEntity>): SldEngineResult {
        val transformers = listOf(
            TransformerEntity(projectId = "gate3", transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = "gate3", generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = "gate3", generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = "gate3", atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DP-G3", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = "gate3", atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DP-G3", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = "gate3",
                circuitName = "DP-G3", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB", cableType = "Cu/XLPE/SWA/PVC", length_m = 25.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 400.0, overrideCableSize = "4x185+95 mm²"
            )
        ) + feederChildren + listOf(
            CircuitEntity(id = 10, projectId = "gate3", circuitName = "UPP-G3-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 11, projectId = "gate3", circuitName = "UPP-G3-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 20, projectId = "gate3", circuitName = "LPP-G3-1", fedFrom = "ATS 2", panelType = "DB"),
            CircuitEntity(id = 21, projectId = "gate3", circuitName = "LPP-G3-2", fedFrom = "ATS 2", panelType = "DB")
        )
        return SldLayoutEngine.calculateLayout(
            activeProjectId = "gate3",
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )
    }

    private fun buildGate3WithManual(
        feederChildren: List<CircuitEntity>,
        manualPositions: Map<String, androidx.compose.ui.geometry.Offset>
    ): SldEngineResult {
        val transformers = listOf(
            TransformerEntity(projectId = "gate3", transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = "gate3", generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = "gate3", generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = "gate3", atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DP-G3", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = "gate3", atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DP-G3", emergencySourceId = "GEN-02", outputBoardId = 20)
        )
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = "gate3",
                circuitName = "DP-G3", fedFrom = "Transformer", transformerId = "TX-01",
                panelType = "MDB", cableType = "Cu/XLPE/SWA/PVC", length_m = 25.0,
                overrideDeviceType = "MCCB", overrideDeviceSize = 400.0, overrideCableSize = "4x185+95 mm²"
            )
        ) + feederChildren + listOf(
            CircuitEntity(id = 10, projectId = "gate3", circuitName = "UPP-G3-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 11, projectId = "gate3", circuitName = "UPP-G3-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 20, projectId = "gate3", circuitName = "LPP-G3-1", fedFrom = "ATS 2", panelType = "DB"),
            CircuitEntity(id = 21, projectId = "gate3", circuitName = "LPP-G3-2", fedFrom = "ATS 2", panelType = "DB")
        )
        return SldLayoutEngine.calculateLayout(
            activeProjectId = "gate3",
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList,
            manualPositions = manualPositions
        )
    }

    private fun mccb(id: Int, name: String, amps: Double, cable: String, panelType: String? = null): CircuitEntity =
        CircuitEntity(
            id = id, projectId = "gate3",
            circuitName = name, fedFrom = "DP-G3", transformerId = "TX-01",
            panelType = panelType, cableType = "Cu/XLPE/SWA/PVC", length_m = 40.0,
            overrideDeviceType = "MCCB", overrideDeviceSize = amps, overrideCableSize = cable
        )
private fun dump(title: String, result: SldEngineResult): String {
        val sb = StringBuilder("\n===== $title =====\n")
        val mdb = result.nodes.first { it.id == "circ_1" }
        val ats1 = result.nodes.first { it.id == "ats_ATS-01" }
        val ats2 = result.nodes.first { it.id == "ats_ATS-02" }

        // SldCanvas geometry replicas
        val encLeft = mdb.x - 36f
        val encRight = mdb.x + mdb.width + 36f
        val busbarRight = mdb.x + mdb.width
        val atsSym1 = SldSymbols.atsSymbolGeometry(ats1.x, ats1.y, ats1.width)
        val atsSym2 = SldSymbols.atsSymbolGeometry(ats2.x, ats2.y, ats2.width)
        val ats1BoxLeft = atsSym1.normalTerminal.x - (atsSym1.emergencyTerminal.x - atsSym1.normalTerminal.x) * 0.08f
        val ats1BoxRight = atsSym1.emergencyTerminal.x + (atsSym1.emergencyTerminal.x - atsSym1.normalTerminal.x) * 0.08f
        val ats2BoxLeft = atsSym2.normalTerminal.x - (atsSym2.emergencyTerminal.x - atsSym2.normalTerminal.x) * 0.08f

        sb.append("MDB(DP-G3): x=${mdb.x} y=${mdb.y} width=${mdb.width} (busbar right=$busbarRight, encRight=$encRight)\n")
        sb.append("ATS1: x=${ats1.x} width=${ats1.width} (symbol box $ats1BoxLeft..$ats1BoxRight)\n")
        sb.append("ATS2: x=${ats2.x} width=${ats2.width} (symbol box $ats2BoxLeft..${ats2.x + ats2.width})\n")

        val gapEnc1 = ats1.x - encRight
        val gapEnc2 = ats2.x - encRight
        val gapBus1 = ats1.x - busbarRight
        val gapAts1ToAts2 = ats2.x - (ats1.x + ats1.width)
        sb.append("gap(MDB enc right -> ATS1 node left) = $gapEnc1\n")
        sb.append("gap(MDB enc right -> ATS2 node left) = $gapEnc2\n")
        sb.append("gap(MDB busbar right -> ATS1 node left) = $gapBus1\n")
        sb.append("gap(ATS1 node right -> ATS2 node left) = $gapAts1ToAts2\n")
        sb.append("encRight - ats1BoxLeft = ${encRight - ats1BoxLeft}  (positive => ATS symbol LEFT of enclosure right)\n")
        if (ats1BoxLeft < encRight) sb.append("!!! ATS1 SYMBOL OVERLAPS MDB ENCLOSURE\n")
        if (ats2BoxLeft < encRight) sb.append("!!! ATS2 SYMBOL OVERLAPS MDB ENCLOSURE\n")

        sb.append("---- all nodes ----\n")
        result.nodes.sortedBy { it.x }.forEach { n ->
            sb.append("  ${n.id.padEnd(16)} ${n.panelType.padEnd(12)} label=${n.label} (${n.ratingText}) x=${n.x} y=${n.y} w=${n.width} h=${n.height}\n")
        }
        return sb.toString()
    }

    @Test
    fun dumpNarrowPanelGate3() {
        val result = buildGate3(
            listOf(mccb(2, "FEEDER-A", 40.0, "5x6 mm²", "DB"))
        )
        File("test_output_gate3_narrow.txt").writeText(dump("NARROW PANEL (1 feeder)", result))
    }

    @Test
    fun dumpWidePanelGate3() {
        // Mirror the screenshot's DP-G3: multiple MCCB columns (40A, 40A, 160A) + more
        val feeders = listOf(
            mccb(2, "Feeder-N1", 40.0, "5x6 mm²", "DB"),
            mccb(3, "Feeder-N2", 40.0, "5x6 mm²", "DB"),
            mccb(4, "Feeder-L1", 160.0, "5x35 mm²", "DB"),
            mccb(5, "SMDB-A", 250.0, "4x120 mm²", "SMDB"),
            mccb(6, "SMDB-B", 250.0, "4x120 mm²", "SMDB"),
            mccb(7, "Pump-1", 80.0, "5x16 mm²", "LOAD")
        )
        File("test_output_gate3_wide.txt").writeText(dump("WIDE PANEL (6 feeders / MCCB columns)", buildGate3(feeders)))
    }

    private fun assertAtsClearance(title: String, result: SldEngineResult, panelId: String, atsIds: List<String>) {
        val panel = result.nodes.first { it.id == panelId }
        val isMainPanel = panel.panelType.uppercase().contains("MDB") || panel.panelType.uppercase().contains("MAIN")
        val encPad = if (isMainPanel) RiserLayoutEngine.MAIN_PANEL_ENCLOSURE_PAD else 0f
        val boxRight = panel.x + panel.width + encPad

        var prevAtsRight = Float.NEGATIVE_INFINITY
        for (atsId in atsIds) {
            val ats = result.nodes.first { it.id == atsId }
            val minLeft = maxOf(boxRight, prevAtsRight)
            assertTrue(
                "$title: $atsId at x=${ats.x} must be >= panel box right $boxRight + gap ${RiserLayoutEngine.ATS_TO_PANEL_MIN_GAP}",
                ats.x >= minLeft + RiserLayoutEngine.ATS_TO_PANEL_MIN_GAP
            )
            println("$title OK: $atsId x=${ats.x}, panelBoxRight=$boxRight, gap=${ats.x - boxRight}")
            prevAtsRight = ats.x + ats.width
        }
    }

    @Test
    fun narrowAndWidePanels_atsClearsPanelBox() {
        val wideFeeders = listOf(
            mccb(2, "Feeder-N1", 40.0, "5x6 mm²", "DB"),
            mccb(3, "Feeder-N2", 40.0, "5x6 mm²", "DB"),
            mccb(4, "Feeder-L1", 160.0, "5x35 mm²", "DB"),
            mccb(5, "SMDB-A", 250.0, "4x120 mm²", "SMDB"),
            mccb(6, "SMDB-B", 250.0, "4x120 mm²", "SMDB"),
            mccb(7, "Pump-1", 80.0, "5x16 mm²", "LOAD")
        )
        val narrow = buildGate3(listOf(mccb(2, "FEEDER-A", 40.0, "5x6 mm²", "DB")))
        val wide = buildGate3(wideFeeders)

        assertAtsClearance("NARROW(1 feeder)", narrow, "circ_1", listOf("ats_ATS-01", "ats_ATS-02"))
        assertAtsClearance("WIDE(6 feeders)", wide, "circ_1", listOf("ats_ATS-01", "ats_ATS-02"))
    }
@Test
    fun staleManualAtsPosition_pushedClearOfPanelBox() {
        // Reproduces the owner-reported symptom exactly: a persisted (stale/dragged)
        // ATS position that sits ON the panel box because the panel has grown wide
        // with feeder columns since that position was saved. Before this fix, the
        // manual override (applied AFTER collision avoidance) re-created the
        // overlap no matter what the engine computed.
        val wideFeeders = listOf(
            mccb(2, "Feeder-N1", 40.0, "5x6 mm²", "DB"),
            mccb(3, "Feeder-N2", 40.0, "5x6 mm²", "DB"),
            mccb(4, "Feeder-L1", 160.0, "5x35 mm²", "DB"),
            mccb(5, "SMDB-A", 250.0, "4x120 mm²", "SMDB"),
            mccb(6, "SMDB-B", 250.0, "4x120 mm²", "SMDB"),
            mccb(7, "Pump-1", 80.0, "5x16 mm²", "LOAD")
        )
        val panel = buildGate3(wideFeeders).nodes.first { it.id == "circ_1" }
        val encRight = panel.x + panel.width + RiserLayoutEngine.MAIN_PANEL_ENCLOSURE_PAD

        // Simulate an old saved position: ATS-01 dragged to x just inside the box.
        val staleAts1 = androidx.compose.ui.geometry.Offset(encRight - 10f, panel.y)
        val staleAts2 = androidx.compose.ui.geometry.Offset(encRight - 10f + 220f, panel.y)

        val result = buildGate3WithManual(
            wideFeeders,
            mapOf("ats_ATS-01" to staleAts1, "ats_ATS-02" to staleAts2)
        )

        File("test_output_gate3_stale_manual.txt").writeText(dump("WIDE PANEL + STALE MANUAL ATS OVERLAP", result))
        assertAtsClearance("STALE MANUAL ATS", result, "circ_1", listOf("ats_ATS-01", "ats_ATS-02"))

        // Emergency generators must stay aligned above their ATS emergency terminal.
        val ats1 = result.nodes.first { it.id == "ats_ATS-01" }
        val gen1 = result.nodes.first { it.id == "gen_GEN-01" }
        val ats1EmergX = ats1.x + ats1.width * 0.75f
        val gen1Center = gen1.x + gen1.width / 2f
        assertEquals(
            "Generator 1 center ($gen1Center) must align with ATS 1 emergency terminal ($ats1EmergX)",
            ats1EmergX, gen1Center, 5.0f
        )
    }
}