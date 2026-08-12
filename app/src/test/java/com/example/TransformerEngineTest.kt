package com.example

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.TransformerEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class TransformerEngineTest {

    @Test
    fun testSeedParityTX01() {
        // Seed case: TX-01 (200 kVA, 400V, %Z=4%, X/R=10)
        // Hand-calc / seed targets:
        // Zt = (400^2 / (200*1000)) * 0.04 * 1000 = 32.0 mOhm
        // Rt = 32 / sqrt(1 + 10^2) = 32 / 10.0498756 = 3.184119 mOhm
        // Xt = 3.184119 * 10 = 31.84119 mOhm
        // Isc3ph = 1.05 * 400 / (sqrt(3) * 32.0) = 420 / 55.4256258 = 7.577722 kA
        val tx01 = TransformerEntity(
            transformerId = "TX-01",
            transformerName = "Transformer 1",
            kVA = 200.0,
            voltage = 400,
            percentZ = 4.0,
            xr = 10.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 200.0,
            pf = 0.8
        )

        val result = TransformerEngine.calculate(tx01, emptyList())

        assertEquals("Zt match (mOhm)", 32.0, result.zt, 0.01)
        assertEquals("Rt match (mOhm)", 3.184119, result.rt, 0.001)
        assertEquals("Xt match (mOhm)", 31.84119, result.xt, 0.001)
        assertEquals("Isc3ph match (kA)", 7.5777, result.isc3Phase, 0.005)
        assertEquals("FLC match (A)", 288.675, result.flc, 0.05)
    }

    @Test
    fun testLargeTransformer1600kVA() {
        val tx1600 = TransformerEntity(
            transformerId = "TX-LARGE",
            transformerName = "Main Substation Tx",
            kVA = 1600.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 12.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 1600.0,
            pf = 0.85
        )

        val result = TransformerEngine.calculate(tx1600, emptyList())

        // Zt = (400^2 / (1600*1000)) * 0.06 * 1000 = 6.0 mOhm
        // Rt = 6 / sqrt(1 + 144) = 0.49826 mOhm
        // Xt = 0.49826 * 12 = 5.9791 mOhm
        // Isc3ph = 420 / (sqrt(3) * 6.0) = 40.4145 kA
        assertEquals(6.0, result.zt, 0.01)
        assertEquals(0.49826, result.rt, 0.005)
        assertEquals(5.9791, result.xt, 0.005)
        assertEquals(40.4145, result.isc3Phase, 0.05)
    }

    @Test
    fun testMultiTransformerExecution() {
        val transformers = (1..5).map { i ->
            TransformerEntity(
                transformerId = "TX-0$i",
                transformerName = "Transformer $i",
                kVA = 500.0 * i,
                voltage = 400,
                percentZ = 5.0,
                xr = 8.0,
                status = if (i <= 2) "Active" else "Inactive",
                sizeMode = "Manual",
                manualKVA = 500.0 * i
            )
        }

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 300.0, transformerId = "TX-01", fedFrom = "Transformer"),
            CircuitEntity(id = 2, circuitName = "MDB-2", loadKVA = 600.0, transformerId = "TX-02", fedFrom = "Transformer")
        )

        val resultsMap = transformers.associate { tx ->
            tx.transformerId to TransformerEngine.calculate(tx, circuits)
        }

        assertEquals(5, resultsMap.size)
        assertTrue(resultsMap["TX-01"]!!.status == "VERIFIED")
        assertTrue(resultsMap["TX-02"]!!.status == "VERIFIED")
        assertEquals("INACTIVE", resultsMap["TX-03"]!!.status)
        assertEquals("INACTIVE", resultsMap["TX-04"]!!.status)
        assertEquals("INACTIVE", resultsMap["TX-05"]!!.status)

        assertEquals(300.0, resultsMap["TX-01"]!!.connectedLoad, 0.01)
        assertEquals(600.0, resultsMap["TX-02"]!!.connectedLoad, 0.01)
    }

    @Test
    fun testOverloadStatus() {
        val tx = TransformerEntity(
            transformerId = "TX-OVERLOAD",
            transformerName = "Small Tx",
            kVA = 100.0,
            voltage = 400,
            percentZ = 4.0,
            xr = 5.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 100.0
        )

        val heavyCircuits = listOf(
            CircuitEntity(id = 1, circuitName = "HEAVY-MDB", loadKVA = 150.0, transformerId = "TX-OVERLOAD", fedFrom = "Transformer")
        )

        val result = TransformerEngine.calculate(tx, heavyCircuits)

        assertEquals("OVERLOADED — FAIL", result.status)
        assertTrue(result.loadingPct > 100.0)
    }

    @Test
    fun testRecursiveMultiTierTransformerLoad() {
        // Hierarchy: Transformer -> MDB-1 (loadKVA=0) -> SMDB-1 (loadKVA=0) -> DB-1 (15kVA) + DB-2 (10kVA)
        val tx = TransformerEntity(
            transformerId = "TX-RECURSIVE",
            transformerName = "Main Tx",
            kVA = 500.0,
            status = "Active",
            sizeMode = "Auto",
            pf = 0.8
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 0.0, fedFrom = "Transformer", transformerId = "TX-RECURSIVE", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "SMDB-1", loadKVA = 0.0, fedFrom = "MDB-1", parentId = 1, transformerId = "TX-RECURSIVE", panelType = "SMDB"),
            CircuitEntity(id = 3, circuitName = "DB-1", loadKVA = 15.0, fedFrom = "SMDB-1", parentId = 2, transformerId = "TX-RECURSIVE", panelType = "LOAD"),
            CircuitEntity(id = 4, circuitName = "DB-2", loadKVA = 10.0, fedFrom = "SMDB-1", parentId = 2, transformerId = "TX-RECURSIVE", panelType = "LOAD")
        )

        val result1 = TransformerEngine.calculate(tx, circuits)
        assertEquals("Multi-tier recursive load must equal sum of leaf loads (25.0 kVA)", 25.0, result1.connectedLoad, 0.01)

        // Scenario C: Add DB-3 (5.0 kVA) under SMDB-1 without modifying MDB-1's loadKVA property
        val updatedCircuits = circuits + CircuitEntity(
            id = 5, circuitName = "DB-3", loadKVA = 5.0, fedFrom = "SMDB-1", parentId = 2, transformerId = "TX-RECURSIVE", panelType = "LOAD"
        )

        val result2 = TransformerEngine.calculate(tx, updatedCircuits)
        assertEquals("Adding DB-3 dynamically recomputes connected load to 30.0 kVA", 30.0, result2.connectedLoad, 0.01)
    }

    @Test
    fun testSimpleDirectLoadsAndManualSizeMode() {
        val txAuto = TransformerEntity(
            transformerId = "TX-SIMPLE",
            kVA = 500.0,
            status = "Active",
            sizeMode = "Auto"
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "FEEDER-1", loadKVA = 10.0, fedFrom = "Transformer", transformerId = "TX-SIMPLE"),
            CircuitEntity(id = 2, circuitName = "FEEDER-2", loadKVA = 20.0, fedFrom = "Transformer", transformerId = "TX-SIMPLE")
        )

        val resAuto = TransformerEngine.calculate(txAuto, circuits)
        assertEquals(30.0, resAuto.connectedLoad, 0.01)

        val txManual = txAuto.copy(sizeMode = "Manual", manualKVA = 1000.0)
        val resManual = TransformerEngine.calculate(txManual, circuits)
        assertEquals(30.0, resManual.connectedLoad, 0.01)
        assertEquals(1000.0, resManual.selectedSize, 0.01)
    }

    @Test
    fun testTransformerSecondaryVoltage_415V() {
        val tx415 = TransformerEntity(
            transformerId = "TX-415V",
            kVA = 1000.0,
            voltage = 415,
            percentZ = 5.0,
            xr = 6.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 1000.0
        )
        val res = TransformerEngine.calculate(tx415, emptyList())
        assertEquals(415.0, res.voltage, 0.01)
        // FLC = (1000 * 1000) / (sqrt(3) * 415) = 1391.24 A
        assertEquals(1391.24, res.flc, 0.05)
        // Zt = (415^2 / (1000 * 1000)) * 0.05 * 1000 = 8.61125 mOhm
        assertEquals(8.61125, res.zt, 0.001)
    }

    @Test
    fun testTransformerSecondaryVoltage_380V() {
        val tx380 = TransformerEntity(
            transformerId = "TX-380V",
            kVA = 1000.0,
            voltage = 380,
            percentZ = 5.0,
            xr = 6.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 1000.0
        )
        val res = TransformerEngine.calculate(tx380, emptyList())
        assertEquals(380.0, res.voltage, 0.01)
        // FLC = (1000 * 1000) / (sqrt(3) * 380) = 1519.34 A
        assertEquals(1519.34, res.flc, 0.05)
        // Zt = (380^2 / (1000 * 1000)) * 0.05 * 1000 = 7.22 mOhm
        assertEquals(7.22, res.zt, 0.001)
    }

    @Test
    fun testTransformerSecondaryVoltage_400V_Regression() {
        val tx400 = TransformerEntity(
            transformerId = "TX-400V",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 6.0,
            status = "Active",
            sizeMode = "Manual",
            manualKVA = 1000.0
        )
        val res = TransformerEngine.calculate(tx400, emptyList())
        assertEquals(400.0, res.voltage, 0.001)
        // FLC = (1000 * 1000) / (sqrt(3) * 400) = 1443.37567 A
        assertEquals(1443.37567, res.flc, 0.001)
        // Zt = (400^2 / (1000 * 1000)) * 0.05 * 1000 = 8.0 mOhm
        assertEquals(8.0, res.zt, 0.0001)
    }
}
