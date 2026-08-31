package com.example

import com.example.engine.EarthingEngine
import com.example.engine.EarthingInput
import org.junit.Assert.*
import org.junit.Test

class EarthingEngineTest {

    @Test
    fun testTcEarth01ReferenceCase() {
        // TC-EARTH-01 (Workbook sheet case) — UPDATED after IEC remediation (P0 CRITICAL-2/3):
        //  ρ = 19.16 Ω·m, L = 3 m, d = 17 mm, Isc = 6.5623 kA, t = 0.4 s, breaker In = 630 A (curve C default)
        //  - correctedFault_A: no longer ×0.8 — full prospective current per IEC 60364-5-54 §54.2.2 → 6562.30 A
        //  - earthConductorExact: 6562.3·√0.4/159 = 26.10 mm² → std 35 mm²
        //  - zsMax: (0.95·230)/(10×630) = 0.0347 Ω (trip multiplier, not the 630 A continuous rating)
        //  - loop: Zs = 230/6562.3 = 0.0350 Ω > 0.0347 Ω → FAIL (correct, marginal — assumption is flagged)
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
        assertEquals(6562.30, result.correctedFault_A, 0.5)
        assertEquals(26.10, result.earthConductorExact_mm2, 0.5)
        assertEquals(35.0, result.earthConductorStandard_mm2, 0.01)
        assertEquals(35.0, result.equipotentialBonding_mm2, 0.01)
        assertEquals(0.0350, result.zs_ohm, 0.005)
        assertEquals(0.0347, result.zsMax_ohm, 0.005)
        assertEquals("FAIL", result.loopCheck)
        assertFalse(result.overallCompliant)
        assertEquals("❌ REVIEW REQUIRED", result.status)
        // Trip-multiplier assumption must be explicitly flagged
        assertTrue(
            "Assumed trip multiplier must be warned",
            result.messages.any { it.contains("assumed trip multiplier") }
        )
    }

    @Test
    fun testZsMaxWorkedExample_B32_CurveB() {
        // IEC 60364-4-41 §411.4.4 worked example:
        // B32 MCB → Ia = 5 × 32 = 160 A → Zs_max = (0.95 × 230)/160 = 1.3656 Ω
        // (NOT 230/32 = 7.19 Ω as the legacy continuous-rating code produced)
        val input = EarthingInput(
            soilResistivity_ohmM = 50.0,
            faultCurrent_kA = 5.0,
            mainBreakerRating_A = 32.0,
            mainBreakerCurve = "B"
        )

        val result = EarthingEngine.calculate(input)

        assertEquals(1.3656, result.zsMax_ohm, 0.005)
        assertEquals("PASS", result.loopCheck) // Zs = 230/5000 = 0.046 Ω <= 1.3656 Ω
        assertTrue(result.overallCompliant)
    }

    @Test
    fun testEarthConductorUsesMaxLEFaultCurrentWhenProvided() {
        // IEC 60364-5-54 §54.2.2: sizing uses the maximum prospective LINE-TO-EARTH fault
        // current Ik1 when supplied, not a reduced 3-phase value and not ×0.8.
        val input = EarthingInput(
            soilResistivity_ohmM = 50.0,
            faultCurrent_kA = 10.0,
            earthFaultCurrent_kA = 6.56,
            kFactor = 159.0,
            disconnectionTime_s = 0.4
        )

        val result = EarthingEngine.calculate(input)

        assertEquals(6560.0, result.correctedFault_A, 0.5)
        assertEquals(26.09, result.earthConductorExact_mm2, 0.05) // 6560·√0.4/159
        assertFalse(
            "No 3-phase fallback warning expected when Ik1 provided",
            result.messages.any { it.contains("3-phase Isc") }
        )
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
        // I_corr = 10000 A (no ×0.8 reduction — IEC 60364-5-54 §54.2.2)
        // S = (10000 * sqrt(0.4))/159 = 39.78 mm² -> std 50 mm²
        assertEquals(39.78, result.earthConductorExact_mm2, 0.5)
        assertEquals(50.0, result.earthConductorStandard_mm2, 0.01)
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

    @Test
    fun testAutoDesign_HitsTargetWithMargin() {
        val result = EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = 50.0,
            soilResistivitySource = "MEASURED",
            userTargetResistance_ohm = 1.0,
            projectTypeDefaultTarget_ohm = 2.0,
            designMarginPct = 10.0
        )

        assertTrue("Should be feasible for 50 ohm-m soil with target 1.0 ohm", result.feasible)
        assertEquals(1.0, result.effectiveTargetResistance_ohm, 0.01)
        assertEquals(0.9, result.designLimitResistance_ohm, 0.01)
        assertNotNull(result.recommended)
        assertTrue(result.recommended!!.calculatedResistance_ohm <= 0.9)
        assertTrue(result.recommended!!.meetsDesignLimit)
    }

    @Test
    fun testAutoDesign_HighResistivity_MultiRodArray() {
        val result = EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = 150.0,
            soilResistivitySource = "SOIL_REPORT",
            userTargetResistance_ohm = 2.0,
            projectTypeDefaultTarget_ohm = 2.0,
            designMarginPct = 10.0
        )

        assertTrue(result.feasible)
        assertNotNull(result.recommended)
        assertTrue(result.recommended!!.rodCount > 1)
        assertTrue(result.recommended!!.rodSpacing_m >= 2.0 * result.recommended!!.rodLength_m)
    }

    @Test
    fun testAutoDesign_UnachievableTarget_ReturnsNoFeasible() {
        val result = EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = 2000.0,
            soilResistivitySource = "ASSUMED",
            userTargetResistance_ohm = 0.1,
            projectTypeDefaultTarget_ohm = 2.0,
            designMarginPct = 20.0,
            maxRods = 10
        )

        assertFalse("Target 0.1 ohm in 2000 ohm-m soil is unachievable in 10 rods", result.feasible)
        assertNull(result.recommended)
        assertTrue(result.warnings.any { it.contains("No feasible configuration") })
    }

    @Test
    fun testAutoDesign_SiteDimensionConstraintRejectsOversizedArray() {
        // Small site (5m x 5m) restricts maximum linear array length
        val result = EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = 500.0,
            soilResistivitySource = "SOIL_REPORT",
            userTargetResistance_ohm = 1.0,
            projectTypeDefaultTarget_ohm = 2.0,
            designMarginPct = 10.0,
            siteLength_m = 5.0,
            siteWidth_m = 5.0,
            maxRods = 50
        )

        // Large arrays exceeding footprint limits should be excluded
        if (result.recommended != null) {
            val reqLength = (result.recommended!!.rodCount - 1) * result.recommended!!.rodSpacing_m
            assertTrue(reqLength <= 10.0)
        }
    }

    @Test
    fun testAutoDesign_PartialSiteDimensionExclusionWarning() {
        // Site (1m x 1m -> max footprint 2.0m) allows single-rod (0m footprint) configs
        // but excludes any multi-rod config (spacing is always >= 3.0m between rods)
        val result = EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = 30.0,
            soilResistivitySource = "SOIL_REPORT",
            userTargetResistance_ohm = 1.03,
            projectTypeDefaultTarget_ohm = 2.0,
            designMarginPct = 10.0,
            siteLength_m = 1.0,
            siteWidth_m = 1.0,
            maxRods = 50
        )

        // Verify that candidates were returned AND a partial-exclusion warning was generated
        assertTrue("Feasible candidates should exist", result.feasible)
        assertNotNull("Recommended candidate should exist", result.recommended)
        assertTrue(
            "Warning should indicate otherwise-viable configurations were excluded due to site dimensions",
            result.warnings.any { it.contains("otherwise-viable configuration(s) were excluded") }
        )
    }

    @Test
    fun testAutoDesign_DefaultTargetsUnchanged() {
        val villaInput = EarthingInput(projectType = "Villa", faultCurrent_kA = 5.0, soilResistivity_ohmM = 50.0)
        val commercialInput = EarthingInput(projectType = "Commercial", faultCurrent_kA = 5.0, soilResistivity_ohmM = 50.0)

        val villaRes = EarthingEngine.calculate(villaInput)
        val commRes = EarthingEngine.calculate(commercialInput)

        assertEquals(5.0, villaRes.targetResistance_ohm, 0.01)
        assertEquals(2.0, commRes.targetResistance_ohm, 0.01)
    }

    @Test
    fun testMigration17To18_ExistingProjectLoadsWithDefaults() {
        val migration = com.example.data.MIGRATION_17_18
        assertEquals(17, migration.startVersion)
        assertEquals(18, migration.endVersion)

        val defaultEntity = com.example.data.EarthingEntity()
        assertFalse(defaultEntity.isAutoDesignEnabled)
        assertNull(defaultEntity.targetResistanceOverride_ohm)
        assertEquals("ASSUMED", defaultEntity.soilResistivitySource)
        assertEquals(10.0, defaultEntity.designMarginPct, 0.01)
        assertNull(defaultEntity.siteLength_m)
        assertNull(defaultEntity.siteWidth_m)
        assertNull(defaultEntity.autoDesignAppliedConfigId)
    }
}
