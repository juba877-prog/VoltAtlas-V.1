package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * ECT-EE-02 — short-circuit fault-location / topology semantics (owner decision D2).
 *
 * ============================================================================
 * MANDATORY 3-NODE FIXTURE (Section 6 of the ECT)
 * ============================================================================
 *
 *     Transformer  (TX-01)
 *         |
 *      Board A          <- circuit [A], feeder TX -> A, length 10 m
 *         |
 *      Breaker Q1       <- Q1 is the protective device of circuit [B]:
 *         |                  it is selected for [B]'s Ib and protects [B]'s
 *         |                  cable, so it is installed at [B]'s ORIGIN,
 *         |                  i.e. on the outgoing way of Board A.
 *      Cable A->B        <- circuit [B]'s own feeder, length 200 m
 *         |
 *      Board B          <- circuit [B], far end of the feeder
 *
 * TOPOLOGY CONTRACT (proved from source, not assumed):
 *
 *  * A `CircuitEntity` is a feeder together with the board at its downstream
 *    end — `HierarchyResolver` names the field `board`
 *    (`HierarchyNode(val board: CircuitEntity, ...)`).
 *  * Its parent is resolved from `fedFrom` by name matching
 *    (`EngineeringCalculationCoordinator.kt:547-569`); a source keyword
 *    (`transformer|ats|generator|grid|source|main|""`) makes it a root.
 *  * Its breaker sits at the cable's ORIGIN (upstream end), because the
 *    device is chosen for this circuit's own `Ib_A` and protects this
 *    circuit's cable.
 *  * `HierarchyResolver.resolveAncestors` returns `[self, parent, ...]`
 *    — the circuit itself is the FIRST element.
 *
 * Therefore, for circuit [B]:
 *
 *     NEAR-END (Q1's installation point, Board A busbar)
 *         = TX + feeder A       -- feeder A->B is DOWNSTREAM of Q1, excluded
 *     FAR-END (Board B)
 *         = TX + feeder A + feeder B
 *
 * ============================================================================
 * INDEPENDENT HAND CALCULATION (never obtained by calling the engine)
 * ============================================================================
 *
 * Transformer (LV): Rt = 2.0 mOhm, Xt = 10.0 mOhm, infinite grid (S_kQ = 0).
 * Feeder A : r20 = 0.10 ohm/km over  10 m, 1 run -> R =  1.0 mOhm, X =  0.8 mOhm
 * Feeder B : r20 = 0.50 ohm/km over 200 m, 1 run -> R = 100.0 mOhm, X = 16.0 mOhm
 *
 * c = 1.05, U0 = 400 V (the value the engine uses, `ShortCircuitEngine.kt:85`)
 *
 *   near : R =   3.0 mOhm, X =  10.8 mOhm -> Z =  11.2090 mOhm
 *          Isc = 1.05*400 / (sqrt(3) * 0.0112090) = 21.632 kA
 *   far  : R = 103.0 mOhm, X =  26.8 mOhm -> Z = 106.4295 mOhm
 *          Isc = 1.05*400 / (sqrt(3) * 0.1064295) =  2.278 kA
 *
 * The two fault points differ by ~9.5x, so no test here can pass "because both
 * paths happen to produce similar values".
 */
class ShortCircuitFaultLocationTest {

    /**
     * ENG-30 style fixture: CHECK 5 calls `ProtectionEngine.calculateAds`, which is
     * DATA MISSING whenever `Catalog.dataSheet` is empty. Other suites populate it
     * as a side effect, so installing it unconditionally removes this test's
     * dependence on whichever class ran before it (Gradle shares one JVM across
     * classes, so ordering decides). Short-circuit tests below do not use it.
     */
    private var originalDataSheet: List<DataSheetEntry> = emptyList()

    @Before
    fun installDataSheetFixture() {
        originalDataSheet = Catalog.dataSheet
        // Rows copied verbatim from the committed fixtures (ProtectionEngineTest:35 mm²,
        // ProtectionAdsCatalogMissingAndTempFactorTest:16 mm²) so no new resistance figure
        // is introduced here. Every size this class resolves must have a row, otherwise
        // calculateAds returns DATA MISSING with r2_ohm_km == null.
        Catalog.dataSheet = listOf(
            DataSheetEntry(size = 35.0, R_Cu20 = 0.524, X = 0.08, R_Al20 = 0.868, R_Cu_70_est = 0.629, R_Al_70_est = 1.042),
            DataSheetEntry(size = 16.0, R_Cu20 = 1.15, X = 0.082, R_Al20 = 1.91, R_Cu_70_est = 1.38, R_Al_70_est = 2.29)
        )
    }

    @After
    fun restoreDataSheetFixture() {
        Catalog.dataSheet = originalDataSheet
    }

    // ---------------------------------------------------------------------
    // Fixture constants — the single source of truth for this file.
    // ---------------------------------------------------------------------
    private val txRt = 2.0    // mOhm
    private val txXt = 10.0   // mOhm
    private val c = 1.05      // ShortCircuitEngine.kt:85
    private val u0 = 400.0    // V

    private val lenA = 10.0    // m
    private val lenB = 200.0   // m
    private val r20A = 0.10    // ohm/km
    private val xA = 0.08      // ohm/km
    private val r20B = 0.50    // ohm/km
    private val xB = 0.08      // ohm/km
    private val parallel = 1

    /** Impedance of a cable segment in mOhm, exactly as IEC 60909-0: R at 20 degC. */
    private fun segMOhm(r20OhmPerKm: Double, xOhmPerKm: Double, lenM: Double) =
        Pair(r20OhmPerKm * lenM / (1000.0 * parallel) * 1000.0,
             xOhmPerKm * lenM / (1000.0 * parallel) * 1000.0)

    private val segA = segMOhm(r20A, xA, lenA)   // (1.0, 0.8) mOhm
    private val segB = segMOhm(r20B, xB, lenB)   // (100.0, 16.0) mOhm

    /** NEAR-END: at Board A busbar = Q1's installation point. Feeder A->B excluded. */
    private val rNear = txRt + segA.first
    private val xNear = txXt + segA.second

    /** FAR-END: at Board B. Feeder A->B participates. */
    private val rFar = rNear + segB.first
    private val xFar = xNear + segB.second

    /** Isc in kA from an explicitly enumerated (R, X) pair in mOhm. */
    private fun isc(rMOhm: Double, xMOhm: Double): Double {
        val zOhm = sqrt(rMOhm * rMOhm + xMOhm * xMOhm) / 1000.0
        return (c * u0) / (sqrt(3.0) * zOhm) / 1000.0
    }

    private val iscNear = isc(rNear, xNear)   // ~21.632 kA
    private val iscFar = isc(rFar, xFar)      // ~2.278 kA

    /** IEC 60909 peak factor kappa from an explicitly enumerated (R, X) pair. */
    private fun kappa(rMOhm: Double, xMOhm: Double) =
        1.02 + 0.98 * exp(-3.0 * (rMOhm / xMOhm))

    private val ipNear = sqrt(2.0) * kappa(rNear, xNear) * iscNear   // ~44.23 kA
    private val ipFar = sqrt(2.0) * kappa(rFar, xFar) * iscFar       // ~3.29 kA

    // Device ratings chosen so that near-end and far-end give OPPOSITE verdicts.
    private val icuQ1 = 10.0    // kA  — passes against the far end, fails at the near end
    private val icmQ1 = 20.0    // kA  — catalog making capacity, passes against ipFar only
    // 35 mm² is a size the committed catalog fixtures already carry, so CHECK 5 needs no new row.
    private val cableArea = 35.0 // mm2 — Iw = 143 * 35 / sqrt(0.1) / 1000 = 15.83 kA
    private val iwQ1 = 143.0 * cableArea / sqrt(0.1) / 1000.0

    private fun txResult() = TransformerResult(
        connectedLoad = 500.0, circuitsAssigned = 2, realPower = 400.0, demandLoad = 400.0,
        diversifiedLoad = 400.0, futureLoad = 400.0, designLoad = 400.0, requiredKVA = 500.0,
        recommendedSize = 630.0, selectedSize = 630.0, loadingPct = 79.4, flc = 909.3,
        zt = 10.19804, rt = txRt, xt = txXt,
        isc3Phase = isc(txRt, txXt), isc1Phase = isc(txRt, txXt) * 0.866,
        status = "VERIFIED", z0 = 9.14, vectorGroup = "Dyn11", voltage = u0
    )

    private fun boardA() = CircuitEntity(
        id = 1, circuitName = "Board A", loadKVA = 400.0,
        fedFrom = "Transformer", transformerId = "TX-01",
        length_m = lenA, parallelRuns = parallel
    )

    private fun boardB() = CircuitEntity(
        id = 2, circuitName = "Board B", loadKVA = 50.0,
        fedFrom = "Board A", transformerId = "TX-01",
        length_m = lenB, parallelRuns = parallel
    )

    private fun sizingA() = SizingResult(
        Ib_A = 577.0, deviceSize_A = 630.0, deviceType = "MCCB", cTotalDerating = 1.0,
        iRequired_perCable_A = 577.0, productCode = "CX-T95", cableSize_mm2 = "95 mm²",
        ratedI_table_A = 160.0, capacity_derated_Iz_A = 160.0, utilizationPct = 91.0,
        neutral_mm2 = "50 mm²", earthCPC_mm2 = "50 mm²", voltDrop_V = 0.5, vdPct = 0.12,
        cumulativeVDpct = 0.12, status = "✓ PASS", failReason = "", cableMaterial = "Cu",
        insulation = "XLPE", kFactor = 143.0, deviceBreakingKA = 50.0, deviceMakingKA = 0.0,
        cableOD_mm = 25.0, rUsed = 0.149, xUsed = xA, clearingTime_s = 0.1,
        resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true,
        primaryFailureCode = null, isDeviceFromCatalog = true, conductorAreaMm2 = 95.0,
        r20 = r20A
    )

    private fun sizingB() = SizingResult(
        Ib_A = 72.0, deviceSize_A = 80.0, deviceType = "MCCB", cTotalDerating = 1.0,
        iRequired_perCable_A = 72.0, productCode = "CX-T35", cableSize_mm2 = "35 mm²",
        ratedI_table_A = 80.0, capacity_derated_Iz_A = 80.0, utilizationPct = 55.0,
        neutral_mm2 = "35 mm²", earthCPC_mm2 = "35 mm²", voltDrop_V = 2.0, vdPct = 0.5,
        cumulativeVDpct = 0.6, status = "✓ PASS", failReason = "", cableMaterial = "Cu",
        insulation = "XLPE", kFactor = 143.0, deviceBreakingKA = icuQ1,
        deviceMakingKA = icmQ1, cableOD_mm = 20.0, rUsed = 0.6, xUsed = xB,
        clearingTime_s = 0.1,
        resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true,
        primaryFailureCode = null, isDeviceFromCatalog = true, conductorAreaMm2 = cableArea,
        r20 = r20B
    )

    private fun parents() = mapOf(2 to 1)

    private fun run() = ShortCircuitEngine.calculate(
        resolvedCircuits = listOf(boardA(), boardB()),
        circuitParents = parents(),
        sizingResults = mapOf(1 to sizingA(), 2 to sizingB()),
        transformerResults = mapOf("TX-01" to txResult())
    )

    // =====================================================================
    // SECTION 4 — CHARACTERIZATION.
    //
    // On the UNMODIFIED code this ran as
    // `characterization_singleFaultPointToday_isTheFarEndFaultAtBoardB` and PASSED at
    // 2026-10-05 12:33 (before-SHA adb2bac6 / 033949f2). It pinned the then-SINGLE fault
    // point: `iscKA` / `zTotalMOhm` / `rMOhm` / `xMOhm` all carried the FAR-END loop
    // (TX + feeder A + feeder B = 106.43 mΩ / 2.278 kA), so the prospective fault current
    // at Q1's own installation point was not obtainable from `ShortCircuitResult` at all —
    // which is exactly why an upstream breaker was rated against an attenuated current.
    //
    // After the fix the far-end quantity lives in its own field `iscFarKA`, so the SAME
    // fixture, the SAME independent hand calculation and the SAME tolerances are re-pointed
    // to it. Nothing here is weakened: the test additionally asserts the near-end value and
    // now proves the two fault points are exposed as two separate quantities.
    // =====================================================================
    @Test
    fun characterization_threeNodeTopology_exposesBothFaultPointsSeparately() {
        val res = run()[2]
        assertNotNull("circuit B must produce a short-circuit result", res)

        // ── FAR-END fault at Board B: TX + feeder A + feeder B (feeder participates) ──
        assertEquals(
            "FAR-END fault at Board B (feeder A->B impedance participates)",
            iscFar, res!!.iscFarKA, 0.05
        )
        // The far-end loop is what 10.58-style references pin; Isc is a bijection of Z for
        // fixed c and U, so recovering Z from the reported Isc states the loop impedance
        // explicitly: TX + feeder A + feeder B = 106.43 mΩ.
        // Tolerance: `iscFarKA` is reported ROUNDED to 2 dp (2.28 kA vs 2.27840 exact),
        // and back-solving magnifies that 0.0016 kA quantisation to ~0.10 mΩ. The exact
        // impedance composition is asserted without rounding on the near-end side, in
        // `target_nearEndBreakerFault_excludesTheDownstreamFeederImpedance`.
        val zFarReported = (c * u0) / (sqrt(3.0) * res.iscFarKA)
        assertEquals(
            "FAR-END loop impedance recovered from iscFarKA must equal TX + feeder A + feeder B",
            sqrt(rFar * rFar + xFar * xFar), zFarReported, 0.15
        )

        // ── NEAR-END fault at Board A (Q1's installation point): TX + feeder A only ────
        assertEquals(
            "NEAR-END fault at Q1's installation point (feeder A->B excluded)",
            iscNear, res.iscKA, 0.05
        )

        // ── The two must be exposed separately and must be materially different ────────
        assertTrue(
            "The two fault points must be materially different: near=${iscNear} kA far=${iscFar} kA",
            iscNear > iscFar * 5.0
        )
        assertTrue(
            "Near-end and far-end must be reported as DIFFERENT values — equality means " +
                "the feeder was folded back into the near-end fault (the EE-02 defect)",
            res.iscKA > res.iscFarKA + 1.0
        )

        // ── Quantities that are fault-point INDEPENDENT must be unchanged by EE-02 ─────
        assertEquals("Cable withstand capability Iw = k*S/sqrt(t)/1000", iwQ1, res.iwKA, 0.02)
        assertEquals("Q1's breaking capacity is carried through unchanged", icuQ1, res.deviceKA, 0.001)
        assertEquals("Source must resolve to TX-01 through Board A", "TX-01", res.sourceName)
    }

    // =====================================================================
    // SECTION 5 — TARGET: must FAIL on the UNMODIFIED code.
    //
    // CHECK 1 — the prospective RMS current seen by Q1 is the fault current at
    // Q1's INSTALLATION POINT (Board A). Feeder A->B lies DOWNSTREAM of Q1 and
    // must not reduce that basis.
    // =====================================================================
    @Test
    fun target_nearEndBreakerFault_excludesTheDownstreamFeederImpedance() {
        val res = run()[2]
        assertNotNull(res)

        val nearZMOhm = sqrt(rNear * rNear + xNear * xNear)
        assertEquals(
            "Q1's prospective fault basis must be the NEAR-END loop: TX + feeder A ONLY " +
                "(feeder A->B is downstream of Q1 and must be excluded)",
            nearZMOhm, res!!.zTotalMOhm, 0.02
        )
        assertEquals(
            "Q1's prospective RMS fault current must be evaluated at Board A (near-end)",
            iscNear, res.iscKA, 0.05
        )
        assertEquals("Near-end resistance must exclude feeder B", rNear, res.rMOhm, 0.02)
        assertEquals("Near-end reactance must exclude feeder B", xNear, res.xMOhm, 0.02)

        // Independent, non-engine proof that the excluded segment really is feeder A->B:
        // the reported loop impedance must be exactly one segment shorter than the far end.
        assertEquals(
            "The near-end loop must differ from the far end by exactly feeder A->B",
            segB.first, rFar - res.rMOhm, 0.001
        )
        assertTrue(
            "Near-end fault current must EXCEED the far-end value " +
                "(adding feeder A->B can only attenuate)",
            res.iscKA > iscFar
        )
    }

    // =====================================================================
    // CHECK 1 (verdict) — Icu must be judged against the near-end fault.
    //
    // Icu = 10 kA: it covers the far-end fault (2.28 kA) but NOT the fault at
    // Q1's own installation point (21.63 kA). The correct verdict is FAIL.
    // =====================================================================
    @Test
    fun target_breakerIcuIsJudgedAgainstTheNearEndFaultPoint() {
        val res = run()[2]
        assertNotNull(res)
        assertTrue("fixture must be discriminating: Icu sits between the two fault points",
            iscFar < icuQ1 && icuQ1 < iscNear)
        assertEquals(
            "Q1 (Icu = 10 kA) cannot interrupt the 21.63 kA fault at its own installation point",
            "FAIL", res!!.deviceCheck
        )
        assertEquals("Row status must follow the breaking-capacity verdict", "FAIL", res.status)
    }

    // =====================================================================
    // CHECK 3 — cable thermal withstand Iw.
    //
    // Iw = 143 * 35 mm2 / sqrt(0.1 s) / 1000 = 15.83 kA is a CAPABILITY and is
    // fault-point independent. The current it must be compared against is the
    // maximum prospective fault current that can flow in that cable, which is
    // the current at its ORIGIN (near-end). Clearing time is NOT touched —
    // that is EE-03 and out of scope.
    // =====================================================================
    @Test
    fun target_cableWithstandIsJudgedAgainstTheNearEndFaultPoint() {
        val res = run()[2]
        assertNotNull(res)
        assertEquals("Iw must be unchanged: clearing-time assumptions are EE-03", iwQ1, res!!.iwKA, 0.02)
        assertTrue("fixture must be discriminating: Iw sits between the two fault points",
            iscFar < iwQ1 && iwQ1 < iscNear)
        assertEquals(
            "The cable must be shown as unable to withstand the near-end prospective current",
            "FAIL", res.cableCheck
        )
    }

    // =====================================================================
    // CHECK 2 — making capacity: RMS and PEAK must stay distinct, and the peak
    // must be built from the NEAR-END fault (same point as Icu).
    //
    //   ip = sqrt(2) * kappa(R/X) * Isc     (RMS -> peak, IEC 60909-0)
    //   kappa = 1.02 + 0.98 * exp(-3 R/X)   (ShortCircuitEngine.kt:167-168)
    //
    // Icm = 20 kA covers ipFar (3.29 kA) but not ipNear (44.23 kA).
    // Only topology/location semantics are asserted here; the peak equation
    // itself is deliberately NOT redesigned.
    // =====================================================================
    @Test
    fun target_makingPeakIpIsBuiltFromTheNearEndFaultAndRmsPeakStayDistinct() {
        val res = run()[2]
        assertNotNull(res)

        // RMS and peak remain distinct quantities (Icm is compared to ip, never to Isc).
        assertTrue("ip must remain a PEAK value and therefore exceed the RMS Isc",
            res!!.ipKA > res.iscKA)
        assertEquals(
            "ip must be sqrt(2) * kappa(near-end R/X) * near-end Isc",
            ipNear, res.ipKA, 0.10
        )
        assertTrue(
            "ip must NOT have been built from the far-end fault " +
                "(near=${ipNear} kA vs far=${ipFar} kA)",
            kotlin.math.abs(res.ipKA - ipFar) > 1.0
        )
        // The engine compares Icm to ip (ShortCircuitEngine.kt:211), not to Isc.
        assertTrue(
            "Icm = 20 kA cannot make the near-end peak of %.2f kA".format(ipNear),
            icmQ1 < ipNear
        )
        assertTrue(
            "The making-capacity shortfall must be surfaced in the note",
            res.note.contains("Making capacity FAIL")
        )
    }

    // =====================================================================
    // CHECK 5 — ADS / minimum-fault path.
    //
    // The ADS loop is modelled in `ProtectionEngine.calculateAds`, NOT in
    // `ShortCircuitEngine`, and its signature takes no `ShortCircuitResult` at
    // all (ProtectionEngine.kt:195-200). Its loop is built from the earthing
    // impedance plus this circuit's OWN conductor over `circuit.length_m`
    // (ProtectionEngine.kt:216), i.e. it already uses the DOWNSTREAM
    // impedance — the far-end/minimum-fault semantics are correct today and
    // this ECT does not alter them.
    // =====================================================================
    @Test
    fun check5_adsMinimumFaultLoopUsesTheDownstreamCableImpedance() {
        val circuit = boardB()
        val ads = ProtectionEngine.calculateAds(circuit, sizingB(), ze_ohm = 0.05, isZeResolved = true)
        assertNotNull(ads)
        assertFalse("Zs must not be NaN", ads.calculatedZs.isNaN())
        assertNotNull(
            "The circuit's own conductor resistance must resolve into the loop",
            ads.r2_ohm_km
        )
        assertTrue(
            "Zs must exceed Ze: the downstream feeder impedance participates in the " +
                "minimum-fault loop (Zs = Ze + cable)",
            ads.calculatedZs > 0.05
        )
    }

    // =====================================================================
    // CHECK 4 — FAR-END fault at Board B: the feeder impedance MUST participate.
    //
    // The far-end value is reported separately (`iscFarKA`) precisely because the
    // near-end basis must not contain it. This test proves the two are distinct
    // and that the far-end one is the hand-calculated TX + A + B loop.
    // =====================================================================
    @Test
    fun target_farEndFaultAtBoardB_includesTheFeederImpedance() {
        val res = run()[2]
        assertNotNull(res)

        assertEquals(
            "FAR-END fault at Board B must include feeder A->B (TX + feeder A + feeder B)",
            iscFar, res!!.iscFarKA, 0.05
        )
        assertTrue(
            "Adding feeder A->B can only attenuate, so the far end must sit BELOW the near end",
            res.iscFarKA < res.iscKA
        )
        assertTrue(
            "The far end must NOT collapse onto the near end — that is the EE-02 defect " +
                "(far=${res.iscFarKA} kA vs near=${res.iscKA} kA)",
            kotlin.math.abs(res.iscFarKA - iscNear) > 1.0
        )
        assertTrue("Far-end prospective current must remain a positive quantity", res.iscFarKA > 0.0)
    }

    // =====================================================================
    // Topology invariant — the same physical fault must be reported once.
    //
    // Board A's FAR-END fault (downstream end of feeder TX->A) and Board B's
    // NEAR-END fault (Q1's installation point, i.e. the Board A busbar) are the
    // SAME location, so they must be the same number. This is an independent,
    // cross-circuit proof that both fault points were placed correctly — it
    // cannot be satisfied by simply scaling every value.
    // =====================================================================
    @Test
    fun topologyInvariant_faultAtA_isFarEndOfAAndNearEndOfB() {
        val results = run()
        val a = results[1]
        val b = results[2]
        assertNotNull(a)
        assertNotNull(b)

        assertEquals(
            "The fault at the Board A busbar must be identical whether seen as A's far end " +
                "or as Q1's near end",
            a!!.iscFarKA, b!!.iscKA, 0.05
        )
        assertTrue(
            "A's own near end (transformer terminals) must be higher than the fault at Board A",
            a.iscKA > a.iscFarKA
        )
    }

    // =====================================================================
    // SECTION 10 — DISCRIMINATION: if the old behaviour is reintroduced, this
    // MUST fail.
    //
    // The old defect was "fold this circuit's own feeder into the near-end
    // breaker fault". Reintroducing it makes `iscKA` collapse onto `iscFarKA`
    // and flips every verdict below, because Icu (10 kA) was deliberately placed
    // BETWEEN the two fault points (2.278 kA far / 21.632 kA near).
    //
    // (The four `target_*` tests are themselves discriminating for the same
    // reason — they failed on the unmodified engine. This test states the
    // invariant directly so the intent survives even if a verdict changes.)
    // =====================================================================
    @Test
    fun discrimination_reintroducingTheFeederIntoTheNearEndFaultMustFail() {
        val res = run()[2]
        assertNotNull(res)

        assertTrue(
            "Near-end fault must strictly exceed the far-end fault — equality means feeder " +
                "A->B was folded back into the breaker's fault basis",
            res!!.iscKA > res.iscFarKA
        )
        assertTrue(
            "The gap between the two fault points must remain material " +
                "(near=${res.iscKA} kA, far=${res.iscFarKA} kA)",
            res.iscKA - res.iscFarKA > 5.0
        )

        // The verdict itself distinguishes the two implementations, because Icu was
        // chosen to sit between them: correct -> FAIL, old behaviour -> PASS.
        assertTrue(
            "Fixture must remain discriminating: Icu must sit between the two fault points",
            iscFar < icuQ1 && icuQ1 < iscNear
        )
        assertEquals(
            "Icu verdict must reflect the near-end fault",
            "FAIL", res.deviceCheck
        )
        assertEquals(
            "Iw verdict must reflect the near-end fault",
            "FAIL", res.cableCheck
        )
        assertEquals("Row status must follow those verdicts", "FAIL", res.status)
    }
}
