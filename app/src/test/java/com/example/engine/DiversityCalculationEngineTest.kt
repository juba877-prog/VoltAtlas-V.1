package com.example.engine

import com.example.data.CircuitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiversityCalculationEngineTest {

    @Test
    fun testIecTable10RdfResolution() {
        assertEquals(1.0, DiversityCalculationEngine.resolveIecTable10Rdf(1), 0.001)
        assertEquals(0.9, DiversityCalculationEngine.resolveIecTable10Rdf(2), 0.001)
        assertEquals(0.9, DiversityCalculationEngine.resolveIecTable10Rdf(3), 0.001)
        assertEquals(0.8, DiversityCalculationEngine.resolveIecTable10Rdf(4), 0.001)
        assertEquals(0.8, DiversityCalculationEngine.resolveIecTable10Rdf(5), 0.001)
        assertEquals(0.7, DiversityCalculationEngine.resolveIecTable10Rdf(6), 0.001)
        assertEquals(0.7, DiversityCalculationEngine.resolveIecTable10Rdf(9), 0.001)
        assertEquals(0.6, DiversityCalculationEngine.resolveIecTable10Rdf(10), 0.001)
        assertEquals(0.6, DiversityCalculationEngine.resolveIecTable10Rdf(24), 0.001)
    }

    @Test
    fun testCircuitClassification() {
        val lgt = CircuitEntity(circuitName = "LGT-01", loadKVA = 5.0)
        assertEquals(LoadCategory.LIGHTING, DiversityCalculationEngine.classifyCircuit(lgt))

        val skt = CircuitEntity(circuitName = "SOCKET-01", loadKVA = 3.0)
        assertEquals(LoadCategory.SMALL_POWER, DiversityCalculationEngine.classifyCircuit(skt))

        val ahu = CircuitEntity(circuitName = "AHU-GF-01", loadKVA = 15.0)
        assertEquals(LoadCategory.HVAC_MOTORS, DiversityCalculationEngine.classifyCircuit(ahu))

        val lift = CircuitEntity(circuitName = "LIFT-PASSENGER", loadKVA = 25.0)
        assertEquals(LoadCategory.ELEVATORS, DiversityCalculationEngine.classifyCircuit(lift))

        val pmp = CircuitEntity(circuitName = "BOOSTER-PUMP-1", loadKVA = 7.5)
        assertEquals(LoadCategory.PUMPS_PLUMBING, DiversityCalculationEngine.classifyCircuit(pmp))

        val fire = CircuitEntity(circuitName = "FIRE-PUMP-MAIN", loadKVA = 55.0)
        assertEquals(LoadCategory.FIRE_LIFE_SAFETY, DiversityCalculationEngine.classifyCircuit(fire))

        val ups = CircuitEntity(circuitName = "UPS-DATA-CENTER", loadKVA = 40.0)
        assertEquals(LoadCategory.DATA_IT_UPS, DiversityCalculationEngine.classifyCircuit(ups))

        val spare = CircuitEntity(circuitName = "SPARE-01", loadKVA = 10.0)
        assertEquals(LoadCategory.SPARE_SPACE, DiversityCalculationEngine.classifyCircuit(spare))

        val sub = CircuitEntity(circuitName = "SMDB-1", panelType = "SMDB", loadKVA = 50.0)
        assertEquals(LoadCategory.SUB_PANEL, DiversityCalculationEngine.classifyCircuit(sub, isSubPanel = true))
    }

    @Test
    fun testSmallPowerTieredDemand() {
        // BS 7671 Table A1 / NEC 220:
        // 3 socket circuits of 10 kVA each -> Total connected = 30 kVA
        // TDL = first 10 kVA @ 100% (10) + remaining 20 kVA @ 50% (10) = 20 kVA
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "SKT-1", loadKVA = 10.0),
            CircuitEntity(id = 2, circuitName = "SKT-2", loadKVA = 10.0),
            CircuitEntity(id = 3, circuitName = "SKT-3", loadKVA = 10.0)
        )
        val res = DiversityCalculationEngine.calculateBoardDiversity(circuits)
        assertEquals(30.0, res.totalConnectedKva, 0.001)
        assertEquals(20.0, res.totalDemandKva, 0.001)
        assertEquals(0.67, res.overallDiversityFactor, 0.01)
    }

    @Test
    fun testHvacMotorsCascadingDemand() {
        // 3 motors: 20 kVA, 15 kVA, 10 kVA
        // 1st @ 100% -> 20.0 kVA
        // 2nd @ 80%  -> 12.0 kVA
        // 3rd @ 60%  -> 6.0 kVA
        // Total TDL = 38.0 kVA (TCL = 45.0 kVA)
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "AHU-1", loadKVA = 20.0),
            CircuitEntity(id = 2, circuitName = "AHU-2", loadKVA = 15.0),
            CircuitEntity(id = 3, circuitName = "CHILLER-PUMP", loadNature = "Motor", loadKVA = 10.0)
        )
        val res = DiversityCalculationEngine.calculateBoardDiversity(circuits)
        assertEquals(45.0, res.totalConnectedKva, 0.001)
        assertEquals(38.0, res.totalDemandKva, 0.001)
        assertEquals(0.84, res.overallDiversityFactor, 0.01)
    }

    @Test
    fun testDutyStandbyPumpsExclusion() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "DOMESTIC-PUMP-DUTY", loadKVA = 15.0),
            CircuitEntity(id = 2, circuitName = "DOMESTIC-PUMP-STANDBY", loadKVA = 15.0)
        )
        val res = DiversityCalculationEngine.calculateBoardDiversity(circuits)
        assertEquals(30.0, res.totalConnectedKva, 0.001)
        assertEquals(15.0, res.totalDemandKva, 0.001) // Standby excluded
        assertEquals(0.50, res.overallDiversityFactor, 0.01)
    }

    @Test
    fun testFireAndSpareLoadsExcludedFromNormalDemand() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "LGT-CORRIDOR", loadKVA = 10.0), // 90% -> 9.0 kVA
            CircuitEntity(id = 2, circuitName = "FIRE-PUMP", loadKVA = 50.0),    // Normal standby -> 0 kVA
            CircuitEntity(id = 3, circuitName = "SPARE-BREAKER", loadKVA = 20.0) // Spare -> 0 kVA
        )
        val res = DiversityCalculationEngine.calculateBoardDiversity(circuits)
        assertEquals(80.0, res.totalConnectedKva, 0.001)
        assertEquals(9.0, res.totalDemandKva, 0.001)
        assertEquals(0.11, res.overallDiversityFactor, 0.01)
    }

    @Test
    fun testBoardDesignCurrentDerivation() {
        // TDL = 12.0 kVA at 400V 3-phase
        // I = 12000 / (sqrt(3) * 400) = 17.32 A
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "GENERIC-1", demandFactor = 1.0, loadKVA = 12.0)
        )
        val res = DiversityCalculationEngine.calculateBoardDiversity(
            circuits = circuits,
            boardVoltageV = 400.0,
            isThreePhase = true
        )
        assertEquals(12.0, res.totalDemandKva, 0.001)
        assertEquals(17.3, res.boardDesignCurrentA, 0.1)
    }
}
