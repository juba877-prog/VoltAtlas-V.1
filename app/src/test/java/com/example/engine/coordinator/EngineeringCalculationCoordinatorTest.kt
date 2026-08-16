package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import com.example.engine.SizingResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EngineeringCalculationCoordinatorTest {

    @Before
    fun setUp() {
        EngineeringCalculationCoordinator.clearCache()
    }

    @Test
    fun testCableCalculationServiceMatchesDirectEngineOutput() {
        val testCircuit = CircuitEntity(
            id = 1,
            circuitName = "Test Circuit 1",
            projectId = "PRJ_TEST",
            loadKVA = 25.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 65.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        val directResult = CableSizingEngine.sizeCircuit(
            circuit = testCircuit,
            parentCumulativeVD = 1.25,
            allCircuits = emptyList()
        )

        val serviceResult = CableCalculationService.calculateSingleCircuit(
            circuit = testCircuit,
            parentCumulativeVD = 1.25,
            upstreamCircuits = emptyList(),
            transformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = "PRJ_TEST"))
        )

        // Verify field-by-field parity
        assertEquals("Design Current Ib must match", directResult.Ib_A, serviceResult.Ib_A, 0.001)
        assertEquals("Derated Capacity Iz must match", directResult.capacity_derated_Iz_A, serviceResult.capacity_derated_Iz_A, 0.001)
        assertEquals("Cable Size must match", directResult.cableSize_mm2, serviceResult.cableSize_mm2)
        assertEquals("Device Size must match", directResult.deviceSize_A, serviceResult.deviceSize_A, 0.001)
        assertEquals("Device Type must match", directResult.deviceType, serviceResult.deviceType)
        assertEquals("Voltage Drop V must match", directResult.voltDrop_V, serviceResult.voltDrop_V, 0.001)
        assertEquals("Voltage Drop % must match", directResult.vdPct, serviceResult.vdPct, 0.001)
        assertEquals("Cumulative Voltage Drop % must match", directResult.cumulativeVDpct, serviceResult.cumulativeVDpct, 0.001)
        assertEquals("Status must match", directResult.status, serviceResult.status)
    }

    @Test
    fun testBuswayServiceCalculationMatchesDirectBuswayEngine() {
        val buswayCircuit = CircuitEntity(
            id = 2,
            circuitName = "Busway Feeder",
            projectId = "PRJ_TEST",
            loadKVA = 300.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 120.0,
            feederType = "Busway",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        val directBusway = BuswayEngine.toSizingResult(
            BuswayEngine.sizeBusway(buswayCircuit, parentCumulativeVD = 0.5)
        )

        val serviceResult = CableCalculationService.calculateSingleCircuit(
            circuit = buswayCircuit,
            parentCumulativeVD = 0.5,
            transformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = "PRJ_TEST"))
        )

        assertEquals(directBusway.deviceSize_A, serviceResult.deviceSize_A, 0.001)
        assertEquals(directBusway.vdPct, serviceResult.vdPct, 0.001)
        assertEquals(directBusway.cumulativeVDpct, serviceResult.cumulativeVDpct, 0.001)
        assertEquals(directBusway.status, serviceResult.status)
    }

    @Test
    fun testCoordinatorResolvesParentChainAndCumulativeVoltageDrop() {
        val parentMdb = CircuitEntity(
            id = 10,
            circuitName = "MDB",
            projectId = "PRJ_HIERARCHY",
            loadKVA = 80.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        val childSmdb = CircuitEntity(
            id = 11,
            circuitName = "SMDB-1",
            projectId = "PRJ_HIERARCHY",
            loadKVA = 30.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 35.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "MDB",
            parentId = 10
        )

        val finalDb = CircuitEntity(
            id = 12,
            circuitName = "DB-LIGHTING",
            projectId = "PRJ_HIERARCHY",
            loadKVA = 8.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 25.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "SMDB-1",
            parentId = 11
        )

        val allCircuits = listOf(parentMdb, childSmdb, finalDb)
        val transformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = "PRJ_HIERARCHY"))

        // Single-entity targeted calculation for the leaf node
        val finalDbResult = EngineeringCalculationCoordinator.calculateCableSizing(
            circuit = finalDb,
            allCircuits = allCircuits,
            transformers = transformers
        )

        // Bulk project calculation
        val bulkResult = EngineeringCalculationCoordinator.calculateProjectCableSizing(
            circuits = allCircuits,
            transformers = transformers
        )

        val mdbSizing = bulkResult.sizingResults[10]!!
        val smdbSizing = bulkResult.sizingResults[11]!!
        val dbSizingBulk = bulkResult.sizingResults[12]!!

        // Verify parent cumulative VD propagates down the tree
        val expectedSmdbCumulativeVD = mdbSizing.vdPct + smdbSizing.vdPct
        assertEquals(expectedSmdbCumulativeVD, smdbSizing.cumulativeVDpct, 0.001)

        val expectedDbCumulativeVD = expectedSmdbCumulativeVD + dbSizingBulk.vdPct
        assertEquals(expectedDbCumulativeVD, dbSizingBulk.cumulativeVDpct, 0.001)

        // Verify targeted single-entity calculation returns the exact same result as bulk calculation
        assertEquals(dbSizingBulk.cumulativeVDpct, finalDbResult.cumulativeVDpct, 0.001)
        assertEquals(dbSizingBulk.cableSize_mm2, finalDbResult.cableSize_mm2)
        assertEquals(dbSizingBulk.deviceSize_A, finalDbResult.deviceSize_A, 0.001)
    }

    @Test
    fun testCoordinatorFreshnessCacheReusesResultAndInvalidatesOnInputChanges() {
        val circuit = CircuitEntity(
            id = 50,
            circuitName = "Pump Motor",
            projectId = "PRJ_CACHE",
            loadKVA = 15.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 40.0,
            cableType = "Cu/XLPE/PVC - 4C",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val transformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = "PRJ_CACHE"))

        val result1 = EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit), transformers)
        val result2 = EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit), transformers)

        // Second call should return cached object
        assertSame(result1, result2)

        // Modify input parameter (length changed from 40m to 120m)
        val modifiedCircuit = circuit.copy(length_m = 120.0)
        val result3 = EngineeringCalculationCoordinator.calculateCableSizing(modifiedCircuit, listOf(modifiedCircuit), transformers)

        assertNotSame(result1, result3)
        assertTrue("Voltage drop should increase with longer length", result3.voltDrop_V > result1.voltDrop_V)
    }
}
