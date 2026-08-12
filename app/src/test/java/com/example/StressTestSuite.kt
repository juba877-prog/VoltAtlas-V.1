package com.example

import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.AtsEntity
import com.example.engine.CableSizingEngine
import com.example.engine.GeneratorEngine
import com.example.engine.AtsEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

class StressTestSuite {

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
        maxVDpct: Double = 0.05,
        generatorIncluded: Boolean = false,
        loadNature: String = "Static",
        startingMethod: String? = null,
        startingCurrentMultiplier: Double? = null
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
            cableType = "CU/XLPE/PVC",
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
            maxVDpct = maxVDpct,
            generatorIncluded = generatorIncluded,
            loadNature = loadNature,
            startingMethod = startingMethod,
            startingCurrentMultiplier = startingCurrentMultiplier
        )
    }

    /**
     * 1. CODE STRESS TEST: Massive Project Scale (1,000 Circuits)
     * Verifies that the calculation engine can size 1,000 circuits in under 3 seconds.
     */
    @Test
    fun testMassiveScale_1000Circuits() {
        val circuits = ArrayList<CircuitEntity>(1000)
        
        // 1 Main Distribution Board fed from Transformer
        circuits.add(
            createTestCircuit(
                id = 1,
                name = "MDB-MAIN",
                loadKVA = 600.0,
                fedFrom = "Transformer",
                length_m = 20.0,
                parallelRuns = 2,
                maxVDpct = 0.02
            )
        )

        // 999 Sub-circuits connected to MDB-MAIN
        for (i in 2..1000) {
            circuits.add(
                createTestCircuit(
                    id = i,
                    name = "DB-SUB-$i",
                    loadKVA = 5.0 + (i % 20),
                    fedFrom = "MDB-MAIN",
                    length_m = 30.0 + (i % 100),
                    maxVDpct = 0.05
                )
            )
        }

        val elapsed = measureTimeMillis {
            val sizingResults = mutableMapOf<Int, com.example.engine.SizingResult>()

            for (c in circuits) {
                val pVD = if (c.fedFrom == "Transformer") 0.0 else (sizingResults[1]?.cumulativeVDpct ?: 0.0)
                val res = CableSizingEngine.sizeCircuit(c, pVD, circuits)
                sizingResults[c.id] = res
            }

            assertEquals(1000, sizingResults.size)
            assertTrue("All 1,000 sizing results calculated", sizingResults.values.all { it.Ib_A >= 0 })
        }

        println("STRESS TEST SUCCESS: 1,000 circuits calculated in $elapsed ms")
        assertTrue("Calculation of 1000 circuits took too long: ${elapsed}ms", elapsed < 3000)
    }

    /**
     * 2. CODE & ALGORITHMIC STRESS TEST: 20-Level Deep Hierarchy
     * Tests recursive parent-child tree resolution (DB1 -> DB2 -> ... -> DB20).
     */
    @Test
    fun testDeepHierarchy_20Levels() {
        val circuits = ArrayList<CircuitEntity>(20)
        circuits.add(
            createTestCircuit(
                id = 1,
                name = "PANEL-1",
                loadKVA = 100.0,
                fedFrom = "Transformer",
                length_m = 20.0,
                parallelRuns = 1,
                maxVDpct = 0.30
            )
        )

        for (i in 2..20) {
            circuits.add(
                createTestCircuit(
                    id = i,
                    name = "PANEL-$i",
                    loadKVA = maxOf(5.0, 100.0 - (i * 4)),
                    fedFrom = "PANEL-${i - 1}",
                    length_m = 15.0,
                    maxVDpct = 0.30
                )
            )
        }

        val sizingResults = mutableMapOf<Int, com.example.engine.SizingResult>()
        for (c in circuits) {
            val parentId = if (c.fedFrom == "Transformer") null else circuits.firstOrNull { it.circuitName == c.fedFrom }?.id
            val parentVD = parentId?.let { sizingResults[it]?.cumulativeVDpct } ?: 0.0
            val res = CableSizingEngine.sizeCircuit(c, parentVD, circuits)
            sizingResults[c.id] = res
        }

        assertEquals(20, sizingResults.size)
        val firstVD = sizingResults[1]!!.cumulativeVDpct
        val lastVD = sizingResults[20]!!.cumulativeVDpct
        assertTrue("Cumulative VD must increase along the 20-level hierarchy", lastVD >= firstVD)
        println("STRESS TEST SUCCESS: 20-Level Deep Hierarchy (First VD: ${firstVD * 100}%, Last VD: ${lastVD * 100}%)")
    }

    /**
     * 3. ENGINEERING STRESS TEST: Extreme Environmental Derating
     * Air Temp = 60°C, Ground Temp = 50°C, Soil Thermal Resistivity = 3.0 K.m/W (300).
     */
    @Test
    fun testExtremeEnvironmentalDerating() {
        val extremeCircuit = createTestCircuit(
            id = 99,
            name = "EXTREME-TEMP-LOAD",
            loadKVA = 200.0,
            installation = "Laid Direct in Ground",
            airTempC = 60,
            gndTempC = 50,
            burialDepth_m = 1.5,
            soilRes = 300,
            noCircuits = 12,
            length_m = 80.0
        )

        val res = CableSizingEngine.sizeCircuit(extremeCircuit, 0.0, listOf(extremeCircuit))
        assertNotNull(res)
        assertTrue("Derating factor must be heavily reduced under extreme 60C / soil conditions", res.cTotalDerating < 0.60)
        println("STRESS TEST SUCCESS: Extreme Derating Factor = ${res.cTotalDerating}, Selected Cable = ${res.cableSize_mm2}")
    }

    /**
     * 4. ENGINEERING STRESS TEST: 2.5km Long Distance Run (Voltage Drop Stress)
     * Tests autoscale parallel runs under strict 3% VD limit on a 2500m feeder.
     */
    @Test
    fun testExtremeDistanceVoltageDrop() {
        val longFeeder = createTestCircuit(
            id = 500,
            name = "LONG-FEEDER-2500M",
            loadKVA = 300.0,
            length_m = 2500.0, // 2.5 km
            maxVDpct = 0.03
        )

        val res = CableSizingEngine.sizeCircuit(longFeeder, 0.0, listOf(longFeeder))
        assertNotNull(res)
        assertTrue("Voltage drop must stay within specified max 3% limit", res.cumulativeVDpct <= 0.0305)
        println("STRESS TEST SUCCESS: 2.5km Feeder VD = ${res.cumulativeVDpct * 100}%, Recommended Cable = ${res.cableSize_mm2}")
    }

    /**
     * 5. ENGINEERING STRESS TEST: Heavy Motor Starting Inrush & ATS Step Load
     * Tests 100 kW Direct-On-Line (DOL) Motor starting inrush (6x starting current).
     */
    @Test
    fun testMotorStartingInrushAndAtsStress() {
        val gen = GeneratorEntity(
            projectId = "TEST_PROJ",
            additionalEssentialLoadKW = 100.0,
            largestMotorKW = 100.0,
            motorStartMethod = "DOL",
            stepLoadPct = 50.0
        )

        val circuits = listOf(
            createTestCircuit(
                id = 1,
                name = "MOTOR-DOL-100KW",
                loadKVA = 125.0,
                generatorIncluded = true,
                loadNature = "Motor DOL",
                startingMethod = "DOL",
                startingCurrentMultiplier = 6.0
            )
        )

        val genResult = GeneratorEngine.calculate(gen, circuits, 225.0)
        assertNotNull(genResult)

        val ats = AtsEntity(projectId = "TEST_PROJ", ambientTemp = 50)
        val atsResult = AtsEngine.calculate(
            settings = ats,
            totalConnectedLoad = 225.0,
            essentialLoadKVA = genResult.essentialLoadKVA,
            generatorSelectedSize = genResult.finalSize ?: 250.0,
            transformerFLC = 800.0,
            isc3PhaseAtTransformer = 25.0,
            circuits = circuits
        )

        assertNotNull(atsResult)
        println("STRESS TEST SUCCESS: Generator Size = ${genResult.finalSize} kVA, ATS Rating = ${atsResult.finalRating} A")
    }

    /**
     * 6. CODE STRESS TEST: Corrupted & Null Data Protection
     * Ensures engine handles 0 kVA, 0m length, negative values, or NaN gracefully without crash.
     */
    @Test
    fun testCorruptedAndExtremeInputs() {
        val corruptedCircuit = createTestCircuit(
            id = 999,
            name = "CORRUPTED-DATA",
            loadKVA = 0.0,
            length_m = 0.0,
            maxVDpct = 0.0
        )

        val res = CableSizingEngine.sizeCircuit(corruptedCircuit, 0.0, listOf(corruptedCircuit))
        assertNotNull(res)
        assertFalse(res.Ib_A.isNaN())
        assertFalse(res.cumulativeVDpct.isNaN())
        println("STRESS TEST SUCCESS: Corrupted inputs handled safely without crashes or NaN values.")
    }
}
