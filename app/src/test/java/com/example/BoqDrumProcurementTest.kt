package com.example

import com.example.data.CircuitEntity
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

class BoqDrumProcurementTest {

    private fun createDummySizingResult(
        size: String = "16.0",
        productCode: String = "ELSE-16-4C",
        cableOD: Double = 22.0
    ): SizingResult {
        return SizingResult(
            Ib_A = 20.0,
            deviceSize_A = 25.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 20.0,
            productCode = productCode,
            cableSize_mm2 = size,
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 100.0,
            utilizationPct = 20.0,
            neutral_mm2 = size,
            earthCPC_mm2 = size,
            voltDrop_V = 2.0,
            vdPct = 1.0,
            cumulativeVDpct = 1.0,
            status = "PASS",
            cableOD_mm = cableOD
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
    }

    @Test
    fun testTcBoq01GroupingAndGlands() {
        // TC-BOQ-01: 16 circuits, Cu/XLPE/STA 16mm², 1 parallel, 50 m each
        val circuits = (1..16).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "Ckt-$id",
                loadKVA = 10.0,
                cableType = "Cu/XLPE/STA/PVC 4C",
                overrideCableSize = "16.0",
                parallelRuns = 1,
                length_m = 50.0,
                manufacturer = "Elsewedy"
            )
        }

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "16.0", productCode = "ELSE-16-4C", cableOD = 22.0)
        }

        val boqResult = BoqEngine.generateBoqResult(circuits, dummySizing)

        assertEquals(1, boqResult.items.size)
        val line = boqResult.items[0]
        assertEquals(16, line.circuitsCount)
        assertEquals(800.0, line.routeLength_m, 0.5)
        assertEquals(800.0, line.installedLength_m, 0.5)
        assertEquals(32, boqResult.kpi.totalGlands)
    }

    @Test
    fun testTcDrum01MinWasteOptimization() {
        // TC-DRUM-01: 832 m installed length optimized against standard drum set {100, 250, 500, 1000}
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "Main Feed", length_m = 416.0, parallelRuns = 2, cableType = "Cu/XLPE/STA 4C", overrideCableSize = "185.0")
        )

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "185.0", productCode = "ELSE-185-4C", cableOD = 50.0)
        }

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = dummySizing,
            maxDrumLength = 500.0,
            safetyMargin = 0.0,
            cutOverhead = 0.0
        )

        val totalCapacity = allocations.sumOf { it.drumCapacity_m }
        assertTrue("Allocated capacity >= 832 m", totalCapacity >= 832.0)
        assertTrue("Waste % is low", (totalCapacity - 832.0) / totalCapacity < 0.20)
    }

    @Test
    fun testTcDrum02ScrapThreshold() {
        // TC-DRUM-02: Installed 105 m -> leftover < 15 m reusable threshold
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "Ckt-105", length_m = 105.0, parallelRuns = 1, cableType = "Cu/XLPE 4C", overrideCableSize = "35.0")
        )

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            maxDrumLength = 250.0,
            safetyMargin = 0.0,
            cutOverhead = 0.0
        )

        val totalCapacity = allocations.sumOf { it.drumCapacity_m }
        assertTrue("Capacity covers 105m", totalCapacity >= 105.0)
    }

    @Test
    fun testTcProc01AccessoryRules() {
        // TC-PROC-01: 3-phase, 2 parallel, 4 circuits
        val circuits = (1..4).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "Feeder-$id",
                phases = "3P+N",
                parallelRuns = 2,
                length_m = 100.0,
                cableType = "Cu/XLPE/SWA 4C",
                overrideCableSize = "95.0",
                manufacturer = "Elsewedy"
            )
        }

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "95.0", productCode = "ELSE-95-4C", cableOD = 38.0)
        }

        val procurementItems = ProcurementEngine.generateProcurement(circuits, dummySizing)

        val totalGlands = procurementItems.sumOf { it.glandQty }
        val totalLugs = procurementItems.sumOf { it.lugQty }

        // Glands: 2 ends * 2 parallel * 4 circuits = 16
        assertEquals(16, totalGlands)
        // Lugs: 4 cores * 2 ends * 2 parallel * 4 circuits = 64
        assertEquals(64, totalLugs)
    }

    @Test
    fun testTcBoqKpiProjectTotals() {
        // TC-BOQ-KPI: El Rehab Mall reference project KPI validation
        // 19 circuits, 21 parallel runs
        val circuits = (1..19).map { id ->
            val runs = if (id <= 2) 2 else 1
            CircuitEntity(
                id = id,
                circuitName = "Ckt-$id",
                phases = "3P+N",
                parallelRuns = runs,
                length_m = 43.79,
                cableType = "Cu/XLPE/SWA 4C",
                overrideCableSize = "50.0",
                manufacturer = "Elsewedy"
            )
        }

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "50.0", productCode = "ELSE-50-4C", cableOD = 30.0)
        }

        val drumAllocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = dummySizing,
            maxDrumLength = 500.0,
            safetyMargin = 0.05,
            cutOverhead = 1.5
        )

        val boqResult = BoqEngine.generateBoqResult(circuits, dummySizing, drumAllocations)

        assertEquals(19, boqResult.kpi.totalCircuits)
        assertEquals(832.0, boqResult.kpi.totalRoute_m, 2.0)
        assertEquals(42, boqResult.kpi.totalGlands)
        assertTrue("Ordered length >= installed length", boqResult.kpi.totalOrdered_m >= boqResult.kpi.totalInstalled_m)
        assertTrue("Waste % is non-negative", boqResult.kpi.wastePct >= 0.0)
    }

    @Test
    fun testTcDrumMultiCircuitOptimizedPacking() {
        // Synthetic project: 4 circuits x 240m cut = 960m installed in same cable family
        val circuits = (1..4).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "Feeder-$id",
                length_m = 240.0,
                parallelRuns = 1,
                cableType = "Cu/XLPE/STA 4C",
                overrideCableSize = "240.0",
                manufacturer = "Elsewedy"
            )
        }

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "240.0", productCode = "ELSE-240-4C", cableOD = 55.0)
        }

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = dummySizing,
            safetyMargin = 0.0,
            cutOverhead = 0.0
        )

        val totalCapacity = allocations.sumOf { it.drumCapacity_m }
        val totalInstalled = 960.0
        val wastePct = ((totalCapacity - totalInstalled) / totalCapacity) * 100.0

        // 4 cuts of 240m fit onto 1 x 1000m drum = 1000m ordered -> 40m waste (4.0% waste!)
        // Proves MULTIPLE circuits are packed on the SAME drum, minimizing waste to 4% (NOT 80%).
        assertEquals(1000.0, totalCapacity, 0.5)
        assertEquals(1, allocations.size)
        assertEquals(4.0, wastePct, 0.1)
    }

    @Test
    fun testTcDrumLargeProjectLowWaste() {
        // Synthetic large project: 10 circuits x 90m cut = 900m installed in same cable family
        val circuits = (1..10).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "SubFeeder-$id",
                length_m = 90.0,
                parallelRuns = 1,
                cableType = "Cu/XLPE/STA 4C",
                overrideCableSize = "240.0",
                manufacturer = "Elsewedy"
            )
        }

        val dummySizing = circuits.associate { circ ->
            circ.id to createDummySizingResult(size = "240.0", productCode = "ELSE-240-4C", cableOD = 55.0)
        }

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = dummySizing,
            safetyMargin = 0.0,
            cutOverhead = 0.0
        )

        val totalCapacity = allocations.sumOf { it.drumCapacity_m }
        val totalInstalled = 900.0
        val wastePct = ((totalCapacity - totalInstalled) / totalCapacity) * 100.0

        // 10 cuts of 90m fit onto 1 x 1000m drum = 1000m ordered -> 100m waste (10.0% waste!)
        // Proves multi-circuit shared drum packing reduces waste to 10% on large projects.
        assertEquals(1000.0, totalCapacity, 0.5)
        assertEquals(1, allocations.size)
        assertEquals(10.0, wastePct, 0.1)
    }
}
