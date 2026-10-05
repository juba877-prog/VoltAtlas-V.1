package com.example

import com.example.data.Catalog
import com.example.engine.SizingResultStatus
import com.example.ui.screens.circuits.CircuitUiModelMapper
import com.example.ui.screens.circuits.CircuitUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * ECT-EE-06 §4 — TARGET REGRESSION (also §11 matrix case B).
 *
 * ============================================================================
 * WHAT THIS TEST IS FOR
 * ============================================================================
 *
 * This is the single assertion EE-06 exists to make true:
 *
 *     Isc > Icu  ⇒  the final circuit verdict is NOT PASS
 *
 * and it is written so that it **compiles against the unmodified implementation**
 * — it uses only the pre-existing one-argument
 * `CircuitUiModelMapper.resolveCircuitStatus(sizingResult)` signature and depends
 * only on [ProtectionVerdictFixture], which in turn uses only pre-existing engine
 * APIs. That is deliberate: §4 requires this test to FAIL on unmodified code, and a
 * test that cannot even compile there would prove nothing.
 *
 * ============================================================================
 * §4 PRECONDITIONS — ALL ASSERTED, IN ORDER
 * ============================================================================
 *
 * A green result must not be reachable by a fixture that simply produced no data,
 * so every §4 precondition is asserted from real production output first:
 *
 *  1. the breaker candidate remains selectable under normal current sizing,
 *  2. `Ib <= In`,
 *  3. Isc is authoritative and available (non-zero),
 *  4. `Icu < Isc`,
 *  5. the authoritative `ProtectionResult.finalStatus` really is `FAIL`.
 *
 * ============================================================================
 * OUTCOME ON THE UNMODIFIED CODE
 * ============================================================================
 *
 * Precondition 5 held (`ProtectionEngine` was always correct), while
 * `resolveCircuitStatus(sizingResult)` returned `PASS` — which is exactly the
 * failure this test reports. That state was recorded directly during the §3
 * characterization run at 10/05/2026 15:34:39 on before-SHA
 * `4da9e0341ac5b8c41530ef5ff592a80462e03197`, where the companion assertion
 * `assertEquals(CircuitUiStatus.PASS, resolveCircuitStatus(sizing))` PASSED.
 * This test asserts the exact logical negation of that recorded observation.
 *
 * ============================================================================
 * WHAT THIS TEST DOES **NOT** DO
 * ============================================================================
 *
 * It does not assert the FAIL label itself, because that requires the post-fix
 * two-argument signature. The FAIL-at-every-consumer-boundary assertions live in
 * `ProtectionVerdictCharacterizationTest` (§11-B/C, §12).
 */
class ProtectionVerdictTargetTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        @BeforeClass
        @JvmStatic
        fun setUp() = ProtectionVerdictFixture.install()
    }

    private fun runPipeline() = ProtectionVerdictFixture.runPipeline("TC-CABLE-003", 630.0)

    // ==================================================================
    // §4 — TARGET: a circuit with Isc > Icu must not present as PASS.
    // ==================================================================
    @Test
    fun target_IscAboveIcu_finalCircuitVerdictIsNeverPass() {
        val p = runPipeline()

        // ── §4 precondition 1: the candidate is still selectable normally ────
        val candidates = Catalog.breakers
            .filter { it.type.equals("MCB", ignoreCase = true) && it.rating_A == p.sizing.deviceSize_A }
        assertTrue(
            "§4 precondition: breaker candidate remains selectable under normal sizing",
            candidates.isNotEmpty()
        )
        assertEquals(
            "§4 precondition: the candidate is the one normal current sizing picks",
            "MCB", p.sizing.deviceType
        )

        // ── §4 precondition 2: Ib <= In ─────────────────────────────────────
        assertTrue(
            "§4 precondition: Ib <= In",
            p.sizing.Ib_A <= p.sizing.deviceSize_A
        )

        // ── §4 precondition 3: Isc authoritative and available ──────────────
        assertTrue(
            "§4 precondition: Isc is authoritative and available",
            p.sc.iscKA > 0.0
        )

        // ── §4 precondition 4: Icu < Isc ────────────────────────────────────
        assertTrue(
            "§4 precondition: Icu (${p.sizing.deviceBreakingKA} kA) < Isc (${p.sc.iscKA} kA)",
            p.sizing.deviceBreakingKA < p.sc.iscKA
        )

        // ── §4 precondition 5: the authoritative protection verdict is FAIL ──
        assertEquals(
            "§4 precondition: ProtectionResult.finalStatus",
            "FAIL",
            p.protection.finalStatus
        )

        // ── §4 TARGET ───────────────────────────────────────────────────────
        assertNotEquals(
            "§4 TARGET FAILED: the circuit presents PASS although Isc > Icu " +
                "(Isc=${p.sc.iscKA} kA, Icu=${p.sizing.deviceBreakingKA} kA, " +
                "ProtectionResult.finalStatus=${p.protection.finalStatus})",
            CircuitUiStatus.PASS,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing)
        )
    }

    // ==================================================================
    // §11-F NORMAL-PASS REGRESSION — must stay green after the fix.
    //
    // Sizing genuinely passes on the false-PASS fixture, so the D5 fix cannot be
    // "solved" by turning every circuit into a failure. Uses only the
    // sizing-side domains, which EE-06 does not touch.
    // ==================================================================
    @Test
    fun normalSizingCase_stillPassesItsOwnDomains() {
        val p = runPipeline()

        assertEquals(SizingResultStatus.PASS, p.sizing.resultStatus)
        assertEquals("✓ PASS", p.sizing.status)
        assertTrue("ampacity domain passes", p.sizing.isAmpacityOk)
        assertTrue("voltage-drop domain passes", p.sizing.isVoltageDropOk)
        assertTrue(
            "VD within the circuit limit (maxVDpct is a fraction)",
            p.sizing.cumulativeVDpct <= p.circuit.maxVDpct * 100.0
        )
        assertTrue("Ib <= In", p.sizing.Ib_A <= p.sizing.deviceSize_A)
        assertTrue("overload domain passes", p.protection.overloadStatus == "PASS")
    }

    // ==================================================================
    // §11-G CANDIDATE-SELECTION NON-REGRESSION (D5) — must stay green.
    //
    // D5 = PLAN DEFAULT (A): no auto-upsize. The breaker must remain the one
    // normal current sizing selected, even though its Icu is below the
    // available fault level.
    // ==================================================================
    @Test
    fun candidateSelection_isUnchanged_byTheIcuVerdict() {
        val p = runPipeline()

        val candidates = Catalog.breakers
            .filter { it.type.equals("MCB", ignoreCase = true) && it.rating_A >= p.sizing.deviceSize_A }
            .sortedBy { it.rating_A }
        assertTrue("shipped catalogue offers a candidate", candidates.isNotEmpty())
        assertEquals(
            "the engine selected the smallest In >= deviceSizeA — Icu plays no part in selection",
            candidates.first().rating_A, p.sizing.deviceSize_A, 0.0
        )
        assertEquals("MCB", p.sizing.deviceType)
        assertEquals(16.0, p.sizing.deviceSize_A, 0.0)
        assertEquals(
            "the breaker is NOT silently replaced by a larger one",
            10.0, p.sizing.deviceBreakingKA, 1e-9
        )
        assertTrue("Ib <= In", p.sizing.Ib_A <= p.sizing.deviceSize_A)
        assertTrue("the scenario really is under-rated (Isc > Icu)", p.sc.iscKA > p.sizing.deviceBreakingKA)
    }
}
