package com.example

import com.example.data.*
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PowerSystemsQaTest {

    @Test
    fun testGeneratorZeroLoad() {
        val settings = GeneratorEntity()
        val circuits = emptyList<CircuitEntity>()
        val result = GeneratorEngine.calculate(settings, circuits, 0.0)
        
        assertEquals("SELECT_LOADS", result.status)
        assertEquals(0.0, result.essentialLoadKVA, 0.0)
        assertNull(result.finalSize)
    }

    @Test
    fun testGeneratorSyntheticLoad() {
        val settings = GeneratorEntity(
            additionalEssentialLoadKW = 0.0,
            largestMotorKW = 45.0,
            motorStartMethod = "DOL",
            motorPf = 0.85,
            generatorPf = 0.8,
            demandFactor = 0.8,
            diversityFactor = 1.0,
            stepLoadPct = 50.0,
            oversizingFactor = 1.0
        )
        // 150 kVA essential load
        val circuits = listOf(
            CircuitEntity(circuitName = "C1", loadKVA = 150.0, generatorIncluded = true, assignedGeneratorId = "GEN-01", loadType = "Emergency", fedFrom = "Transformer", transformerId = "TX-01", phases = "3P", voltageV = 400, pf = 0.8, safetyFactor = 1.0, manufacturer = "ABB", cableType = "Cu/XLPE", installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 50.0, maxVDpct = 0.03)
        )
        
        val result = GeneratorEngine.calculate(settings, circuits, 500.0)
        
        // From manual calculation:
        assertEquals(150.0, result.essentialLoadKVA, 0.01)
        assertEquals(120.0, result.essentialLoadKVA_calc, 0.01)
        assertEquals(96.0, result.essentialLoadKW, 0.01)
        assertEquals(317.65, result.motorStartingKVA, 0.01)
        assertEquals(377.65, result.stepLoadAtStart, 0.01)
        // Excel R26 formula: required_kVA = MAX(essential, stepLoad) / GenPF (377.65 / 0.8 = 472.06 kVA)
        assertEquals(472.06, result.requiredGenerator, 0.01)
        assertEquals(472.06, result.designCapacity, 0.01)
        assertEquals(500.0, result.finalSize!!, 0.01)
    }

    @Test
    fun testAtsZeroLoad() {
        val settings = AtsEntity()
        val result = AtsEngine.calculate(settings, 0.0, 0.0, 0.0, 0.0, 0.0, listOf(CircuitEntity(circuitName = "C1", loadKVA = 150.0, generatorIncluded = true, loadType = "Emergency", fedFrom = "Transformer", transformerId = "TX-01", phases = "3P", voltageV = 400, pf = 0.8, safetyFactor = 1.0, manufacturer = "ABB", cableType = "Cu/XLPE", installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 50.0, maxVDpct = 0.03, atsIncluded = true)))
        // The circuit is flagged but NOT assigned to any ATS, so it is attributed to no
        // instance: the honest result is SELECT_LOADS with no rating (never fabricated).
        assertEquals("SELECT_LOADS", result.status)
        assertNull(result.finalRating)
    }

    @Test
    fun testAtsSyntheticLoad() {
        val settings = AtsEntity(
            loadCoverage = "Emergency Only",
            systemVoltage = 400,
            designSafetyFactor = 1.25,
            futureGrowthFactor = 10.0
        )
        // Inputs from generator test
        val result = AtsEngine.calculate(settings, totalConnectedLoad = 500.0, essentialLoadKVA = 150.0, generatorSelectedSize = 750.0, transformerFLC = 288.68, isc3PhaseAtTransformer = 7.58, circuits = listOf(CircuitEntity(circuitName = "C1", loadKVA = 150.0, generatorIncluded = true, assignedAtsId = "ATS-01", loadType = "Emergency", fedFrom = "Transformer", transformerId = "TX-01", phases = "3P", voltageV = 400, pf = 0.8, safetyFactor = 1.0, manufacturer = "ABB", cableType = "Cu/XLPE", installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 50.0, maxVDpct = 0.03, atsIncluded = true)))
        
        assertEquals(150.0, result.selectedLoad, 0.01)
        assertEquals(216.51, result.loadCurrent, 0.01)
        assertEquals(297.70, result.designCurrent, 0.01)
        assertEquals(1082.53, result.generatorFLC, 0.01)
        assertEquals(1082.53, result.governingCurrent, 0.01)
        // Excel ATS frame selection rule: next standard ATS frame >= governingCurrent (1082.53 A -> 1250 A)
        assertEquals(1250.0, result.finalRating!!, 0.01)
        assertEquals(7.58, result.requiredIcw, 0.01)
        // Excel ATS peak factor rule: Icm = Icw * 2.2 (7.58 * 2.2 = 16.676 kA)
        assertEquals(16.676, result.requiredIcm, 0.01)
    }

    @Test
    fun testUpsZeroLoad() {
        val settings = UpsEntity()
        val circuits = emptyList<CircuitEntity>()
        val result = UpsEngine.calculate(settings, circuits)
        assertEquals("SELECT_LOADS", result.status)
        assertNull(result.recommendedSize)
    }

    @Test
    fun testUpsSyntheticLoad() {
        val settings = UpsEntity(
            systemVoltage = 400,
            upsPf = 0.9,
            upsEfficiency = 94.0,
            autonomyMinutes = 15,
            tempDeratingFactor = 1.0,
            agingFactor = 1.25,
            designMarginPct = 20.0
        )
        val circuits = listOf(
            CircuitEntity(circuitName = "C1", loadKVA = 50.0, upsBacked = true, assignedUpsId = "UPS-01", loadType = "Normal", fedFrom = "Transformer", transformerId = "TX-01", phases = "3P", voltageV = 400, pf = 0.8, safetyFactor = 1.0, manufacturer = "ABB", cableType = "Cu/XLPE", installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 50.0, maxVDpct = 0.03)
        )
        
        val result = UpsEngine.calculate(settings, circuits)
        
        assertEquals(50.0, result.totalCriticalLoadKVA, 0.01)
        assertEquals(40.0, result.totalCriticalLoadKW, 0.01)
        assertEquals(50.0, result.upsApparentPowerReq, 0.01)
        assertEquals(60.0, result.designUpsKVA, 0.01)
        assertEquals(57.45, result.batteryLoadKW!!, 0.01)
        assertEquals(14.36, result.batteryEnergyReq!!, 0.01)
        assertEquals(17.95, result.correctedBatteryCapacity!!, 0.01)
        assertEquals(44.88, result.batteryAh!!, 0.01)
        assertEquals(80.0, result.recommendedSize!!, 0.01)
    }

    @Test
    fun testPfCorrection() {
        val globalSettings = PfGlobalEntity(
            targetPf = 0.97,
            kvarStepSize = 50.0,
            unityToleranceKvar = 0.5
        )
        val transformers = listOf(
            TransformerEntity(transformerId = "TX-01", transformerName = "TX-01", status = "Active", kVA = 250.0, voltage = 400, percentZ = 4.0, xr = 10.0, txType = "Oil", sourceType = "MV", sizeMode = "Auto", manualKVA = 250.0, demandFactor = 1.0, diversity = 1.0, growth = 1.0, safety = 1.0, pf = 0.8)
        )
        val txSettings = listOf(
            PfTransformerEntity(transformerId = "TX-01", harmonicFilter = "Not Provided", detuningReactor = "Not Provided")
        )
        val txResults = mapOf(
            "TX-01" to TransformerResult(
                connectedLoad = 211.4,
                circuitsAssigned = 1,
                realPower = 169.12,
                demandLoad = 169.12, // Unit Audit Corrected: demandLoad is already in kW
                diversifiedLoad = 211.4,
                futureLoad = 211.4,
                designLoad = 211.4,
                requiredKVA = 211.4,
                recommendedSize = 250.0,
                selectedSize = 250.0,
                loadingPct = 84.56,
                flc = 360.84,
                zt = 0.04,
                rt = 0.01,
                xt = 0.038,
                isc3Phase = 9.02,
                isc1Phase = 9.02,
                status = "OK",
                z0 = 0.04,
                vectorGroup = "Dyn11"
            )
        )
        
        val results = PfCorrectionEngine.calculate(globalSettings, txSettings, transformers, txResults)
        val r = results[0]
        
        // kW = 169.12 kW directly
        assertEquals(169.12, r.existingKW, 0.01)
        assertEquals(126.84, r.existingKvar, 0.01)
        assertEquals(42.38, r.targetKvar, 0.01)
        assertEquals(84.45, r.requiredKvar, 0.01)
        assertEquals(2.0, r.bankSteps, 0.0)
        assertEquals(100.0, r.bankCapacity, 0.0)
        assertEquals(26.84, r.correctedReactive, 0.01)
        assertEquals(0.9876, r.correctedPF, 0.0001)
        assertEquals("REVIEW — HARMONIC/DETUNING INPUTS", r.status)
    }

    @Test
    fun testPfCorrectionDirectHandCalculated() {
        // Scenario A: Normal correction (activePowerKW = 100.0, existingPF = 0.8, targetPF = 0.95, stepSize = 25.0)
        val globalSettingsA = PfGlobalEntity(
            targetPf = 0.95,
            kvarStepSize = 25.0,
            unityToleranceKvar = 0.5,
            systemVoltage = 400
        )
        val transformersA = listOf(
            TransformerEntity(
                projectId = "DEFAULT_PROJECT",
                transformerId = "TX-A",
                transformerName = "TX-A",
                kVA = 160.0,
                voltage = 400,
                percentZ = 4.0,
                xr = 5.0,
                txType = "Dry",
                sourceType = "MV",
                status = "Active",
                sizeMode = "Auto",
                manualKVA = 160.0,
                demandFactor = 1.0,
                diversity = 1.0,
                growth = 1.0,
                safety = 1.0,
                pf = 0.8
            )
        )
        val txSettingsA = listOf(
            PfTransformerEntity(transformerId = "TX-A", harmonicFilter = "Yes", detuningReactor = "Yes")
        )
        val txResultsA = mapOf(
            "TX-A" to TransformerResult(
                connectedLoad = 125.0,
                circuitsAssigned = 1,
                realPower = 100.0,
                demandLoad = 100.0, // kW
                diversifiedLoad = 100.0,
                futureLoad = 100.0,
                designLoad = 100.0,
                requiredKVA = 125.0,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 78.1,
                flc = 230.9,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val resultsA = PfCorrectionEngine.calculate(globalSettingsA, txSettingsA, transformersA, txResultsA)
        val rA = resultsA[0]

        assertEquals(100.0, rA.existingKW, 0.01)
        assertEquals(0.8, rA.existingPF, 0.01)
        assertEquals(0.95, rA.targetPf, 0.01)
        assertEquals(75.0, rA.existingKvar, 0.01)
        assertEquals(32.868, rA.targetKvar, 0.01)
        assertEquals(42.132, rA.requiredKvar, 0.01) // exactRequiredKvar
        assertEquals(2.0, rA.bankSteps, 0.01)
        assertEquals(50.0, rA.bankCapacity, 0.01) // selectedBankKvar
        assertEquals(25.0, rA.correctedReactive, 0.01)
        assertEquals(0.9701, rA.correctedPF, 0.001)
        assertEquals("COMPLIANT", rA.status)

        // Scenario B: Target PF <= Existing PF (targetPF = 0.8, existingPF = 0.9, no correction required)
        val globalSettingsB = PfGlobalEntity(
            targetPf = 0.80,
            kvarStepSize = 25.0,
            unityToleranceKvar = 0.5
        )
        val transformersB = listOf(
            TransformerEntity(
                projectId = "DEFAULT_PROJECT",
                transformerId = "TX-B",
                transformerName = "TX-B",
                kVA = 160.0,
                voltage = 400,
                percentZ = 4.0,
                xr = 5.0,
                txType = "Dry",
                sourceType = "MV",
                status = "Active",
                sizeMode = "Auto",
                manualKVA = 160.0,
                demandFactor = 1.0,
                diversity = 1.0,
                growth = 1.0,
                safety = 1.0,
                pf = 0.90
            )
        )
        val txResultsB = mapOf(
            "TX-B" to TransformerResult(
                connectedLoad = 111.1,
                circuitsAssigned = 1,
                realPower = 100.0,
                demandLoad = 100.0,
                diversifiedLoad = 100.0,
                futureLoad = 100.0,
                designLoad = 100.0,
                requiredKVA = 111.1,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 69.4,
                flc = 230.9,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val resultsB = PfCorrectionEngine.calculate(globalSettingsB, emptyList(), transformersB, txResultsB)
        val rB = resultsB[0]
        assertEquals(0.0, rB.requiredKvar, 0.01)
        assertEquals(0.0, rB.bankCapacity, 0.01)
        assertEquals("NO CORRECTION REQUIRED", rB.status)

        // Scenario C: Invalid inputs (negative power)
        val txResultsC = mapOf(
            "TX-B" to TransformerResult(
                connectedLoad = -50.0,
                circuitsAssigned = 1,
                realPower = -50.0,
                demandLoad = -50.0,
                diversifiedLoad = -50.0,
                futureLoad = -50.0,
                designLoad = -50.0,
                requiredKVA = -50.0,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 0.0,
                flc = 0.0,
                zt = 0.0,
                rt = 0.0,
                xt = 0.0,
                isc3Phase = 0.0,
                isc1Phase = 0.0,
                status = "FAIL",
                z0 = 0.0,
                vectorGroup = "Dyn11"
            )
        )
        val resultsC = PfCorrectionEngine.calculate(globalSettingsB, emptyList(), transformersB, txResultsC)
        assertEquals("INVALID INPUT", resultsC[0].status)
    }

    @Test
    fun testIecBenchmark_GeneratorSizing38kVA() {
        // Benchmark: Essential load 38.00 kVA -> S_min = 38 / 0.80 = 47.50 kVA
        // Recommended size must be 50.0 kVA with operating loading = 76.0% (Optimal load)
        val settings = GeneratorEntity(
            generatorPf = 0.8,
            demandFactor = 1.0,
            diversityFactor = 1.0,
            stepLoadPct = 50.0,
            oversizingFactor = 1.0,
            largestMotorKW = 0.0
        )
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "ESSENTIAL", loadKVA = 38.00, generatorIncluded = true, assignedGeneratorId = "GEN-01")
        )
        val result = GeneratorEngine.calculate(settings, circuits, 87.90)

        assertEquals("essentialLoadKVA", 38.00, result.essentialLoadKVA, 0.01)
        assertEquals("requiredGenerator S_min", 47.50, result.requiredGenerator, 0.01)
        assertEquals("finalSize", 50.0, result.finalSize ?: 0.0, 0.01)
        assertEquals("loadingPct", 76.0, result.loadingPct ?: 0.0, 0.1)
        assertEquals("Optimal load", result.status)
    }

    @Test
    fun testIecBenchmark_PfCorrection56kW() {
        // Benchmark: Active load 56.26 kW, cosφ1=0.80, target cosφ2=0.97, step=10 kVAR
        // Q_initial = 56.26 * tan(acos 0.80) = 42.20 kVAR
        // Q_target  = 56.26 * tan(acos 0.97) = 14.10 kVAR
        // Q_req = 42.20 - 14.10 = 28.09 kVAR -> 3 steps of 10 = 30.0 kVAR bank
        // Q_net = 42.20 - 30.0 = +12.20 kVAR > 0 (Lagging)
        // PF_corr = 56.26 / sqrt(56.26^2 + 12.20^2) = 0.9773
        val globalSettings = PfGlobalEntity(targetPf = 0.97, kvarStepSize = 10.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-01", pf = 0.80, status = "Active"))
        val txResults = mapOf(
            "TX-01" to TransformerResult(
                connectedLoad = 70.0,
                circuitsAssigned = 1,
                realPower = 56.26,
                demandLoad = 56.26,
                diversifiedLoad = 56.26,
                futureLoad = 56.26,
                designLoad = 56.26,
                requiredKVA = 70.325,
                recommendedSize = 100.0,
                selectedSize = 100.0,
                loadingPct = 56.0,
                flc = 144.3,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val txSettings = listOf(PfTransformerEntity(transformerId = "TX-01", harmonicFilter = "Yes", detuningReactor = "No"))
        val results = PfCorrectionEngine.calculate(globalSettings, txSettings, transformers, txResults)
        assertEquals(1, results.size)
        val res = results[0]

        assertEquals(56.26, res.existingKW, 0.01)
        assertEquals(42.20, res.existingKvar, 0.05)
        assertEquals(28.09, res.requiredKvar, 0.05)
        assertEquals(30.0, res.bankCapacity, 0.01)
        assertEquals(12.20, res.correctedReactive, 0.05)
        assertEquals(0.9773, res.correctedPF, 0.001)
        assertEquals("LAGGING", res.pfState)
        assertEquals("COMPLIANT", res.status)
    }

    @Test
    fun testIecBenchmark_UpsSizing6k4VA() {
        // Benchmark: Critical load 6.40 kVA, designMargin 20% -> design = 7.68 kVA
        // d = 7.68 / 0.8 = 9.60 kVA -> recommended = 10.0 kVA
        // operating loading = 6.40 / 10.0 = 64.0% (Optimal)
        val settings = UpsEntity(
            upsPf = 0.8,
            designMarginPct = 20.0,
            systemVoltage = 400
        )
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "CRITICAL", loadKVA = 6.40, upsBacked = true, assignedUpsId = "UPS-01", pf = 0.8)
        )
        val result = UpsEngine.calculate(settings, circuits)

        assertEquals(6.40, result.totalCriticalLoadKVA, 0.01)
        assertEquals(7.68, result.designUpsKVA, 0.01)
        assertEquals(10.0, result.recommendedSize ?: 0.0, 0.01)
        assertEquals(64.0, result.loadingPct ?: 0.0, 0.1)
        assertTrue("Status optimal", result.status.contains("Optimal"))
    }
}
