package com.example.engine.coordinator

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.data.TransformerEntity
import com.example.engine.AdsResult
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import com.example.engine.CoordinationEngine
import com.example.engine.CoordinationResult
import com.example.engine.ProtectionEngine
import com.example.engine.ProtectionResult
import com.example.engine.ShortCircuitEngine
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.TransformerEngine
import com.example.engine.TransformerResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EngineeringCalculationCoordinatorTest {

    @Before
    fun setUp() {
        EngineeringCalculationCoordinator.clearCache()
        if (Catalog.cableTypes.isEmpty()) {
            Catalog.cableTypes = listOf(
                CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70"),
                CableType(3, "Cu/XLPE/PVC - 4C", "Cu", "XLPE", "None", "4C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(4, "Cu/XLPE/SWA/PVC", "Cu", "XLPE", "SWA", "4C", "0.6/1kV", "IEC", "R_Cu90")
            )
        }
        if (Catalog.dataSheet.isEmpty()) {
            Catalog.dataSheet = listOf(
                DataSheetEntry(1.5, 12.1, 0.118, 19.5, 14.52, 23.4),
                DataSheetEntry(2.5, 7.41, 0.109, 12.1, 8.89, 14.52),
                DataSheetEntry(4.0, 4.61, 0.101, 7.41, 5.53, 8.89),
                DataSheetEntry(6.0, 3.08, 0.093, 4.61, 3.7, 5.53),
                DataSheetEntry(10.0, 1.83, 0.086, 3.08, 2.2, 3.7),
                DataSheetEntry(16.0, 1.15, 0.082, 1.91, 1.38, 2.29),
                DataSheetEntry(25.0, 0.727, 0.080, 1.20, 0.872, 1.44),
                DataSheetEntry(35.0, 0.524, 0.078, 0.868, 0.629, 1.04),
                DataSheetEntry(50.0, 0.387, 0.078, 0.641, 0.464, 0.77),
                DataSheetEntry(70.0, 0.268, 0.077, 0.443, 0.322, 0.532),
                DataSheetEntry(95.0, 0.193, 0.076, 0.320, 0.232, 0.384),
                DataSheetEntry(120.0, 0.153, 0.075, 0.253, 0.184, 0.304),
                DataSheetEntry(150.0, 0.124, 0.075, 0.206, 0.149, 0.247),
                DataSheetEntry(185.0, 0.0991, 0.074, 0.164, 0.119, 0.197),
                DataSheetEntry(240.0, 0.0754, 0.074, 0.125, 0.0905, 0.15),
                DataSheetEntry(300.0, 0.0601, 0.073, 0.100, 0.0721, 0.12),
                DataSheetEntry(400.0, 0.0470, 0.073, 0.0778, 0.0564, 0.0934),
                DataSheetEntry(500.0, 0.0366, 0.072, 0.0605, 0.0439, 0.0726),
                DataSheetEntry(630.0, 0.0283, 0.072, 0.0469, 0.0340, 0.0563)
            )
        }
        if (Catalog.cableCatalog.isEmpty()) {
            val sizes = listOf(
                1.5 to 10.0, 2.5 to 11.0, 4.0 to 12.0, 6.0 to 13.0, 10.0 to 15.0,
                16.0 to 16.0, 25.0 to 19.0, 35.0 to 21.0, 50.0 to 24.0, 70.0 to 27.0,
                95.0 to 30.0, 120.0 to 33.0, 150.0 to 37.0, 185.0 to 41.0, 240.0 to 46.0,
                300.0 to 51.0, 400.0 to 58.0, 500.0 to 64.0, 630.0 to 72.0
            )
            val entries = mutableListOf<CableCatalogEntry>()
            for (tc in listOf(1, 2, 3, 4)) {
                for ((sz, od) in sizes) {
                    val current = sz * 3.0 + 10.0
                    entries.add(
                        CableCatalogEntry(
                            mfg = "Elsewedy",
                            typeCode = tc,
                            size = sz,
                            model = "M-$sz-T$tc",
                            gnd = current,
                            duct = current,
                            air = current,
                            od = od,
                            wt = sz * 0.03 + 0.2
                        )
                    )
                }
            }
            Catalog.cableCatalog = entries
        }
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
        assertSame("Subsequent call with same inputs should return cached instance", result1, result2)

        // Modify input parameter (length changed from 40m to 120m)
        val modifiedCircuit = circuit.copy(length_m = 120.0)
        val result3 = EngineeringCalculationCoordinator.calculateCableSizing(modifiedCircuit, listOf(modifiedCircuit), transformers)

        assertNotSame("Modified input parameter should bust cache and return fresh instance", result1, result3)

        // Explicit cache invalidation should also force a fresh calculation
        EngineeringCalculationCoordinator.invalidateCircuit(circuit.id)
        val result4 = EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit), transformers)
        assertNotSame("Invalidating circuit cache should force fresh instance", result1, result4)
    }

    @Test
    fun testTransformerCalculationServiceMatchesDirectEngineOutput() {
        val transformer = TransformerEntity(
            projectId = "PRJ_TX_TEST",
            transformerId = "TX-01",
            transformerName = "Main Substation TX",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 10.0,
            txType = "Oil",
            sourceType = "Medium Voltage",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 1000.0,
            demandFactor = 0.85,
            diversity = 1.1,
            growth = 1.15,
            safety = 1.0,
            vectorGroup = "Dyn11",
            z0OverZ1 = 1.0,
            pf = 0.85
        )

        val circuits = listOf(
            CircuitEntity(
                id = 101,
                circuitName = "MDB-1",
                projectId = "PRJ_TX_TEST",
                loadKVA = 350.0,
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            CircuitEntity(
                id = 102,
                circuitName = "MDB-2",
                projectId = "PRJ_TX_TEST",
                loadKVA = 450.0,
                fedFrom = "Transformer",
                transformerId = "TX-01"
            )
        )

        val directResult = TransformerEngine.calculate(transformer, circuits)
        val serviceResult = TransformerCalculationService.calculateSingleTransformer(transformer, circuits)

        assertEquals("Connected Load must match", directResult.connectedLoad, serviceResult.connectedLoad, 0.001)
        assertEquals("Demand Load must match", directResult.demandLoad, serviceResult.demandLoad, 0.001)
        assertEquals("Design Load must match", directResult.designLoad, serviceResult.designLoad, 0.001)
        assertEquals("Required kVA must match", directResult.requiredKVA, serviceResult.requiredKVA, 0.001)
        assertEquals("Recommended Size must match", directResult.recommendedSize, serviceResult.recommendedSize, 0.001)
        assertEquals("Selected Size must match", directResult.selectedSize, serviceResult.selectedSize, 0.001)
        assertEquals("Loading Pct must match", directResult.loadingPct, serviceResult.loadingPct, 0.001)
        assertEquals("Full Load Current FLC must match", directResult.flc, serviceResult.flc, 0.001)
        assertEquals("Impedance Zt must match", directResult.zt, serviceResult.zt, 0.00001)
        assertEquals("Resistance Rt must match", directResult.rt, serviceResult.rt, 0.00001)
        assertEquals("Reactance Xt must match", directResult.xt, serviceResult.xt, 0.00001)
        assertEquals("Isc 3Phase must match", directResult.isc3Phase, serviceResult.isc3Phase, 0.001)
        assertEquals("Isc 1Phase must match", directResult.isc1Phase, serviceResult.isc1Phase, 0.001)
        assertEquals("Status must match", directResult.status, serviceResult.status)
        assertEquals("Vector Group must match", directResult.vectorGroup, serviceResult.vectorGroup)
    }

    @Test
    fun testCoordinatorResolvesAssignedCircuitsForTargetedTransformer() {
        val tx1 = TransformerEntity(
            projectId = "PRJ_MULTI_TX",
            transformerId = "TX-01",
            transformerName = "Transformer 1",
            kVA = 1000.0,
            status = "Active"
        )
        val tx2 = TransformerEntity(
            projectId = "PRJ_MULTI_TX",
            transformerId = "TX-02",
            transformerName = "Transformer 2",
            kVA = 500.0,
            status = "Active"
        )
        val allTransformers = listOf(tx1, tx2)

        val circuits = listOf(
            CircuitEntity(id = 201, circuitName = "Feeder 1A", projectId = "PRJ_MULTI_TX", loadKVA = 200.0, fedFrom = "Transformer", transformerId = "TX-01"),
            CircuitEntity(id = 202, circuitName = "Feeder 1B", projectId = "PRJ_MULTI_TX", loadKVA = 300.0, fedFrom = "Transformer", transformerId = "TX-01"),
            CircuitEntity(id = 203, circuitName = "Feeder 2A", projectId = "PRJ_MULTI_TX", loadKVA = 150.0, fedFrom = "Transformer", transformerId = "TX-02")
        )

        val tx1Result = EngineeringCalculationCoordinator.calculateTransformer("TX-01", circuits, allTransformers)
        val tx2Result = EngineeringCalculationCoordinator.calculateTransformer("TX-02", circuits, allTransformers)

        assertEquals("TX-01 should have 2 circuits assigned", 2, tx1Result.circuitsAssigned)
        assertEquals("TX-01 connected load must be 500 kVA", 500.0, tx1Result.connectedLoad, 0.001)

        assertEquals("TX-02 should have 1 circuit assigned", 1, tx2Result.circuitsAssigned)
        assertEquals("TX-02 connected load must be 150 kVA", 150.0, tx2Result.connectedLoad, 0.001)
    }

    @Test
    fun testCoordinatorTransformerFreshnessCacheReusesAndInvalidates() {
        val transformer = TransformerEntity(
            projectId = "PRJ_TX_CACHE",
            transformerId = "TX-MAIN",
            kVA = 1000.0,
            sizeMode = "Manual",
            manualKVA = 1000.0,
            status = "Active"
        )
        val circuits = listOf(
            CircuitEntity(id = 301, circuitName = "Main Panel", projectId = "PRJ_TX_CACHE", loadKVA = 400.0, fedFrom = "Transformer", transformerId = "TX-MAIN")
        )

        val result1 = EngineeringCalculationCoordinator.calculateTransformer(transformer, circuits)
        val result2 = EngineeringCalculationCoordinator.calculateTransformer(transformer, circuits)

        // Second call should return the exact cached instance
        assertSame(result1, result2)

        // Modify assigned circuit load
        val modifiedCircuits = listOf(
            circuits[0].copy(loadKVA = 600.0)
        )
        val result3 = EngineeringCalculationCoordinator.calculateTransformer(transformer, modifiedCircuits)

        assertNotSame(result1, result3)
        assertEquals(600.0, result3.connectedLoad, 0.001)
        assertTrue("Loading percentage should increase with higher load", result3.loadingPct > result1.loadingPct)

        // Invalidate explicitly and verify recalculation
        EngineeringCalculationCoordinator.invalidateTransformer("TX-MAIN")
        val result4 = EngineeringCalculationCoordinator.calculateTransformer(transformer, modifiedCircuits)
        assertEquals(result3.connectedLoad, result4.connectedLoad, 0.001)
    }

    @Test
    fun testViewModelTransformerCalculationEquivalence() {
        val tx = TransformerEntity(
            projectId = "PRJ_REGRESSION",
            transformerId = "TX-01",
            transformerName = "Service TX",
            kVA = 1250.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 10.0,
            status = "Active"
        )
        val circuits = listOf(
            CircuitEntity(id = 401, circuitName = "MDB", projectId = "PRJ_REGRESSION", loadKVA = 500.0, fedFrom = "Transformer", transformerId = "TX-01"),
            CircuitEntity(id = 402, circuitName = "SMDB", projectId = "PRJ_REGRESSION", loadKVA = 200.0, fedFrom = "MDB", parentId = 401, transformerId = "TX-01")
        )

        val directMap = listOf(tx).associate { it.transformerId to TransformerEngine.calculate(it, circuits) }
        val coordinatorMap = EngineeringCalculationCoordinator.calculateProjectTransformers(circuits, listOf(tx))

        assertEquals(directMap.keys, coordinatorMap.keys)
        val directRes = directMap["TX-01"]!!
        val coordRes = coordinatorMap["TX-01"]!!

        assertEquals(directRes.connectedLoad, coordRes.connectedLoad, 0.001)
        assertEquals(directRes.flc, coordRes.flc, 0.001)
        assertEquals(directRes.zt, coordRes.zt, 0.00001)
        assertEquals(directRes.isc3Phase, coordRes.isc3Phase, 0.001)
        assertEquals(directRes.status, coordRes.status)
    }

    @Test
    fun testShortCircuitCalculationServiceMatchesDirectEngineOutput() {
        val circuits = listOf(
            CircuitEntity(id = 501, circuitName = "MDB-Main", projectId = "PRJ_SC_TEST", length_m = 30.0, parallelRuns = 1, fedFrom = "Transformer", transformerId = "TX-01")
        )
        val parents = emptyMap<Int, Int>()
        val sizings = mapOf(
            501 to SizingResult(
                Ib_A = 400.0,
                deviceSize_A = 500.0,
                deviceType = "MCCB",
                cTotalDerating = 0.8,
                iRequired_perCable_A = 500.0,
                productCode = "CU-XLPE-4C-240",
                cableSize_mm2 = "240 mm²",
                ratedI_table_A = 550.0,
                capacity_derated_Iz_A = 440.0,
                utilizationPct = 90.0,
                neutral_mm2 = "120 mm²",
                earthCPC_mm2 = "120 mm²",
                voltDrop_V = 3.5,
                vdPct = 0.875,
                cumulativeVDpct = 0.875,
                status = "PASS",
                rUsed = 0.098,
                xUsed = 0.075,
                cableOD_mm = 52.0,
                kFactor = 143.0,
                clearingTime_s = 0.1,
                deviceBreakingKA = 50.0
            )
        )
        val txResults = mapOf(
            "TX-01" to TransformerResult(
                connectedLoad = 600.0,
                circuitsAssigned = 1,
                realPower = 510.0,
                demandLoad = 510.0,
                diversifiedLoad = 510.0,
                futureLoad = 510.0,
                designLoad = 510.0,
                requiredKVA = 600.0,
                recommendedSize = 1000.0,
                selectedSize = 1000.0,
                loadingPct = 60.0,
                flc = 1443.37,
                zt = 8.0,
                rt = 0.8,
                xt = 7.96,
                isc3Phase = 30.31,
                isc1Phase = 30.31,
                status = "VERIFIED",
                z0 = 7.16,
                vectorGroup = "Dyn11",
                voltage = 400.0
            )
        )

        val directResult = ShortCircuitEngine.calculate(circuits, parents, sizings, txResults)[501]!!
        val serviceResult = ShortCircuitCalculationService.calculateProjectShortCircuit(circuits, parents, sizings, txResults)[501]!!

        assertEquals("iscKA must match", directResult.iscKA, serviceResult.iscKA, 0.001)
        assertEquals("iwKA must match", directResult.iwKA, serviceResult.iwKA, 0.001)
        assertEquals("deviceKA must match", directResult.deviceKA, serviceResult.deviceKA, 0.001)
        assertEquals("deviceCheck must match", directResult.deviceCheck, serviceResult.deviceCheck)
        assertEquals("cableCheck must match", directResult.cableCheck, serviceResult.cableCheck)
        assertEquals("status must match", directResult.status, serviceResult.status)
        assertEquals("ipKA must match", directResult.ipKA, serviceResult.ipKA, 0.001)
        assertEquals("zTotalMOhm must match", directResult.zTotalMOhm, serviceResult.zTotalMOhm, 0.001)
        assertEquals("rMOhm must match", directResult.rMOhm, serviceResult.rMOhm, 0.001)
        assertEquals("xMOhm must match", directResult.xMOhm, serviceResult.xMOhm, 0.001)
    }

    @Test
    fun testCoordinatorTargetedShortCircuitAssemblesDependencyBranch() {
        val tx = TransformerEntity(
            projectId = "PRJ_SC_BRANCH",
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 10.0,
            status = "Active"
        )
        val mdb = CircuitEntity(
            id = 601,
            circuitName = "MDB",
            projectId = "PRJ_SC_BRANCH",
            loadKVA = 300.0,
            length_m = 20.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            cableType = "Cu/XLPE/PVC - 4C"
        )
        val smdb = CircuitEntity(
            id = 602,
            circuitName = "SMDB-1",
            projectId = "PRJ_SC_BRANCH",
            loadKVA = 80.0,
            length_m = 40.0,
            fedFrom = "MDB",
            parentId = 601,
            transformerId = "TX-01",
            cableType = "Cu/XLPE/PVC - 4C"
        )

        val allCircuits = listOf(mdb, smdb)
        val allTransformers = listOf(tx)

        // Bulk calculation
        val sizingResult = EngineeringCalculationCoordinator.calculateProjectCableSizing(allCircuits, allTransformers)
        val txResult = EngineeringCalculationCoordinator.calculateProjectTransformers(allCircuits, allTransformers)
        val bulkScMap = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = sizingResult.resolvedCircuits,
            circuitParents = sizingResult.circuitParents,
            sizingResults = sizingResult.sizingResults,
            transformerResults = txResult
        )

        // Targeted calculation for SMDB-1
        val targetedSc = EngineeringCalculationCoordinator.calculateShortCircuit(
            circuitId = 602,
            allCircuits = allCircuits,
            allTransformers = allTransformers
        )

        val bulkSmdbSc = bulkScMap[602]!!
        assertEquals("Targeted iscKA must match bulk result", bulkSmdbSc.iscKA, targetedSc.iscKA, 0.001)
        assertEquals("Targeted zTotalMOhm must match bulk result", bulkSmdbSc.zTotalMOhm, targetedSc.zTotalMOhm, 0.001)
        assertEquals("Targeted rMOhm must match bulk result", bulkSmdbSc.rMOhm, targetedSc.rMOhm, 0.001)
        assertEquals("Targeted xMOhm must match bulk result", bulkSmdbSc.xMOhm, targetedSc.xMOhm, 0.001)
    }

    @Test
    fun testCoordinatorShortCircuitFreshnessCacheReusesAndInvalidates() {
        val tx = TransformerEntity(
            projectId = "PRJ_SC_CACHE",
            transformerId = "TX-01",
            kVA = 1000.0,
            status = "Active"
        )
        val circuit = CircuitEntity(
            id = 701,
            circuitName = "Feeder Panel",
            projectId = "PRJ_SC_CACHE",
            loadKVA = 150.0,
            length_m = 50.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            cableType = "Cu/XLPE/PVC - 4C"
        )
        val allCircuits = listOf(circuit)
        val allTransformers = listOf(tx)

        val res1 = EngineeringCalculationCoordinator.calculateShortCircuit(701, allCircuits, allTransformers)
        val res2 = EngineeringCalculationCoordinator.calculateShortCircuit(701, allCircuits, allTransformers)

        assertSame("Second call should return cached instance", res1, res2)

        // Invalidate circuit cache and verify recalculation
        EngineeringCalculationCoordinator.invalidateCircuit(701)
        val res3 = EngineeringCalculationCoordinator.calculateShortCircuit(701, allCircuits, allTransformers)
        assertNotSame("Invalidated circuit should produce new instance", res1, res3)
        assertEquals(res1.iscKA, res3.iscKA, 0.001)
    }

    @Test
    fun testViewModelShortCircuitCalculationEquivalence() {
        val tx = TransformerEntity(
            projectId = "PRJ_SC_VM_EQUIV",
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 10.0,
            status = "Active"
        )
        val circuit1 = CircuitEntity(
            id = 801,
            circuitName = "MDB",
            projectId = "PRJ_SC_VM_EQUIV",
            loadKVA = 400.0,
            length_m = 25.0,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val circuit2 = CircuitEntity(
            id = 802,
            circuitName = "DB-1",
            projectId = "PRJ_SC_VM_EQUIV",
            loadKVA = 100.0,
            length_m = 35.0,
            fedFrom = "MDB",
            parentId = 801,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(circuit1, circuit2)
        val allTransformers = listOf(tx)

        val cableProject = EngineeringCalculationCoordinator.calculateProjectCableSizing(allCircuits, allTransformers)
        val txMap = EngineeringCalculationCoordinator.calculateProjectTransformers(allCircuits, allTransformers)

        val directScMap = ShortCircuitEngine.calculate(
            resolvedCircuits = cableProject.resolvedCircuits,
            circuitParents = cableProject.circuitParents,
            sizingResults = cableProject.sizingResults,
            transformerResults = txMap
        )

        val coordinatorScMap = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = cableProject.resolvedCircuits,
            circuitParents = cableProject.circuitParents,
            sizingResults = cableProject.sizingResults,
            transformerResults = txMap
        )

        assertEquals(directScMap.keys, coordinatorScMap.keys)
        for (id in directScMap.keys) {
            val d = directScMap[id]!!
            val c = coordinatorScMap[id]!!
            assertEquals("iscKA parity on $id", d.iscKA, c.iscKA, 0.001)
            assertEquals("iwKA parity on $id", d.iwKA, c.iwKA, 0.001)
            assertEquals("deviceKA parity on $id", d.deviceKA, c.deviceKA, 0.001)
            assertEquals("deviceCheck parity on $id", d.deviceCheck, c.deviceCheck)
            assertEquals("cableCheck parity on $id", d.cableCheck, c.cableCheck)
            assertEquals("status parity on $id", d.status, c.status)
            assertEquals("zTotalMOhm parity on $id", d.zTotalMOhm, c.zTotalMOhm, 0.001)
        }
    }

    @Test(timeout = 2000)
    fun testCircularReferenceParentChainTerminatesGracefullyWithoutHang() {
        val circuitA = CircuitEntity(
            id = 901,
            circuitName = "Loop-Panel-A",
            projectId = "PRJ_CIRCULAR",
            fedFrom = "Loop-Panel-B",
            parentId = 902,
            length_m = 20.0
        )
        val circuitB = CircuitEntity(
            id = 902,
            circuitName = "Loop-Panel-B",
            projectId = "PRJ_CIRCULAR",
            fedFrom = "Loop-Panel-A",
            parentId = 901,
            length_m = 20.0
        )
        val circularCircuits = listOf(circuitA, circuitB)

        // 1. Ancestor resolution must break cycle and terminate
        val ancestorsA = EngineeringCalculationCoordinator.resolveAncestorChain(circuitA, circularCircuits)
        assertNotNull(ancestorsA)
        assertTrue("Ancestors list should not contain infinite loop", ancestorsA.size <= 2)

        // 2. Targeted cable sizing must terminate safely
        val cableResultA = EngineeringCalculationCoordinator.calculateCableSizing(circuitA, circularCircuits)
        assertNotNull(cableResultA)

        // 3. Targeted short circuit calculation must terminate safely
        val scResultA = EngineeringCalculationCoordinator.calculateShortCircuit(901, circularCircuits, emptyList())
        assertNotNull(scResultA)

        // 4. Bulk short circuit calculation must terminate safely
        val parentsMap = mapOf(901 to 902, 902 to 901)
        val bulkSc = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = circularCircuits,
            circuitParents = parentsMap,
            sizingResults = emptyMap(),
            transformerResults = emptyMap()
        )
        assertNotNull(bulkSc)
    }

    @Test
    fun testProtectionCalculationServiceMatchesDirectEngineOutput() {
        val circuit = CircuitEntity(
            id = 1001,
            circuitName = "Sub-Feeder-1",
            projectId = "PRJ_PROT_TEST",
            length_m = 45.0,
            loadKVA = 50.0,
            overrideDeviceType = "C"
        )
        val sizingResult = SizingResult(
            Ib_A = 72.0,
            deviceSize_A = 80.0,
            deviceType = "MCCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = 80.0,
            productCode = "CU-XLPE-4C-35",
            cableSize_mm2 = "35 mm²",
            ratedI_table_A = 130.0,
            capacity_derated_Iz_A = 104.0,
            utilizationPct = 76.9,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 2.1,
            vdPct = 0.525,
            cumulativeVDpct = 0.525,
            status = "PASS",
            rUsed = 0.627,
            xUsed = 0.082,
            cableOD_mm = 24.0,
            kFactor = 143.0,
            clearingTime_s = 0.1,
            deviceBreakingKA = 36.0,
            cableMaterial = "Cu"
        )
        val scResult = ShortCircuitResult(
            circuitId = 1001,
            sourceName = "TX-01",
            cableMm2 = "35 mm²",
            iscKA = 12.5,
            iwKA = 23.4,
            deviceKA = 36.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 87.2,
            status = "PASS",
            note = ""
        )

        // 1. ADS Parity Check
        val directAds = ProtectionEngine.calculateAds(circuit, sizingResult, ze_ohm = 0.02)
        val serviceAds = ProtectionCalculationService.calculateAds(circuit, sizingResult, ze_ohm = 0.02)
        assertEquals("calculatedZs match", directAds.calculatedZs, serviceAds.calculatedZs, 0.001)
        assertEquals("maxZsAllowed match", directAds.maxZsAllowed, serviceAds.maxZsAllowed, 0.001)
        assertEquals("status match", directAds.status, serviceAds.status)
        assertEquals("disconnectionTime match", directAds.disconnectionTime, serviceAds.disconnectionTime)

        // 2. Protection Parity Check
        val directProt = ProtectionEngine.evaluateProtection(circuit, sizingResult, scResult, upstreamIn_A = 160.0, upstreamName = "MDB")
        val serviceProt = ProtectionCalculationService.evaluateProtection(circuit, sizingResult, scResult, upstreamIn_A = 160.0, upstreamName = "MDB")
        assertEquals("Ib_A match", directProt.Ib_A, serviceProt.Ib_A, 0.001)
        assertEquals("In_A match", directProt.In_A, serviceProt.In_A, 0.001)
        assertEquals("Iz_A match", directProt.Iz_A, serviceProt.Iz_A, 0.001)
        assertEquals("iscKA match", directProt.iscKA, serviceProt.iscKA, 0.001)
        assertEquals("icuKA match", directProt.icuKA, serviceProt.icuKA, 0.001)
        assertEquals("overloadStatus match", directProt.overloadStatus, serviceProt.overloadStatus)
        assertEquals("shortCircuitStatus match", directProt.shortCircuitStatus, serviceProt.shortCircuitStatus)
        assertEquals("cableWithstandStatus match", directProt.cableWithstandStatus, serviceProt.cableWithstandStatus)
        assertEquals("selectivityStatus match", directProt.selectivityStatus, serviceProt.selectivityStatus)
        assertEquals("finalStatus match", directProt.finalStatus, serviceProt.finalStatus)
    }

    @Test
    fun testCoordinatorTargetedProtectionAssemblesDependencyBranch() {
        val tx = TransformerEntity(
            projectId = "PRJ_PROT_BRANCH",
            transformerId = "TX-01",
            kVA = 800.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 8.0,
            status = "Active"
        )
        val mdb = CircuitEntity(
            id = 1101,
            circuitName = "MDB",
            projectId = "PRJ_PROT_BRANCH",
            loadKVA = 250.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val db1 = CircuitEntity(
            id = 1102,
            circuitName = "DB-1",
            projectId = "PRJ_PROT_BRANCH",
            loadKVA = 60.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "MDB",
            parentId = 1101,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(mdb, db1)
        val allTransformers = listOf(tx)

        // Calculate targeted ADS and Protection
        val adsResult = EngineeringCalculationCoordinator.calculateAds(1102, allCircuits, allTransformers)
        val protResult = EngineeringCalculationCoordinator.calculateProtection(1102, allCircuits, allTransformers)

        assertNotNull(adsResult)
        assertEquals(1102, adsResult.circuitId)
        assertTrue(adsResult.calculatedZs > 0.0)

        assertNotNull(protResult)
        assertEquals(1102, protResult.circuitId)
        assertTrue(protResult.In_A > 0.0)
        assertEquals("PASS", protResult.overloadStatus)
    }

    @Test
    fun testCoordinatorProtectionFreshnessCacheReusesAndInvalidates() {
        val tx = TransformerEntity(
            projectId = "PRJ_PROT_CACHE",
            transformerId = "TX-01",
            kVA = 1000.0,
            status = "Active"
        )
        val circuit = CircuitEntity(
            id = 1201,
            circuitName = "Feeder-1",
            projectId = "PRJ_PROT_CACHE",
            loadKVA = 80.0,
            length_m = 40.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            cableType = "Cu/XLPE/PVC - 4C"
        )
        val allCircuits = listOf(circuit)
        val allTransformers = listOf(tx)

        val ads1 = EngineeringCalculationCoordinator.calculateAds(1201, allCircuits, allTransformers)
        val ads2 = EngineeringCalculationCoordinator.calculateAds(1201, allCircuits, allTransformers)
        assertSame("Second call to calculateAds should return cached instance", ads1, ads2)

        val prot1 = EngineeringCalculationCoordinator.calculateProtection(1201, allCircuits, allTransformers)
        val prot2 = EngineeringCalculationCoordinator.calculateProtection(1201, allCircuits, allTransformers)
        assertSame("Second call to calculateProtection should return cached instance", prot1, prot2)

        // Invalidate circuit and verify new instances are returned
        EngineeringCalculationCoordinator.invalidateCircuit(1201)
        val ads3 = EngineeringCalculationCoordinator.calculateAds(1201, allCircuits, allTransformers)
        val prot3 = EngineeringCalculationCoordinator.calculateProtection(1201, allCircuits, allTransformers)

        assertNotSame("Invalidated circuit should produce fresh ADS instance", ads1, ads3)
        assertNotSame("Invalidated circuit should produce fresh Protection instance", prot1, prot3)
        assertEquals(ads1.calculatedZs, ads3.calculatedZs, 0.001)
        assertEquals(prot1.In_A, prot3.In_A, 0.001)
    }

    @Test
    fun testViewModelProtectionCalculationEquivalence() {
        val tx = TransformerEntity(
            projectId = "PRJ_PROT_VM_EQUIV",
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 10.0,
            status = "Active"
        )
        val circuit1 = CircuitEntity(
            id = 1301,
            circuitName = "Main-MDB",
            projectId = "PRJ_PROT_VM_EQUIV",
            loadKVA = 350.0,
            length_m = 25.0,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val circuit2 = CircuitEntity(
            id = 1302,
            circuitName = "Sub-DB",
            projectId = "PRJ_PROT_VM_EQUIV",
            loadKVA = 75.0,
            length_m = 35.0,
            fedFrom = "Main-MDB",
            parentId = 1301,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(circuit1, circuit2)
        val allTransformers = listOf(tx)

        val cableProject = EngineeringCalculationCoordinator.calculateProjectCableSizing(allCircuits, allTransformers)
        val txMap = EngineeringCalculationCoordinator.calculateProjectTransformers(allCircuits, allTransformers)
        val scMap = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = cableProject.resolvedCircuits,
            circuitParents = cableProject.circuitParents,
            sizingResults = cableProject.sizingResults,
            transformerResults = txMap
        )

        // Direct ViewModel-style calculations
        val directAdsMap = cableProject.resolvedCircuits.associate { circ ->
            val s = cableProject.sizingResults[circ.id]
            if (s != null) circ.id to ProtectionEngine.calculateAds(circ, s) else circ.id to null
        }.filterValues { it != null }.mapValues { it.value!! }

        val directProtMap = cableProject.resolvedCircuits.associate { circ ->
            val s = cableProject.sizingResults[circ.id]
            val sc = scMap[circ.id]
            val pId = cableProject.circuitParents[circ.id]
            val pSizing = if (pId != null) cableProject.sizingResults[pId] else null
            val uIn = pSizing?.deviceSize_A ?: (txMap[circ.transformerId]?.flc ?: 0.0)
            val pName = if (pId != null) (allCircuits.find { it.id == pId }?.circuitName ?: "Parent Board") else "Incomer (${circ.transformerId})"
            if (s != null) circ.id to ProtectionEngine.evaluateProtection(circ, s, sc, uIn, pName) else circ.id to null
        }.filterValues { it != null }.mapValues { it.value!! }

        // Coordinator bulk calculations
        val coordAdsMap = EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = cableProject.resolvedCircuits,
            sizingResults = cableProject.sizingResults
        )
        val coordProtMap = EngineeringCalculationCoordinator.calculateProjectProtection(
            resolvedCircuits = cableProject.resolvedCircuits,
            circuitParents = cableProject.circuitParents,
            sizingResults = cableProject.sizingResults,
            shortCircuitResults = scMap,
            transformerResults = txMap,
            allCircuits = allCircuits
        )

        assertEquals(directAdsMap.keys, coordAdsMap.keys)
        for (id in directAdsMap.keys) {
            val dAds = directAdsMap[id]!!
            val cAds = coordAdsMap[id]!!
            assertEquals("calculatedZs on $id", dAds.calculatedZs, cAds.calculatedZs, 0.001)
            assertEquals("maxZsAllowed on $id", dAds.maxZsAllowed, cAds.maxZsAllowed, 0.001)
            assertEquals("ads status on $id", dAds.status, cAds.status)
        }

        assertEquals(directProtMap.keys, coordProtMap.keys)
        for (id in directProtMap.keys) {
            val dProt = directProtMap[id]!!
            val cProt = coordProtMap[id]!!
            assertEquals("Ib_A on $id", dProt.Ib_A, cProt.Ib_A, 0.001)
            assertEquals("In_A on $id", dProt.In_A, cProt.In_A, 0.001)
            assertEquals("Iz_A on $id", dProt.Iz_A, cProt.Iz_A, 0.001)
            assertEquals("iscKA on $id", dProt.iscKA, cProt.iscKA, 0.001)
            assertEquals("overloadStatus on $id", dProt.overloadStatus, cProt.overloadStatus)
            assertEquals("shortCircuitStatus on $id", dProt.shortCircuitStatus, cProt.shortCircuitStatus)
            assertEquals("cableWithstandStatus on $id", dProt.cableWithstandStatus, cProt.cableWithstandStatus)
            assertEquals("selectivityStatus on $id", dProt.selectivityStatus, cProt.selectivityStatus)
            assertEquals("finalStatus on $id", dProt.finalStatus, cProt.finalStatus)
        }
    }

    @Test(timeout = 2000)
    fun testProtectionCircularReferenceParentChainTerminatesGracefullyWithoutHang() {
        val circuitA = CircuitEntity(
            id = 1401,
            circuitName = "Loop-Panel-A",
            projectId = "PRJ_PROT_CIRCULAR",
            fedFrom = "Loop-Panel-B",
            parentId = 1402,
            length_m = 20.0
        )
        val circuitB = CircuitEntity(
            id = 1402,
            circuitName = "Loop-Panel-B",
            projectId = "PRJ_PROT_CIRCULAR",
            fedFrom = "Loop-Panel-A",
            parentId = 1401,
            length_m = 20.0
        )
        val circularCircuits = listOf(circuitA, circuitB)

        // Targeted ADS must terminate safely
        val adsResult = EngineeringCalculationCoordinator.calculateAds(1401, circularCircuits)
        assertNotNull(adsResult)

        // Targeted Protection must terminate safely
        val protResult = EngineeringCalculationCoordinator.calculateProtection(1401, circularCircuits, emptyList())
        assertNotNull(protResult)

        // Bulk ADS and Protection must terminate safely
        val parentsMap = mapOf(1401 to 1402, 1402 to 1401)
        val bulkAds = EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = circularCircuits,
            sizingResults = emptyMap()
        )
        assertNotNull(bulkAds)

        val bulkProt = EngineeringCalculationCoordinator.calculateProjectProtection(
            resolvedCircuits = circularCircuits,
            circuitParents = parentsMap,
            sizingResults = emptyMap(),
            shortCircuitResults = emptyMap(),
            transformerResults = emptyMap(),
            allCircuits = circularCircuits
        )
        assertNotNull(bulkProt)
    }

    @Test
    fun testCoordinationCalculationServiceMatchesDirectEngineOutput() {
        val circuit = CircuitEntity(
            id = 1501,
            circuitName = "Sub-DB-1",
            projectId = "PRJ_COORD_TEST"
        )
        val sizingResult = SizingResult(
            Ib_A = 70.0,
            deviceSize_A = 100.0,
            deviceType = "MCCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = 100.0,
            productCode = "CU-XLPE-4C-50",
            cableSize_mm2 = "50 mm²",
            ratedI_table_A = 160.0,
            capacity_derated_Iz_A = 128.0,
            utilizationPct = 78.1,
            neutral_mm2 = "25 mm²",
            earthCPC_mm2 = "25 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "PASS"
        )

        // Case 1: Selectivity OK (250A / 100A = 2.5 >= 2.0)
        val directRes1 = CoordinationEngine.checkCoordination(circuit, sizingResult, "MDB", 250.0)
        val serviceRes1 = CoordinationCalculationService.calculateCoordination(circuit, sizingResult, "MDB", 250.0)
        assertEquals(directRes1.downstreamCircuitId, serviceRes1.downstreamCircuitId)
        assertEquals(directRes1.downstreamName, serviceRes1.downstreamName)
        assertEquals(directRes1.upstreamName, serviceRes1.upstreamName)
        assertEquals(directRes1.ratio, serviceRes1.ratio, 0.001)
        assertEquals(directRes1.status, serviceRes1.status)
        assertEquals(directRes1.reason, serviceRes1.reason)

        // Case 2: Selectivity Warning (125A / 100A = 1.25 < 1.6)
        val directRes2 = CoordinationEngine.checkCoordination(circuit, sizingResult, "MDB", 125.0)
        val serviceRes2 = CoordinationCalculationService.calculateCoordination(circuit, sizingResult, "MDB", 125.0)
        assertEquals(directRes2.ratio, serviceRes2.ratio, 0.001)
        assertEquals(directRes2.status, serviceRes2.status)
        assertEquals(directRes2.reason, serviceRes2.reason)
    }

    @Test
    fun testCoordinatorTargetedCoordinationResolvesRealUpstreamRating() {
        val tx = TransformerEntity(
            projectId = "PRJ_COORD_TARGETED",
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.0,
            status = "Active"
        )
        val mdb = CircuitEntity(
            id = 1601,
            circuitName = "Main-MDB",
            projectId = "PRJ_COORD_TARGETED",
            loadKVA = 250.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val subDb = CircuitEntity(
            id = 1602,
            circuitName = "Sub-DB",
            projectId = "PRJ_COORD_TARGETED",
            loadKVA = 50.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Main-MDB",
            parentId = 1601,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(mdb, subDb)
        val allTransformers = listOf(tx)

        val coordResult = EngineeringCalculationCoordinator.calculateCoordination(1602, allCircuits, allTransformers)
        assertNotNull(coordResult)
        assertEquals(1602, coordResult.downstreamCircuitId)
        assertEquals("Sub-DB", coordResult.downstreamName)
        assertEquals("Main-MDB", coordResult.upstreamName)
        assertTrue("Ratio should be calculated based on real upstream breaker rating", coordResult.ratio > 0.0)
        assertEquals("✓ OK", coordResult.status)
    }

    @Test
    fun testCoordinatorRootCoordinationUsesVerifiedSourceOrReturnsIncomplete() {
        val tx = TransformerEntity(
            projectId = "PRJ_COORD_ROOT",
            transformerId = "TX-01",
            kVA = 800.0,
            manualKVA = 800.0,
            sizeMode = "Manual",
            voltage = 400,
            percentZ = 5.0,
            status = "Active"
        )
        val rootMdb = CircuitEntity(
            id = 1701,
            circuitName = "MDB",
            projectId = "PRJ_COORD_ROOT",
            loadKVA = 300.0,
            voltageV = 400,
            pf = 0.85,
            safetyFactor = 1.0,
            length_m = 15.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        // Case A: Transformer verified
        val resA = EngineeringCalculationCoordinator.calculateCoordination(1701, listOf(rootMdb), listOf(tx))
        assertEquals("Incomer (TX-01)", resA.upstreamName)
        assertTrue("Upstream rating should match transformer FLC", resA.ratio > 0.0)
        assertEquals("✓ OK", resA.status)

        // Case B: Transformer missing from project
        val resB = EngineeringCalculationCoordinator.calculateCoordination(1701, listOf(rootMdb), emptyList())
        assertEquals("WARNING", resB.status)
        assertTrue(resB.reason.contains("not found"))
    }

    @Test
    fun testCoordinatorCoordinationCacheReusesAndInvalidatesOnUpstreamChange() {
        val tx = TransformerEntity(
            projectId = "PRJ_COORD_CACHE",
            transformerId = "TX-01",
            kVA = 1000.0,
            status = "Active"
        )
        val parent = CircuitEntity(
            id = 1801,
            circuitName = "MDB",
            projectId = "PRJ_COORD_CACHE",
            loadKVA = 200.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val child = CircuitEntity(
            id = 1802,
            circuitName = "DB-1",
            projectId = "PRJ_COORD_CACHE",
            loadKVA = 40.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 25.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            fedFrom = "MDB",
            parentId = 1801,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(parent, child)
        val allTransformers = listOf(tx)

        // 1. Initial calculation
        val res1 = EngineeringCalculationCoordinator.calculateCoordination(1802, allCircuits, allTransformers)
        assertNotNull(res1)

        // 2. Second call with unchanged project inputs -> cache hit
        val res2 = EngineeringCalculationCoordinator.calculateCoordination(1802, allCircuits, allTransformers)
        assertSame("Unchanged inputs must reuse cached CoordinationResult", res1, res2)

        // 3. Upstream parent rating changes (e.g. load increases from 200 kVA to 600 kVA -> breaker increases to 1000A)
        val modifiedParent = parent.copy(loadKVA = 600.0)
        val modifiedCircuits = listOf(modifiedParent, child)

        // Notice: We do NOT call invalidateCircuit(1802) for the child!
        // Downstream staleness is detected because upstream parent's new rating alters computeCoordinationSignature.
        val res3 = EngineeringCalculationCoordinator.calculateCoordination(1802, modifiedCircuits, allTransformers)
        assertNotSame("Changed upstream rating must produce a fresh CoordinationResult", res1, res3)
        assertTrue("Ratio must increase with larger upstream breaker", res3.ratio > res1.ratio)
    }

    @Test
    fun testViewModelCoordinationCalculationEquivalence() {
        val tx = TransformerEntity(
            projectId = "PRJ_COORD_VM",
            transformerId = "TX-01",
            kVA = 1000.0,
            status = "Active"
        )
        val mdb = CircuitEntity(
            id = 1901,
            circuitName = "MDB",
            projectId = "PRJ_COORD_VM",
            loadKVA = 300.0,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val smdb1 = CircuitEntity(
            id = 1902,
            circuitName = "SMDB-1",
            projectId = "PRJ_COORD_VM",
            loadKVA = 100.0,
            fedFrom = "MDB",
            parentId = 1901,
            transformerId = "TX-01"
        )
        val smdb2 = CircuitEntity(
            id = 1903,
            circuitName = "SMDB-2",
            projectId = "PRJ_COORD_VM",
            loadKVA = 60.0,
            fedFrom = "MDB",
            parentId = 1901,
            transformerId = "TX-01"
        )
        val allCircuits = listOf(mdb, smdb1, smdb2)
        val allTransformers = listOf(tx)

        val cableProject = EngineeringCalculationCoordinator.calculateProjectCableSizing(allCircuits, allTransformers)
        val txMap = EngineeringCalculationCoordinator.calculateProjectTransformers(allCircuits, allTransformers)

        // Direct ViewModel logic
        val directCoordList = cableProject.resolvedCircuits.mapNotNull { circ ->
            val sizingRes = cableProject.sizingResults[circ.id]
            val parentId = cableProject.circuitParents[circ.id]
            if (sizingRes != null && parentId != null) {
                val parentCirc = allCircuits.find { it.id == parentId }
                val parentSizing = cableProject.sizingResults[parentId]
                if (parentCirc != null && parentSizing != null) {
                    CoordinationEngine.checkCoordination(circ, sizingRes, parentCirc.circuitName, parentSizing.deviceSize_A)
                } else null
            } else if (sizingRes != null && circ.fedFrom.equals("Transformer", ignoreCase = true)) {
                val txRes = txMap[circ.transformerId]
                if (txRes != null) {
                    CoordinationEngine.checkCoordination(circ, sizingRes, "Incomer (${circ.transformerId})", txRes.flc)
                } else null
            } else null
        }

        // Coordinator bulk logic
        val coordList = EngineeringCalculationCoordinator.calculateProjectCoordination(
            resolvedCircuits = cableProject.resolvedCircuits,
            circuitParents = cableProject.circuitParents,
            sizingResults = cableProject.sizingResults,
            transformerResults = txMap,
            allCircuits = allCircuits
        )

        assertEquals(directCoordList.size, coordList.size)
        for (i in directCoordList.indices) {
            val d = directCoordList[i]
            val c = coordList[i]
            assertEquals("downstreamCircuitId on index $i", d.downstreamCircuitId, c.downstreamCircuitId)
            assertEquals("downstreamName on index $i", d.downstreamName, c.downstreamName)
            assertEquals("upstreamName on index $i", d.upstreamName, c.upstreamName)
            assertEquals("ratio on index $i", d.ratio, c.ratio, 0.001)
            assertEquals("status on index $i", d.status, c.status)
            assertEquals("reason on index $i", d.reason, c.reason)
        }
    }

    @Test(timeout = 2000)
    fun testCoordinationCircularReferenceTerminatesWithoutHang() {
        val circuitA = CircuitEntity(
            id = 2001,
            circuitName = "Loop-A",
            projectId = "PRJ_COORD_CIRCULAR",
            fedFrom = "Loop-B",
            parentId = 2002
        )
        val circuitB = CircuitEntity(
            id = 2002,
            circuitName = "Loop-B",
            projectId = "PRJ_COORD_CIRCULAR",
            fedFrom = "Loop-A",
            parentId = 2001
        )
        val circularCircuits = listOf(circuitA, circuitB)

        // Targeted calculation with cycle must terminate safely
        val res = EngineeringCalculationCoordinator.calculateCoordination(2001, circularCircuits, emptyList())
        assertNotNull(res)

        // Bulk calculation must terminate safely
        val parentsMap = mapOf(2001 to 2002, 2002 to 2001)
        val bulkList = EngineeringCalculationCoordinator.calculateProjectCoordination(
            resolvedCircuits = circularCircuits,
            circuitParents = parentsMap,
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            allCircuits = circularCircuits
        )
        assertNotNull(bulkList)
        assertTrue(bulkList.isEmpty())
    }

    @Test
    fun testCoordinationMissingUpstreamDoesNotReturnVerifiedPass() {
        val isolatedCircuit = CircuitEntity(
            id = 2101,
            circuitName = "Isolated-DB",
            projectId = "PRJ_MISSING_UPSTREAM",
            loadKVA = 30.0,
            fedFrom = "NonExistentBoard",
            parentId = null,
            transformerId = ""
        )

        val result = EngineeringCalculationCoordinator.calculateCoordination(
            circuitId = 2101,
            allCircuits = listOf(isolatedCircuit),
            allTransformers = emptyList()
        )

        assertNotNull(result)
        assertEquals("WARNING", result.status)
        assertEquals(0.0, result.ratio, 0.001)
        assertFalse("Missing upstream must never return ✓ OK status", result.status.contains("OK"))
    }
}
