package com.example

import com.example.data.*
import com.example.engine.CableSizingEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class MasterEngineeringStressTest {

    private var originalCableTypes: List<CableType> = emptyList()
    private var originalDataSheet: List<DataSheetEntry> = emptyList()
    private var originalCableCatalog: List<CableCatalogEntry> = emptyList()

    @Before
    fun setupCatalog() {
        originalCableTypes = Catalog.cableTypes
        originalDataSheet = Catalog.dataSheet
        originalCableCatalog = Catalog.cableCatalog

        if (Catalog.cableTypes.isEmpty()) {
            Catalog.cableTypes = listOf(
                CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70")
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
            Catalog.cableCatalog = listOf(
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 1.5, model = "M-1.5", gnd = 18.5, duct = 18.5, air = 18.5, od = 10.0, wt = 0.2),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 2.5, model = "M-2.5", gnd = 25.0, duct = 25.0, air = 25.0, od = 11.0, wt = 0.3),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 4.0, model = "M-4", gnd = 34.0, duct = 34.0, air = 34.0, od = 12.0, wt = 0.4),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 6.0, model = "M-6", gnd = 43.0, duct = 43.0, air = 43.0, od = 13.0, wt = 0.5),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 10.0, model = "M-10", gnd = 60.0, duct = 60.0, air = 60.0, od = 15.0, wt = 0.7),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 16.0, model = "M-16", gnd = 80.0, duct = 80.0, air = 80.0, od = 16.0, wt = 1.0),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 25.0, model = "M-25", gnd = 105.0, duct = 105.0, air = 105.0, od = 19.0, wt = 1.2),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 35.0, model = "M-35", gnd = 128.0, duct = 128.0, air = 128.0, od = 21.0, wt = 1.5),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 50.0, model = "M-50", gnd = 154.0, duct = 154.0, air = 154.0, od = 24.0, wt = 1.9),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 70.0, model = "M-70", gnd = 194.0, duct = 194.0, air = 194.0, od = 27.0, wt = 2.5),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 95.0, model = "M-95", gnd = 238.0, duct = 238.0, air = 238.0, od = 30.0, wt = 3.2),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 120.0, model = "M-120", gnd = 276.0, duct = 276.0, air = 276.0, od = 33.0, wt = 3.9),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 150.0, model = "M-150", gnd = 314.0, duct = 314.0, air = 314.0, od = 37.0, wt = 4.8),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 185.0, model = "M-185", gnd = 361.0, duct = 361.0, air = 361.0, od = 41.0, wt = 5.9),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 240.0, model = "M-240", gnd = 429.0, duct = 429.0, air = 429.0, od = 46.0, wt = 7.6),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 300.0, model = "M-300", gnd = 495.0, duct = 495.0, air = 495.0, od = 51.0, wt = 9.4),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 400.0, model = "M-400", gnd = 570.0, duct = 570.0, air = 570.0, od = 58.0, wt = 12.0),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 500.0, model = "M-500", gnd = 650.0, duct = 650.0, air = 650.0, od = 64.0, wt = 15.0),
                CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 630.0, model = "M-630", gnd = 750.0, duct = 750.0, air = 750.0, od = 72.0, wt = 19.0)
            )
        }
    }

    @After
    fun tearDownCatalog() {
        Catalog.cableTypes = originalCableTypes
        Catalog.dataSheet = originalDataSheet
        Catalog.cableCatalog = originalCableCatalog
    }

    private fun createTestCircuit(
        id: Int,
        name: String,
        loadKVA: Double,
        fedFrom: String = "Transformer",
        length_m: Double = 30.0,
        parallelRuns: Int = 1,
        airTempC: Int = 40,
        gndTempC: Int = 40,
        burialDepth_m: Double = 0.6,
        soilRes: Int = 125,
        noCircuits: Int = 1,
        formation: String = "Flat",
        spacing: String = "Touching",
        installation: String = "Laid in Free Air",
        maxVDpct: Double = 0.05
    ): CircuitEntity {
        return CircuitEntity(
            id = id,
            circuitName = name,
            loadKVA = loadKVA,
            fedFrom = fedFrom,
            transformerId = "TX-01",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.85,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE",
            installation = installation,
            parallelRuns = parallelRuns,
            airTempC = airTempC,
            gndTempC = gndTempC,
            burialDepth_m = burialDepth_m,
            soilRes = soilRes,
            noCircuits = noCircuits,
            formation = formation,
            spacing = spacing,
            length_m = length_m,
            maxVDpct = maxVDpct
        )
    }

    @Test
    fun testMassiveScale_1000Circuits() {
        val circuits = ArrayList<CircuitEntity>(1000)
        circuits.add(createTestCircuit(1, "MDB-MAIN", 500.0, "Transformer", 20.0, 2, maxVDpct = 0.02))

        for (i in 2..1000) {
            circuits.add(createTestCircuit(i, "DB-SUB-$i", 2.0 + (i % 15), "MDB-MAIN", 25.0 + (i % 50), maxVDpct = 0.05))
        }

        val elapsed = measureTimeMillis {
            val sizingResults = mutableMapOf<Int, com.example.engine.SizingResult>()
            for (c in circuits) {
                val pVD = if (c.fedFrom == "Transformer") 0.0 else (sizingResults[1]?.cumulativeVDpct ?: 0.0)
                val res = CableSizingEngine.sizeCircuit(c, pVD, circuits)
                sizingResults[c.id] = res
            }
            assertEquals(1000, sizingResults.size)
            assertTrue("All 1,000 sizing results computed without NaN", sizingResults.values.all { !it.Ib_A.isNaN() })
        }

        println("MASTER STRESS: 1,000 circuits sized in $elapsed ms")
        assertTrue("Sizing 1,000 circuits took too long: ${elapsed}ms", elapsed < 4000)
    }

    @Test
    fun testDeepHierarchy_50Levels() {
        val circuits = ArrayList<CircuitEntity>(50)
        circuits.add(createTestCircuit(1, "PANEL-1", 200.0, "Transformer", 10.0, maxVDpct = 0.40))

        for (i in 2..50) {
            circuits.add(createTestCircuit(i, "PANEL-$i", maxOf(1.0, 200.0 - (i * 3.5)), "PANEL-${i - 1}", 10.0, maxVDpct = 0.40))
        }

        val sizingResults = mutableMapOf<Int, com.example.engine.SizingResult>()
        for (c in circuits) {
            val parentId = if (c.fedFrom == "Transformer") null else circuits.firstOrNull { it.circuitName == c.fedFrom }?.id
            val parentVD = parentId?.let { sizingResults[it]?.cumulativeVDpct } ?: 0.0
            val res = CableSizingEngine.sizeCircuit(c, parentVD, circuits)
            sizingResults[c.id] = res
        }

        assertEquals(50, sizingResults.size)
        val firstVD = sizingResults[1]!!.cumulativeVDpct
        val lastVD = sizingResults[50]!!.cumulativeVDpct
        assertTrue("Cumulative VD must increase along 50-level tree", lastVD >= firstVD)
        println("MASTER STRESS: 50-Level Hierarchy (Root VD: ${firstVD * 100}%, Leaf VD: ${lastVD * 100}%)")
    }

    @Test
    fun testBoundaryAndCorruptedInputs() {
        val testCases = listOf(
            createTestCircuit(1, "ZERO-LOAD", 0.0, length_m = 0.0, maxVDpct = 0.0),
            createTestCircuit(2, "EXTREME-MEGA-LOAD", 1000000.0, length_m = 50.0),
            createTestCircuit(3, "NEGATIVE-PF", 50.0).copy(pf = -0.85),
            createTestCircuit(4, "UNKNOWN-CABLE-TYPE", 20.0).copy(cableType = "INVALID_CABLE_TYPE_XYZ")
        )

        for (c in testCases) {
            val res = CableSizingEngine.sizeCircuit(c, 0.0, testCases)
            assertNotNull("Result must not be null for corrupted input ${c.circuitName}", res)
            assertFalse("Ib must not be NaN for ${c.circuitName}", res.Ib_A.isNaN())
            assertFalse("cumulativeVDpct must not be NaN for ${c.circuitName}", res.cumulativeVDpct.isNaN())
        }
        println("MASTER STRESS: Boundary and corrupted inputs handled safely without crashes.")
    }

    @Test
    fun testExtremeEnvironmentalConditions() {
        val extremeCircuit = createTestCircuit(
            id = 999,
            name = "SAHARA-EXTREME-FEEDER",
            loadKVA = 300.0,
            installation = "Laid Direct in Ground",
            airTempC = 60,
            gndTempC = 50,
            burialDepth_m = 5.0,
            soilRes = 300,
            noCircuits = 15,
            length_m = 100.0
        )

        val res = CableSizingEngine.sizeCircuit(extremeCircuit, 0.0, listOf(extremeCircuit))
        assertNotNull(res)
        assertTrue("Derating factor must be calculated cleanly non-negative", res.cTotalDerating >= 0.0 && res.cTotalDerating <= 1.0)
        println("MASTER STRESS: Extreme Derating = ${res.cTotalDerating}, Selected Size = ${res.cableSize_mm2}")
    }

    @Test
    fun testLongDistanceFeeder10km() {
        val longFeeder = createTestCircuit(
            id = 777,
            name = "LONG-FEEDER-10KM",
            loadKVA = 150.0,
            length_m = 10000.0, // 10 km
            maxVDpct = 0.05
        )

        val res = CableSizingEngine.sizeCircuit(longFeeder, 0.0, listOf(longFeeder))
        assertNotNull(res)
        assertFalse("Voltage drop must not be NaN for 10km feeder", res.cumulativeVDpct.isNaN())
        println("MASTER STRESS: 10km Feeder Sized = ${res.cableSize_mm2}, Status = ${res.status}")
    }
}
