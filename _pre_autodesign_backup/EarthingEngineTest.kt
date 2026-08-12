package com.example

import com.example.engine.EarthingEngine
import com.example.engine.EarthingInput
import org.junit.Assert.*
import org.junit.Test

class EarthingEngineTest {

    @Test
    fun testTcEarth01ReferenceCase() {
        // TC-EARTH-01 (Workbook sheet case):
        // ρ = 19.16 Ω·m, L = 3 m, d = 17 mm, Isc = 6.5623 kA, t = 0.4 s, breaker = 630 A
        val input = EarthingInput(
            soilResistivity_ohmM = 19.16,
            rodLength_m = 3.0,
            rodDiameter_mm = 17.0,
            rodSpacing_m = 5.0,
            ringConductor_mm2 = 25.0,
            ringBurialDepth_m = 0.6,
            kFactor = 159.0,
            projectType = "Commercial",
            faultCurrent_kA = 6.5623,
            systemVoltage_V = 400.0,
            mainBreakerRating_A = 630.0,
            disconnectionTime_s = 0.4,
            u0_V = 230.0,
            totalConnectedKVA = 200.0
        )

        val result = EarthingEngine.calculate(input)

        assertEquals("Low Resistivity", result.soilClass)
        assertEquals(6.36, result.singleRodResistance_ohm, 0.5)
        assertEquals(5249.85, result.correctedFault_A, 0.5)
        assertEquals(20.88, result.earthConductorExact_mm2, 0.5)
        assertEquals(25.0, result.earthConductorStandard_mm2, 0.01)
        assertEquals(25.0, result.equipotentialBonding_mm2, 0.01)
        assertEquals(0.0350, result.zs_ohm, 0.005)
        assertEquals(0.3651, result.zsMax_ohm, 0.005)
        assertEquals("PASS", result.loopCheck)
        assertTrue("Achieved R <= 2.0", result.achievedResistance_ohm <= 2.0)
        assertTrue("Overall Compliant", result.overallCompliant)
        assertEquals("✅ COMPLIANT", result.status)
    }

    @Test
    fun testTcEarth02HighSoilResistivity() {
        // TC-EARTH-02: ρ = 500 Ω·m, fault = 10 kA
        val input = EarthingInput(
            soilResistivity_ohmM = 500.0,
            rodLength_m = 3.0,
            rodDiameter_mm = 17.0,
            faultCurrent_kA = 10.0,
            kFactor = 159.0,
            disconnectionTime_s = 0.4
        )

        val result = EarthingEngine.calculate(input)

        assertEquals("High", result.soilClass)
        // I_corr = 8000 A -> S = (8000 * sqrt(0.4))/159 = 31.82 mm² -> std 35 mm²
        assertEquals(31.82, result.earthConductorExact_mm2, 0.5)
        assertEquals(35.0, result.earthConductorStandard_mm2, 0.01)
        assertTrue("Rods required > 1", result.requiredRods >= 1)
        assertFalse("Calculated values should not be NaN", result.singleRodResistance_ohm.isNaN())
    }

    @Test
    fun testTcEarth03SoilClassification() {
        // TC-EARTH-03: Soil classification boundaries
        val baseInput = EarthingInput(faultCurrent_kA = 5.0)

        assertEquals("Low Resistivity", EarthingEngine.calculate(baseInput.copy(soilResistivity_ohmM = 30.0)).soilClass)
        assertEquals("Normal", EarthingEngine.calculate(baseInput.copy(soilResistivity_ohmM = 80.0)).soilClass)
        assertEquals("Moderate", EarthingEngine.calculate(baseInput.copy(soilResistivity_ohmM = 200.0)).soilClass)
        assertEquals("High", EarthingEngine.calculate(baseInput.copy(soilResistivity_ohmM = 500.0)).soilClass)
        assertEquals("Very High", EarthingEngine.calculate(baseInput.copy(soilResistivity_ohmM = 1500.0)).soilClass)
    }

    @Test
    fun testTcEarth04GuardUnconfigured() {
        // TC-EARTH-04: faultCurrent_kA = 0 or soilResistivity = 0 -> SELECT_LOADS
        val inputZeroFault = EarthingInput(soilResistivity_ohmM = 50.0, faultCurrent_kA = 0.0)
        val resultZeroFault = EarthingEngine.calculate(inputZeroFault)

        assertEquals("SELECT_LOADS", resultZeroFault.status)
        assertFalse(resultZeroFault.overallCompliant)
        assertEquals(0.0, resultZeroFault.correctedFault_A, 0.01)

        val inputZeroRho = EarthingInput(soilResistivity_ohmM = 0.0, faultCurrent_kA = 6.5)
        val resultZeroRho = EarthingEngine.calculate(inputZeroRho)

        assertEquals("SELECT_LOADS", resultZeroRho.status)
        assertFalse(resultZeroRho.overallCompliant)
    }
}
