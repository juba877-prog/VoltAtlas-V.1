package com.example

import com.example.data.CircuitEntity
import com.example.engine.DrumScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class DrumScheduleEngineTest {

    @Test
    fun testFfdBinPackingWithMultipleDrums() {
        // Create 5 identical circuits of length 120m (600m total installed)
        // Standard manufactured drum capacity set {100, 250, 500, 1000}:
        // The engine packs all 5 cuts into a single 1000m drum (600m used, 400m remaining),
        // achieving optimal single-drum shipping without splitting cuts across multiple drums.
        val circuits = (1..5).map { i ->
            CircuitEntity(id = i, circuitName = "C$i", length_m = 120.0, parallelRuns = 1, cableType = "Cu/XLPE", overrideCableSize = "16 mm²")
        }

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = emptyMap()
        )

        assertEquals(1, allocations.size)
        val drum1 = allocations[0]

        assertEquals(5, drum1.allocatedCuts.size)
        assertEquals(1000.0, drum1.drumCapacity_m, 0.001)
        assertEquals(600.0, drum1.totalUsedLength_m, 0.001)
        assertEquals(400.0, drum1.wasteLength_m, 0.001)
    }

    @Test
    fun testCutsExceedingStandardCapacity() {
        // A single cut of 600m exceeding 500m capacity
        // The algorithm selects the 1000m standard manufactured drum to safely fit the 600m run
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "ExtraLong", length_m = 600.0, parallelRuns = 1, cableType = "Cu/XLPE", overrideCableSize = "16 mm²")
        )

        val allocations = DrumScheduleEngine.generateDetailedDrumSchedule(
            circuits = circuits,
            sizingResults = emptyMap()
        )

        assertEquals(1, allocations.size)
        val alloc = allocations[0]
        assertEquals(1, alloc.allocatedCuts.size)
        assertEquals(600.0, alloc.allocatedCuts[0].length_m, 0.001)
        assertEquals(1000.0, alloc.drumCapacity_m, 0.001)
        assertEquals(600.0, alloc.totalUsedLength_m, 0.001)
        assertEquals(400.0, alloc.wasteLength_m, 0.001)
    }
}
