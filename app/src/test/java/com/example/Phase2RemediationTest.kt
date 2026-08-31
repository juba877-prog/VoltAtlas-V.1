package com.example

import com.example.data.CircuitEntity
import com.example.engine.GeneratorResult
import com.example.engine.ProtectionEngine
import com.example.engine.ShortCircuitEngine
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.TransformerResult
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 2 (P1) remediation regression tests:
 *  - CRITICAL-4: short-circuit source polymorphism (generator islanded path, no transformer in Z path)
 *  - CRITICAL-5: IEC 60947-2 peak making capacity check (Icm >= ip)
 *  - H6: Ze derived from the feeding transformer instead of hardcoded 0.01 Ω
 */
class Phase2RemediationTest {

    private fun tx(isc3: Double): TransformerResult = TransformerResult(
        connectedLoad = 500.0, circuitsAssigned = 1, realPower = 400.0, demandLoad = 400.0,
        diversifiedLoad = 400.0, futureLoad = 400.0, designLoad = 400.0, requiredKVA = 500.0,
        recommendedSize = 500.0, selectedSize = 500.0, loadingPct = 80.0, flc = 721.7,
        zt = 16.0, rt = 1.59, xt = 15.92, isc3Phase = isc3, isc1Phase = 13.13,
        status = "VERIFIED", z0 = 15.2, vectorGroup = "Dyn11", voltage = 400.0
    )

    private fun sizing(inA: Double = 630.0, icu: Double = 50.0, icm: Double = 0.0, r: Double = 0.076, x: Double = 0.073) = SizingResult(
        Ib_A = 577.0, deviceSize_A = inA, deviceType = "MCCB", cTotalDerating = 1.0,
        iRequired_perCable_A = 577.0, productCode = "P", cableSize_mm2 = "300 mm²",
        ratedI_table_A = 629.0, capacity_derated_Iz_A = 629.0, utilizationPct = 91.7,
        neutral_mm2 = "150 mm²", earthCPC_mm2 = "150 mm²", voltDrop_V = 0.5, vdPct = 0.1,
        cumulativeVDpct = 0.1, status = "✓ PASS", failReason = "", cableMaterial = "Cu",
        cableOD_mm = 62.1, deviceBreakingKA = icu, deviceMakingKA = icm, kFactor = 143.0,
        clearingTime_s = 0.1, rUsed = r, xUsed = x,
        resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true,
        primaryFailureCode = null
    )

    // ------------------------------------------------------------------
    // CRITICAL-4 — generator-fed (islanded) circuit
    // ------------------------------------------------------------------

    @Test
    fun testGeneratorFedCircuit_usesSubtransientImpedance_withoutTransformer() {
        // 250 kVA gen, x″d = 12%: Z_G = 0.12·400²/250000 = 0.0768 Ω
        // X_G = Z/√(1+0.15²) = 0.07595 Ω, R_G = 0.15·X = 0.01139 Ω
        // I″kG = 1.05·400/(√3·0.0768) ≈ 3.16 kA  (transformer ABSENT from the path)
        val circuit = CircuitEntity(
            id = 10, circuitName = "EMERGENCY-DB", loadKVA = 100.0,
            fedFrom = "Generator", transformerId = "", assignedGeneratorId = "GEN-01", length_m = 0.1
        )
        val sizings = mapOf(10 to sizing(inA = 250.0, icu = 36.0))

        val res = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = emptyMap(),
            sizingResults = sizings,
            transformerResults = emptyMap(),                      // no transformer data at all
            generatorResults = mapOf("GEN-01" to GeneratorResult(status = "Optimal load", finalSize = 250.0))
        )[10]!!

        assertEquals("Generator-fed circuit must return a valid I″k (no DATA MISSING)", "PASS", res.status)
        assertEquals("GEN-01", res.sourceName)
        assertEquals(3.16, res.iscKA, 0.05)
        assertTrue("Assumed x″d must be flagged", res.note.contains("ASSUMED x″d"))
        assertTrue("K_G assumption must be flagged", res.note.contains("K_G"))
    }

    @Test
    fun testGeneratorFedCircuit_withoutGeneratorData_returnsDataMissing() {
        val circuit = CircuitEntity(
            id = 11, circuitName = "EMERGENCY-DB", loadKVA = 100.0,
            fedFrom = "Generator", transformerId = "", length_m = 0.1
        )
        val res = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = emptyMap(),
            sizingResults = mapOf(11 to sizing(inA = 250.0)),
            transformerResults = emptyMap(),
            generatorResults = emptyMap()
        )[11]!!

        assertEquals("DATA MISSING", res.status)
        assertTrue(res.note.contains("Generator"))
    }

    @Test
    fun testAtsEmergencySelection_routesToGenerator() {
        val genCircuit = CircuitEntity(
            id = 12, circuitName = "ATS-EMERGENCY-DB", loadKVA = 100.0,
            fedFrom = "ATS", transformerId = "TX-01", assignedAtsId = "ATS-01",
            assignedGeneratorId = "GEN-01", length_m = 0.1
        )
        val sizings = mapOf(12 to sizing(inA = 250.0, icu = 36.0))

        val normalRes = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(genCircuit), circuitParents = emptyMap(),
            sizingResults = sizings, transformerResults = mapOf("TX-01" to tx(15.16))
        )[12]!!
        assertEquals("Default ATS selection = NORMAL source (transformer)", "TX-01", normalRes.sourceName)

        val emergencyRes = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(genCircuit), circuitParents = emptyMap(),
            sizingResults = sizings, transformerResults = mapOf("TX-01" to tx(15.16)),
            generatorResults = mapOf("GEN-01" to GeneratorResult(finalSize = 250.0)),
            atsSelectedSource = mapOf("ATS-01" to "EMERGENCY")
        )[12]!!
        assertEquals("EMERGENCY selection routes to the generator", "GEN-01", emergencyRes.sourceName)
    }

    // ------------------------------------------------------------------
    // CRITICAL-4 — LV motor-group contribution (IEC 60909 §6.10, toggle default OFF)
    // ------------------------------------------------------------------

    @Test
    fun testMotorContribution_toggleOff_byDefault() {
        val circuit = CircuitEntity(id = 13, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val sizings = mapOf(13 to sizing())

        val off = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), sizings, mapOf("TX-01" to tx(15.16)),
            motorContributionEnabled = false, motorGroupKVA = 100.0
        )[13]!!
        assertEquals(15.16, off.iscKA, 0.05)
    }

    @Test
    fun testMotorContribution_toggleOn_superimposesContribution() {
        // 100 kVA motor group, I_LR/I_rM = 5.5:
        // I_rM = 100000/(√3·400) = 144.3 A; Z_M = (1/5.5)·(400/(√3·144.3)) = 0.2908 Ω
        // I″kM = 1.05·400/(√3·0.2908) ≈ 0.83 kA -> total ≈ 15.16 + 0.83 = 15.99 kA
        val circuit = CircuitEntity(id = 14, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val sizings = mapOf(14 to sizing())

        val on = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), sizings, mapOf("TX-01" to tx(15.16)),
            motorContributionEnabled = true, motorGroupKVA = 100.0
        )[14]!!
        assertEquals(15.99, on.iscKA, 0.06)
        assertTrue("Motor contribution must be flagged when active", on.note.contains("Motor-group contribution ENABLED"))
    }

    // ------------------------------------------------------------------
    // CRITICAL-5 — IEC 60947-2 making capacity check (Icm >= ip)
    // ------------------------------------------------------------------

    @Test
    fun testMakingCapacity_catalogIcmSufficient_passes() {
        // ip ≈ √2·κ·15.16; κ ≈ 1.0365 -> ip ≈ 22.2 kA; catalog Icm = 75.6 kA >= ip -> PASS
        val circuit = CircuitEntity(id = 15, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val res = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), mapOf(15 to sizing(icu = 36.0, icm = 75.6)),
            mapOf("TX-01" to tx(15.16))
        )[15]!!

        assertEquals("PASS", res.status)
        assertFalse(res.note.contains("Making capacity"))
    }

    @Test
    fun testMakingCapacity_insufficientCatalogIcm_flagsWarning_byDefault() {
        // Catalog Icm = 17 kA < ip ≈ 22 kA -> WARNING (default mode, not FAIL)
        val circuit = CircuitEntity(id = 16, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val res = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), mapOf(16 to sizing(icu = 36.0, icm = 17.0)),
            mapOf("TX-01" to tx(15.16))
        )[16]!!

        assertEquals("WARNING", res.status)
        assertTrue(res.note.contains("Making capacity FAIL"))
        assertTrue(res.note.contains("from catalog"))
    }

    @Test
    fun testMakingCapacity_insufficientIcm_fails_whenConfigured() {
        val circuit = CircuitEntity(id = 17, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val res = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), mapOf(17 to sizing(icu = 36.0, icm = 17.0)),
            mapOf("TX-01" to tx(15.16)), makingCheckFails = true
        )[17]!!

        assertEquals("FAIL", res.status)
    }

    @Test
    fun testMakingCapacity_estimatedAs2_2xIcu_whenNotInCatalog() {
        // No catalog Icm (0.0) -> estimated 2.2×36 = 79.2 kA >= ip -> PASS but flagged
        val circuit = CircuitEntity(id = 18, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val res = ShortCircuitEngine.calculate(
            listOf(circuit), emptyMap(), mapOf(18 to sizing(icu = 36.0, icm = 0.0)),
            mapOf("TX-01" to tx(15.16))
        )[18]!!

        assertEquals("PASS", res.status)
        assertTrue(res.note.contains("estimated as 2.2×Icu"))
    }

    // ------------------------------------------------------------------
    // H6 — Ze derived from the feeding transformer
    // ------------------------------------------------------------------

    @Test
    fun testDeriveZeFromSource_helper() {
        // 500 kVA / 4%: Zt = 0.04·400²/500000 = 0.0128 Ω; Isc3 ≈ 18.93 kA
        // Ze = 1.05·400/(√3·18.93) = 0.0128 Ω (physically = Zt), NOT the 0.01 Ω fallback
        val ze = ProtectionEngine.deriveZeFromSource(isc3Phase_kA = 18.93, un_V = 400.0)
        assertEquals(0.0128, ze, 0.0008)
        assertEquals(0.0, ProtectionEngine.deriveZeFromSource(0.0, 400.0), 1e-9)
    }

    @Test
    fun testCoordinatorAds_usesDerivedZe_withLabelledFallback() {
        val circuit = CircuitEntity(id = 19, circuitName = "MDB", loadKVA = 400.0,
            fedFrom = "Transformer", transformerId = "TX-01", length_m = 0.1)
        val sizings = mapOf(19 to sizing(inA = 630.0, r = 0.0, x = 0.0))

        // With transformer data: Ze = 0.0128 Ω (not 0.01) — calculatedZs starts at derived Ze
        val withTx = EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = listOf(circuit),
            sizingResults = sizings,
            transformerResults = mapOf("TX-01" to tx(18.93))
        )[19]!!
        assertEquals(0.0128, withTx.calculatedZs, 0.0008)
        assertFalse(withTx.warnings.any { it.contains("Ze assumed") })

        // Without transformer data: 0.01 Ω fallback, explicitly labelled
        val withoutTx = EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = listOf(circuit),
            sizingResults = sizings,
            transformerResults = mapOf("TX-OTHER" to tx(18.93))
        )[19]!!
        assertEquals(0.01, withoutTx.calculatedZs, 1e-9)
        assertTrue("Fallback Ze must be labelled", withoutTx.warnings.any { it.contains("Ze assumed 0.01") })
    }
}