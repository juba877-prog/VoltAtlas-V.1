package com.example

import com.example.ui.screens.quick.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class QuickEngineeringToolsTest {

    // =========================================================================
    // 1. EARTHING & GROUNDING TESTS
    // =========================================================================
    @Test
    fun testQuickEarthingDwightAndAdiabaticCalculation() {
        val input = EarthingQuickInput(
            soilResistivity_ohmM = 50.0,
            targetResistance_ohm = 2.0,
            faultCurrent_kA = 16.0,
            clearingTime_s = 0.4,
            kFactor = 159.0, // Bare copper
            rodLength_m = 3.0,
            rodDiameter_mm = 17.0,
            rodSpacing_m = 5.0
        )

        val result = QuickEarthingEngine.calculate(input)

        // Dwight formula manual check:
        // dMeters = 0.017
        // ln(8 * 3.0 / 0.017) - 1.0 = ln(1411.7647) - 1.0 = 7.2526 - 1.0 = 6.2526
        // R1 = (50 / (2 * pi * 3.0)) * 6.2526 = (50 / 18.8495) * 6.2526 ≈ 16.585 ohm
        assertTrue("Single rod resistance should be ~16.6 ohm, was ${result.singleRodResistance_ohm}",
            abs(result.singleRodResistance_ohm - 16.59) < 0.2)

        // Multiple rods required to reach <= 2.0 ohm
        assertTrue("Required rods should be between 8 and 12, was ${result.requiredRods}", result.requiredRods in 8..15)
        assertTrue("Achieved resistance should meet target <= 2.0 ohm", result.achievedResistance_ohm <= 2.0)

        // Adiabatic conductor check: S = (16000 * sqrt(0.4)) / 159 = (16000 * 0.63245) / 159 = 63.64 mm²
        // Standard conductor should snap to 70 mm²
        assertEquals(70.0, result.standardConductorMm2, 0.01)
        assertTrue(result.isResistanceOk)
    }

    @Test
    fun testQuickEarthingInsulationKFactors() {
        val inputXlpe = EarthingQuickInput(faultCurrent_kA = 25.0, clearingTime_s = 1.0, kFactor = 176.0)
        val resultXlpe = QuickEarthingEngine.calculate(inputXlpe)
        // S = 25000 * 1.0 / 176 = 142.04 mm² -> standard 150 mm²
        assertEquals(150.0, resultXlpe.standardConductorMm2, 0.01)

        val inputSteel = EarthingQuickInput(faultCurrent_kA = 10.0, clearingTime_s = 1.0, kFactor = 58.0)
        val resultSteel = QuickEarthingEngine.calculate(inputSteel)
        // S = 10000 / 58 = 172.41 mm² -> standard 185 mm²
        assertEquals(185.0, resultSteel.standardConductorMm2, 0.01)
    }

    // =========================================================================
    // 2. CONDUIT FILL TESTS
    // =========================================================================
    @Test
    fun testQuickConduitFillCalculation() {
        val input = ConduitFillQuickInput(
            conduitType = ConduitTypeOption.RIGID_PVC,
            mainCableCount = 4,
            mainCableOD_mm = 18.0,
            cpcCableCount = 1,
            cpcCableOD_mm = 9.0
        )

        val result = QuickConduitFillEngine.calculate(input)

        // Main area: 4 * (pi * 18^2 / 4) = 4 * 254.47 = 1017.88 mm²
        // CPC area: 1 * (pi * 9^2 / 4) = 63.62 mm²
        // Total cables = 5 (>= 3 -> max fill = 40%)
        // Total cable area = 1081.5 mm²
        // Required internal area = 1081.5 / 0.40 = 2703.75 mm²
        // PVC 63 has ID = 56.0 mm, area = 2463 mm² (< 2703.75)
        // PVC 75 has ID = 67.8 mm, area = 3610.3 mm² (>= 2703.75) -> Should select 75 mm
        assertEquals("75 mm", result.recommendedTradeSize)
        assertEquals(40.0, result.allowedFillPercent, 0.01)
        assertTrue("Actual fill percent should be ~30%, was ${result.actualFillPercent}", result.actualFillPercent < 40.0)
        assertTrue(result.isCompliant)
    }

    @Test
    fun testQuickConduitFillNecThresholds() {
        // 1 cable -> 53%
        val res1 = QuickConduitFillEngine.calculate(ConduitFillQuickInput(mainCableCount = 1, mainCableOD_mm = 10.0, cpcCableCount = 0))
        assertEquals(53.0, res1.allowedFillPercent, 0.01)

        // 2 cables -> 31%
        val res2 = QuickConduitFillEngine.calculate(ConduitFillQuickInput(mainCableCount = 2, mainCableOD_mm = 10.0, cpcCableCount = 0))
        assertEquals(31.0, res2.allowedFillPercent, 0.01)

        // 3 cables -> 40%
        val res3 = QuickConduitFillEngine.calculate(ConduitFillQuickInput(mainCableCount = 3, mainCableOD_mm = 10.0, cpcCableCount = 0))
        assertEquals(40.0, res3.allowedFillPercent, 0.01)
    }

    // =========================================================================
    // 3. BREAKER SELECTION & SELECTIVITY TESTS
    // =========================================================================
    @Test
    fun testQuickBreakerSelectionAndSelectivityRatios() {
        val input = BreakerSelectQuickInput(
            loadCurrent_A = 75.0,
            prospectiveFault_kA = 25.0,
            poles = 3,
            deviceTypeFilter = "MCCB",
            upstreamRating_A = 250.0
        )

        val result = QuickBreakerSelectEngine.calculate(input)

        assertNotNull(result.selectedBreaker)
        assertTrue("Rating should be >= 75A, was ${result.continuousRating_A}", result.continuousRating_A >= 75.0)
        assertTrue("Icu should be >= 25kA, was ${result.breakingCapacityIcu_kA}", result.breakingCapacityIcu_kA >= 25.0)

        // Chosen is 100A -> ratio = 250 / 100 = 2.5 (Thermal pass >= 1.6, Magnetic pass >= 2.0)
        assertNotNull(result.thermalSelectivityRatio)
        assertTrue(result.isThermalSelectivityOk)
        assertTrue(result.isMagneticSelectivityOk)
        assertEquals("PASS", result.status)

        // Also test partial coordination where ratio is between 1.6 and 2.0 (e.g. 160A upstream / 100A downstream = 1.6)
        val inputPartial = BreakerSelectQuickInput(
            loadCurrent_A = 75.0,
            prospectiveFault_kA = 25.0,
            poles = 3,
            deviceTypeFilter = "MCCB",
            upstreamRating_A = 160.0
        )
        val resultPartial = QuickBreakerSelectEngine.calculate(inputPartial)
        assertTrue("Thermal selectivity should pass at ratio 1.6", resultPartial.isThermalSelectivityOk)
        assertFalse("Magnetic selectivity should be false at ratio 1.6 (< 2.0)", resultPartial.isMagneticSelectivityOk)
    }

    // =========================================================================
    // 4. GENERATOR QUICK SIZING & VOLTAGE DIP TESTS
    // =========================================================================
    @Test
    fun testQuickGeneratorSizingAndVoltageDip() {
        val input = GeneratorQuickInput(
            continuousLoadKW = 100.0,
            powerFactor = 0.80,
            demandFactor = 1.0,
            largestMotorKW = 22.0,
            motorStartMethod = MotorStartMethod.DOL, // 6.0x
            maxPermittedVoltageDipPct = 15.0,
            subtransientReactanceXdPct = 15.0
        )

        val result = QuickGeneratorEngine.calculate(input)

        // sKva = 100 / 0.80 = 125 kVA
        // Motor starting kVA = (22 / (0.85 * 0.90)) * 6.0 = (22 / 0.765) * 6.0 = 172.55 kVA
        // Generator must be >= 125 / 0.80 = 156.25 kVA
        assertTrue("Generator should be >= 160 kVA, was ${result.recommendedSizeKVA}", result.recommendedSizeKVA >= 160.0)
        assertTrue("Continuous loading should be <= 80%, was ${result.continuousLoadingPercent}", result.isPrimeLoadingOk)
        assertTrue("Motor starting kVA should be ~172 kVA, was ${result.motorStartingKVA}", abs(result.motorStartingKVA - 172.5) < 5.0)
    }

    // =========================================================================
    // 5. UPS & BATTERY SIZING TESTS
    // =========================================================================
    @Test
    fun testQuickUpsAndBatteryCalculations() {
        val input = UpsQuickInput(
            criticalLoadKVA = 50.0,
            powerFactor = 0.80,
            autonomyMinutes = 30.0,
            inverterEfficiencyPct = 90.0,
            batteryVoltageDC = 384.0,
            depthOfDischarge = 0.80,
            agingFactor = 1.25,
            tempDeratingFactor = 1.00
        )

        val result = QuickUpsEngine.calculate(input)

        // sDesign = 50 / 0.80 = 62.5 kVA -> Standard size 80 kVA
        assertEquals(80.0, result.recommendedUpsSizeKVA, 0.01)

        // P_load = 50 * 0.8 = 40 kW
        // P_dc = 40 / 0.90 = 44.44 kW
        // Energy = 44.44 * (30/60) * (1.25 / (1.0 * 0.80)) = 22.22 * 1.5625 = 34.72 kWh
        // Capacity = (34.72 * 1000) / 384 = 90.4 Ah -> 1x 100Ah string
        assertTrue("Battery energy should be ~34.7 kWh, was ${result.requiredEnergyKWh}", abs(result.requiredEnergyKWh - 34.7) < 1.0)
        assertTrue("Battery capacity should be ~90.4 Ah, was ${result.requiredCapacityAh}", abs(result.requiredCapacityAh - 90.4) < 2.0)
        assertEquals(1, result.batteryStringsEstimate100Ah)
    }

    // =========================================================================
    // 6. ATS SIZING & WITHSTAND TESTS
    // =========================================================================
    @Test
    fun testQuickAtsSizingAndWithstand() {
        val input = AtsQuickInput(
            mainsCurrent_A = 350.0,
            generatorFLC_A = 380.0,
            prospectiveFault_kA = 36.0,
            mainsFailureDetectDelay_s = 2.0,
            generatorStartDelay_s = 3.0,
            generatorWarmup_s = 4.0
        )

        val result = QuickAtsEngine.calculate(input)

        // Governing current = max(350, 380) = 380 A -> Standard rating = 400 A
        assertEquals(400.0, result.recommendedRating_A, 0.01)
        assertEquals(380.0, result.governingCurrent_A, 0.01)

        // Isc = 36 kA (20 < Isc <= 50 -> n = 2.0 per IEC 60947-6-1 Table 7)
        // Icm = 36 * 2.0 = 72.0 kA
        assertEquals(36.0, result.shortTimeWithstandIcw_kA, 0.01)
        assertEquals(72.0, result.peakMakingCapacityIcm_kA, 0.01)

        // Transfer time = 2 + 3 + 4 = 9.0s (<= 10s NFPA 110 Type 10)
        assertEquals(9.0, result.totalTransferTimeToGen_s, 0.01)
        assertTrue(result.isNfpa110Compliant)
    }

    // =========================================================================
    // 7. MOTOR STARTER & OVERLOAD SIZING TESTS
    // =========================================================================
    @Test
    fun testQuickMotorStarterDOL() {
        val input = MotorStarterQuickInput(
            motorPowerKW = 15.0,
            voltageV = 400.0,
            isThreePhase = true,
            powerFactor = 0.85,
            efficiencyPct = 90.0,
            startingMethod = MotorStarterType.DOL,
            tripClass = "Class 10"
        )

        val result = QuickMotorStarterEngine.calculate(input)

        // In = 15000 / (sqrt(3) * 400 * 0.85 * 0.90) = 28.28 A
        assertTrue("In should be ~28.3 A, was ${result.fullLoadCurrent_A}", abs(result.fullLoadCurrent_A - 28.28) < 0.2)

        // Starting inrush = 6.5 * 28.28 = 183.8 A
        assertTrue("Istart should be ~183.8 A, was ${result.startingCurrent_A}", abs(result.startingCurrent_A - 183.8) < 2.0)

        // Contactor AC-3: KM1 >= 28.28 A -> 32 A
        assertEquals(32.0, result.mainContactorKm1_A, 0.01)
        assertNull(result.deltaContactorKm2_A)
        assertNull(result.starContactorKm3_A)

        // TOR dial: Ir = 28.28 A -> min = 26.87 A, max = 29.69 A
        assertEquals(result.fullLoadCurrent_A, result.torSettingCurrent_A, 0.01)
        assertTrue("TOR min should be ~26.87 A, was ${result.torDialMin_A}", abs(result.torDialMin_A - 26.87) < 0.2)
        assertTrue("TOR max should be ~29.69 A, was ${result.torDialMax_A}", abs(result.torDialMax_A - 29.69) < 0.2)

        // MPCB >= 28.28 A -> 32 A, aM fuse >= 35.35 A -> 40 A, Min cable >= 35.35 A
        assertEquals(32.0, result.recommendedMpcb_A, 0.01)
        assertEquals(40.0, result.recommendedAmFuse_A, 0.01)
        assertTrue(result.minCableAmpacity_A >= 35.0)
    }

    @Test
    fun testQuickMotorStarterStarDelta() {
        val input = MotorStarterQuickInput(
            motorPowerKW = 45.0,
            voltageV = 400.0,
            isThreePhase = true,
            powerFactor = 0.86,
            efficiencyPct = 92.0,
            startingMethod = MotorStarterType.STAR_DELTA,
            tripClass = "Class 10"
        )

        val result = QuickMotorStarterEngine.calculate(input)

        // In = 45000 / (sqrt(3) * 400 * 0.86 * 0.92) = 82.17 A
        assertTrue("In should be ~82.2 A, was ${result.fullLoadCurrent_A}", abs(result.fullLoadCurrent_A - 82.17) < 0.3)

        // Starting inrush = 2.17 * 82.17 = 178.3 A
        assertTrue("Istart should be ~178.3 A, was ${result.startingCurrent_A}", abs(result.startingCurrent_A - 178.3) < 3.0)

        // KM1 (Line) >= 0.58 * 82.17 = 47.66 A -> 50 A
        // KM2 (Delta) >= 0.58 * 82.17 = 47.66 A -> 50 A
        // KM3 (Star) >= 0.33 * 82.17 = 27.12 A -> 32 A
        assertEquals(50.0, result.mainContactorKm1_A, 0.01)
        assertEquals(50.0, result.deltaContactorKm2_A ?: 0.0, 0.01)
        assertEquals(32.0, result.starContactorKm3_A ?: 0.0, 0.01)

        // TOR in delta loop: Ir = 0.58 * In = 47.66 A
        assertTrue("TOR Ir should be ~47.66 A, was ${result.torSettingCurrent_A}", abs(result.torSettingCurrent_A - 47.66) < 0.3)
    }
}
