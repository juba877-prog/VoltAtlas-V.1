package com.example.ui

import com.example.data.CircuitEntity
import com.example.engine.EarthingInput
import com.example.engine.EarthingEngine
import com.example.engine.TransformerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 1 regression tests — P0-9 (U0 basis), P0-10 (main breaker basis),
 * P0-11 (system voltage basis).
 *
 * Audited defects (C-01/C-02/C-03):
 *  - `u0_V` was never passed → the engine's hardcoded 230 V drove Zs_max for every
 *    project (wrong Ω for 380/415/480 V systems);
 *  - `systemVoltage_V = 400.0` was hardcoded → FLC and downstream numbers wrong on
 *    every non-400 V project;
 *  - `mainBreaker = transformerResults.values.firstOrNull()?.flc ?: 630.0` → map-order
 *    first transformer in multi-tx projects, silent 630 A with none, and either value
 *    drove the displayed Zs_max / Loop Check verdict.
 */
class EarthingInputBasisTest {

    private fun txResult(voltage: Double, flc: Double) = TransformerResult(
        connectedLoad = 0.0,
        circuitsAssigned = 0,
        realPower = 0.0,
        demandLoad = 0.0,
        diversifiedLoad = 0.0,
        futureLoad = 0.0,
        designLoad = 0.0,
        requiredKVA = 0.0,
        recommendedSize = 0.0,
        selectedSize = 0.0,
        loadingPct = 0.0,
        flc = flc,
        zt = 0.0,
        rt = 0.0,
        xt = 0.0,
        isc3Phase = 0.0,
        isc1Phase = 0.0,
        status = "OK",
        z0 = 0.0,
        vectorGroup = "Dyn11",
        voltage = voltage
    )

    private fun circuit(
        id: Int = 1,
        voltageV: Int = 400,
        phases: String = "3P+N",
        projectId: String = "proj_earthing_basis"
    ) = CircuitEntity(
        id = id,
        projectId = projectId,
        circuitName = "C$id",
        fedFrom = "Transformer",
        voltageV = voltageV,
        phases = phases
    )

    // =========================================================================
    // P0-9 — U0 derives from the project voltage (engine unchanged, caller passes)
    // =========================================================================

    @Test
    fun p0_9_u0DerivesFromProjectVoltage380_415_480() {
        for ((lineV, expectedU0) in listOf(380 to 220, 400 to 230, 415 to 240, 480 to 277)) {
            val basis = EarthingInputBasis.compute(
                transformerResults = mapOf("TX-01" to txResult(voltage = lineV.toDouble(), flc = 1000.0)),
                circuits = listOf(circuit(voltageV = lineV)),
                defaultSystemVoltage_V = 400
            )
            assertEquals("U0 for a $lineV V system", expectedU0.toDouble(), basis.u0_V, 0.0)
            assertTrue("U0 must be passed explicitly (>0)", basis.u0_V > 0.0)
            assertEquals(lineV.toDouble(), basis.systemVoltage_V, 0.0)
        }
    }

    @Test
    fun p0_9_zsBasisFollowsProjectU0_notForced230() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf("TX-01" to txResult(voltage = 415.0, flc = 1394.4)),
            circuits = listOf(circuit(voltageV = 415)),
            defaultSystemVoltage_V = 400
        )
        assertEquals("415 V ⇒ U0 = 240 V", 240.0, basis.u0_V, 0.0)

        fun zsMaxWith(u0: Double, inMain: Double): Double = EarthingEngine.calculate(
            EarthingInput(
                faultCurrent_kA = 5.0,
                mainBreakerRating_A = inMain,
                u0_V = u0
            ),
            2.0
        ).zsMax_ohm

        val viaBasis = zsMaxWith(basis.u0_V, 630.0)
        val explicit240 = zsMaxWith(240.0, 630.0)
        val oldForced230 = zsMaxWith(230.0, 630.0)

        assertEquals("basis-driven Zs_max must equal the explicit-240 V calculation", explicit240, viaBasis, 0.00001)
        assertTrue(
            "Zs_max must differ from the old forced-230 V basis (was $oldForced230)",
            Math.abs(viaBasis - oldForced230) > 0.0001
        )
        // Zs_max = 0.95·U0 / Ia — scales linearly with U0: 240/230 ratio must hold.
        assertEquals(240.0 / 230.0, viaBasis / oldForced230, 0.001)
    }

    @Test
    fun p0_9_phaseNeutralMapIsExact() {
        assertEquals(220, EarthingInputBasis.phaseNeutralVoltageOf(380))
        assertEquals(230, EarthingInputBasis.phaseNeutralVoltageOf(400))
        assertEquals(240, EarthingInputBasis.phaseNeutralVoltageOf(415))
        assertEquals(277, EarthingInputBasis.phaseNeutralVoltageOf(480))
        // Non-standard line voltage ⇒ U0 ≈ UL/√3 (truncated, same as circuit creation).
        assertEquals((440 / 1.732).toInt(), EarthingInputBasis.phaseNeutralVoltageOf(440))
    }

    // =========================================================================
    // P0-11 — system voltage is project-derived, never hardcoded 400 V
    // =========================================================================

    @Test
    fun p0_11_systemVoltageDerivesFromTransformer415() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf("TX-01" to txResult(voltage = 415.0, flc = 1394.4)),
            circuits = listOf(circuit(voltageV = 415)),
            defaultSystemVoltage_V = 400
        )
        assertEquals("stored transformer secondary voltage must win over the 400 default", 415.0, basis.systemVoltage_V, 0.0)

        // The engine's FLC now uses the project voltage: 1000 kVA / (√3·415) ≈ 1391.21 A
        // (the old hardcoded 400 V produced 1443.38 A — a ~3.7 % fabrication).
        val result = EarthingEngine.calculate(
            EarthingInput(
                totalConnectedKVA = 1000.0,
                systemVoltage_V = basis.systemVoltage_V,
                faultCurrent_kA = 5.0
            ),
            2.0
        )
        assertEquals("FLC must be computed on 415 V", 1391.21, result.fullLoadCurrent_A, 0.01)
        assertTrue(
            "must not be the old 400 V value (1443.38 A)",
            Math.abs(result.fullLoadCurrent_A - 1443.38) > 1.0
        )
    }

    @Test
    fun p0_11_systemVoltageFallsBackToCircuitThenUserDefault() {
        // No transformer ⇒ unique 3-phase circuit voltage.
        val circuitBasis = EarthingInputBasis.compute(
            transformerResults = emptyMap(),
            circuits = listOf(circuit(id = 1, voltageV = 480), circuit(id = 2, voltageV = 230, phases = "1P+N")),
            defaultSystemVoltage_V = 400
        )
        assertEquals(480.0, circuitBasis.systemVoltage_V, 0.0)
        assertEquals(277.0, circuitBasis.u0_V, 0.0)

        // No transformer, no circuit data ⇒ the user's explicit default preference.
        val defaultBasis = EarthingInputBasis.compute(
            transformerResults = emptyMap(),
            circuits = emptyList(),
            defaultSystemVoltage_V = 415
        )
        assertEquals(415.0, defaultBasis.systemVoltage_V, 0.0)
        assertEquals(240.0, defaultBasis.u0_V, 0.0)
    }

    @Test
    fun p0_11_ambiguousVoltagesSurfaceNote_notSilentChoice() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf(
                "TX-01" to txResult(voltage = 400.0, flc = 1000.0),
                "TX-02" to txResult(voltage = 415.0, flc = 1000.0)
            ),
            circuits = listOf(circuit(id = 1, voltageV = 415), circuit(id = 2, voltageV = 415)),
            defaultSystemVoltage_V = 400
        )
        assertEquals("unique circuit basis resolves transformer disagreement", 415.0, basis.systemVoltage_V, 0.0)
        assertTrue(
            "ambiguity must be disclosed",
            basis.notes.any { it.contains("System voltage: AMBIGUOUS") }
        )
    }

    // =========================================================================
    // P0-10 — main breaker basis: unique only, else DATA MISSING (no silent 630)
    // =========================================================================

    @Test
    fun p0_10_emptyTransformerProjectIsDataMissing_not630() {
        val basis = EarthingInputBasis.compute(
            transformerResults = emptyMap(),
            circuits = listOf(circuit()),
            defaultSystemVoltage_V = 400
        )
        assertNull("no transformer ⇒ no main-breaker basis", basis.mainBreakerRating_A)
        assertFalse(basis.loopVerdictAvailable)
        assertTrue(
            "DATA MISSING must be disclosed",
            basis.notes.any { it.contains("Main breaker: DATA MISSING") }
        )

        // The VM passes 0.0 (engine's internal 630 fallback exists but never drives a verdict).
        assertEquals(0.0, basis.mainBreakerRating_A ?: 0.0, 0.0)
        val finalized = EarthingInputBasis.applyToResult(
            base = EarthingEngine.calculate(EarthingInput(faultCurrent_kA = 5.0, mainBreakerRating_A = 0.0), 2.0),
            basis = basis,
            autoDesign = null
        )
        assertEquals("displayed verdict must be DATA MISSING", "DATA MISSING", finalized.loopCheck)
        assertFalse("compliance cannot be claimed without a loop basis", finalized.overallCompliant)
        assertTrue(finalized.status.contains("DATA MISSING"))
        assertTrue(
            "the engine's assumed 630 A must be explicitly disclosed",
            finalized.messages.any { it.contains("630 A") && it.contains("DATA MISSING") }
        )
    }

    @Test
    fun p0_10_multipleTransformersAreAmbiguous_notMapOrderFirst() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf(
                // Deliberately different FLCs: the old code took firstOrNull() by map order.
                "TX-A" to txResult(voltage = 400.0, flc = 800.0),
                "TX-B" to txResult(voltage = 400.0, flc = 1250.0)
            ),
            circuits = listOf(circuit()),
            defaultSystemVoltage_V = 400
        )
        assertNull("differing FLCs have no unique main device", basis.mainBreakerRating_A)
        assertFalse(basis.loopVerdictAvailable)
        assertTrue(
            basis.notes.any { it.contains("Main breaker: DATA MISSING") && it.contains("transformers") }
        )

        val finalized = EarthingInputBasis.applyToResult(
            base = EarthingEngine.calculate(
                EarthingInput(faultCurrent_kA = 5.0, mainBreakerRating_A = basis.mainBreakerRating_A ?: 0.0),
                2.0
            ),
            basis = basis,
            autoDesign = null
        )
        assertEquals("DATA MISSING", finalized.loopCheck)
        assertFalse(finalized.overallCompliant)
    }

    @Test
    fun p0_10_multipleTransformersWithIdenticalFlcShareOneBasis() {
        // Two parallel transformers of the same rating: the DISTINCT value is unique and
        // therefore defensible (unlike the old map-order pick of differing units).
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf(
                "TX-A" to txResult(voltage = 400.0, flc = 1000.0),
                "TX-B" to txResult(voltage = 400.0, flc = 1000.0)
            ),
            circuits = listOf(circuit()),
            defaultSystemVoltage_V = 400
        )
        assertEquals(1000.0, basis.mainBreakerRating_A!!, 0.0)
        assertTrue(basis.loopVerdictAvailable)
    }

    @Test
    fun p0_10_singleTransformerUsesItsFlcAndKeepsVerdict() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf("TX-01" to txResult(voltage = 415.0, flc = 697.0)),
            circuits = listOf(circuit(voltageV = 415)),
            defaultSystemVoltage_V = 400
        )
        assertEquals(697.0, basis.mainBreakerRating_A!!, 0.0)
        assertTrue(basis.loopVerdictAvailable)
        assertTrue(basis.notes.any { it.contains("Main breaker basis") })

        val finalized = EarthingInputBasis.applyToResult(
            base = EarthingEngine.calculate(
                EarthingInput(faultCurrent_kA = 5.0, mainBreakerRating_A = basis.mainBreakerRating_A, u0_V = basis.u0_V),
                2.0
            ),
            basis = basis,
            autoDesign = null
        )
        assertTrue("unique basis ⇒ engine verdict stands", finalized.loopCheck == "PASS" || finalized.loopCheck == "FAIL")
        assertNotNull(finalized.status)
    }

    // =========================================================================
    // Integrated basis → input → result (all three at once)
    // =========================================================================

    @Test
    fun integrated_basisDrivesTheExactEarthingInputTheVmPasses() {
        val basis = EarthingInputBasis.compute(
            transformerResults = mapOf("TX-01" to txResult(voltage = 415.0, flc = 697.0)),
            circuits = listOf(circuit(voltageV = 415)),
            defaultSystemVoltage_V = 400
        )
        val input = EarthingInput(
            faultCurrent_kA = 25.0,
            systemVoltage_V = basis.systemVoltage_V,
            mainBreakerRating_A = basis.mainBreakerRating_A ?: 0.0,
            u0_V = basis.u0_V,
            totalConnectedKVA = 1000.0
        )
        assertEquals(415.0, input.systemVoltage_V, 0.0)
        assertEquals(240.0, input.u0_V, 0.0)
        assertEquals(697.0, input.mainBreakerRating_A, 0.0)

        val result = EarthingInputBasis.applyToResult(EarthingEngine.calculate(input, 2.0), basis, null)
        // basis notes are disclosed alongside the engine's own messages
        assertTrue(result.messages.size > 0)
        assertTrue(result.messages.any { it.contains("Main breaker basis") })
    }
}
