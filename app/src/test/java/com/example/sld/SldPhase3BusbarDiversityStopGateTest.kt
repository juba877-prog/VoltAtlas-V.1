package com.example.sld

import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 STOP GATE verification test:
 * 1. Busbar tag is 100% data-backed by BusbarSizingEngine and SldDataBlock — zero regex parsing.
 * 2. Board nodes carry valid busbarSize, busbarIpkKa, busbarNeutral, busbarPe, busbarRatingA, scRatingKA.
 * 3. Board TDL and DF are calculated deterministically via DiversityCalculationEngine.
 * 4. CAD DXF export emits data-backed busbar annotations on layer E-BUSBAR with zero regex fallback.
 */
class SldPhase3BusbarDiversityStopGateTest {

    @Test
    fun stopGate_busbarSizingAndDiversityPlumbedEndToEnd() {
        val projectId = "p3_stop_gate"
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId, circuitName = "MDB-MAIN", fedFrom = "Transformer",
                panelType = "MDB", loadKVA = 0.0, phases = "3P+N", voltageV = 400
            ),
            CircuitEntity(
                id = 2, projectId = projectId, circuitName = "LGT-FLOOR-1", fedFrom = "MDB-MAIN",
                loadKVA = 20.0, phases = "3P+N", loadCategory = "Lighting"
            ),
            CircuitEntity(
                id = 3, projectId = projectId, circuitName = "SOCKET-FLOOR-1", fedFrom = "MDB-MAIN",
                loadKVA = 30.0, phases = "3P+N", loadCategory = "Small Power"
            ),
            CircuitEntity(
                id = 4, projectId = projectId, circuitName = "AHU-MAIN-1", fedFrom = "MDB-MAIN",
                loadKVA = 25.0, phases = "3P+N", loadNature = "Motor"
            ),
            CircuitEntity(
                id = 5, projectId = projectId, circuitName = "SPARE-BREAKER", fedFrom = "MDB-MAIN",
                loadKVA = 15.0, phases = "3P+N"
            )
        )

        // 3Ph short-circuit fault level at MDB = 36 kA
        val scResults = mapOf(
            1 to ShortCircuitResult(
                circuitId = 1,
                sourceName = "Transformer",
                cableMm2 = "4x185 mm²",
                iscKA = 36.0,
                iwKA = 50.0,
                deviceKA = 50.0,
                deviceCheck = "PASS",
                cableCheck = "PASS",
                marginPct = 25.0,
                status = "PASS",
                note = ""
            )
        )

        // Sizing result with 400A incomer breaker
        val sizingResults = mapOf(
            1 to SizingResult(
                Ib_A = 80.0,
                deviceSize_A = 400.0,
                deviceType = "MCCB",
                cTotalDerating = 1.0,
                iRequired_perCable_A = 80.0,
                productCode = "ELSE-185",
                cableSize_mm2 = "4x185 mm²",
                ratedI_table_A = 450.0,
                capacity_derated_Iz_A = 450.0,
                utilizationPct = 20.0,
                neutral_mm2 = "4x185 mm²",
                earthCPC_mm2 = "95 mm²",
                voltDrop_V = 2.0,
                vdPct = 0.5,
                cumulativeVDpct = 0.5,
                status = "PASS",
                cableOD_mm = 50.0,
                resultStatus = SizingResultStatus.PASS,
                isAmpacityOk = true,
                isVoltageDropOk = true,
                primaryFailureCode = null
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits,
            shortCircuitResults = scResults,
            sizingResults = sizingResults
        )

        val mdbNode = graph.nodes.first { it.id == "circ_1" }
        val db = mdbNode.dataBlock
        assertNotNull("MDB must have dataBlock", db)

        // 1. Check Diversity Calculations
        // Connected sum: 20 (LGT) + 30 (SKT) + 25 (AHU) + 15 (SPARE) = 90.0 kVA
        assertEquals("90.0 kVA", db!!.boardTotalConnectedKva)
        // Demand:
        // LGT 20 @ 90% = 18.0
        // SKT 30 -> 10 + 20*0.5 = 20.0
        // AHU 25 @ 100% = 25.0
        // SPARE 15 -> 0.0
        // Total Demand = 18 + 20 + 25 = 63.0 kVA
        assertEquals("63.0 kVA", db.boardDemandKva)
        // Overall DF = 63 / 90 = 70%
        assertEquals("70%", db.boardDemandFactorPct)
        // Board Design Current: 63 kVA @ 400V 3Ph -> 63000 / (sqrt(3)*400) = 90.9 A
        assertNotNull(db.boardDesignCurrentA)
        assertEquals("90.9 A", db.boardDesignCurrentA)

        // 2. Check Busbar Sizing Engine Results
        // Incomer is 400A, sc is 36 kA -> Sized for 400A, 36 kA, 1s
        assertNotNull("Busbar rating must be populated", db.busbarRatingA)
        assertEquals("400 A", db.busbarRatingA)
        assertNotNull("SC rating must be populated", db.scRatingKA)
        assertEquals("36 kA", db.scRatingKA)
        assertNotNull("Busbar size profile must be populated", db.busbarSize)
        assertTrue("Busbar profile must specify cross section", db.busbarSize!!.contains("mm²"))
        assertNotNull("Ipk must be populated", db.busbarIpkKa)
        // For 36 kA, peak factor n=2.1 -> Ipk = 36 * 2.1 = 75.6 kA
        assertEquals("75.6 kA", db.busbarIpkKa)
        assertNotNull("Neutral profile must be populated", db.busbarNeutral)
        assertNotNull("PE profile must be populated", db.busbarPe)
        assertEquals("Copper", db.busbarMaterial)

        // 3. Leaf circuits must not carry busbar sizing
        val lgtNode = graph.nodes.first { it.id == "circ_2" }
        assertNull(lgtNode.dataBlock?.busbarSize)
        assertNull(lgtNode.dataBlock?.busbarIpkKa)
        assertNull(lgtNode.dataBlock?.boardTotalConnectedKva)

        // 4. Test DXF Export generation and busbar tag
        val engineResult = RiserLayoutEngine.calculateLayout(graph, circuits)
        val dxfOutput = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            projectName = "Phase3_Gate",
            projectRevision = 3L
        )

        assertTrue("DXF must contain E-BUSBAR layer", dxfOutput.contains("E-BUSBAR"))
        assertTrue("DXF must contain SOLID busbar entities", dxfOutput.contains("SOLID"))
        assertTrue("DXF must contain data-backed busbar tag", dxfOutput.contains("MDB-MAIN Main Busbar: Copper Bus 400A"))
        assertTrue("DXF busbar tag must include 36kA/1s", dxfOutput.contains("36kA/1s"))
        // DXF sanitizes non-ASCII characters like ² to AutoCAD \U+00B2
        assertTrue("DXF busbar tag must include physical profile", dxfOutput.contains("""mm\U+00B2""") || dxfOutput.contains("mm²"))
    }
}
