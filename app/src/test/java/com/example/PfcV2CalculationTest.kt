package com.example

import com.example.engine.converters.CapacitorConnectionType
import com.example.engine.converters.ElectricalConverters
import com.example.ui.screens.quick.*
import org.junit.Assert.*
import org.junit.Test

class PfcV2CalculationTest {

    // =========================================================================
    // 1. ABSOLUTE ENGINEERING REFERENCE TESTS (FIRST PRINCIPLES)
    // =========================================================================

    @Test
    fun totalBankKvar_3PhaseDelta_400V_50Hz_returnsCorrectPerBranchUf() {
        // Reference Case:
        // Total Bank Qc = 42.13182 kVAR (from 100 kW, PF 0.80 -> 0.95)
        // 3-Phase 400V Line-to-Line @ 50 Hz
        // Delta Bank: 3 branches, each sees 400V
        // C_branch = (42.13182 * 1e9) / (3 * 2π * 50 * 400²) = 279.395 µF / branch
        val totalQc = 42.13182
        val voltage = 400.0
        val freq = 50.0

        val ufPerBranch = ElectricalConverters.kvarToMicroFarad(
            kvar = totalQc,
            voltageV = voltage,
            freqHz = freq,
            connection = CapacitorConnectionType.DELTA
        )

        assertEquals(279.395, ufPerBranch, 0.05)

        // Via Semantic API
        val semanticUf = ElectricalConverters.totalBankKvarToPerBranchMicroFarad(totalQc, voltage, freq, CapacitorConnectionType.DELTA)
        assertEquals(279.395, semanticUf, 0.05)
    }

    @Test
    fun totalBankKvar_3PhaseStar_400V_50Hz_returnsCorrectPerBranchUf() {
        // Reference Case:
        // Total Bank Qc = 42.13182 kVAR
        // 3-Phase 400V Line-to-Line @ 50 Hz
        // Star Bank: 3 branches, each sees 400/√3 V
        // C_phase = (42.13182 * 1e9) / (2π * 50 * 400²) = 838.185 µF / phase
        val totalQc = 42.13182
        val voltage = 400.0
        val freq = 50.0

        val ufPerPhase = ElectricalConverters.kvarToMicroFarad(
            kvar = totalQc,
            voltageV = voltage,
            freqHz = freq,
            connection = CapacitorConnectionType.STAR
        )

        assertEquals(838.185, ufPerPhase, 0.05)

        // Via Semantic API
        val semanticUf = ElectricalConverters.totalBankKvarToPerBranchMicroFarad(totalQc, voltage, freq, CapacitorConnectionType.STAR)
        assertEquals(838.185, semanticUf, 0.05)
    }

    @Test
    fun deltaVsStar_branchCapacitanceRatio_isExactlyThree() {
        val totalQc = 42.13182
        val voltage = 400.0
        val freq = 50.0

        val cDelta = ElectricalConverters.kvarToMicroFarad(totalQc, voltage, freq, CapacitorConnectionType.DELTA)
        val cStar = ElectricalConverters.kvarToMicroFarad(totalQc, voltage, freq, CapacitorConnectionType.STAR)

        // C_Star must be exactly 3 * C_Delta
        assertEquals(3.0 * cDelta, cStar, 1e-4)
    }

    @Test
    fun higherFrequency_requiresLowerCapacitance() {
        val totalQc = 42.13182
        val voltage = 400.0

        val c50 = ElectricalConverters.kvarToMicroFarad(totalQc, voltage, 50.0, CapacitorConnectionType.DELTA)
        val c60 = ElectricalConverters.kvarToMicroFarad(totalQc, voltage, 60.0, CapacitorConnectionType.DELTA)

        // For the same kVAR rating, 60Hz requires proportionally less capacitance: C_60 = (50/60) * C_50
        val expected60 = (50.0 / 60.0) * c50
        assertEquals(expected60, c60, 1e-4)
        assertTrue("60 Hz capacitance must be lower than 50 Hz capacitance", c60 < c50)
    }

    @Test
    fun microFaradToTotalBankKvar_isPhysicalInverse() {
        val originalTotalKvar = 75.0
        val voltage = 400.0
        val freq = 50.0

        // Delta roundtrip
        val deltaUf = ElectricalConverters.kvarToMicroFarad(originalTotalKvar, voltage, freq, CapacitorConnectionType.DELTA)
        val deltaKvarBack = ElectricalConverters.microFaradToKvar(deltaUf, voltage, freq, CapacitorConnectionType.DELTA)
        assertEquals(originalTotalKvar, deltaKvarBack, 1e-4)

        // Star roundtrip
        val starUf = ElectricalConverters.kvarToMicroFarad(originalTotalKvar, voltage, freq, CapacitorConnectionType.STAR)
        val starKvarBack = ElectricalConverters.microFaradToKvar(starUf, voltage, freq, CapacitorConnectionType.STAR)
        assertEquals(originalTotalKvar, starKvarBack, 1e-4)
    }

    @Test
    fun onePhaseCapacitance_isCorrect() {
        // Single Phase 4.21318 kVAR @ 230 V, 50 Hz
        // C_1P = (4.21318 * 1e9) / (2π * 50 * 230²) = 253.515 µF
        val totalQc = 4.21318
        val voltage = 230.0
        val freq = 50.0

        val uf1P = ElectricalConverters.kvarToMicroFarad(totalQc, voltage, freq, CapacitorConnectionType.SINGLE_OR_STAR)
        assertEquals(253.515, uf1P, 0.05)

        val kvarBack = ElectricalConverters.microFaradToKvar(uf1P, voltage, freq, CapacitorConnectionType.SINGLE_OR_STAR)
        assertEquals(totalQc, kvarBack, 1e-4)
    }

    @Test
    fun invalidPhysicalInputs_rejectedByAuthority() {
        // Zero or negative voltage
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(42.0, 0.0, 50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(42.0, -400.0, 50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.microFaradToKvar(200.0, 0.0, 50.0), 1e-6)

        // Zero or negative frequency
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(42.0, 400.0, 0.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(42.0, 400.0, -50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.microFaradToKvar(200.0, 400.0, 0.0), 1e-6)

        // Zero or negative kVAR / uF
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(0.0, 400.0, 50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.kvarToMicroFarad(-42.0, 400.0, 50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.microFaradToKvar(0.0, 400.0, 50.0), 1e-6)
        assertEquals(0.0, ElectricalConverters.microFaradToKvar(-200.0, 400.0, 50.0), 1e-6)
    }

    // =========================================================================
    // 2. PFC V2 END-TO-END DOMAIN ENGINE INTEGRATION TESTS
    // =========================================================================

    @Test
    fun test3Phase_ActivePowerMode_ExactQcAndDeltaI() {
        val input = PfcInputState(
            inputMode = PfcInputMode.ACTIVE_POWER_KW,
            phase = PfcSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            powerKwText = "100.0",
            initialPfText = "0.80",
            targetPfText = "0.95",
            frequency = PfcFrequency.FREQ_50HZ,
            connection = PfcCapacitorConnection.DELTA
        )

        val result = PfcCalculationEngine.calculate(input)
        assertNotNull("3P active power calculation must succeed", result)

        // Math Verification:
        // P = 100 kW, S1 = 125 kVA, Q1 = 75 kVAR
        // Target cosphi2 = 0.95 -> S2 = 100 / 0.95 = 105.263 kVA
        // Q2 = 100 * tan(acos(0.95)) = 32.868 kVAR
        // Qc = 75.0 - 32.868 = 42.132 kVAR
        assertEquals(100.0, result!!.activePowerKw, 0.001)
        assertEquals(125.0, result.initialApparentKva, 0.01)
        assertEquals(105.263, result.targetApparentKva, 0.01)
        assertEquals(75.0, result.initialReactiveKvar, 0.01)
        assertEquals(32.868, result.targetReactiveKvar, 0.01)
        assertEquals(42.132, result.requiredQcKvar, 0.01)

        // Line currents:
        // I1 = 125000 / (sqrt(3) * 400) = 180.422 A
        // I2 = 105263.16 / (sqrt(3) * 400) = 151.934 A
        // delta I = 28.488 A (15.79%)
        assertEquals(180.422, result.initialCurrentA, 0.01)
        assertEquals(151.934, result.targetCurrentA, 0.01)
        assertEquals(28.488, result.currentReductionA, 0.01)
        assertEquals(15.79, result.currentReductionPct, 0.05)

        // Verified Physics Capacitance: Delta 400V 50Hz
        // C_delta = (42.1318 * 1e9) / (3 * 2 * pi * 50 * 400^2) = 279.40 uF per branch
        assertEquals(279.40, result.capacitanceMicroFarad, 0.1)
    }

    @Test
    fun test1Phase_ActivePowerMode_UsesSinglePhaseCurrentEquation() {
        val input = PfcInputState(
            inputMode = PfcInputMode.ACTIVE_POWER_KW,
            phase = PfcSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            powerKwText = "10.0",
            initialPfText = "0.80",
            targetPfText = "0.95",
            frequency = PfcFrequency.FREQ_50HZ
        )

        val result = PfcCalculationEngine.calculate(input)
        assertNotNull("1P calculation must succeed", result)
        assertFalse("isThreePhase must be false", result!!.isThreePhase)

        // P = 10 kW, S1 = 12.5 kVA, S2 = 10.526 kVA
        // Qc = 10 * (tan(acos(0.8)) - tan(acos(0.95))) = 4.213 kVAR
        assertEquals(4.213, result.requiredQcKvar, 0.01)

        // 1-Phase Current: I1 = S1 * 1000 / 230 = 12500 / 230 = 54.348 A
        // I2 = 10526.32 / 230 = 45.767 A
        assertEquals(54.348, result.initialCurrentA, 0.01)
        assertEquals(45.767, result.targetCurrentA, 0.01)
        assertEquals(8.581, result.currentReductionA, 0.01)

        // 1-Phase Capacitance: C_1P = (4.2132 * 1e9) / (2 * pi * 50 * 230^2) = 253.52 uF
        assertEquals(253.52, result.capacitanceMicroFarad, 0.1)
    }

    @Test
    fun test3Phase_ApparentPowerMode_DerivesActivePowerAuthoritatively() {
        val input = PfcInputState(
            inputMode = PfcInputMode.APPARENT_POWER_KVA,
            phase = PfcSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            powerKvaText = "125.0",
            initialPfText = "0.80",
            targetPfText = "0.95"
        )

        val result = PfcCalculationEngine.calculate(input)
        assertNotNull("kVA mode must succeed", result)

        // Derives P = 125 * 0.8 = 100 kW
        assertEquals(100.0, result!!.activePowerKw, 0.001)
        assertEquals(125.0, result.initialApparentKva, 0.001)
        assertEquals(42.132, result.requiredQcKvar, 0.01)
        assertEquals(180.422, result.initialCurrentA, 0.01)
    }

    @Test
    fun test3Phase_CurrentMode_DerivesKvaAndKwAuthoritatively() {
        val input = PfcInputState(
            inputMode = PfcInputMode.CURRENT_AMPS,
            phase = PfcSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            currentAText = "180.422",
            initialPfText = "0.80",
            targetPfText = "0.95"
        )

        val result = PfcCalculationEngine.calculate(input)
        assertNotNull("Current mode must succeed", result)

        // Derives S1 = sqrt(3) * 400 * 180.422 / 1000 ≈ 125.0 kVA
        // Derives P = 125 * 0.8 = 100 kW
        assertEquals(125.0, result!!.initialApparentKva, 0.05)
        assertEquals(100.0, result.activePowerKw, 0.05)
        assertEquals(42.132, result.requiredQcKvar, 0.05)
    }

    @Test
    fun testPfcV2UsesSameAuthoritativeCapacitanceAuthority() {
        val pKw = 250.0
        val cos1 = 0.75
        val cos2 = 0.95
        val volt = 400.0
        val freq = 50.0

        val input = PfcInputState(
            powerKwText = pKw.toString(),
            initialPfText = cos1.toString(),
            targetPfText = cos2.toString(),
            voltageV = volt,
            frequency = PfcFrequency.FREQ_50HZ,
            connection = PfcCapacitorConnection.DELTA
        )

        val result = PfcCalculationEngine.calculate(input)!!
        val directPfc = ElectricalConverters.powerFactorCorrection(pKw, cos1, cos2, volt)
        val directUf = ElectricalConverters.kvarToMicroFarad(directPfc.qcKvar, volt, freq, CapacitorConnectionType.DELTA)
        val semanticUf = ElectricalConverters.totalBankKvarToPerBranchMicroFarad(directPfc.qcKvar, volt, freq, CapacitorConnectionType.DELTA)

        assertEquals("Qc must match ElectricalConverters.powerFactorCorrection", directPfc.qcKvar, result.requiredQcKvar, 0.0001)
        assertEquals("deltaAmps must match ElectricalConverters.powerFactorCorrection", directPfc.deltaAmps, result.currentReductionA, 0.0001)
        assertEquals("uf must match ElectricalConverters.kvarToMicroFarad", directUf, result.capacitanceMicroFarad, 0.0001)
        assertEquals("uf must match semantic API", semanticUf, result.capacitanceMicroFarad, 0.0001)
    }

    @Test
    fun testValidation_TargetPfLessOrEqualToInitialPf_ReturnsNull() {
        val equalPfInput = PfcInputState(initialPfText = "0.85", targetPfText = "0.85")
        assertNull("Equal PF must be rejected", PfcCalculationEngine.calculate(equalPfInput))

        val lowerPfInput = PfcInputState(initialPfText = "0.90", targetPfText = "0.80")
        assertNull("Lower target PF must be rejected", PfcCalculationEngine.calculate(lowerPfInput))
    }

    @Test
    fun testValidation_InvalidPfBoundaries_ReturnsNull() {
        assertNull("Zero initial PF must return null", PfcCalculationEngine.calculate(PfcInputState(initialPfText = "0.0")))
        assertNull("Negative initial PF must return null", PfcCalculationEngine.calculate(PfcInputState(initialPfText = "-0.8")))
        assertNull("Initial PF > 1.0 must return null", PfcCalculationEngine.calculate(PfcInputState(initialPfText = "1.05")))

        assertNull("Zero target PF must return null", PfcCalculationEngine.calculate(PfcInputState(targetPfText = "0.0")))
        assertNull("Target PF > 1.0 must return null", PfcCalculationEngine.calculate(PfcInputState(targetPfText = "1.05")))
    }

    @Test
    fun testValidation_ZeroOrNegativePower_ReturnsNull() {
        assertNull("Zero kW must return null", PfcCalculationEngine.calculate(PfcInputState(inputMode = PfcInputMode.ACTIVE_POWER_KW, powerKwText = "0.0")))
        assertNull("Negative kW must return null", PfcCalculationEngine.calculate(PfcInputState(inputMode = PfcInputMode.ACTIVE_POWER_KW, powerKwText = "-50.0")))
        assertNull("Zero kVA must return null", PfcCalculationEngine.calculate(PfcInputState(inputMode = PfcInputMode.APPARENT_POWER_KVA, powerKvaText = "0.0")))
        assertNull("Zero Amps must return null", PfcCalculationEngine.calculate(PfcInputState(inputMode = PfcInputMode.CURRENT_AMPS, currentAText = "0.0")))
    }

    @Test
    fun testValidation_ZeroOrNegativeVoltage_ReturnsNull() {
        assertNull("Zero voltage must return null", PfcCalculationEngine.calculate(PfcInputState(voltageV = 0.0)))
        assertNull("Negative voltage must return null", PfcCalculationEngine.calculate(PfcInputState(voltageV = -400.0)))
    }

    @Test
    fun testStepByStepSubstitutions_ContainsAllFiveSteps() {
        val input = PfcInputState(
            powerKwText = "100.0",
            initialPfText = "0.80",
            targetPfText = "0.95"
        )
        val result = PfcCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)
        assertEquals("Must generate 5 structured calculation steps", 5, result!!.steps.size)

        assertTrue("Step 1 must explain active power", result.steps[0].title.contains("Active Power"))
        assertTrue("Step 2 must explain reactive powers", result.steps[1].title.contains("Reactive Powers"))
        assertTrue("Step 3 must explain Qc", result.steps[2].title.contains("Capacitive Compensation"))
        assertTrue("Step 4 must explain current reduction", result.steps[3].title.contains("Line Current Relief"))
        assertTrue("Step 5 must explain capacitance", result.steps[4].title.contains("Capacitance"))
    }
}
