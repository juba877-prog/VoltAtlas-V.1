package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.ShortCircuitEngine
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.TransformerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2B — G4 (utility grid fault level → source-to-feeder fault study) and
 * G5 (real transformer primary/secondary voltages).
 */
class Phase2BGridFaultAndVoltageTest {

    @Test
    fun gridFaultMVA_reducesIscAndFlagsSource() {
        val txBase = TransformerEntity(
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 6.0,
            status = "Active",
            primaryVoltageV = 11000
        )
        val txInfinite = txBase
        val txWithGrid = txBase.copy(gridFaultMVA = 500.0)

        val resInfinite = TransformerEngine.calculate(txInfinite, emptyList())
        val resWithGrid = TransformerEngine.calculate(txWithGrid, emptyList())

        // G4: grid impedance must be present and must reduce Z_total.
        assertTrue("Grid S_kQ must be reported", resWithGrid.gridFaultMVA > 0.0)
        assertTrue("Grid Z reflected to LV must be non-zero", resWithGrid.zGridMOhm > 0.0)
        assertTrue("Grid R must be non-zero", resWithGrid.rGridMOhm > 0.0)
        assertTrue("Grid X must be non-zero", resWithGrid.xGridMOhm > 0.0)
        assertTrue("Transformer impedance must be unchanged by grid input", resInfinite.zt == resWithGrid.zt)

        // Physical check: Z_Q,LV = (c_max · U_sec²) / S_kQ, c_max = 1.1, U_sec = 400 V, S_kQ = 500 MVA.
        val expectedZGridOhm = (1.1 * 400.0 * 400.0) / (500.0 * 1e6)
        assertEquals("Grid impedance magnitude must match IEC 60909-0 formula", expectedZGridOhm * 1000.0, resWithGrid.zGridMOhm, 0.001)
        // R = Z/√(1 + 10²), X = 10·R.
        val expectedR = expectedZGridOhm / kotlin.math.sqrt(1.0 + 100.0)
        assertEquals("Grid R split must match X/R=10 assumption", expectedR * 1000.0, resWithGrid.rGridMOhm, 0.001)
        assertEquals("Grid X split must be 10·R", expectedR * 10.0 * 1000.0, resWithGrid.xGridMOhm, 0.001)
    }

    @Test
    fun gridFaultMVA_reducesShortCircuitIsc() {
        val txBase = TransformerEntity(
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 6.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 1000.0
        )
        val resInfinite = TransformerEngine.calculate(txBase, emptyList())
        val resWithGrid = TransformerEngine.calculate(txBase.copy(gridFaultMVA = 500.0), emptyList())

        val circuit = CircuitEntity(id = 1, circuitName = "MDB-FEEDER", loadKVA = 500.0, fedFrom = "Transformer", transformerId = "TX-01", voltageV = 400, length_m = 0.1)
        val sizing = SizingResult(
            Ib_A = 722.0, deviceSize_A = 800.0, deviceType = "MCCB", cTotalDerating = 1.0, iRequired_perCable_A = 750.0,
            productCode = "T", cableSize_mm2 = "500 mm²", ratedI_table_A = 780.0, capacity_derated_Iz_A = 780.0,
            utilizationPct = 92.5, neutral_mm2 = "250 mm²", earthCPC_mm2 = "250 mm²", voltDrop_V = 0.1, vdPct = 0.02,
            cumulativeVDpct = 0.02, status = "✓ PASS", failReason = "", cableMaterial = "Cu", cableOD_mm = 80.0,
            deviceBreakingKA = 50.0, kFactor = 143.0, clearingTime_s = 0.1, rUsed = 0.0306, xUsed = 0.0312, r20 = 0.0306,
            resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null,
            isDeviceFromCatalog = true
        )

        val scInfinite = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = mapOf(1 to 0),
            sizingResults = mapOf(1 to sizing),
            transformerResults = mapOf("TX-01" to resInfinite)
        )[1]!!
        val scWithGrid = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = mapOf(1 to 0),
            sizingResults = mapOf(1 to sizing),
            transformerResults = mapOf("TX-01" to resWithGrid)
        )[1]!!

        assertTrue(
            "Isc with finite grid must be lower than infinite-grid Isc ($scWithGrid.iscKA vs $scInfinite.iscKA)",
            scWithGrid.iscKA < scInfinite.iscKA
        )
        assertTrue(
            "Total impedance with finite grid must be higher (grid Z added)",
            scWithGrid.zTotalMOhm > scInfinite.zTotalMOhm
        )
        assertTrue("Grid source contribution must be flagged", scWithGrid.note.contains("Grid source impedance included"))
        assertFalse("Infinite-grid result must not claim a grid source", scInfinite.note.contains("Grid source impedance"))
    }

    @Test
    fun gridNode_usesRealPrimaryVoltageAndNeverFabricates() {
        val txPrimary = TransformerEntity(projectId = "p", transformerId = "TX-01", kVA = 1000.0, voltage = 400, primaryVoltageV = 22000)
        val graphWith = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "p",
            transformers = listOf(txPrimary),
            circuits = emptyList()
        )
        val gridWith = graphWith.nodes.find { it.id == "grid_TX-01" }!!
        assertEquals("22 kV", gridWith.voltage)
        assertEquals("Utility Grid (22 kV)", gridWith.displayName)
        assertEquals("22 kV", gridWith.dataBlock?.transformerPrimaryVoltage)
        assertEquals("22 kV / 400 V", graphWith.nodes.find { it.id == "tx_TX-01" }!!.voltage)

        val txNoPrimary = TransformerEntity(projectId = "p", transformerId = "TX-01", kVA = 1000.0, voltage = 400)
        val graphWithout = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "p",
            transformers = listOf(txNoPrimary),
            circuits = emptyList()
        )
        val gridWithout = graphWithout.nodes.find { it.id == "grid_TX-01" }!!
        assertNull("Grid node must not fabricate a supply voltage", gridWithout.voltage)
        assertEquals("Utility Grid", gridWithout.displayName)
        assertEquals("400 V", graphWithout.nodes.find { it.id == "tx_TX-01" }!!.voltage)
    }

    @Test
    fun gridFaultMVA_surfacesOnGridAndTxBlocks() {
        val tx = TransformerEntity(projectId = "p", transformerId = "TX-01", kVA = 1000.0, voltage = 400, primaryVoltageV = 11000, gridFaultMVA = 250.0)
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "p",
            transformers = listOf(tx),
            circuits = emptyList()
        )
        val gridDb = graph.nodes.find { it.id == "grid_TX-01" }!!.dataBlock
        val txDb = graph.nodes.find { it.id == "tx_TX-01" }!!.dataBlock
        assertNotNull("S_kQ must surface on the grid block", gridDb?.gridFaultMVA)
        assertEquals("250.0 MVA", gridDb?.gridFaultMVA)
        assertEquals("250.0 MVA", txDb?.gridFaultMVA)
        assertEquals("11 kV", gridDb?.transformerPrimaryVoltage)
    }
}